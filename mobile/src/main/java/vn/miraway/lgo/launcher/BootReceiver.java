package vn.miraway.lgo.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.

        Log.i("BroadcastReceiver", "onReceive");

        boolean auto_start = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("auto_start_app_switch", false);

        Log.i("BroadcastReceiver", "auto_start: " + auto_start);

        if (auto_start){
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){
                Intent mainIntent = new Intent(context, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(mainIntent);
            }
        }
    }
}
