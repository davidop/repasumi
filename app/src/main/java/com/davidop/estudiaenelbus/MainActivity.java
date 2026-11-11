package com.davidop.estudiaenelbus;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.ValueCallback;
import java.util.ArrayList;
import java.util.List;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.WindowInsets;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/** Beta: interfaz embebida. Sin permiso INTERNET. */
public final class MainActivity extends Activity {
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String HOME = ORIGIN + "/assets/index.html";
    private WebView web;
    private NativeLearningEngine learning;
    private NativeGguf localModel;
    private static final int PICK_GGUF = 835;
    private static final int PICK_PAGES = 832;
    private ValueCallback<Uri[]> pendingPhotos;
    private TextToSpeech tts;
    private volatile boolean localSpanishVoice = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        tts = new TextToSpeech(this, code -> {
            if (code != TextToSpeech.SUCCESS || tts == null) return;
            try {
                Set<Voice> voices = tts.getVoices();
                if (voices != null) {
                    for (Voice voice : voices) {
                        Locale loc = voice.getLocale();
                        if ("es".equals(loc.getLanguage()) && !voice.isNetworkConnectionRequired()) {
                            tts.setVoice(voice);
                            localSpanishVoice = true;
                            if ("ES".equals(loc.getCountry())) break;
                        }
                    }
                }
                tts.setSpeechRate(0.92f);
            } catch (Exception ignored) { localSpanishVoice = false; }
        });

        learning = new NativeLearningEngine(this, (event, payload) -> {
            if (web == null) return;
            String eventJson = org.json.JSONObject.quote(event);
            String payloadJson = org.json.JSONObject.quote(payload);
            web.evaluateJavascript("window.repasumiNativeEvent&&window.repasumiNativeEvent(" + eventJson + "," + payloadJson + ")", null);
        });
        localModel = new NativeGguf(this, (event, payload) -> runOnUiThread(() -> {
            if (web == null) return;
            String a = org.json.JSONObject.quote(event);
            String b = org.json.JSONObject.quote(payload);
            web.evaluateJavascript("window.repasumiNativeEvent&&window.repasumiNativeEvent(" + a + "," + b + ")", null);
        }));
        web = new WebView(this);
        web.setBackgroundColor(0xFFFFFDF8);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return !HOME.equals(req.getUrl().toString());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                if (HOME.equals(req.getUrl().toString())) {
                    try {
                        InputStream input = getAssets().open("index.html");
                        return new WebResourceResponse("text/html", "UTF-8", input);
                    } catch (Exception ignored) { return blocked(); }
                }
                return blocked();
            }
            private WebResourceResponse blocked() {
                return new WebResourceResponse("text/plain", "UTF-8",
                        new ByteArrayInputStream(new byte[0]));
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingPhotos != null) pendingPhotos.onReceiveValue(null);
                pendingPhotos = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("image/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(intent, PICK_PAGES);
                return true;
            }
        });
        web.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(web);
        web.loadUrl(HOME);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_GGUF) {
            if (result == RESULT_OK && data != null && data.getData() != null)
                localModel.importModel(data.getData());
            return;
        }
        if (request == PICK_PAGES + 1) {
            ArrayList<Uri> photos = new ArrayList<>();
            if (result == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    if (count > 30) { web.evaluateJavascript("window.repasumiNativeEvent&&window.repasumiNativeEvent('error','Selecciona un máximo de 30 fotografías.')", null); return; }
                    for (int i = 0; i < count; i++) photos.add(data.getClipData().getItemAt(i).getUri());
                } else if (data.getData() != null) photos.add(data.getData());
            }
            if (!photos.isEmpty()) learning.scanImages(photos);
            return;
        }
        if (request != PICK_PAGES) return;
        ArrayList<Uri> images = new ArrayList<>();
        if (result == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                for (int i = 0; i < Math.min(30, data.getClipData().getItemCount()); i++)
                    images.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) images.add(data.getData());
        }
        if (pendingPhotos != null) {
            pendingPhotos.onReceiveValue(images.isEmpty() ? null : images.toArray(new Uri[0]));
            pendingPhotos = null;
        }
    }

    public final class AndroidBridge {
        @JavascriptInterface public void checkModel() { localModel.check(); }
        @JavascriptInterface public void openModelPage() {
            runOnUiThread(() -> {
                Intent browser = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf"));
                try { startActivity(browser); }
                catch (Exception ex) { Toast.makeText(MainActivity.this,
                    "Abre Hugging Face desde tu navegador para descargar el modelo.", Toast.LENGTH_LONG).show(); }
            });
        }
        @JavascriptInterface public void importModel() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, PICK_GGUF);
            });
        }
        @JavascriptInterface public void scanPhotos() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(intent, PICK_PAGES + 1);
            });
        }
        @JavascriptInterface public void generateFromOCR(String level) { localModel.generate(learning.getExtracted(), level); }

        @JavascriptInterface public void copyText(String value) {
            runOnUiThread(() -> {
                ClipboardManager manager =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("Repasumi", value));
            });
        }
        @JavascriptInterface public void speak(String value) {
            runOnUiThread(() -> {
                if (tts != null && localSpanishVoice) {
                    tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "question");
                } else {
                    Toast.makeText(MainActivity.this,
                        "Instala una voz en español sin conexión para escuchar preguntas.",
                        Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override protected void onDestroy() {
        if (learning != null) learning.close();
        if (localModel != null) localModel.close();
        if (web != null) {
            web.removeJavascriptInterface("AndroidBridge");
            web.destroy();
        }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
