package eu.pharmaledger.epi.jailbreak;

import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import com.scottyab.rootbeer.RootBeer;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class JailbreakInfo {
    public static String TAG = JailbreakInfo.class.getCanonicalName();

    private Boolean isRooted;
    private Boolean rootManagementApps;
    private Boolean potentiallyDangerousApps;
    private Boolean rootCloakingApps;
    private Boolean testKeys;
    private Boolean dangerousProps;
    private Boolean busyBoxBinary;
    private Boolean suBinary;
    private Boolean suExists;
    private Boolean rwPaths;

    public static JailbreakInfo fromRootBeer(RootBeer rootBeer) {
        return fromRootBeer(rootBeer, new JailbreakInfo());
    }

    public static JailbreakInfo fromRootBeer(RootBeer rootBeer, JailbreakInfo info) {
        info.isRooted = true;
        info.rootManagementApps = rootBeer.detectRootManagementApps();
        info.potentiallyDangerousApps = rootBeer.detectPotentiallyDangerousApps();
        info.rootCloakingApps = rootBeer.detectRootCloakingApps();
        info.testKeys = rootBeer.detectTestKeys();
        info.dangerousProps = rootBeer.checkForDangerousProps();
        info.busyBoxBinary = rootBeer.checkForBusyBoxBinary();
        info.suBinary = rootBeer.checkForSuBinary();
        info.suExists = rootBeer.checkSuExists();
        info.rwPaths = rootBeer.checkForRWPaths();

        return info;
    }

    private static void setJsonWriterBooleanField(JsonWriter writer, String fieldName, Boolean value) throws Exception {
        if (value != null) {
            writer.name(fieldName).value(value);
        } else {
            writer.name(fieldName).nullValue();
        }
    }

    public boolean isRootedDevice() {
        return Boolean.TRUE.equals(isRooted);
    }

    public String toJSON() {
        String json = "{}";
        try (StringWriter sw = new StringWriter(); JsonWriter writer = new JsonWriter(sw)) {
            writer.beginObject();
            setJsonWriterBooleanField(writer, "isRooted", isRooted);
            setJsonWriterBooleanField(writer, "rootManagementApps", rootManagementApps);
            setJsonWriterBooleanField(writer, "potentiallyDangerousApps", potentiallyDangerousApps);
            setJsonWriterBooleanField(writer, "rootCloakingApps", rootCloakingApps);
            setJsonWriterBooleanField(writer, "testKeys", testKeys);
            setJsonWriterBooleanField(writer, "dangerousProps", dangerousProps);
            setJsonWriterBooleanField(writer, "busyBoxBinary", busyBoxBinary);
            setJsonWriterBooleanField(writer, "suBinary", suBinary);
            setJsonWriterBooleanField(writer, "suExists", suExists);
            setJsonWriterBooleanField(writer, "rwPaths", rwPaths);
            writer.endObject();

            json = sw.toString();
        } catch (Exception exception) {
            Log.e(TAG, "Failed to generate jailbreak detected file content", exception);
        }
        return json;
    }
}