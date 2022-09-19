package eu.pharmaledger.epi;

import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class FileService {
    private static final String TAG = FileService.class.getCanonicalName();

    public boolean deleteFolderRecursively(File file) {
        try {
            boolean res = true;
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

    public boolean copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) {
        Log.d(TAG, "copyAssetFolder(): Copy asset from " + fromAssetPath + " to " + toPath);
        try {
            String[] files = assetManager.list(fromAssetPath);
            boolean res = true;

            if (files.length == 0) {
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

    private boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
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
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Returns the content of a file as string
     */
    public String getFileContent(String toPath) {
        StringBuilder sb = new StringBuilder();

        File file = new File(toPath);
        if (file.exists() && file.canRead()) {
            try {
                FileReader rf = new FileReader(file);
                BufferedReader br = new BufferedReader(rf);
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            } catch (IOException ioex) {
                ioex.printStackTrace();
            }
        }

        return sb.toString();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void copy(File origin, File dest) throws IOException {
        Files.copy(origin.toPath(), dest.toPath());
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
