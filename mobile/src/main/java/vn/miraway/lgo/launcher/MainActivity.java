package vn.miraway.lgo.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.SystemUpdatePolicy;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import org.xwalk.core.XWalkView;
import org.xwalk.core.XWalkPreferences;
import org.xwalk.core.XWalkSettings;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.security.Policy;
import java.security.PolicySpi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import android.view.View;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.webkit.URLUtil;
import android.preference.EditTextPreference;
import android.preference.PreferenceScreen;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Timer timer = new Timer();

    XWalkView xWalkView;
    XWalkSettings xWalkSettings;

    private int countToStart = 10;
    private int count = 0;
    private long startMillis=0;
    Intent intent;

    SharedPreferences prefs;
    SharedPreferences.Editor editor;

    private PowerManager.WakeLock wakeLock;
    private OnScreenOffReceiver onScreenOffReceiver;
    private boolean registerOnScreenOffReceiver = false;

    private ComponentName mAdminComponentName;
    private DevicePolicyManager mDevicePolicyManager;

    private URL url;
    private URL baseUrl;


    HttpURLConnection connect;
    boolean sv_online = false;

    Handler handler = new Handler();

    IntentFilter filter;

    final String TAG = "Main";

    final int TimerTick = 30000;
    final int CheckOnnectionCountError = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainView = (View) findViewById(R.id.main);
        intent = new Intent(this, SettingsActivity.class);

        mAdminComponentName = new ComponentName(this,AdminReceiver.class);
        mDevicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);




        //Setting
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        editor = prefs.edit();

        setRequestedOrientation(Integer.parseInt(prefs.getString("screen_orientation_list", "0")));


        if (prefs.getBoolean("pin_app_switch", false)){
            startLockTask();
        }

        //registerKioskModeScreenOffReceiver
        filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        onScreenOffReceiver = new OnScreenOffReceiver();
        wakeLock = getWakeLock();
//        registerKioskModeScreenOffReceiver();

        //Disable Lock Screen & dismiss screen
        DisableLockScreen();

        url = UrlParse(getIntent().getStringExtra("address"));

        XWalkPreferences.setValue(XWalkPreferences.REMOTE_DEBUGGING, true);

        xWalkView = (XWalkView) findViewById(R.id.XWalkView);
        xWalkSettings = xWalkView.getSettings();


        xWalkView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                int action = motionEvent.getAction();
                if (action == MotionEvent.ACTION_UP){
                    //get system current milliseconds
                    long time= System.currentTimeMillis();


                    //if it is the first time, or if it has been more than 3 seconds since the first tap ( so it is like a new try), we reset everything
                    if (startMillis==0 || (time-startMillis> 3000) ) {
                        startMillis=time;
                        count=1;
                    }
                    //it is not the first, and it has been  less than 3 seconds since the first
                    else{ //  time-startMillis< 3000
                        count++;
                    }

                    if (count==countToStart) {
                        startActivity(intent);
                    }

                    return false;
                }

                return false;
            }
        });


        if (url !=  null){
            if (url.toString() != ""){
                startXwalk(url.toString());
            }else{
                url = UrlParse(getUrl());
            }
        }else{
            url = UrlParse(getUrl());
        }

        if (url != null){
            startXwalk(url.toString());
        }




        timer.schedule(new TimerTask() {
            @Override
            public void run() {

                Hide();

                if (baseUrl != null){
                    boolean status = CheckConnection(baseUrl);

                    if (status){
                        handler.removeCallbacks(ServerOff);
                        handler.post(ServerOn);
                    }else{
                        handler.removeCallbacks(ServerOn);
                        handler.post(ServerOff);
                    }
                }

            }

        }, 1000, TimerTick);
    }

    private final Runnable ServerOn = new Runnable() {
        @Override
        public void run() {

            Log.i(TAG, "Server On");

            if (!sv_online){
                sv_online = true;

                Log.i(TAG, "Server On - Reload");

                if (url != null){
                    startXwalk(url.toString());
                }

                registerKioskModeScreenOffReceiver();
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//                DisableLockScreen();

                Toast.makeText(getBaseContext(), "Make screen alway on", Toast.LENGTH_LONG).show();
            }

            if (xWalkView.getUrl() == null){
                url = UrlParse(getUrl());

                startXwalk(url.toString());
            }



            errCount = 0;
        }
    };

    private int errCount = 0;

    private final Runnable ServerOff = new Runnable() {
        @Override
        public void run() {

            Log.i(TAG, "Server Off " + errCount);



            if (errCount >= CheckOnnectionCountError){

                Log.i(TAG, "Server Off Clear Setting");
//                Toast.makeText(getBaseContext(), "Make screen default", Toast.LENGTH_LONG).show();

                sv_online = false;
                unRegisterKioskModeScreenOffReceiver();
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//                EnableLockScreen();
            }

            if (errCount >= 50){
                errCount = 0;
            }

            errCount++;

        }
    };

    private URL UrlParse(String url){
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            Log.i(TAG, "UrlParse: " + e);
        }

        return null;
    }

    private boolean CheckConnection(URL u){

        boolean temp = false;

        try {

            Log.i(TAG, "Check Connection url: " + u.toString());

            if (url != null){

                connect = (HttpURLConnection)u.openConnection();
                connect.setConnectTimeout(30000);
                connect.connect();

                if (connect.getResponseCode() == HttpURLConnection.HTTP_OK){
                    temp = true;
                }else{
                    temp = false;
                }
            }
        } catch (Exception e) {
            temp = false;
        } finally {
            if(connect != null){
                connect.disconnect();
            }
        }

        return temp;
    }

    private void setDefaultCosuPolicies(boolean active){

        // Set user restrictions
        setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, active);
        setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, active);
        setUserRestriction(UserManager.DISALLOW_ADD_USER, active);
        setUserRestriction(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, active);
        setUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME, active);

        // Disable keyguard and status bar
        mDevicePolicyManager.setKeyguardDisabled(mAdminComponentName, active);
        mDevicePolicyManager.setStatusBarDisabled(mAdminComponentName, active);

        // Enable STAY_ON_WHILE_PLUGGED_IN
        enableStayOnWhilePluggedIn(active);

        // Set system update policy
        if (active){
            mDevicePolicyManager.setSystemUpdatePolicy(mAdminComponentName, SystemUpdatePolicy.createWindowedInstallPolicy(60, 120));
        } else {
            mDevicePolicyManager.setSystemUpdatePolicy(mAdminComponentName,null);
        }

        // set this Activity as a lock task package
        mDevicePolicyManager.setLockTaskPackages(mAdminComponentName,active ? new String[]{getPackageName()} : new String[]{});

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
        intentFilter.addCategory(Intent.CATEGORY_HOME);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);

        if (active) {
            // set Cosu activity as home intent receiver so that it is started
            // on reboot
            mDevicePolicyManager.addPersistentPreferredActivity(mAdminComponentName, intentFilter, new ComponentName(getPackageName(), MainActivity.class.getName()));
        } else {
            mDevicePolicyManager.clearPackagePersistentPreferredActivities(mAdminComponentName, getPackageName());
        }
    }

    private void setUserRestriction(String restriction, boolean disallow){
        if (disallow) {
            mDevicePolicyManager.addUserRestriction(mAdminComponentName,restriction);
        } else {
            mDevicePolicyManager.clearUserRestriction(mAdminComponentName,restriction);
        }
    }

    private void enableStayOnWhilePluggedIn(boolean enabled){
        if (enabled) {
            mDevicePolicyManager.setGlobalSetting(mAdminComponentName, Settings.Global.STAY_ON_WHILE_PLUGGED_IN,Integer.toString(BatteryManager.BATTERY_PLUGGED_AC| BatteryManager.BATTERY_PLUGGED_USB| BatteryManager.BATTERY_PLUGGED_WIRELESS));
        } else {
            mDevicePolicyManager.setGlobalSetting(mAdminComponentName,Settings.Global.STAY_ON_WHILE_PLUGGED_IN,"0");
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("pin_app_switch")){
            if (sharedPreferences.getBoolean("pin_app_switch", false)){

                if(mDevicePolicyManager.isDeviceOwnerApp(getPackageName())){
                    // App is whitelisted
                    setDefaultCosuPolicies(true);
                }
                startLockTask();

            }else{
//                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
//
//                if (am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED) {
//                    stopLockTask();
//                }

                stopLockTask();

                if(mDevicePolicyManager.isDeviceOwnerApp(getPackageName())){
                    // App is whitelisted
                    setDefaultCosuPolicies(false);
                }
            }
        }

        if (key.equals("admin_app_switch")){
            if (sharedPreferences.getBoolean("admin_app_switch", false)){
                if (!mDevicePolicyManager.isDeviceOwnerApp(getPackageName())){
                    editor.putBoolean("admin_app_switch", false);
                    editor.commit();
                }
            }else{
                if (mDevicePolicyManager.isDeviceOwnerApp(getPackageName())){
//                    mDevicePolicyManager.removeActiveAdmin(mAdminComponentName);
                    mDevicePolicyManager.clearDeviceOwnerApp(mAdminComponentName.getPackageName());
                }
            }
        }

        if (key.equals("screen_orientation_list")){
            setRequestedOrientation(Integer.parseInt(prefs.getString("screen_orientation_list", "0")));
        }

        if (key.equals("address") || key.equals("application_list")){
            url = UrlParse(getUrl());
            sv_online = !sv_online;
        }

//        url = UrlParse(getUrl());
//        sv_online = !sv_online;
////        startXwalk(url.toString());
    }

    public String getUrl(){
        String address = prefs.getString("address", null);
        String app = prefs.getString("application_list", null);

        baseUrl = UrlParse(address);

        if (address == null || app == null){
            startActivity(intent);
        }else{
            switch (app){
                default:
                    break;
                case "1":
                    address += "/device/#/kiosk";
                    break;
                case "2":
                    address += "/device/#/screen";
                    break;
                case "3":
                    address += "/device/#/feedback";
                    break;
                case "4":
                    address += "/app/#/counter";
                    break;
            }
        }



        return address;
    }



    public void startXwalk(String address){
        xWalkSettings.setSupportZoom(false);
        xWalkSettings.setCacheMode(Integer.parseInt(prefs.getString("cache_mode_list","-1")));

        Log.i(TAG,"startXwalk - Get Url: " + xWalkView.getUrl());

        if (xWalkView.getUrl() == null || !xWalkView.getUrl().equals(url)){
            xWalkView.loadUrl(address);
        }else{
            xWalkView.reload(Integer.parseInt(prefs.getString("cache_mode_list","-1")));
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int eventaction = event.getAction();
        if (eventaction == MotionEvent.ACTION_UP) {
            //get system current milliseconds
            long time= System.currentTimeMillis();
            //if it is the first time, or if it has been more than 3 seconds since the first tap ( so it is like a new try), we reset everything
            if (startMillis==0 || (time-startMillis> 3000) ) {
                startMillis=time;
                count=1;
            }
            //it is not the first, and it has been  less than 3 seconds since the first
            else{ //  time-startMillis< 3000
                count++;
            }
            if (count==countToStart) {
                //do whatever you need
                startActivity(intent);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }


    @Override
    public void onPause()
    {
        super.onPause();
    }


    private View mainView;
    private final Handler mHideHandler = new Handler();

    private void Hide(){

        //mHideHandler.post(HideActionBar);

        mHideHandler.removeCallbacks(OutFullScreen);

        mHideHandler.postDelayed(FullScreen, 300);
    }

    private void show(){

        mHideHandler.removeCallbacks(FullScreen);
        mHideHandler.postDelayed(OutFullScreen, 300);
    }


    private final Runnable FullScreen = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {

            mainView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LOW_PROFILE
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };

    private final Runnable HideActionBar = new Runnable() {
        @Override
        public void run() {
            // Hide UI first
            ActionBar actionBar = getSupportActionBar();

            if (actionBar != null) {
                Log.i(TAG, "Hide action bar");
                actionBar.hide();
            }
        }
    };



    private final Runnable OutFullScreen = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {

            mainView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
        }
    };

    private void registerKioskModeScreenOffReceiver() {
        // register screen off receiver
        if (!registerOnScreenOffReceiver){
            try
            {
                registerReceiver(onScreenOffReceiver, filter);

                if (wakeLock.isHeld()){
                    wakeLock.release();
                }

                wakeLock.acquire();
                wakeLock.release();

                registerOnScreenOffReceiver = true;
            }
            catch (Exception ex){
                Log.w(TAG, "registerKioskModeScreenOffReceiver: " + ex);
            }
        }
    }

    private void unRegisterKioskModeScreenOffReceiver() {
        // register screen off receiver
        if (registerOnScreenOffReceiver){
            try
            {
                unregisterReceiver(onScreenOffReceiver);
                registerOnScreenOffReceiver = false;
            }
            catch (Exception ex){
                Log.w(TAG, "unRegisterKioskModeScreenOffReceiver: " + ex);
            }
        }
    }

    public PowerManager.WakeLock getWakeLock() {
        if(wakeLock == null) {
            // lazy loading: first call, create wakeLock via PowerManager.
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "launcher:wakelock");
        }
        return wakeLock;
    }

    public void DisableLockScreen(){
        KeyguardManager keyguardManager = (KeyguardManager)getSystemService(Activity.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock lock = keyguardManager.newKeyguardLock(KEYGUARD_SERVICE);
        lock.disableKeyguard();
//        lock.reenableKeyguard();
    }

    public void EnableLockScreen(){
        KeyguardManager keyguardManager = (KeyguardManager)getSystemService(Activity.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock lock = keyguardManager.newKeyguardLock(KEYGUARD_SERVICE);
//        lock.disableKeyguard();
        lock.reenableKeyguard();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(!hasFocus) {
            // Close every kind of system dialog
            Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            sendBroadcast(closeDialog);
        }
    }

    private final List blockedKeys = new ArrayList(Arrays.asList(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP));

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (blockedKeys.contains(event.getKeyCode())) {
            return true;
        } else {
            return super.dispatchKeyEvent(event);
        }
    }

    @Override
    protected void onDestroy(){
        unRegisterKioskModeScreenOffReceiver();
        super.onDestroy();
    }
}
