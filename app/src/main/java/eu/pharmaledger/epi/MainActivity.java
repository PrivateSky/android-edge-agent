package eu.pharmaledger.epi;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

import eu.pharmaledger.epi.jailbreak.JailbreakDetection;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getCanonicalName();

    private static final String PRELOADER_FILE_NAME = "preloader.html";
    private static final String DEFAULT_PRELOADER_FILE_NAME = "default-preloader.html";
    private static final String MAIN_NODE_SCRIPT = "/MobileServerLauncher.js";

    //We just want one instance of node running in the background.
    private static boolean _startedNodeAlready = false;
    private static int NODE_PORT;

    private final NodeJsService nodeJsService = new NodeJsService();
    /**
     * Lock object use to orchestrate the booter and monitor threads
     */
    private final Object lock = new Object();
    /**
     * Keeps current process Id
     */
    private int mProcessId;
    private WebView myWebView;
    /**
     * Monitors the APID written by Node upon booting
     */
    private Thread pidMonitor;
    /**
     * Boots the whole Node + Application stack
     */
    private Thread booter;
    private AppManager appManager;
    private ChromeService chromeService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String preloaderRelativePath = DEFAULT_PRELOADER_FILE_NAME;
        try {
            if (Arrays.asList(getResources().getAssets().list("")).contains(PRELOADER_FILE_NAME)) {
                preloaderRelativePath = PRELOADER_FILE_NAME;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        appManager = new AppManager(MainActivity.this, getApplicationContext(), getResources());
        chromeService = new ChromeService(getPackageManager(), MainActivity.this);

        setContentView(R.layout.activity_main);

        if (!chromeService.isChromeVersionOK()) {
            chromeService.showWarning();
        } else {
            appManager.cleanPidFile();

            NODE_PORT = nodeJsService.getFreePort();
            Log.i(TAG, "Free port is" + NODE_PORT);

            String mainUrl = appManager.getMainUrl(NODE_PORT);

            myWebView = findViewById(R.id.myWebView);
            appManager.initialiseWebView(myWebView, NODE_PORT, mainUrl, getAssets());
            myWebView.loadUrl(MessageFormat.format("file:///android_asset/{0}", preloaderRelativePath));

            mProcessId = android.os.Process.myPid();

            Log.i(TAG, "Running onCreate(...)");

            if (!_startedNodeAlready) {
                _startedNodeAlready = true;

                //Watch for APID update to know when to trigger page load
                pidMonitor = new Thread(() -> {
                    synchronized (lock) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    Log.i(TAG, "APID monitor awaken.");

                    appManager.waitUntilPidFileCreation();
                    loadPage(mainUrl);
                });
                pidMonitor.start();


                booter = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (appManager.wasAPKUpdated()) {
                            appManager.setupInstallation();
                        }

                        if (appManager.getNodeJsFolder().exists()) {
                            String jailbreakDetectedFilePath = appManager.getWebserverFolderPath() + "/external-volume/jailbreak/details";
                            File jailbreakDetectedFile = new File(jailbreakDetectedFilePath);
                            if (!jailbreakDetectedFile.exists()) {
                                Log.i(TAG, MessageFormat.format("{0} doesn't exist. Checking for jailbreak...", jailbreakDetectedFilePath));
                                JailbreakDetection jailbreakDetection = new JailbreakDetection();
                                jailbreakDetection.detect(getApplicationContext(), jailbreakDetectedFile);
                            }

                            Log.i(TAG, "Initiate startNodeWithArguments(...) call");

                            JSONObject env = new JSONObject();
                            try {
                                env.put("PSK_CONFIG_LOCATION", appManager.getWebserverFolderPath() + "/external-volume/config");
                                env.put("PSK_ROOT_INSTALATION_FOLDER", appManager.getNodeJsFolderPath());
                                env.put("BDNS_ROOT_HOSTS", "http://localhost:" + NODE_PORT);
                            } catch (Exception ex) {
                                Log.w(TAG, "Env JSON problem : " + ex);
                            }

                            //Wake up APID monitor thread
                            synchronized (lock) {
                                lock.notify();
                            }

                            List<String> nodeJsArguments = Arrays.asList(
                                    appManager.getNodeJsFolderPath() + MAIN_NODE_SCRIPT,
                                    "--port=" + NODE_PORT,
                                    "--rootFolder=" + appManager.getWebserverFolderPath(),
                                    "--bundle=./pskWebServer.js",
                                    "--apic=" + mProcessId, //Android's process Id
                                    "--env=" + env
                            );
                            nodeJsService.startNodeProcess(nodeJsArguments, getApplicationContext());
                        } else {
                            Log.i(TAG, "Folder  " + appManager.getNodeJsFolderPath() + " does not exists");
                        }
                    }
                });

                booter.start();
            }
        }
    }

    /**
     * Loads the page of the web app
     *
     * @param mainUrl
     */
    void loadPage(String mainUrl) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        myWebView.loadUrl(mainUrl);
                    }
                }
        );
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final WebView myWebView = findViewById(R.id.myWebView);

        if ((keyCode == KeyEvent.KEYCODE_BACK) && myWebView.canGoBack()) {
            myWebView.goBack();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
}
