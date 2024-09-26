package org.lineageos.audiofx.activity

import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.media.AudioDeviceInfo
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import org.lineageos.audiofx.Constants
import org.lineageos.audiofx.R
import org.lineageos.audiofx.fragment.AudioFxFragment
import org.lineageos.audiofx.fragment.AudioFxFragment2
import org.lineageos.audiofx.service.AudioFxService
import org.lineageos.audiofx.service.DevicePreferenceManager
import org.lineageos.audiofx.widget.DynamicMaterialSwitch

class ActivityMusic2 : AppCompatActivity() {
    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }
    private val menuItems = mutableMapOf<MenuItem, AudioDeviceInfo>()
    private var menuDevices: MenuItem? = null
    private var currentDeviceToggle: DynamicMaterialSwitch? = null

    private lateinit var config: MasterConfigControl

    private var callingPackage: String? = null
    private var systemDevice: AudioDeviceInfo? = null
    private var userSelection: AudioDeviceInfo? = null

    private var fragment: AudioFxFragment2? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (DEBUG) {
            Log.i(TAG, "onCreate() called with savedInstanceState = [$savedInstanceState]")
        }

        super.onCreate(savedInstanceState)

        // Setup edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        callingPackage = intent.getStringExtra(ActivityMusic.EXTRA_CALLING_PACKAGE)
        Log.i(TAG, "calling package: $callingPackage")

        config = MasterConfigControl.getInstance(this)
        if (savedInstanceState != null) {
            userSelection = config.getDeviceById(savedInstanceState.getInt("user_device"))
            systemDevice = config.getDeviceById(savedInstanceState.getInt("system_device"))
        }

        if (defaultsSetup()) {
            init(savedInstanceState)
            return
        }

        Log.w(TAG, "waiting for service.")
        val prefs = Constants.getGlobalPrefs(this)
        prefs.registerOnSharedPreferenceChangeListener(object : OnSharedPreferenceChangeListener {
            override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
                if (key == Constants.SAVED_DEFAULTS && defaultsSetup()) {
                    preferences.unregisterOnSharedPreferenceChangeListener(this)
                    config.onResetDefaults()
                    init(savedInstanceState)
                    invalidateOptionsMenu()
                }
            }
        })

        startService(Intent(this@ActivityMusic2, AudioFxService::class.java))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("user_device", userSelection?.id ?: -1)
        outState.putInt("system_device", systemDevice?.id ?: -1)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // should null it out if one was there, compat redirector with package will go through
        // onCreate
        callingPackage = intent.getStringExtra(ActivityMusic.EXTRA_CALLING_PACKAGE)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.devices, menu)
        menuDevices = menu.findItem(R.id.devices)

        menu.findItem(R.id.global_toggle)?.actionView?.findViewById<DynamicMaterialSwitch>(R.id.menu_switch)
            ?.let { setCurrentDeviceToggle(it) }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)

        val subMenu = menuDevices?.subMenu ?: return false
        subMenu.clear()
        menuItems.clear()

        val currentDevice: AudioDeviceInfo = config.getCurrentDevice()
        var selectedItem: MenuItem? = null

        for (deviceType in DEVICE_TYPES) {
            val devices: List<AudioDeviceInfo> = config.getConnectedDevices(*deviceType.second)
            for (device in devices) {
                val item: MenuItem = subMenu.add(
                    R.id.devices,
                    View.generateViewId(),
                    Menu.NONE,
                    MasterConfigControl.getDeviceDisplayString(this, device)
                )

                item.setIcon(deviceType.first)
                menuItems[item] = device

                if (currentDevice.id == device.id) {
                    selectedItem = item
                }
            }
        }

        subMenu.setGroupCheckable(R.id.devices, true, true)
        if (selectedItem != null) {
            selectedItem.setChecked(true)
            menuDevices?.setIcon(selectedItem.icon)
        }

        return super.onPrepareOptionsMenu(menu)
    }

    private fun init(savedInstanceState: Bundle?) {
        if (savedInstanceState == null && findViewById<View?>(R.id.main_fragment) != null) {
            val fragment = AudioFxFragment2()
            supportFragmentManager.beginTransaction()
                .add(R.id.main_fragment, fragment, ActivityMusic.TAG_AUDIOFX).commit()

            this.fragment = fragment
            currentDeviceToggle?.let { setCurrentDeviceToggle(it) }
        }

        applyOemDecor()
    }

    private fun applyOemDecor() {
        if (config.hasMaxxAudio()) {
            supportActionBar?.setSubtitle(R.string.powered_by_maxx_audio)
        } else if (config.hasDts()) {
            supportActionBar?.setIcon(R.drawable.logo_dts_fc)
        }
    }

    private fun defaultsSetup(): Boolean {
        val targetVersion = DevicePreferenceManager.CURRENT_PREFS_INT_VERSION
        val prefs = Constants.getGlobalPrefs(this)
        val currentVersion = prefs.getInt(Constants.AUDIOFX_GLOBAL_PREFS_VERSION_INT, 0)
        val defaultsSaved = prefs.getBoolean(Constants.SAVED_DEFAULTS, false)
        return defaultsSaved && currentVersion >= targetVersion
    }

    private fun setCurrentDeviceToggle(currentDeviceToggle: DynamicMaterialSwitch) {
        Log.d(TAG, "setCurrentDeviceToggle: $currentDeviceToggle, $fragment")
        this.currentDeviceToggle = currentDeviceToggle
        currentDeviceToggle.isChecked = config.isCurrentDeviceEnabled
        currentDeviceToggle.setOnCheckedChangeListener(this::onCurrentDeviceToggleChanged)

        //fragment?.setCurrentDeviceToggle(currentDeviceToggle)
    }

    private fun onCurrentDeviceToggleChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        config.setCurrentDeviceEnabled(isChecked)
    }

    companion object {
        private val TAG: String = ActivityMusic::class.java.simpleName
        private val DEBUG: Boolean = Log.isLoggable(TAG, Log.DEBUG)

        private val DEVICE_TYPES = arrayOf(
            Pair(
                R.drawable.ic_action_dsp_icons_speaker,
                intArrayOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            ), Pair(
                R.drawable.ic_action_dsp_icons_headphones, intArrayOf(
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET
                )
            ), Pair(
                R.drawable.ic_action_dsp_icons_lineout, intArrayOf(
                    AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL
                )
            ), Pair(
                R.drawable.ic_action_dsp_icons_bluetooth,
                intArrayOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            ), Pair(
                R.drawable.ic_action_device_usb, intArrayOf(
                    AudioDeviceInfo.TYPE_USB_ACCESSORY,
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET
                )
            )
        )
    }
}