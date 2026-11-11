package com.davidop.estudiaenelbus;

import android.app.Activity;
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

    public final class AndroidBridge {
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
        if (web != null) {
            web.removeJavascriptInterface("AndroidBridge");
            web.destroy();
        }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
