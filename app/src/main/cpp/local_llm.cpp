#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cstdint>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>
#include "llama.h"

// Exactly one app-private model handle. All native calls are serialized by the Java executor.
// No sockets, telemetry, model download or OCR logging.
namespace {
struct Engine {
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    const llama_vocab *vocab = nullptr;
    ~Engine() {
        if (context) llama_free(context);
        if (model) llama_model_free(model);
    }
};

void fail(JNIEnv* env, const char* msg) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls) env->ThrowNew(cls, msg);
}
std::string str(JNIEnv* env, jstring s) {
    if (!s) return "";
    const char *p = env->GetStringUTFChars(s, nullptr);
    if (!p) return "";
    std::string result(p);
    env->ReleaseStringUTFChars(s, p);
    return result;
}
jstring utf8(JNIEnv* env, const std::string& s) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(s.size()));
    if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(s.size()),
        reinterpret_cast<const jbyte*>(s.data()));
    jclass stringClass = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(stringClass, "<init>", "([BLjava/lang/String;)V");
    jstring encoding = env->NewStringUTF("UTF-8");
    auto* result = (jstring) env->NewObject(stringClass, ctor, bytes, encoding);
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(encoding);
    env->DeleteLocalRef(stringClass);
    return result;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_davidop_estudiaenelbus_NativeGguf_nativeLoadModel(
    JNIEnv* env, jclass, jstring jpath) {
    std::string path = str(env, jpath);
    if (path.empty()) { fail(env, "El archivo del modelo está vacío."); return 0; }
    auto engine = std::make_unique<Engine>();
    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    engine->model = llama_model_load_from_file(path.c_str(), mp);
    if (!engine->model) {
        fail(env, "No se pudo abrir el GGUF.");
        return 0;
    }
    engine->vocab = llama_model_get_vocab(engine->model);
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 4096;
    cp.n_batch = 256;
    cp.n_ubatch = 256;
    cp.n_threads = 4;
    cp.n_threads_batch = 4;
    cp.offload_kqv = false;
    engine->context = llama_init_from_model(engine->model, cp);
    if (!engine->context) {
        fail(env, "No hay memoria suficiente para ejecutar el modelo.");
        return 0;
    }
    return reinterpret_cast<jlong>(engine.release());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_davidop_estudiaenelbus_NativeGguf_nativeComplete(
    JNIEnv* env, jclass, jlong handle, jstring jprompt, jint requested) {
    auto* engine = reinterpret_cast<Engine*>(handle);
    if (!engine || !engine->context || !engine->vocab) {
        fail(env, "Primero debes importar un modelo.");
        return nullptr;
    }
    const std::string prompt = str(env, jprompt);
    int maxOutput = std::clamp((int)requested, 100, 800);
    if (prompt.size() > 13000) {
        fail(env, "El texto de entrada es demasiado largo para la IA local.");
        return nullptr;
    }
    int needed = llama_tokenize(engine->vocab, prompt.c_str(),
        (int)prompt.size(), nullptr, 0, true, true);
    if (needed >= 0 || -needed > 4096 - maxOutput - 32) {
        fail(env, "La entrada supera la ventana de contexto: usa menos texto.");
        return nullptr;
    }
    std::vector<llama_token> tokens((size_t)-needed);
    int count = llama_tokenize(engine->vocab, prompt.c_str(), (int)prompt.size(),
        tokens.data(), (int)tokens.size(), true, true);
    if (count <= 0) {
        fail(env, "El modelo no ha podido codificar el texto.");
        return nullptr;
    }
    tokens.resize((size_t)count);

    llama_memory_clear(llama_get_memory(engine->context), false);
    llama_batch batch = llama_batch_init(256, 0, 1);
    int pos = 0;
    bool failed = false;
    for (size_t start=0; start<tokens.size() && !failed; start+=256) {
        int n = (int)std::min((size_t)256, tokens.size()-start);
        batch.n_tokens = n;
        for (int j=0;j<n;j++) {
            batch.token[j] = tokens[start+(size_t)j];
            batch.pos[j] = pos+j;
            batch.n_seq_id[j] = 1;
            batch.seq_id[j][0] = 0;
            batch.logits[j] = (start+(size_t)j == tokens.size()-1) ? 1 : 0;
        }
        failed = llama_decode(engine->context, batch) != 0;
        pos += n;
    }

    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.18f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(12345));
    std::string output;
    output.reserve(4500);

    for(int i=0; i<maxOutput && !failed; i++) {
        llama_token token = llama_sampler_sample(sampler, engine->context, -1);
        if (llama_vocab_is_eog(engine->vocab, token)) break;
        char piece[1024];
        int written = llama_token_to_piece(engine->vocab, token, piece, sizeof(piece), 0, false);
        if (written > 0) output.append(piece, (size_t)written);
        batch.n_tokens = 1;
        batch.token[0] = token;
        batch.pos[0] = pos++;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = 1;
        if (llama_decode(engine->context, batch) != 0) failed = true;
    }
    llama_sampler_free(sampler);
    llama_batch_free(batch);
    if (failed) {
        fail(env, "La inferencia local ha fallado. Cierra otras aplicaciones e inténtalo de nuevo.");
        return nullptr;
    }
    return utf8(env, output);
}

extern "C" JNIEXPORT void JNICALL
Java_com_davidop_estudiaenelbus_NativeGguf_nativeClose(
    JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<Engine*>(handle);
}
