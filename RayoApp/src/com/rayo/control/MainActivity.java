package com.rayo.control;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements BleManager.BleListener {

    private static final int REQ_PERMISSIONS = 101;
    private static final int REQ_ENABLE_BT = 102;

    private BleManager bleManager;
    private boolean isLightOn = false;
    private boolean isConnected = false;
    private int currentProfile = 0; // 0..3
    private int lastBatteryPercent = -1;

    private TextView tvStatus;
    private TextView tvBattery;
    private Button btnScan;
    private Button btnCommandPalette;

    private Button btnLightOn;
    private Button btnLightOff;

    private Button[] btnProfiles = new Button[4];
    private TextView tvActiveProfile;

    private TextView tvBrightnessLabel;
    private SeekBar sbBrightness;
    private Spinner spnLedTop, spnLedMid, spnLedBot;

    private TextView tvLedTopBrightLabel, tvLedMidBrightLabel, tvLedBotBrightLabel;
    private SeekBar sbLedTopBright, sbLedMidBright, sbLedBotBright;
    private Switch swPerLedBrightness;

    private Switch swBrake;
    private Spinner spnBrakeSens;
    private Button btnApplyLeds;

    private Switch swTheft;
    private Button btnApplyFeatures;
    private Switch swShowDevOptions;

    private TextView tvBleLog;
    private Button btnClearLog;
    private UiDesignHelpers.FloatingToastController floatingToast;
    private long lastUserProfileSelectTime = 0;

    private String[] patterns;
    private String[] sensitivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        patterns = getResources().getStringArray(R.array.patterns_array);
        sensitivity = getResources().getStringArray(R.array.sensitivity_array);

        initViews();
        bleManager = new BleManager(this, this);

        checkPermissions();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvBattery = findViewById(R.id.tvBattery);
        btnScan = findViewById(R.id.btnScan);
        btnCommandPalette = findViewById(R.id.btnCommandPalette);

        floatingToast = new UiDesignHelpers.FloatingToastController(
                findViewById(R.id.layoutFloatingToast),
                (TextView) findViewById(R.id.tvToastMessage),
                (Button) findViewById(R.id.btnToastUndo)
        );

        btnLightOn = findViewById(R.id.btnLightOn);
        btnLightOff = findViewById(R.id.btnLightOff);

        btnProfiles[0] = findViewById(R.id.btnProfile0);
        btnProfiles[1] = findViewById(R.id.btnProfile1);
        btnProfiles[2] = findViewById(R.id.btnProfile2);
        btnProfiles[3] = findViewById(R.id.btnProfile3);
        tvActiveProfile = findViewById(R.id.tvActiveProfile);

        tvBrightnessLabel = findViewById(R.id.tvBrightnessLabel);
        sbBrightness = findViewById(R.id.sbBrightness);

        spnLedTop = findViewById(R.id.spnLedTop);
        spnLedMid = findViewById(R.id.spnLedMid);
        spnLedBot = findViewById(R.id.spnLedBot);

        tvLedTopBrightLabel = findViewById(R.id.tvLedTopBrightLabel);
        sbLedTopBright = findViewById(R.id.sbLedTopBright);
        tvLedMidBrightLabel = findViewById(R.id.tvLedMidBrightLabel);
        sbLedMidBright = findViewById(R.id.sbLedMidBright);
        tvLedBotBrightLabel = findViewById(R.id.tvLedBotBrightLabel);
        sbLedBotBright = findViewById(R.id.sbLedBotBright);
        swPerLedBrightness = findViewById(R.id.swPerLedBrightness);

        ArrayAdapter<String> patternAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, patterns);
        spnLedTop.setAdapter(patternAdapter);
        spnLedMid.setAdapter(patternAdapter);
        spnLedBot.setAdapter(patternAdapter);

        // Initial default selections
        spnLedTop.setSelection(0); // Constant
        spnLedMid.setSelection(2); // Blink
        spnLedBot.setSelection(1); // Glow

        // Individual LED Brightness listeners
        sbLedTopBright.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLedTopBrightLabel.setText(getString(R.string.brightness_label_format, progress));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (!swPerLedBrightness.isChecked()) {
                    sbBrightness.setProgress(seekBar.getProgress());
                }
                applyCurrentPatterns(false);
            }
        });

        sbLedMidBright.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLedMidBrightLabel.setText(getString(R.string.brightness_label_format, progress));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (!swPerLedBrightness.isChecked()) {
                    sbBrightness.setProgress(seekBar.getProgress());
                }
                applyCurrentPatterns(false);
            }
        });

        sbLedBotBright.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLedBotBrightLabel.setText(getString(R.string.brightness_label_format, progress));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (!swPerLedBrightness.isChecked()) {
                    sbBrightness.setProgress(seekBar.getProgress());
                }
                applyCurrentPatterns(false);
            }
        });

        swBrake = findViewById(R.id.swBrake);
        spnBrakeSens = findViewById(R.id.spnBrakeSens);
        spnBrakeSens.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sensitivity));
        spnBrakeSens.setSelection(1);

        btnApplyLeds = findViewById(R.id.btnApplyLeds);

        swTheft = findViewById(R.id.swTheft);
        btnApplyFeatures = findViewById(R.id.btnApplyFeatures);

        // Brightness SeekBar listener
        sbBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvBrightnessLabel.setText(getString(R.string.brightness_label_format, progress));
                if (fromUser && !swPerLedBrightness.isChecked()) {
                    sbLedTopBright.setProgress(progress);
                    sbLedMidBright.setProgress(progress);
                    sbLedBotBright.setProgress(progress);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                applyCurrentPatterns(false);
            }
        });

        // Click Listeners
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isConnected) {
                    bleManager.disconnect();
                } else {
                    if (ensureBluetoothEnabled()) {
                        bleManager.startScan();
                    }
                }
            }
        });

        // 1. DEDICATED LIGHT ON SEGMENT
        btnLightOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLightWithUndo(true);
            }
        });

        // 2. DEDICATED LIGHT OFF SEGMENT
        btnLightOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLightWithUndo(false);
            }
        });
        updatePowerSegmentUI(false);

        // 3. PROFILE SEGMENTED CONTROL
        for (int i = 0; i < 4; i++) {
            final int pIdx = i;
            btnProfiles[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectProfileWithUndo(pIdx);
                }
            });
        }
        selectProfileUI(0);

        // 4. APPLY LED PATTERNS & INDIVIDUAL BRIGHTNESS
        btnApplyLeds.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyCurrentPatterns(true);
            }
        });

        // 5. APPLY ALARM
        btnApplyFeatures.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bleManager.setLight(isLightOn, swTheft.isChecked());
                floatingToast.show(getString(R.string.toast_alarm_applied), null);
            }
        });

        // 6. DEVELOPER OPTIONS (COLLAPSIBLE BLE LOG)
        swShowDevOptions = findViewById(R.id.swShowDevOptions);
        final View layoutDevLogContainer = findViewById(R.id.layoutDevLogContainer);
        if (swShowDevOptions != null && layoutDevLogContainer != null) {
            swShowDevOptions.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    layoutDevLogContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                }
            });
        }

        tvBleLog = findViewById(R.id.tvBleLog);
        btnClearLog = findViewById(R.id.btnClearLog);
        if (btnClearLog != null) {
            btnClearLog.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (tvBleLog != null) tvBleLog.setText("");
                }
            });
        }

        // Command Palette button
        if (btnCommandPalette != null) {
            btnCommandPalette.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openCommandPalette();
                }
            });
        }

        UiDesignHelpers.attachPressFeedback(
                btnScan, btnCommandPalette,
                btnLightOn, btnLightOff,
                btnProfiles[0], btnProfiles[1], btnProfiles[2], btnProfiles[3],
                btnApplyLeds, btnApplyFeatures, btnClearLog
        );

        // 7. BUY ME A COFFEE
        View btnBuyMeACoffee = findViewById(R.id.btnBuyMeACoffee);
        if (btnBuyMeACoffee != null) {
            btnBuyMeACoffee.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/02eOWKzm5W"));
                        startActivity(browserIntent);
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    private void setLightWithUndo(final boolean turnOn) {
        final boolean prevState = isLightOn;
        isLightOn = turnOn;
        updatePowerSegmentUI(turnOn);
        bleManager.setLight(turnOn, swTheft.isChecked());
        String msg = getString(turnOn ? R.string.toast_light_on : R.string.toast_light_off);
        floatingToast.show(msg, (prevState != turnOn) ? new Runnable() {
            @Override
            public void run() {
                isLightOn = prevState;
                updatePowerSegmentUI(prevState);
                bleManager.setLight(prevState, swTheft.isChecked());
            }
        } : null);
    }

    private void selectProfileWithUndo(final int pIdx) {
        final int prevProfile = currentProfile;
        lastUserProfileSelectTime = System.currentTimeMillis();
        selectProfileUI(pIdx);
        bleManager.selectProfile(pIdx);
        String msg = getString(R.string.toast_profile_selected, pIdx + 1);
        floatingToast.show(msg, (prevProfile != pIdx) ? new Runnable() {
            @Override
            public void run() {
                lastUserProfileSelectTime = System.currentTimeMillis();
                selectProfileUI(prevProfile);
                bleManager.selectProfile(prevProfile);
            }
        } : null);
    }

    private void openCommandPalette() {
        List<UiDesignHelpers.CommandItem> cmds = new ArrayList<>();
        cmds.add(new UiDesignHelpers.CommandItem(
                getString(isConnected ? R.string.btn_disconnect : R.string.btn_scan),
                tvStatus.getText().toString(),
                new Runnable() {
                    @Override
                    public void run() {
                        btnScan.performClick();
                    }
                }
        ));
        cmds.add(new UiDesignHelpers.CommandItem(
                getString(R.string.light_state_on),
                getString(R.string.sec_power_switch),
                new Runnable() {
                    @Override
                    public void run() {
                        setLightWithUndo(true);
                    }
                }
        ));
        cmds.add(new UiDesignHelpers.CommandItem(
                getString(R.string.light_state_off),
                getString(R.string.sec_power_switch),
                new Runnable() {
                    @Override
                    public void run() {
                        setLightWithUndo(false);
                    }
                }
        ));
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            cmds.add(new UiDesignHelpers.CommandItem(
                    getString(R.string.toast_profile_selected, idx + 1),
                    getString(R.string.sec_profiles),
                    new Runnable() {
                        @Override
                        public void run() {
                            selectProfileWithUndo(idx);
                        }
                    }
            ));
        }
        cmds.add(new UiDesignHelpers.CommandItem(
                getString(R.string.btn_apply_leds),
                getString(R.string.sec_led_patterns),
                new Runnable() {
                    @Override
                    public void run() {
                        applyCurrentPatterns(true);
                    }
                }
        ));
        cmds.add(new UiDesignHelpers.CommandItem(
                getString(R.string.btn_apply_alarm),
                getString(R.string.sec_smart_features),
                new Runnable() {
                    @Override
                    public void run() {
                        btnApplyFeatures.performClick();
                    }
                }
        ));
        if (swShowDevOptions != null) {
            cmds.add(new UiDesignHelpers.CommandItem(
                    getString(R.string.cmd_toggle_dev_log),
                    getString(R.string.sec_developer_options),
                    new Runnable() {
                        @Override
                        public void run() {
                            swShowDevOptions.setChecked(!swShowDevOptions.isChecked());
                        }
                    }
            ));
        }
        UiDesignHelpers.showCommandPalette(this, cmds);
    }

    private void applyCurrentPatterns(boolean showToast) {
        if (!isConnected) {
            if (showToast) {
                floatingToast.show(getString(R.string.status_disconnected), null);
            }
            return;
        }

        int masterBright = sbBrightness.getProgress();
        int topBri = sbLedTopBright.getProgress();
        int midBri = sbLedMidBright.getProgress();
        int botBri = sbLedBotBright.getProgress();

        int top = spnLedTop.getSelectedItemPosition();
        int mid = spnLedMid.getSelectedItemPosition();
        int bot = spnLedBot.getSelectedItemPosition();

        boolean brake = swBrake.isChecked();
        int brakeSens = spnBrakeSens.getSelectedItemPosition();
        boolean perLed = swPerLedBrightness.isChecked();

        if (!perLed) {
            topBri = masterBright;
            midBri = masterBright;
            botBri = masterBright;
        }

        bleManager.setModePattern(currentProfile, masterBright, topBri, top, midBri, mid, botBri, bot, brake, brakeSens, perLed);
        if (showToast) {
            floatingToast.show(getString(R.string.toast_apply_leds), null);
        }
    }

    private void updatePowerSegmentUI(boolean on) {
        int primaryText = getColor(R.color.text_primary);
        int secondaryText = getColor(R.color.text_secondary);
        int okText = getColor(R.color.status_ok_fg);

        if (on) {
            btnLightOn.setBackgroundResource(R.drawable.bg_segment_ok);
            btnLightOn.setTextColor(okText);
            btnLightOn.setTypeface(null, Typeface.BOLD);

            btnLightOff.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            btnLightOff.setTextColor(secondaryText);
            btnLightOff.setTypeface(null, Typeface.NORMAL);
        } else {
            btnLightOff.setBackgroundResource(R.drawable.bg_segment_active);
            btnLightOff.setTextColor(primaryText);
            btnLightOff.setTypeface(null, Typeface.BOLD);

            btnLightOn.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            btnLightOn.setTextColor(secondaryText);
            btnLightOn.setTypeface(null, Typeface.NORMAL);
        }
    }

    private void selectProfileUI(int index) {
        currentProfile = index;
        int primaryText = getColor(R.color.text_primary);
        int secondaryText = getColor(R.color.text_secondary);
        for (int i = 0; i < 4; i++) {
            if (i == index) {
                btnProfiles[i].setBackgroundResource(R.drawable.bg_segment_active);
                btnProfiles[i].setTextColor(primaryText);
                btnProfiles[i].setTypeface(null, Typeface.BOLD);
            } else {
                btnProfiles[i].setBackgroundColor(android.graphics.Color.TRANSPARENT);
                btnProfiles[i].setTextColor(secondaryText);
                btnProfiles[i].setTypeface(null, Typeface.NORMAL);
            }
        }
        tvActiveProfile.setText(getString(R.string.active_profile_format, index + 1));
    }

    private boolean checkPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12, 13, 14, 15
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            // Android 11 and older
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), REQ_PERMISSIONS);
            return false;
        }
        return true;
    }

    private boolean ensureBluetoothEnabled() {
        if (!bleManager.isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            try {
                startActivityForResult(enableBtIntent, REQ_ENABLE_BT);
            } catch (Exception ignored) {}
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) allGranted = false;
            }
            if (allGranted) {
                floatingToast.show(getString(R.string.perm_granted), null);
            } else {
                floatingToast.show(getString(R.string.perm_denied), null);
            }
        }
    }

    // BLE LISTENER CALLBACKS
    @Override
    public void onConnectionStateChange(boolean connected, String deviceName, String mac) {
        this.isConnected = connected;
        if (connected) {
            tvStatus.setText(getString(R.string.status_connected, deviceName, mac));
            tvStatus.setTextColor(getColor(R.color.status_ok_fg));
            btnScan.setText(R.string.btn_disconnect);
            btnScan.setBackgroundResource(R.drawable.bg_btn_destructive);
            btnScan.setTextColor(getColor(R.color.destructive_tint_fg));
        } else {
            lastBatteryPercent = -1;
            tvStatus.setText(R.string.status_disconnected);
            tvStatus.setTextColor(getColor(R.color.text_secondary));
            tvBattery.setText(R.string.battery_placeholder);
            tvBattery.setTextColor(getColor(R.color.text_secondary));
            btnScan.setText(R.string.btn_scan);
            btnScan.setBackgroundResource(R.drawable.bg_btn_primary);
            btnScan.setTextColor(getColor(R.color.accent_on_primary));
        }
    }

    @Override
    public void onBatteryUpdate(int percent) {
        int prev = lastBatteryPercent;
        lastBatteryPercent = percent;
        tvBattery.setTextColor(getColor(percent <= 20 ? R.color.status_warn_fg : R.color.status_ok_fg));
        UiDesignHelpers.animateIntText(tvBattery, prev, percent, getString(R.string.battery_format));
    }

    @Override
    public void onLightStateUpdate(boolean isOn) {
        this.isLightOn = isOn;
        updatePowerSegmentUI(isOn);
    }

    @Override
    public void onActiveModeUpdate(int mode) {
        if (System.currentTimeMillis() - lastUserProfileSelectTime < 1500) {
            // Ignore temporary in-flight status right after user clicked a profile button
            return;
        }
        if (mode >= 0 && mode < 4 && mode != currentProfile) {
            selectProfileUI(mode);
        }
    }

    @Override
    public void onModeDataReceived(int mode, int masterBrightness,
                                   int topBri, int topPat,
                                   int midBri, int midPat,
                                   int botBri, int botPat,
                                   boolean brakeActive, int brakeSens) {
        selectProfileUI(mode);
        sbBrightness.setProgress(masterBrightness > 0 ? masterBrightness : 100);

        sbLedTopBright.setProgress(topBri > 0 ? topBri : 100);
        sbLedMidBright.setProgress(midBri > 0 ? midBri : 100);
        sbLedBotBright.setProgress(botBri > 0 ? botBri : 100);

        tvLedTopBrightLabel.setText(getString(R.string.brightness_label_format, sbLedTopBright.getProgress()));
        tvLedMidBrightLabel.setText(getString(R.string.brightness_label_format, sbLedMidBright.getProgress()));
        tvLedBotBrightLabel.setText(getString(R.string.brightness_label_format, sbLedBotBright.getProgress()));

        swBrake.setChecked(brakeActive);

        if (brakeSens >= 0 && brakeSens < sensitivity.length) {
            spnBrakeSens.setSelection(brakeSens);
        }
        if (topPat >= 0 && topPat < patterns.length) {
            spnLedTop.setSelection(topPat);
        }
        if (midPat >= 0 && midPat < patterns.length) {
            spnLedMid.setSelection(midPat);
        }
        if (botPat >= 0 && botPat < patterns.length) {
            spnLedBot.setSelection(botPat);
        }

        floatingToast.show(getString(R.string.toast_profile_loaded, mode + 1), null);
    }

    @Override
    public void onRawPacketLog(String direction, byte[] data) {
        if (tvBleLog == null || data == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(direction).append(": ");
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        sb.append("\n");
        tvBleLog.append(sb.toString());

        // Keep last ~50 lines if buffer gets very long
        if (tvBleLog.getLineCount() > 50) {
            int linesToRemove = tvBleLog.getLineCount() - 40;
            String cur = tvBleLog.getText().toString();
            int idx = 0;
            for (int i = 0; i < linesToRemove && idx != -1; i++) {
                idx = cur.indexOf('\n', idx);
                if (idx != -1) idx++;
            }
            if (idx != -1 && idx < cur.length()) {
                tvBleLog.setText(cur.substring(idx));
            }
        }
    }

    @Override
    public void onTheftAlert(boolean alarmActive) {
        if (alarmActive) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.theft_dialog_title)
                    .setMessage(R.string.theft_dialog_message)
                    .setPositiveButton(R.string.theft_dialog_stop, null)
                    .show();
        }
    }

    @Override
    public void onStatusMessage(String message) {
        tvStatus.setText(message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleManager != null) {
            bleManager.disconnect();
        }
    }
}
