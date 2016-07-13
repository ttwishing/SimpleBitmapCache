package com.ttwishing.library.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.zip.ZipFile;

/**
 * Created by kurt on 11/19/15.
 */
public class VersionUtil {

    private static Info _cachedInfo;
    private static Long cachedAppBuildTime;

    public static int getVersionCodeFromManifest(Context context) {
        return getInfo(context).versionCode;
    }

    public static String getVersionNameFromManifest(Context context) {
        return getInfo(context).versionName;
    }


    public static Info getInfo(Context context) {
        if (_cachedInfo == null) {
            try {
                PackageInfo localPackageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                _cachedInfo = new Info(localPackageInfo.versionCode, localPackageInfo.versionName);
                return _cachedInfo;
            } catch (PackageManager.NameNotFoundException e) {
                _cachedInfo = new Info(1, "3.2.0.0.0.1");
            }
        }
        return _cachedInfo;
    }


    public static class Info {
        public final int versionCode;
        public final String versionName;

        private Info(int versionCode, String versionName) {
            this.versionCode = versionCode;
            this.versionName = versionName;
        }
    }

}
