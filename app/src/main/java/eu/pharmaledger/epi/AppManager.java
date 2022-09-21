package eu.pharmaledger.epi;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Process;
import android.util.Log;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppManager {
    public static final String NODEJS_PROJECT_FOLDER_NAME = "nodejs-project";
    public static final String WEBSERVER_FOLDER_NAME = "apihub-root";
    public static final String WEBSERVER_RELATIVE_PATH = NODEJS_PROJECT_FOLDER_NAME + "/" + WEBSERVER_FOLDER_NAME;

    private static final String TAG = AppManager.class.getCanonicalName();

    private static final String TRUSTLOADER_INDEX_RELATIVE_PATH = "app/loader/index.html";
    private static final String STANDALONE_INDEX_RELATIVE_PATH = "app/index.html";

    private final MainActivity mainActivity;
    private final Context applicationContext;
    private final Resources resources;

    private final FileService fileService = new FileService();
    private final String nodeJsFolderPath;
    private final String webserverFolderPath;

    private final File nodeJsFolder;
    private final File libsFolder;

    public AppManager(MainActivity mainActivity, Context applicationContext, Resources resources) {
        this.mainActivity = mainActivity;
        this.applicationContext = applicationContext;
        this.resources = resources;

        nodeJsFolderPath = applicationContext.getFilesDir().getAbsolutePath() + "/" + NODEJS_PROJECT_FOLDER_NAME;
        webserverFolderPath = nodeJsFolderPath + "/" + WEBSERVER_FOLDER_NAME;

        nodeJsFolder = new File(nodeJsFolderPath);

        String libsFolderDir = applicationContext.getFilesDir().getAbsolutePath() + "/libs";
        libsFolder = new File(libsFolderDir);
    }

    public String getNodeJsFolderPath() {
        return nodeJsFolderPath;
    }

    public String getWebserverFolderPath() {
        return webserverFolderPath;
    }

    public File getNodeJsFolder() {
        return nodeJsFolder;
    }

    public void setupInstallation() {
        Log.d(TAG, "APK updated. Trigger re-installation of node asset folder");

        long t1 = System.currentTimeMillis();

        //Recursively delete any existing nodejs-project.
        if (nodeJsFolder.exists()) {
            fileService.deleteFolderRecursively(nodeJsFolder);
        }
        nodeJsFolder.mkdirs();

        long t2 = System.currentTimeMillis();
        Log.d(TAG, "Deletion of folder took: " + (t2 - t1) + " ms");

        copyApplicationAssets();
        copyNodeJsDependencyLibs();

        long t3 = System.currentTimeMillis();
        Log.d(TAG, "Assets copy took: " + (t3 - t2) + " ms");

        saveLastUpdateTime();
    }

    public String getMainUrl(int nodePort) {
        boolean isStandaloneIndexFilePresent = false;
        try {
            if (Arrays.asList(resources.getAssets().list(WEBSERVER_RELATIVE_PATH + "/app")).contains("index.html")) {
                isStandaloneIndexFilePresent = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        String indexPage = isStandaloneIndexFilePresent ? STANDALONE_INDEX_RELATIVE_PATH : TRUSTLOADER_INDEX_RELATIVE_PATH;
        return MessageFormat.format("http://localhost:{0}/{1}", String.valueOf(nodePort), indexPage);
    }

    public void cleanPidFile() {
        String apidFilePath = webserverFolderPath + "/pid";
        File apidFile = new File(apidFilePath);
        if (apidFile.exists()) {
            if (!apidFile.delete()) {
                Log.i(TAG, "Could not delete pid file");
            }
        }
    }

    public void waitUntilPidFileCreation() {
        int currentPid = Process.myPid();
        while (true) {
            try {
                Thread.sleep(1000);
                //Let's see what we got
                String apidFilePath = webserverFolderPath + "/pid";
                File apidFile = new File(apidFilePath);
                if (apidFile.exists()) {
                    String data = fileService.getFileContent(apidFilePath);
                    try {
                        int apid = Integer.parseInt(data.trim());
                        if (apid == currentPid) {
                            break;
                        }
                    } catch (NumberFormatException nfex) {
                        Log.w(TAG, "APID is not an integer: " + nfex);
                    }
                }

                Log.d(TAG, "APID Monitor scan done.");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean wasAPKUpdated() {
        SharedPreferences prefs = applicationContext.getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE);
        long previousLastUpdateTime = prefs.getLong("NODEJS_MOBILE_APK_LastUpdateTime", 0);
        long lastUpdateTime = 1;
        try {
            PackageInfo packageInfo = applicationContext.getPackageManager().getPackageInfo(applicationContext.getPackageName(), 0);
            lastUpdateTime = packageInfo.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return (lastUpdateTime != previousLastUpdateTime);
    }

    public void saveLastUpdateTime() {
        long lastUpdateTime = 1;
        try {
            PackageInfo packageInfo = applicationContext.getPackageManager().getPackageInfo(applicationContext.getPackageName(), 0);
            lastUpdateTime = packageInfo.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        SharedPreferences prefs = applicationContext.getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("NODEJS_MOBILE_APK_LastUpdateTime", lastUpdateTime);
        editor.commit();
    }

    public void initialiseWebView(WebView webView, int port, String mainUrl, AssetManager assetManager) {
        //Enable inner navigation for WebView
        webView.setWebViewClient(new InnerWebViewClient(port, mainUrl, assetManager));

        //Enable JavaScript for WebView
        WebSettings webSettings = webView.getSettings();
        webView.clearCache(true);
        webView.clearHistory();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setGeolocationEnabled(true);

        webView.setWebChromeClient(new WebChromeClient() {
            private String geolocationOrigin;
            private GeolocationPermissions.Callback geolocationCallback;
            final ActivityResultLauncher<String[]> locationPermissionRequest =
                    mainActivity.registerForActivityResult(new ActivityResultContracts
                                    .RequestMultiplePermissions(), result -> {
                                Boolean fineLocationGranted = result.getOrDefault(
                                        Manifest.permission.ACCESS_FINE_LOCATION, false);
                                Boolean coarseLocationGranted = result.getOrDefault(
                                        Manifest.permission.ACCESS_COARSE_LOCATION, false);
                                if (fineLocationGranted != null && fineLocationGranted) {
                                    Log.i(TAG, "Precise location access granted.");
                                    geolocationCallback.invoke(geolocationOrigin, true, true);
                                } else if (coarseLocationGranted != null && coarseLocationGranted) {
                                    Log.i(TAG, "Only approximate location access granted.");
                                    geolocationCallback.invoke(geolocationOrigin, true, false);
                                } else {
                                    Log.i(TAG, "No location access granted.");
                                    geolocationCallback.invoke(geolocationOrigin, false, false);
                                }
                            }
                    );

            private PermissionRequest request;
            final ActivityResultLauncher<String> getPermission =
                    mainActivity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                        if (isGranted) {
                            request.grant(request.getResources());
                        } else {
                            //EXPLAIN TO USER WHY PERMISSION ARE NECESSARY FOR FUNCTINALITY
                        }
                    });

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                this.request = request;

                if (isCameraPermission(request)) {
                    if (hasPermission(Manifest.permission.CAMERA)) {
                        request.grant(request.getResources());
                    } else {
                        getPermission.launch(Manifest.permission.CAMERA);
                    }
                    return;
                }

                if (hasPermission(request.getResources()[0])) {
                    request.grant(request.getResources());
                    return;
                }

                getPermission.launch(request.getResources()[0]);
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) && !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    geolocationOrigin = origin;
                    geolocationCallback = callback;

                    locationPermissionRequest.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    });
                    return;
                }

                callback.invoke(origin, true, false);
            }

            private boolean isCameraPermission(PermissionRequest request) {
                // WebChrome client has custom permission instead of the standard android.permission.CAMERA
                for (String permission : request.getResources()) {
                    if (permission.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                            || permission.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        return true;
                    }
                }
                return false;
            }
        });

        WebView.setWebContentsDebuggingEnabled(true);
    }

    private void copyApplicationAssets() {
        try {
            String[] rootFiles = applicationContext.getAssets().list(NODEJS_PROJECT_FOLDER_NAME);
            for (String rootFile : rootFiles) {
                if (!rootFile.equalsIgnoreCase(WEBSERVER_FOLDER_NAME)) {
                    fileService.copyAssetFolder(applicationContext.getAssets(),
                            NODEJS_PROJECT_FOLDER_NAME + "/" + rootFile, nodeJsFolderPath + "/" + rootFile);
                }
            }

            new File(webserverFolderPath).mkdirs();
            String[] apihubRootFiles = applicationContext.getAssets().list(WEBSERVER_RELATIVE_PATH);
            for (String apihubRootFile : apihubRootFiles) {
                if (!apihubRootFile.equalsIgnoreCase("app")) {
                    String relativeFilePath = WEBSERVER_RELATIVE_PATH + "/" + apihubRootFile;
                    String[] folderFiles = applicationContext.getAssets().list(relativeFilePath);
                    if (folderFiles.length != 0) {
                        // current entry is a folder
                        new File(webserverFolderPath + "/" + apihubRootFile).mkdirs();
                    }

                    fileService.copyAssetFolder(applicationContext.getAssets(),
                            relativeFilePath, webserverFolderPath + "/" + apihubRootFile);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy assets folder", e);
            throw new RuntimeException(e);
        }
    }

    private void copyNodeJsDependencyLibs() {
        try {
            String libSourceFolder = getLibSourceFolder();
            fileService.copyAssetFolder(applicationContext.getAssets(), libSourceFolder, libsFolder.getAbsolutePath());

            String symlinksFileContent = fileService.getAssetContent(applicationContext.getAssets(), "symlinks.json");
            Gson gson = new Gson();
            SymlinkConfig[] symlinkConfigArray = gson.fromJson(symlinksFileContent, SymlinkConfig[].class);
            List<SymlinkConfig> symlinkConfigs = new ArrayList<>(Arrays.asList(symlinkConfigArray));

            for (SymlinkConfig symlinkConfig : symlinkConfigs) {
                String symlinkPath = libsFolder.getAbsolutePath() + "/" + symlinkConfig.getSymlinkName();
                String originalFilePath = libsFolder.getAbsolutePath() + "/" + symlinkConfig.getOriginalFile();
                SystemUtils.createSymLink(symlinkPath, originalFilePath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy assets folder", e);
            throw new RuntimeException(e);
        }
    }

    private boolean hasPermission(String permission) {
        Log.i(TAG, MessageFormat.format("Checking for {0} permission.", permission));
        if (ContextCompat.checkSelfPermission(mainActivity, permission) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, MessageFormat.format("Permission {0} not granted.", permission));
            return false;
        }

        return true;
    }

    @NonNull
    private String getLibSourceFolder() {
        String architecture = System.getProperty("os.arch");
        String libSourceFolder;
        if ("aarch64".equalsIgnoreCase(architecture)) {
            libSourceFolder = "arm64-v8a";
        } else if ("x86_64".equalsIgnoreCase(architecture)) {
            libSourceFolder = "x86_64";
        } else if ("i686".equalsIgnoreCase(architecture)) {
            libSourceFolder = "x86";
        } else {
            libSourceFolder = "armeabi-v7a";
        }
        return libSourceFolder;
    }
}
