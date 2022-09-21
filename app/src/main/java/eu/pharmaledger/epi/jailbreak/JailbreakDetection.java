package eu.pharmaledger.epi.jailbreak;

import android.content.Context;
import android.util.Log;

import com.scottyab.rootbeer.RootBeer;

import java.io.File;
import java.io.FileWriter;
import java.text.MessageFormat;

public class JailbreakDetection {
    private static final String TAG = JailbreakDetection.class.getCanonicalName();

    public void detect(final Context applicationContext, final File jailbreakDetectedFile) {
        JailbreakInfo jailbreakInfo = getJailbreakInfoFromRootBeer(applicationContext);
        writeJailbreakInfoToFile(jailbreakDetectedFile, jailbreakInfo);
    }

    private JailbreakInfo getJailbreakInfoFromRootBeer(Context applicationContext) {
        JailbreakInfo jailbreakInfo = new JailbreakInfo();
        final RootBeer rootBeer = new RootBeer(applicationContext);
        if (rootBeer.isRooted()) {
            Log.i(TAG, "Device is rooted!");
            JailbreakInfo.fromRootBeer(rootBeer, jailbreakInfo);
        } else {
            Log.i(TAG, "Didn't find indication of rooted device!");
        }
        return jailbreakInfo;
    }

    private void writeJailbreakInfoToFile(File jailbreakDetectedFile, JailbreakInfo jailbreakInfo) {
        if (!jailbreakInfo.isRootedDevice()) {
            Log.i(TAG, "Device not seen as rooted device so skip jailbreak info file write");
            return;
        }
        if (!jailbreakDetectedFile.getParentFile().exists()) {
            jailbreakDetectedFile.getParentFile().mkdirs();
        }

        String json = jailbreakInfo.toJSON();
        Log.i(TAG, MessageFormat.format("Writing jailbreak info ({0}) to file {1}", json, jailbreakDetectedFile.getAbsolutePath()));
        try (FileWriter fileWriter = new FileWriter(jailbreakDetectedFile, false)) {
            fileWriter.write(json);
        } catch (Exception exception) {
            Log.e(TAG, MessageFormat.format("Failed to write: {0}", jailbreakDetectedFile.getAbsolutePath()), exception);
        }
    }
}