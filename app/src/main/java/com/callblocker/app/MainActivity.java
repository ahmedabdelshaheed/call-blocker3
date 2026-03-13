package com.callblocker.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private SharedPreferences prefs;
    private ListView lvWhitelist;
    private ArrayAdapter<String> adapter;
    private List<String> whitelistNumbers;
    private Switch switchEnabled;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("CallBlocker", Context.MODE_PRIVATE);
        whitelistNumbers = new ArrayList<>(getWhitelistFromPrefs());

        initViews();
        requestPermissions();
        updateStatus();

        // شغّل الخدمة تلقائياً لو كانت مفعّلة
        if (prefs.getBoolean("enabled", true)) {
            startBlockerService();
        }
    }

    private void initViews() {
        switchEnabled = findViewById(R.id.switchEnabled);
        tvStatus = findViewById(R.id.tvStatus);
        lvWhitelist = findViewById(R.id.lvWhitelist);
        EditText etNumber = findViewById(R.id.etNumber);
        Button btnAdd = findViewById(R.id.btnAdd);

        switchEnabled.setChecked(prefs.getBoolean("enabled", true));
        switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("enabled", isChecked).apply();
            if (isChecked) {
                startBlockerService();
                tvStatus.setText("✅ التطبيق شغّال - المكالمات محجوبة");
                tvStatus.setTextColor(getColor(R.color.green));
            } else {
                stopService(new Intent(this, CallBlockerService.class));
                tvStatus.setText("⛔ التطبيق متوقف");
                tvStatus.setTextColor(getColor(R.color.red));
            }
        });

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, whitelistNumbers);
        lvWhitelist.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> {
            String num = etNumber.getText().toString().trim();
            if (!num.isEmpty() && !whitelistNumbers.contains(num)) {
                whitelistNumbers.add(num);
                adapter.notifyDataSetChanged();
                saveWhitelistToPrefs();
                etNumber.setText("");
                Toast.makeText(this, "✅ تم إضافة " + num, Toast.LENGTH_SHORT).show();
            }
        });

        // حذف رقم بالضغط الطويل
        lvWhitelist.setOnItemLongClickListener((parent, view, position, id) -> {
            String removed = whitelistNumbers.get(position);
            whitelistNumbers.remove(position);
            adapter.notifyDataSetChanged();
            saveWhitelistToPrefs();
            Toast.makeText(this, "🗑️ تم حذف " + removed, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void updateStatus() {
        if (prefs.getBoolean("enabled", true)) {
            tvStatus.setText("✅ التطبيق شغّال - المكالمات محجوبة");
            tvStatus.setTextColor(getColor(R.color.green));
        } else {
            tvStatus.setText("⛔ التطبيق متوقف");
            tvStatus.setTextColor(getColor(R.color.red));
        }
    }

    private void startBlockerService() {
        Intent intent = new Intent(this, CallBlockerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private Set<String> getWhitelistFromPrefs() {
        return prefs.getStringSet("whitelist", new HashSet<>());
    }

    private void saveWhitelistToPrefs() {
        prefs.edit().putStringSet("whitelist", new HashSet<>(whitelistNumbers)).apply();
    }

    private void requestPermissions() {
        String[] perms = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ANSWER_PHONE_CALLS
        };
        List<String> needed = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "⚠️ التطبيق محتاج كل الصلاحيات عشان يشتغل", Toast.LENGTH_LONG).show();
            }
        }
    }
}
