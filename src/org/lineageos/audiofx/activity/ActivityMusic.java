/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.audiofx.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.lineageos.audiofx.Constants;
import org.lineageos.audiofx.R;
import org.lineageos.audiofx.fragment.AudioFxFragment;
import org.lineageos.audiofx.service.AudioFxService;
import org.lineageos.audiofx.service.DevicePreferenceManager;

import java.util.List;
import java.util.Map;

public class ActivityMusic extends AppCompatActivity {

    public static final String TAG_AUDIOFX = "audiofx";
    public static final String EXTRA_CALLING_PACKAGE = "audiofx::extra_calling_package";
    private static final String TAG = ActivityMusic.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    MasterConfigControl mConfig;
    private final CompoundButton.OnCheckedChangeListener mGlobalEnableToggleListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
            mConfig.setCurrentDeviceEnabled(isChecked);
        }
    };

    private final Map<MenuItem, AudioDeviceInfo> mMenuItems = new ArrayMap<>();
    private MenuItem mMenuDevices;
    boolean mDeviceChanging;

    private AudioDeviceInfo mSystemDevice;
    private AudioDeviceInfo mUserSelection;

    String mCallingPackage;
    private MaterialSwitch mCurrentDeviceToggle;
    private boolean mWaitingForService = true;
    private SharedPreferences.OnSharedPreferenceChangeListener mServiceReadyObserver;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        if (DEBUG) {
            Log.i(TAG, "onCreate() called with " + "savedInstanceState = [" + savedInstanceState + "]");
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setSupportActionBar(findViewById(R.id.toolbar));

        mCallingPackage = getIntent().getStringExtra(EXTRA_CALLING_PACKAGE);
        Log.i(TAG, "calling package: " + mCallingPackage);

        mConfig = MasterConfigControl.getInstance(this);

        final SharedPreferences globalPrefs = Constants.getGlobalPrefs(this);

        if (savedInstanceState != null) {
            int user = savedInstanceState.getInt("user_device");
            mUserSelection = mConfig.getDeviceById(user);
            int system = savedInstanceState.getInt("system_device");
            mSystemDevice = mConfig.getDeviceById(system);
        }


        mWaitingForService = !defaultsSetup();
        if (mWaitingForService) {
            Log.w(TAG, "waiting for service.");
            mServiceReadyObserver = new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    if (key.equals(Constants.SAVED_DEFAULTS) && defaultsSetup()) {
                        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
                        mConfig.onResetDefaults();
                        init(savedInstanceState);

                        mWaitingForService = false;
                        invalidateOptionsMenu();
                        mServiceReadyObserver = null;
                    }
                }
            };
            globalPrefs.registerOnSharedPreferenceChangeListener(mServiceReadyObserver);
            startService(new Intent(ActivityMusic.this, AudioFxService.class));
            // TODO add loading fragment if service initialization takes too long
        } else {
            init(savedInstanceState);
        }
    }

    private boolean defaultsSetup() {
        final int targetVersion = DevicePreferenceManager.CURRENT_PREFS_INT_VERSION;
        final SharedPreferences prefs = Constants.getGlobalPrefs(this);
        final int currentVersion = prefs.getInt(Constants.AUDIOFX_GLOBAL_PREFS_VERSION_INT, 0);
        final boolean defaultsSaved = prefs.getBoolean(Constants.SAVED_DEFAULTS, false);
        return defaultsSaved && currentVersion >= targetVersion;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // should null it out if one was there, compat redirector with package will go through
        // onCreate
        mCallingPackage = intent.getStringExtra(EXTRA_CALLING_PACKAGE);
    }

    @Override
    protected void onDestroy() {
        if (mServiceReadyObserver != null) {
            Constants.getGlobalPrefs(this).unregisterOnSharedPreferenceChangeListener(mServiceReadyObserver);
            mServiceReadyObserver = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.devices, menu);
        mMenuDevices = menu.findItem(R.id.devices);

        mCurrentDeviceToggle = menu.findItem(R.id.global_toggle).getActionView().findViewById(R.id.menu_switch);
        mCurrentDeviceToggle.setOnCheckedChangeListener(mGlobalEnableToggleListener);
        setGlobalToggleChecked(mConfig.isCurrentDeviceEnabled());

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mMenuDevices.getSubMenu().clear();
        mMenuItems.clear();

        final AudioDeviceInfo currentDevice = mConfig.getCurrentDevice();

        MenuItem selectedItem = null;

        List<AudioDeviceInfo> speakerDevices = mConfig.getConnectedDevices(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        if (speakerDevices.size() > 0) {
            AudioDeviceInfo ai = speakerDevices.get(0);
            int viewId = View.generateViewId();
            MenuItem item = mMenuDevices.getSubMenu().add(R.id.devices, viewId, Menu.NONE, MasterConfigControl.getDeviceDisplayString(this, ai));
            item.setIcon(R.drawable.ic_action_dsp_icons_speaker);
            mMenuItems.put(item, ai);
            selectedItem = item;
        }

        List<AudioDeviceInfo> headsetDevices = mConfig.getConnectedDevices(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET);
        if (headsetDevices.size() > 0) {
            AudioDeviceInfo ai = headsetDevices.get(0);
            int viewId = View.generateViewId();
            MenuItem item = mMenuDevices.getSubMenu().add(R.id.devices, viewId, Menu.NONE, MasterConfigControl.getDeviceDisplayString(this, ai));
            item.setIcon(R.drawable.ic_action_dsp_icons_headphones);
            mMenuItems.put(item, ai);
            if (currentDevice.getId() == ai.getId()) {
                selectedItem = item;
            }
        }

        List<AudioDeviceInfo> lineOutDevices = mConfig.getConnectedDevices(AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL);
        if (lineOutDevices.size() > 0) {
            AudioDeviceInfo ai = lineOutDevices.get(0);
            int viewId = View.generateViewId();
            MenuItem item = mMenuDevices.getSubMenu().add(R.id.devices, viewId, Menu.NONE, MasterConfigControl.getDeviceDisplayString(this, ai));
            item.setIcon(R.drawable.ic_action_dsp_icons_lineout);
            mMenuItems.put(item, ai);
            if (currentDevice.getId() == ai.getId()) {
                selectedItem = item;
            }
        }

        List<AudioDeviceInfo> bluetoothDevices = mConfig.getConnectedDevices(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
        for (AudioDeviceInfo ai : bluetoothDevices) {
            int viewId = View.generateViewId();
            MenuItem item = mMenuDevices.getSubMenu().add(R.id.devices, viewId, Menu.NONE, MasterConfigControl.getDeviceDisplayString(this, ai));
            item.setIcon(R.drawable.ic_action_dsp_icons_bluetooth);
            mMenuItems.put(item, ai);
            if (currentDevice.getId() == ai.getId()) {
                selectedItem = item;
            }
        }

        List<AudioDeviceInfo> usbDevices = mConfig.getConnectedDevices(AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET);
        for (AudioDeviceInfo ai : usbDevices) {
            int viewId = View.generateViewId();
            MenuItem item = mMenuDevices.getSubMenu().add(R.id.devices, viewId, Menu.NONE, MasterConfigControl.getDeviceDisplayString(this, ai));
            item.setIcon(R.drawable.ic_action_device_usb);
            mMenuItems.put(item, ai);
            if (currentDevice.getId() == ai.getId()) {
                selectedItem = item;
            }
        }
        mMenuDevices.getSubMenu().setGroupCheckable(R.id.devices, true, true);
        if (selectedItem != null) {
            selectedItem.setChecked(true);
            mMenuDevices.setIcon(selectedItem.getIcon());
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        AudioDeviceInfo device = mMenuItems.get(item);

        if (device != null) {
            mDeviceChanging = true;
            if (item.isCheckable()) {
                item.setChecked(!item.isChecked());
            }
            mSystemDevice = mConfig.getSystemDevice();
            mUserSelection = device;
            this.runOnUiThread(() -> mConfig.setCurrentDevice(mUserSelection, true));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("user_device", mUserSelection == null ? -1 : mUserSelection.getId());
        outState.putInt("system_device", mSystemDevice == null ? -1 : mSystemDevice.getId());
    }

    private void init(Bundle savedInstanceState) {
        mConfig = MasterConfigControl.getInstance(this);

        if (savedInstanceState == null && findViewById(R.id.main_fragment) != null) {
            getSupportFragmentManager().beginTransaction().add(R.id.main_fragment, new AudioFxFragment(), TAG_AUDIOFX).commit();
        }

        applyOemDecor();
    }

    private void applyOemDecor() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) return;

        if (mConfig.hasMaxxAudio()) {
            actionBar.setSubtitle(R.string.powered_by_maxx_audio);
        } else if (mConfig.hasDts()) {
            actionBar.setIcon(R.drawable.logo_dts_fc);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (DEBUG) {
            Log.i(TAG, "onConfigurationChanged() called with " + "newConfig = [" + newConfig + "]");
        }
        if (newConfig.orientation != getResources().getConfiguration().orientation) {
            mCurrentDeviceToggle = null;
        }
    }

    public void setGlobalToggleChecked(boolean checked) {
        if (mCurrentDeviceToggle != null) {
            mCurrentDeviceToggle.setOnCheckedChangeListener(null);
            mCurrentDeviceToggle.setChecked(checked);
            mCurrentDeviceToggle.setOnCheckedChangeListener(mGlobalEnableToggleListener);
        }
    }

    public MaterialSwitch getGlobalSwitch() {
        if (mCurrentDeviceToggle == null) {
            mCurrentDeviceToggle = findViewById(R.id.global_toggle);
        }

        return mCurrentDeviceToggle;
    }
}
