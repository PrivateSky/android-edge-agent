package com.yourorg.sample;

import android.Manifest;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
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
import android.widget.ProgressBar;

import java.io.*;
import java.net.ServerSocket;
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

    public static String NODEJS_PATH = "/data/data/com.yourorg.sample/files/nodejs-project";
    public static String WEBSERVER_PATH = NODEJS_PATH + "/web-server";

    Button buttonVersions;
    ProgressBar progressBar;

    /**Keeps current process Id*/
    int mProcessId;
    WebView myWebView;

    /**Monitors the APID written by Node upon booting*/
    Thread pidMonitor;

    /**Boots the whole Node + Application stack*/
    Thread booter;

    /**Lock object use to orchestrate the booter and monitor threads*/
    Object lock = new Object();


    /**Updates progress bar inside the main UI*/
    void updateProgressBar(final boolean present){
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        if(present){
                            progressBar.setVisibility(View.VISIBLE);
                            progressBar.setIndeterminate(true);
                        }
                        else{
                            progressBar.setVisibility(View.GONE);
                            progressBar.setIndeterminate(false);
                        }
                    }
                }
        );
    }

    void cleanPidFile(){
        String apidFilePath = WEBSERVER_PATH + "/pid";
        File apidFile = new File(apidFilePath);
        if(apidFile.exists()){
            if(!apidFile.delete()){
                Log.i(TAG, "Could not delete pid file" );
            }
        }
    }

    /**Get a free port to runt the NodeJS*/
    int getFreePort(){
        int port = -1;
        try {
            ServerSocket socket = new ServerSocket(0);
            // here's your free port
            port = socket.getLocalPort();
            socket.close();
        }
        catch (IOException ioe) {
            Log.i(TAG, "Could not get a free port: " + ioe.getMessage());
        }

        return port;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Remove title bar
//        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);

        cleanPidFile();

        NODE_PORT = getFreePort();
        Log.i(TAG, "Free port is" + NODE_PORT);

//        listPorts();

//        buttonVersions = (Button) findViewById(R.id.btVersions);
//        buttonVersions.setVisibility(View.GONE);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        if (checkPermission()) {
            //do we need to do something special???
        } else {
            requestPermission();
        }

        mProcessId = android.os.Process.myPid();

        Log.i(TAG, "Running onCreate(...)");

        if( !_startedNodeAlready ) {
            _startedNodeAlready=true;

            //Watch for APID update to know when to trigger page load
            pidMonitor = new Thread(new Runnable() {
                @Override
                public void run() {
                    //put it to sleep
                    synchronized (lock) {
                        try {
                            lock.wait();
                        } catch(InterruptedException e){
                            e.printStackTrace();
                        }
                    }
                    Log.i(TAG, "APID monitor awaken." );

                    //try to

                    while(true){
                        try {
                            Thread.sleep(1000);
                            //Let's see what we got
                            String apidFilePath = WEBSERVER_PATH + "/pid";
                            File apidFile = new File(apidFilePath);
                            if(apidFile.exists()){
                                String data = getFileContent(apidFilePath);
                                try{
                                    int apid = Integer.parseInt(data.trim());
                                    if(apid == mProcessId){
                                        break;
                                    }
                                }catch (NumberFormatException nfex){
                                    Log.w(TAG, "APID is not an integer: " + nfex.toString() );
                                }
                            }

                            Log.d(TAG, "APID Monitor scan done.");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    //We know it's ok so
                    updateProgressBar(false);
                    loadPage();
                }
            });
            pidMonitor.start();


            booter = new Thread(new Runnable() {
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
                        updateProgressBar(true);
                        copyAssetFolder(getApplicationContext().getAssets(), "nodejs-project", nodeDir);
                        long t3 = System.currentTimeMillis();
                        Log.d(TAG, "Folder copy took: " + (t3-t2) + " ms");
                        updateProgressBar(false);

                        saveLastUpdateTime();
                    }

                    if (nodeDirReference.exists()) {
                        Log.i(TAG, "Initiate startNodeWithArguments(...) call");

                        JSONObject env  = new JSONObject();
                        try {
                            env.put("PSK_CONFIG_LOCATION", WEBSERVER_PATH + "/external-volume/config");
                            env.put("PSK_ROOT_INSTALATION_FOLDER", NODEJS_PATH);
                            env.put("BDNS_ROOT_HOSTS", "http://localhost:" + NODE_PORT);
                        } catch (Exception ex){
                            Log.w(TAG, "Env JSON problem : " + ex.toString());
                        }

                        String[] args = new String[]{
                                "node",
                                nodeDir + MAIN_NODE_SCRIPT,
                                "--port=" + NODE_PORT,
                                "--rootFolder=" + WEBSERVER_PATH,
                                "--bundle=./pskWebServer.js",
                                "--apic=" + mProcessId, //Android's process Id
                                "--env=" + env.toString()


                        };
                        Log.i(TAG, "Arguments to launch Node are : " + Arrays.toString(args));

                        //Wake up APID monitor thread
                        synchronized (lock) {
                            lock.notify();
                        }

                        Integer retVal = startNodeWithArguments(args);

                        Log.i(TAG, "run: xxx Returned value : " + retVal);
                    }
                    else {
                        Log.i(TAG, "Folder  " + nodeDirReference.getAbsolutePath() + " does not exists" );
                    }
                }
            });
            booter.start();

        }

        myWebView = (WebView) findViewById(R.id.myWebView);

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

//        buttonVersions.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//                loadPage();
//            }
//        });

    }

    /**Loads the page of the web app*/
    void loadPage(){
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        myWebView.loadUrl("http://localhost:" + NODE_PORT  + INDEX_PAGE);
                    }
                }
        );
    }

    private boolean checkPermission() {
        Log.i(TAG, "Checking for camera permission.");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Camera permission not granted!");
            return false;
        }
        return true;
    }

    private static final int PERMISSION_REQUEST_CODE = 200;
    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                PERMISSION_REQUEST_CODE);
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
        Log.d(TAG, "copyAssetFolder(): Copy asset from " +  fromAssetPath + " to " + toPath);
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

    /**Returns the content of a file as string*/
    private String getFileContent(String toPath){
        StringBuilder sb = new StringBuilder();

        File file = new File(toPath);
        if(file.exists() && file.canRead()){
            try{
                FileReader rf = new FileReader(file);
                BufferedReader br = new BufferedReader(rf);
                String line;
                while( (line = br.readLine()) != null){
                    sb.append(line);
                }
            } catch (IOException ioex){
                ioex.printStackTrace();
            }
        }

        return  sb.toString();
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
