package fr.mika.cochepdf;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Coquille Android : affiche l'appli CochePDF (assets/index.html) dans une WebView,
 * gère l'import du PDF, l'ouverture depuis d'autres applis et l'enregistrement du PDF rempli.
 */
public class MainActivity extends Activity {

    private static final int REQ_OPEN = 1;
    private static final int REQ_SAVE = 2;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private boolean pageReady = false;
    private Uri pendingUri;
    private byte[] bytesToSave;
    private String nameToSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setSupportZoom(false);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (pendingUri != null) {
                    Uri u = pendingUri;
                    pendingUri = null;
                    sendPdfToPage(u);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/pdf");
                try {
                    startActivityForResult(i, REQ_OPEN);
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Aucun sélecteur de fichiers disponible", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });

        web.loadUrl("file:///android_asset/index.html");
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    /** PDF reçu via "Ouvrir avec" ou "Partager". */
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri == null) return;
        if (pageReady) sendPdfToPage(uri); else pendingUri = uri;
    }

    private void sendPdfToPage(Uri uri) {
        try {
            byte[] data = readAll(uri);
            String b64 = Base64.encodeToString(data, Base64.NO_WRAP);
            String name = displayName(uri);
            web.evaluateJavascript("window.CochePDF.loadBase64('" + b64 + "', " + jsString(name) + ")", null);
        } catch (Exception e) {
            Toast.makeText(this, "Impossible de lire ce PDF : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private byte[] readAll(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new Exception("fichier introuvable");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private String displayName(Uri uri) {
        String name = "document.pdf";
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) name = c.getString(0);
        } catch (Exception ignored) { }
        return name;
    }

    private static String jsString(String s) {
        StringBuilder b = new StringBuilder("'");
        for (char ch : s.toCharArray()) {
            if (ch == '\'' || ch == '\\') b.append('\\').append(ch);
            else if (ch < 0x20 || ch == 0x2028 || ch == 0x2029) b.append(String.format("\\u%04x", (int) ch));
            else b.append(ch);
        }
        return b.append("'").toString();
    }

    /** Appelé par la page pour enregistrer le PDF rempli. */
    private class Bridge {
        @JavascriptInterface
        public void savePdf(final String base64, final String fileName) {
            runOnUiThread(() -> {
                bytesToSave = Base64.decode(base64, Base64.DEFAULT);
                nameToSave = fileName;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/pdf");
                i.putExtra(Intent.EXTRA_TITLE, fileName);
                try {
                    startActivityForResult(i, REQ_SAVE);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Enregistrement impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri uri = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
        if (requestCode == REQ_OPEN) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(uri != null ? new Uri[]{uri} : null);
                fileCallback = null;
            }
        } else if (requestCode == REQ_SAVE) {
            if (uri == null || bytesToSave == null) { bytesToSave = null; return; }
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new Exception("destination introuvable");
                out.write(bytesToSave);
                web.evaluateJavascript("window.CochePDFSaved(true, " + jsString(nameToSave) + ")", null);
            } catch (Exception e) {
                web.evaluateJavascript("window.CochePDFSaved(false, " + jsString(String.valueOf(e.getMessage())) + ")", null);
            }
            bytesToSave = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
