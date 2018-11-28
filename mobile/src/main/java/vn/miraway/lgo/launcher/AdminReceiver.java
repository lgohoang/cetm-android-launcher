package vn.miraway.lgo.launcher;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Lgo on 06/12/2017.
 */

public class AdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onDisabled(Context context, Intent intent) {
        // Called when the app is about to be deactivated as a device administrator.
        // Deletes previously stored password policy.
        super.onDisabled(context, intent);
//        SharedPreferences prefs = context.getSharedPreferences(APP_PREF, Activity.MODE_PRIVATE);
//        prefs.edit().clear().commit();
    }
}
