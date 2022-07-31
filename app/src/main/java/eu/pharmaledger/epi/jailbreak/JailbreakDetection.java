package eu.pharmaledger.epi.jailbreak;

import android.content.Context;
import android.content.res.Resources;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.safetynet.SafetyNet;
import com.google.android.gms.safetynet.SafetyNetApi;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.scottyab.rootbeer.RootBeer;

import java.io.File;
import java.io.FileWriter;
import java.security.SecureRandom;
import java.text.MessageFormat;

import eu.pharmaledger.epi.R;

public class JailbreakDetection {
    private static final String TAG = JailbreakDetection.class.getCanonicalName();

    public void detect(final Context applicationContext, final File jailbreakDetectedFile) {
        String apiKey = applicationContext.getString(R.string.safety_net_api_key);

        // The nonce should be at least 16 bytes in length.
        // You must generate the value of API_KEY in the Google APIs dashboard.
        SafetyNet.getClient(applicationContext).attest(generateNonce(), apiKey)
                .addOnSuccessListener(new OnSuccessListener<SafetyNetApi.AttestationResponse>() {
                    @Override
                    public void onSuccess(SafetyNetApi.AttestationResponse response) {
                        // Indicates communication with the service was successful.
                        // Use response.getJwsResult() to get the result data.
                        String result = decodeJws(response.getJwsResult());
                        Log.i(TAG, "Received result from SafetyNet: " + result);
                        JailbreakInfo jailbreakInfo = JailbreakInfo.fromSafetyNetResponse(result);
                        addAdditionalRootBeerInfoIfRootedDevice(applicationContext, jailbreakInfo);
                        writeJailbreakInfoToFile(jailbreakDetectedFile, jailbreakInfo);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        // An error occurred while communicating with the service.
                        if (e instanceof ApiException) {
                            // An error with the Google Play services API contains some
                            // additional details.
                            ApiException apiException = (ApiException) e;
                            Log.e(TAG, MessageFormat.format("An error has occurred with Google Play services, error {0}",
                                    apiException.getStatusCode()), e);
                        } else {
                            Log.e(TAG, MessageFormat.format("An error unknown has occurred with Google Play services, error {0}",
                                    e.getMessage()), e);

                        }

                        JailbreakInfo jailbreakInfo = new JailbreakInfo();
                        addAdditionalRootBeerInfoIfRootedDevice(applicationContext, jailbreakInfo);
                        writeJailbreakInfoToFile(jailbreakDetectedFile, jailbreakInfo);
                    }
                });
    }

    private void addAdditionalRootBeerInfoIfRootedDevice(Context applicationContext, JailbreakInfo jailbreakInfo) {
        final RootBeer rootBeer = new RootBeer(applicationContext);
        if (rootBeer.isRooted()) {
            Log.i(TAG, "Device is rooted!");
            JailbreakInfo.fromRootBeer(rootBeer, jailbreakInfo);
        } else {
            Log.i(TAG, "Didn't find indication of rooted device!");
        }
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

    private String decodeJws(String jwsResult) {
        if (jwsResult == null) {
            return "";
        }
        final String[] jwtParts = jwsResult.split("\\.");
        if (jwtParts.length == 3) {
            String decodedPayload = new String(Base64.decode(jwtParts[1], Base64.DEFAULT));
            return decodedPayload;
        } else {
            return "";
        }
    }

    private byte[] generateNonce() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] nonce = new byte[16];
        secureRandom.nextBytes(nonce);
        return nonce;
    }
}
