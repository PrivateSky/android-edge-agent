package eu.pharmaledger.epi;

import static eu.pharmaledger.epi.AppManager.WEBSERVER_RELATIVE_PATH;

import android.content.res.AssetManager;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Locale;

public class InnerWebViewClient extends WebViewClient {
    private static final String TAG = InnerWebViewClient.class.getCanonicalName();

    private final int port;
    private final String mainUrl;
    private final AssetManager assetManager;

    public InnerWebViewClient(int port, String mainUrl, AssetManager assetManager) {
        this.port = port;
        this.mainUrl = mainUrl;
        this.assetManager = assetManager;
    }

    private static String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            extension = extension.toLowerCase(Locale.getDefault());
            // some extensions are missing from MimeTypeMap
            if (extension.equals("js")) {
                return "text/javascript";
            }

            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }

    @Nullable
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (request.getUrl().toString().equalsIgnoreCase(mainUrl) || isLocalStaticFileRequest(request)) {

            String filePath = request.getUrl().getPath();

            if (isRequestToMainIndex(request, filePath)) {
                try {
                    InputStream inputStream = assetManager.open(WEBSERVER_RELATIVE_PATH + "/app/index.html");
                    return new WebResourceResponse(getMimeType("index.html"), "UTF-8", inputStream);
                } catch (Exception e) {
                    // catch any error and let the request past to apihub
                    e.printStackTrace();
                }
            }

            try {
                String assetFilePath = MessageFormat.format("{0}/app{1}", WEBSERVER_RELATIVE_PATH, filePath);
                InputStream inputStream = assetManager.open(assetFilePath);
                return new WebResourceResponse(getMimeType(filePath), "UTF-8", inputStream);
            } catch (Exception e) {
                // catch any error and let the request past to apihub
                Log.e(TAG, "Failed to read file: " + filePath, e);
                e.printStackTrace();
            }
        }

        return super.shouldInterceptRequest(view, request);
    }

    private boolean isRequestToMainIndex(WebResourceRequest request, String filePath) {
        return request.getUrl().toString().equalsIgnoreCase(mainUrl) || filePath.equals("/") || filePath.equalsIgnoreCase("/index.html");
    }

    private boolean isLocalStaticFileRequest(WebResourceRequest request) {
        String scheme = request.getUrl().getScheme().trim();
        return request.getMethod().equalsIgnoreCase("GET")
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                && request.getUrl().getHost().equalsIgnoreCase("localhost")
                && request.getUrl().getPort() == port && isFileRequest(request.getUrl().getPath());
    }

    private boolean isFileRequest(String path) {
        if (path.equals("/")) {
            // for root we default to index.html file
            return true;
        }
        // check for dot presence in last URL group
        int lastSlashIndex = path.indexOf("/");
        if (lastSlashIndex == -1) {
            return false;
        }

        int dotIndex = path.indexOf(".", lastSlashIndex + 1);
        return dotIndex != -1;
    }

}