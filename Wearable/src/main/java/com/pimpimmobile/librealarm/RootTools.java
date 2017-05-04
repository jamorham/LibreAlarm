package com.pimpimmobile.librealarm;

import android.content.Context;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.PowerManager;
import android.util.Log;

import com.pimpimmobile.librealarm.shareddata.AlgorithmUtil;
import com.pimpimmobile.librealarm.shareddata.PreferencesUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

class RootTools {

    private static final String TAG = "LibreAlarm" + RootTools.class.getSimpleName();
    private static final boolean DEBUG = AlgorithmUtil.DEBUG;

    private static Boolean sHasRoot = null;
    private static boolean sScriptsCreated = false;
    private static boolean mNfcDestinationState = false;


    private static PowerManager.WakeLock mWakeLock;


    public static synchronized boolean isHasRoot() {
        if (sHasRoot == null) {
            sHasRoot = (new File("/system/xbin/su").exists());
        }
        return sHasRoot;
    }

    private static synchronized void createScripts() {
        if (sScriptsCreated) return;
        // switches to lowest possible power levels on cpu
        final File file_dir = libreAlarm.getAppContext().getFilesDir();
        if (!file_dir.exists()) {
            Log.d(TAG, "Creating folder for: " + file_dir);
            file_dir.mkdirs();
        }

        String script_name = file_dir + "/powersave.sh";
        writeToFile(script_name, "echo powersave > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor\necho 0 >/sys/devices/system/cpu/cpu1/online\n");
        //writeToFile(script_name, "echo powersave > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor\n\n");

        // restore cpu speed somewhat
        script_name = file_dir + "/performance.sh";
        // seems to crash sometimes waking up the second cpu so lets not do that
        //writeToFile(script_name, "echo interactive > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor\necho 1 >/sys/devices/system/cpu/cpu1/online\n");
        writeToFile(script_name, "echo interactive > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor\n\n");


        // disables the touch sense on the keypad by nuking the driver
        script_name = file_dir + "/killtouch.sh";
        writeToFile(script_name, "echo synaptics_dsx.0 >/sys/bus/platform/drivers/synaptics_dsx/unbind\n" +
                "a=`grep -l ^/system/bin/key_sleep_vibrate_service /proc/*/cmdline`\n" +
                "if [ \"$a\" != \"\" ]\n" +
                "then\n" +
                "let p=6\n" +
                "while [ $p -lt 14 ] && [ \"${a:$p:1}\" != \"/\" ] \n" +
                "do\n" +
                "let p=$p+1\n" +
                "done\n" +
                "let l=$p-6\n" +
                "b=\"${a:6:$l}\"\n" +
                "echo \"$b\"\n" +
                "kill \"$b\"\n" +
                "fi\n" +
                "setprop ctl.stop key_vibrate\n" +
                "\n");

        // removes xdrip watchface if present
        script_name = file_dir + "/uninstallxdrip.sh";
        writeToFile(script_name, "pm uninstall com.eveningoutpost.xdrip\n" +
                "\n");


        sScriptsCreated = true;
    }

    private static void writeToFile(String filename, String data) {
        try {
            File the_file = new File(filename);
            // if (!the_file.exists())
            //  {
            FileOutputStream out = new FileOutputStream(the_file);
            out.write(data.getBytes(Charset.forName("UTF-8")));
            out.close();
            // }
        } catch (IOException e) {
            Log.e(TAG, "File write failed: " + e.toString());
        }
    }

    public static void executeScripts(boolean state) {
        executeScripts(state, 0);
    }

    // platform specific method for enabling/disabling nfc - not sure if there is a better api based method
    public static void executeScripts(final boolean state, final long delay) {
        if (!isHasRoot()) return;
        createScripts();
        if (delay > 0) {
            JoH.runOnUiThreadDelayed(new Runnable() {
                @Override
                public void run() {
                    new Thread() {
                        @Override
                        public void run() {
                            runRootScripts(state);
                        }
                    }.start();
                }
            }, delay);
        } else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    runRootScripts(state);
                }
            }
            ).start();
        }
    }

    private static synchronized void runRootScripts(boolean state) {
        mWakeLock = JoH.getWakeLock("Nfc-control", 30000);
        final Context mContext = libreAlarm.getAppContext();
        try {
            if (mNfcDestinationState == state) {
                Log.e(TAG, "Destination state changed from: " + state + " to " + mNfcDestinationState + " .. skipping switch!");
            } else {
                //final boolean needs_root = true; // unclear at the moment whether we need root for this

                /*if (state) {
                    if (JoH.quietratelimit("speedup-cpu", PreferencesUtil.slowCpu(mContext) ? 1 : 3600)) {
                        if (DEBUG) Log.d(TAG, "Switching to higher performance cpu speed");
                        final Process execute4 = Runtime.getRuntime().exec("su -c sh " + mContext.getFilesDir() + "/performance.sh");
                    }
                }*/

                if (state || PreferencesUtil.toggleNFC(mContext)) {
                    final NfcManager nfcManager =
                            (NfcManager) libreAlarm.getAppContext().getSystemService(Context.NFC_SERVICE);
                    final NfcAdapter mNfcAdapter = nfcManager.getDefaultAdapter(); // could be static?
                    if (mNfcAdapter != null) {
                        if (mNfcAdapter.isEnabled() != state) {
                            for (int counter = 0; counter < 5; counter++) {
                                Log.i(TAG, "Trying to switch nfc " + (state ? "on" : "off"));
                                // static version of this is duplicated below
                                final Process execute = Runtime.getRuntime().exec("su -c service call nfc " + (state ? "6" : "5")); // turn NFC on or off
                                if (showProcessOutput(execute) != null) {
                                    Log.e(TAG, "Got error- retrying.." + counter);
                                } else {
                                    break;
                                }
                            }
                        } else {
                            Log.d(TAG, "Nfc adapter is already in desired state of: " + (state ? "on" : "off"));
                        }
                    } else {
                        Log.e(TAG, "No NFC adapter found!!");
                    }
                }

                if (!state) {
                    if (PreferencesUtil.disableTouchscreen(mContext)) {
                        // TODO check if already disabled
                        if (JoH.quietratelimit("disable-touchscreen", 7200)) {
                            if (DEBUG) Log.d(TAG, "Disabling touchscreen!");
                            final Process execute1 = Runtime.getRuntime().exec("su -c sh " + mContext.getFilesDir() + "/killtouch.sh");
                            if (DEBUG) showProcessOutput(execute1);
                        }
                    }

                    if ((WearActivity.got_tag_data) && (PreferencesUtil.uninstallxDrip(mContext))) {
                        if (JoH.quietratelimit("uninstall-xdrip", 60000)) {
                            Log.d(TAG, "Attempting to uninstall xdrip");
                            final Process execute1 = Runtime.getRuntime().exec("su -c sh " + mContext.getFilesDir() + "/uninstallxdrip.sh");
                            if (DEBUG) showProcessOutput(execute1);
                        }
                    }

                    if (PreferencesUtil.slowCpu(mContext)) {
                        if (DEBUG) Log.d(TAG, "Switching to lower powersave cpu speed");
                        final Process execute2 = Runtime.getRuntime().exec("su -c sh " + mContext.getFilesDir() + "/powersave.sh");
                        if (DEBUG) showProcessOutput(execute2);
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Got exception executing root nfc off: " + e.toString());
        } finally {
            if (mWakeLock.isHeld()) mWakeLock.release();
        }
        mNfcDestinationState = state;
    }


    private static String showProcessOutput(Process execute) {
        try {
            execute.waitFor();
            if (DEBUG)
                Log.d(TAG, "PROCESS OUTPUT: " + (new BufferedReader(new InputStreamReader(execute.getInputStream())).readLine()));
            String error = (new BufferedReader(new InputStreamReader(execute.getErrorStream())).readLine());
            if (DEBUG) Log.d(TAG, " PROCESS ERROR: " + error);
            return error;
        } catch (InterruptedException | IOException e) {
            Log.d(TAG, "Got error showing process output: " + e.toString());
        }
        return "other error";
    }


    public static Process swichNFCState(boolean state) {
        Log.i(TAG, "Trying to switch nfc " + (state ? "on" : "off"));
        try {
            return Runtime.getRuntime().exec("su -c service call nfc " + (state ? "6" : "5"));
        } catch (Exception e) {
            Log.e(TAG, "Got exception changing nfc state: " + e);
        }
        return null;
    }

}
