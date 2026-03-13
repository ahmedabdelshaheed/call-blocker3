package com.callblocker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class CallReceiver extends BroadcastReceiver {

    private static final String TAG = "CallBlocker";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences("CallBlocker", Context.MODE_PRIVATE);

        // لو التطبيق متوقف، ما نعملش حاجة
        if (!prefs.getBoolean("enabled", true)) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state) && incomingNumber != null) {
            Log.d(TAG, "Incoming call from: " + incomingNumber);

            // جيب قائمة الأرقام المستثناة
            Set<String> whitelist = prefs.getStringSet("whitelist", new HashSet<>());

            boolean isWhitelisted = false;
            for (String num : whitelist) {
                // مقارنة مرنة (بدون كود الدولة)
                if (normalizeNumber(incomingNumber).endsWith(normalizeNumber(num)) ||
                    normalizeNumber(num).endsWith(normalizeNumber(incomingNumber))) {
                    isWhitelisted = true;
                    break;
                }
            }

            if (!isWhitelisted) {
                Log.d(TAG, "Blocking call from: " + incomingNumber);
                rejectCall(context);

                // سجّل آخر مكالمة محجوبة
                prefs.edit()
                    .putString("last_blocked", incomingNumber)
                    .putLong("last_blocked_time", System.currentTimeMillis())
                    .apply();
            } else {
                Log.d(TAG, "Allowing call from whitelisted: " + incomingNumber);
            }
        }
    }

    private void rejectCall(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Android 9+ - الطريقة الرسمية
                TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                if (telecomManager != null) {
                    telecomManager.endCall();
                }
            } else {
                // Android قديم - Reflection
                TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                Class<?> c = Class.forName(telephonyManager.getClass().getName());
                Method m = c.getDeclaredMethod("getITelephony");
                m.setAccessible(true);
                Object iTelephony = m.invoke(telephonyManager);
                Method endCall = iTelephony.getClass().getDeclaredMethod("endCall");
                endCall.setAccessible(true);
                endCall.invoke(iTelephony);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error rejecting call: " + e.getMessage());
        }
    }

    private String normalizeNumber(String num) {
        if (num == null) return "";
        return num.replaceAll("[^0-9]", "");
    }
}
