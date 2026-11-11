package com.davidop.estudiaenelbus;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** OCR y Gemini Nano, enteramente en el dispositivo. Los textos OCR viven solo en RAM.
 *  Nunca se usa una API de inferencia cloud. Un modelo AICore descargable puede necesitar
 *  red para obtener sus archivos de modelo, no para las fotos ni el temario.
 */
public final class NativeLearningEngine {
    public interface Events { void emit(String event, String payload); }

    private final Activity activity;
    private final Events events;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextRecognizer recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS); // Modelo OCR incluido
    private volatile String extracted = "";
    private volatile boolean busy = false;

    public NativeLearningEngine(Activity activity, Events events) {
        this.activity = activity;
        this.events = events;
    }

    private void emit(String event, String data) {
        main.post(() -> events.emit(event, data));
    }
    private static String message(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) root = root.getCause();
        String msg = root.getMessage();
        return msg == null || msg.isEmpty() ? root.getClass().getSimpleName() : msg;
    }
    private void error(String msg) { emit("error", msg); }

    public String getExtracted() { return extracted; }

    public void scanImages(List<Uri> uris) {
        if (uris == null || uris.isEmpty() || uris.size() > 6) {
            error("Selecciona entre 1 y 6 imágenes."); return;
        }
        if (busy) { error("Ya hay una operación en curso."); return; }
        busy = true;
        extracted = "";
        ArrayList<String> texts = new ArrayList<>();
        scanNext(uris, 0, texts);
    }

    private void scanNext(List<Uri> uris, int index, ArrayList<String> texts) {
        if (index >= uris.size()) {
            busy = false;
            StringBuilder result = new StringBuilder();
            JSONArray pages = new JSONArray();
            for (int i = 0; i < texts.size(); i++) {
                String content = texts.get(i).trim();
                if (content.length() < 40) continue; // Ignorar fotos ilegibles
                String page = "Foto " + (i + 1) + ":\n" + content;
                if (result.length() + page.length() > 10000) {
                    emit("ocr_progress", "Texto extenso: se utilizará el fragmento reconocido inicialmente.");
                    break;
                }
                if (result.length() > 0) result.append("\n\n");
                result.append(page);
                pages.put(i + 1);
            }
            if (result.length() < 100) {
                error("No se ha podido leer suficiente texto. Prueba con fotos nítidas.");
                return;
            }
            extracted = result.toString();
            try {
                JSONObject data = new JSONObject();
                data.put("text", extracted);
                data.put("readable", pages.length());
                data.put("total", uris.size());
                emit("ocr_result", data.toString());
            } catch (Exception ex) { error("Error al preparar el texto leído."); }
            return;
        }
        int n = index + 1;
        emit("ocr_progress", "Leyendo foto " + n + " de " + uris.size() + "…");
        try {
            InputImage image = InputImage.fromFilePath(activity, uris.get(index));
            recognizer.process(image)
                .addOnSuccessListener(result -> {
                    texts.add(result.getText());
                    scanNext(uris, n, texts);
                })
                .addOnFailureListener(err -> {
                    // Las imágenes ilegibles o corruptas no bloquean el resto.
                    texts.add("");
                    scanNext(uris, n, texts);
                });
        } catch (Exception e) {
            texts.add("");
            scanNext(uris, n, texts);
        }
    }

    public void close() {
        recognizer.close();
        extracted = "";
    }
}
