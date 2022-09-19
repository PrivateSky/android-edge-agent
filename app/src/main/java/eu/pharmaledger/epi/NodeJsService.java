package eu.pharmaledger.epi;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NodeJsService {
    private static final String TAG = NodeJsService.class.getCanonicalName();

    /**
     * Get a free port to runt the NodeJS
     */
    int getFreePort() {
        int port = -1;
        try {
            ServerSocket socket = new ServerSocket(0);
            // here's your free port
            port = socket.getLocalPort();
            socket.close();
        } catch (IOException ioe) {
            Log.i(TAG, "Could not get a free port: " + ioe.getMessage());
        }

        return port;
    }

    public void startNodeProcess(List<String> arguments, Context applicationContext) {
        // current node version is 18.7.0, built using Termux
        arguments = new ArrayList<>(arguments);
        arguments.add(0, "./libnode.so");

        Log.i(TAG, "Arguments to launch Node are : " + Arrays.toString(arguments.toArray()));

        String libsFolderDir = applicationContext.getFilesDir().getAbsolutePath() + "/libs";
        File libsFolder = new File(libsFolderDir);

        ProcessBuilder processBuilder = new ProcessBuilder(arguments);
        processBuilder.environment().put("LD_LIBRARY_PATH", libsFolder.getAbsolutePath());

        File nativeLibraryDir = new File(applicationContext.getApplicationInfo().nativeLibraryDir);

        try {
            Process process = processBuilder
                    .directory(nativeLibraryDir)
                    .redirectErrorStream(true)
                    .start();

            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            // Grab the results
            StringBuilder log = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Log.i(TAG, "PROCESS: " + line);
            }

            Integer retVal = process.waitFor();
            Log.i(TAG, "run: xxx Returned value : " + retVal);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start node", e);
        }
    }
}
