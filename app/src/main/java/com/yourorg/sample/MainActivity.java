package com.yourorg.sample;

import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import java.net.*;
import java.io.*;
import java.nio.file.Files;

public class MainActivity extends AppCompatActivity {
    public static String TAG = MainActivity.class.getCanonicalName();

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("node");
    }

    //We just want one instance of node running in the background.
    public static boolean _startedNodeAlready=false;

    /**Relative path to /src/main/assets/nodejs-project folder*/
//    public static String MAIN_NODE_SCRIPT = "/epi-workspace/bin/MobileServerLauncher.js";
    public static String MAIN_NODE_SCRIPT = "/main.js";

    public static int NODE_PORT = 3000;

    /**First page to call once the Node server is up and running*/
    public static String INDEX_PAGE = "/index.html";
//    public static String INDEX_PAGE = "/";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i(TAG, "Running onCreate(...) :) ");

        if( !_startedNodeAlready ) {
            _startedNodeAlready=true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    //The path where we expect the node project to be at runtime.
                    String nodeDir=getApplicationContext().getFilesDir().getAbsolutePath()+"/nodejs-project";
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
                        Integer retVal = startNodeWithArguments(new String[]{"node",
                                nodeDir + MAIN_NODE_SCRIPT
                        });
                        Log.i(TAG, "run: xxx Returned value : " + retVal);
                    }
                    else {
                        Log.i(TAG, "Folder  " + nodeDirReference.getAbsolutePath() + " does not exists" );
                    }


                }
            }).start();
        }

        final Button buttonVersions = (Button) findViewById(R.id.btVersions);
//        final TextView textViewVersions = (TextView) findViewById(R.id.tvVersions);
        final WebView myWebView = (WebView) findViewById(R.id.myWebView);

        buttonVersions.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

//                URL localNodeServer = new URL("http://localhost:" + NODE_PORT  + INDEX_PAGE);
                myWebView.loadUrl("http://localhost:" + NODE_PORT  + INDEX_PAGE);

//                //Network operations should be done in the background.
//                new AsyncTask<Void,Void,String>() {
//                    @Override
//                    protected String doInBackground(Void... params) {
//                        String nodeResponse="";
//                        try {
//                            URL localNodeServer = new URL("http://localhost:" + NODE_PORT  + INDEX_PAGE);
//                            BufferedReader in = new BufferedReader(
//                                    new InputStreamReader(localNodeServer.openStream()));
//                            String inputLine;
//                            while ((inputLine = in.readLine()) != null)
//                                nodeResponse=nodeResponse+inputLine;
//                            in.close();
//                        } catch (Exception ex) {
//                            nodeResponse=ex.toString();
//                        }
//                        return nodeResponse;
//                    }
//                    @Override
//                    protected void onPostExecute(String result) {
//                        textViewVersions.setText(result);
//                    }
//                }.execute();
            }
        });

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

//            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
//                assetManager.
//                File srcFile = new File( new URI("file:///android_asset/" + fromAssetPath));
//                copy(srcFile, destFile);
//            }
//            else{
                in = assetManager.open(fromAssetPath);
                out = new FileOutputStream(toPath);
                copyFile(in, out);
                in.close();
                in = null;
                out.flush();
                out.close();
                out = null;
//            }
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
