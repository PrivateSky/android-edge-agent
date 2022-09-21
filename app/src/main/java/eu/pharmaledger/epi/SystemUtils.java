package eu.pharmaledger.epi;

import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.MessageFormat;

public class SystemUtils {
    public static String TAG = SystemUtils.class.getCanonicalName();

    public static void executeCommand(String command) {
        try {
            Log.i(TAG, "Executing command: " + command);

            Process process = Runtime.getRuntime().exec(command);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            int read;
            char[] buffer = new char[4096];
            StringBuffer output = new StringBuffer();
            while ((read = reader.read(buffer)) > 0) {
                output.append(buffer, 0, read);
            }
            reader.close();

            // Waits for the command to finish.
            process.waitFor();

            Log.i(TAG, "COMMAND OUTPUT: " + output);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean createSymLink(String symLinkFilePath, String originalFilePath) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Os.symlink(originalFilePath, symLinkFilePath);
                return true;
            }
            final Class<?> libcore = Class.forName("libcore.io.Libcore");
            final java.lang.reflect.Field fOs = libcore.getDeclaredField("os");
            fOs.setAccessible(true);
            final Object os = fOs.get(null);
            final java.lang.reflect.Method method = os.getClass().getMethod("symlink", String.class, String.class);
            method.invoke(os, originalFilePath, symLinkFilePath);
            return true;
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.EEXIST) {
                Log.i(TAG, MessageFormat.format("Symlink {0} already exists. Removing it...", symLinkFilePath));
                File existingSymlink = new File(symLinkFilePath);
                existingSymlink.delete();
                return createSymLink(symLinkFilePath, originalFilePath);
            }
            throw new RuntimeException(e);
        } catch (Exception e) {
            Log.e(TAG, MessageFormat.format("Failed to create Symlink to {0} from original {1}", symLinkFilePath, originalFilePath), e);
            throw new RuntimeException(e);
        }
    }
}
