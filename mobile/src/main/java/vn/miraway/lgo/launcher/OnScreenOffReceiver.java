package vn.miraway.lgo.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

public class OnScreenOffReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.

        Log.i("BroadcastReceiver", "OnScreenOffReceiver");

        if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){
            MainActivity ctx = (MainActivity) context;
            // is Kiosk Mode active?
//            if(isKioskModeActive(ctx)) {
                wakeUpDevice(ctx);
//            }
        }

    }

    private void wakeUpDevice(MainActivity context) {
        PowerManager.WakeLock wakeLock = context.getWakeLock(); // get WakeLock reference via AppContext

        if (wakeLock.isHeld()) {
            wakeLock.release(); // release old wake lock
        }

        // create a new wake lock...
        wakeLock.acquire();

        // ... and release again
        wakeLock.release();
    }

//    private boolean isKioskModeActive(final Context context) {
//        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
//        return sp.getBoolean(PREF_KIOSK_MODE, false);
//    }
}
