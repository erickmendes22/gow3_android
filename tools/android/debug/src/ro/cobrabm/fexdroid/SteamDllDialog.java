package ro.cobrabm.fexdroid;

import android.app.Activity;
import android.widget.Toast;
import java.io.File;

/**
 * Debug-only replacement: Steam/Lossless download is a release feature. Public so the launcher's
 * Home page (com.gow3.android) can reach it; the real release implementation is supplied out of
 * band and gated on tools/android/inputs/fetch_steam_libs.sh writing steam-libs/jars.txt.
 */
public final class SteamDllDialog {
    public SteamDllDialog(Activity activity, File ignoredTarget) {
        this.activity = activity;
    }

    private final Activity activity;

    public void show() {
        Toast.makeText(activity,
            "Steam downloads are not part of this build. Use IMPORT .DLL on the launcher Home page "
                + "to add Lossless.dll yourself.", Toast.LENGTH_LONG).show();
    }
}