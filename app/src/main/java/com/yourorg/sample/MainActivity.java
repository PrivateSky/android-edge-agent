package com.yourorg.sample;

import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    public static String TAG = MainActivity.class.getCanonicalName();

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("node");
    }

    //We just want one instance of node running in the background.
    public static boolean _startedNodeAlready=false;

    public static int NODE_PORT = 3000;

    public static String MAIN_NODE_SCRIPT = "/MobileServerLauncher.js";

    /**First page to call once the Node server is up and running*/
    public static String INDEX_PAGE = "/app/loader/index.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i(TAG, "Running onCreate(...)");

        if( !_startedNodeAlready ) {
            _startedNodeAlready=true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    //The path where we expect the node project to be at runtime.
                    String nodeDir = getApplicationContext().getFilesDir().getAbsolutePath() + "/nodejs-project";
                    File nodeDirReference=new File(nodeDir);

                    if (wasAPKUpdated()) {
                        Log.d(TAG, "APK updated. Trigger re-installation of node asset folder");

                        long t1 = System.currentTimeMillis();

                        //Recursively delete any existing nodejs-project.
                        if (nodeDirReference.exists()) {
                            deleteFolderRecursively(new File(nodeDir));
                        }
                        long t2 = System.currentTimeMillis();
                        Log.d(TAG, "Deletion of folder took: " + (t2-t1) + " ms");

                        //Copy the node project from assets into the application's data path.
                        copyAssetFolder(getApplicationContext().getAssets(), "nodejs-project", nodeDir);
                        long t3 = System.currentTimeMillis();
                        Log.d(TAG, "Folder copy took: " + (t3-t2) + " ms");

                        saveLastUpdateTime();
                    }

                    if (nodeDirReference.exists()) {
                        Log.i(TAG, "Initiate startNodeWithArguments(...) call");

                        JSONObject env  = new JSONObject();
                        try {
                            env.put("PSK_CONFIG_LOCATION", "/data/data/com.yourorg.sample/files/nodejs-project/web-server/external-volume/config");
                            env.put("PSK_ROOT_INSTALATION_FOLDER", "/data/data/com.yourorg.sample/files/nodejs-project/");
                            env.put("BDNS_ROOT_HOSTS", "http://localhost:3000");
                        } catch (Exception ex){
                            Log.w(TAG, "Env JSON problem : " + ex.toString());
                        }

                        String[] args = new String[]{
                                "node",
                                nodeDir + MAIN_NODE_SCRIPT,
                                "--port=" + NODE_PORT,
                                        "--rootFolder=" + "/data/data/com.yourorg.sample/files/nodejs-project/web-server",
                                        "--bundle=./pskWebServer.js",
                                        "--env=" + env.toString()


                        };
                        Log.i(TAG, "Arguments to launch Node are : " + Arrays.toString(args));

                        Integer retVal = startNodeWithArguments(args);

                        Log.i(TAG, "run: xxx Returned value : " + retVal);
                    }
                    else {
                        Log.i(TAG, "Folder  " + nodeDirReference.getAbsolutePath() + " does not exists" );
                    }


                }
            }).start();
        }

        final Button buttonVersions = (Button) findViewById(R.id.btVersions);
        final WebView myWebView = (WebView) findViewById(R.id.myWebView);

        //Enable inner navigation for WebView
        myWebView.setWebViewClient(new InnerWebViewClient());

        //Enable JavaScript for WebView
        WebSettings webSettings = myWebView.getSettings();
        myWebView.clearCache(true);
        myWebView.clearHistory();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        myWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        myWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        buttonVersions.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                myWebView.loadUrl("http://localhost:" + NODE_PORT  + INDEX_PAGE);
            }
        });

    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final WebView myWebView = (WebView) findViewById(R.id.myWebView);

        if ((keyCode == KeyEvent.KEYCODE_BACK) && myWebView.canGoBack()) {
            myWebView.goBack();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native Integer startNodeWithArguments(String[] arguments);

    private boolean wasAPKUpdated() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE);
        long previousLastUpdateTime = prefs.getLong("NODEJS_MOBILE_APK_LastUpdateTime", 0);
        long lastUpdateTime = 1;
        try {
            PackageInfo packageInfo = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
            lastUpdateTime = packageInfo.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return (lastUpdateTime != previousLastUpdateTime);
    }

    private void saveLastUpdateTime() {
        long lastUpdateTime = 1;
        try {
            PackageInfo packageInfo = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
            lastUpdateTime = packageInfo.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("NODEJS_MOBILE_APK_LastUpdateTime", lastUpdateTime);
        editor.commit();
    }

    private static boolean deleteFolderRecursively(File file) {
        try {
            boolean res=true;
            for (File childFile : file.listFiles()) {
                if (childFile.isDirectory()) {
                    res &= deleteFolderRecursively(childFile);
                } else {
                    res &= childFile.delete();
                }
            }
            res &= file.delete();
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) {
//        Log.d(TAG, "copyAssetFolder(): Copy asset from " +  fromAssetPath + " to " + toPath);
        try {
            String[] files = assetManager.list(fromAssetPath);
            boolean res = true;

            if (files.length==0) {
                //If it's a file, it won't have any assets "inside" it.
                res &= copyAsset(assetManager,
                        fromAssetPath,
                        toPath);
            } else {
                new File(toPath).mkdirs();
                for (String file : files)
                    res &= copyAssetFolder(assetManager,
                            fromAssetPath + "/" + file,
                            toPath + "/" + file);
            }
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
        InputStream in = null;
        OutputStream out = null;
        try {

            File destFile = new File(toPath);
            destFile.createNewFile();
            in = assetManager.open(fromAssetPath);
            out = new FileOutputStream(toPath);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
            return true;
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void copy(File origin, File dest) throws IOException {
        Files.copy(origin.toPath(), dest.toPath());
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

}
