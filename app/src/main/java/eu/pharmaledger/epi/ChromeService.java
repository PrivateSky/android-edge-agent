package eu.pharmaledger.epi;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

public class ChromeService {
    //Minimum version of Chrome supported
    public static int MIN_CHROME = 69;

    private final PackageManager packageManager;
    private final MainActivity mainActivity;

    public ChromeService(PackageManager packageManager, MainActivity mainActivity) {
        this.packageManager = packageManager;
        this.mainActivity = mainActivity;
    }

    public boolean isChromeVersionOK() {
        return getChromeVersion() >= MIN_CHROME;
    }

    public void showWarning() {
        AlertDialog alertDialog = new AlertDialog.Builder(mainActivity).create();
        alertDialog.setTitle("Wrong Chrome version");
        alertDialog.setMessage("Minimum Chrome version should be: " + MIN_CHROME + ". Please update you " +
                " Chrome version to the last possible one for your device and try again.");
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        mainActivity.finish();

                        //Sling user to Google Play's Chrome page
                        mainActivity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.android.chrome")));
                    }
                });
        alertDialog.show();
    }

    private int getChromeVersion() {
        PackageInfo pInfo;
        try {
            pInfo = packageManager.getPackageInfo("com.android.chrome", 0);
        } catch (PackageManager.NameNotFoundException e) {
            //chrome is not installed on the device
            return -1;
        }
        if (pInfo != null) {
            //Chrome has versions like 68.0.3440.91, we need to find the major version
            //using the first dot we find in the string
            int firstDotIndex = pInfo.versionName.indexOf(".");
            //take only the number before the first dot excluding the dot itself
            String majorVersion = pInfo.versionName.substring(0, firstDotIndex);
            return Integer.parseInt(majorVersion);
        }
        return -1;
    }

}
