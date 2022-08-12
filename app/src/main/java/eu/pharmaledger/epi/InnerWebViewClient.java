package eu.pharmaledger.epi;

import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.GeolocationPermissions;

public class InnerWebViewClient extends WebViewClient {

    public boolean shouldOverrideUrlLoading(WebView view, String url)
    {
        //do whatever you want with the url that is clicked inside the webview.
        //for example tell the webview to load that url.
        view.loadUrl(url);
        //return true if this method handled the link event
        //or false otherwise
        return true;
    }

	@Override
	public void onGeolocationPermissionsShowPrompt(String origin,
			GeolocationPermissions.Callback callback) {
		// Always grant permission since the app itself requires location
		// permission and the user has therefore already granted it
		callback.invoke(origin, true, false);
	}
}