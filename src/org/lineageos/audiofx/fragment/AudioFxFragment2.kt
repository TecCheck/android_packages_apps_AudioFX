package org.lineageos.audiofx.fragment

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.app.AlertDialog
import android.content.Intent
import android.media.AudioDeviceInfo
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.lineageos.audiofx.Compatibility
import org.lineageos.audiofx.Constants
import org.lineageos.audiofx.R
import org.lineageos.audiofx.activity.ActivityMusic
import org.lineageos.audiofx.activity.EqualizerManager
import org.lineageos.audiofx.activity.MasterConfigControl
import org.lineageos.audiofx.activity.StateCallbacks.DeviceChangedCallback
import org.lineageos.audiofx.fragment.AudioFxFragment.ColorUpdateListener
import org.lineageos.audiofx.widget.DynamicMaterialSwitch
import org.lineageos.audiofx.widget.InterceptableLinearLayout

class AudioFxFragment2 : Fragment(), DeviceChangedCallback {

    private lateinit var config: MasterConfigControl
    private lateinit var eqManager: EqualizerManager

    var disabledColor: Int = 0
        private set
    var currentBackgroundColor: Int = 0
        private set

    private var colorAnimator: ValueAnimator? = null
    private val mColorUpdateListener = AnimatorUpdateListener { animation ->
        updateColor(animation.animatedValue as Int, false)
    }

    private lateinit var interceptLayout: InterceptableLinearLayout
    private var eqFragment: EqualizerFragment2? = null
    private var controlFragment: ControlsFragment2? = null

    private val currentDeviceToggle: DynamicMaterialSwitch? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        config = MasterConfigControl.getInstance(activity)
        eqManager = config.equalizerManager

        disabledColor = resources.getColor(R.color.disabled_eq, context?.theme)
        currentBackgroundColor = disabledColor
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        if (container == null) {
            // no longer displaying this fragment
            Log.w(TAG, "container is null.")
            return null
        }

        val root = inflater.inflate(
            if (config.hasMaxxAudio()) R.layout.fragment_audiofx_maxxaudio else R.layout.fragment_audiofx,
            container,
            false
        )

        showFragments(savedInstanceState)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        interceptLayout = view.findViewById(R.id.interceptable_layout)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        removeFragments()
    }

    override fun onResume() {
        config.callbacks.addDeviceChangedCallback(this)
        config.bindService()
        config.setAutoBindToService(true)
        updateEnabledState()

        super.onResume()
        updateColor()

        promptIfNotDefault()
    }

    override fun onPause() {
        config.setAutoBindToService(false)
        config.callbacks.removeDeviceChangedCallback(this)
        super.onPause()
        config.unbindService()
    }

    override fun onDeviceChanged(device: AudioDeviceInfo?, userChange: Boolean) {
        updateEnabledState()
    }

    override fun onGlobalDeviceToggle(checked: Boolean) {
        val animatorListener: Animator.AnimatorListener = object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                if (checked) updateEnabledState()
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!checked) updateEnabledState()
            }

            override fun onAnimationCancel(animation: Animator) {}

            override fun onAnimationRepeat(animation: Animator) {}
        }

        animateColorToNext(animatorListener, null)
    }

    private fun showFragments(savedInstanceState: Bundle?) {
        val fragmentTransaction = childFragmentManager.beginTransaction()

        if (eqFragment == null && savedInstanceState != null) {
            eqFragment = childFragmentManager.findFragmentByTag(TAG_EQUALIZER) as EqualizerFragment2?
            eqFragment?.let { fragmentTransaction.show(it) }
        } else {
            fragmentTransaction.add(
                R.id.equalizer, EqualizerFragment2().also { eqFragment = it }, TAG_EQUALIZER
            )
        }

        if (controlFragment == null && savedInstanceState != null) {
            controlFragment =
                childFragmentManager.findFragmentByTag(TAG_CONTROLS) as ControlsFragment2?
            controlFragment?.let { fragmentTransaction.show(it) }
        } else {
            fragmentTransaction.add(
                R.id.controls, ControlsFragment2().also { controlFragment = it }, TAG_CONTROLS
            )
        }

        fragmentTransaction.commit()
    }

    private fun removeFragments() {
        val fragmentTransaction = childFragmentManager.beginTransaction()

        eqFragment?.let { fragmentTransaction.remove(it) }
        eqFragment = null

        controlFragment?.let { fragmentTransaction.remove(it) }
        controlFragment = null

        fragmentTransaction.commitAllowingStateLoss()
    }

    private fun updateEnabledState() {
        eqFragment?.updateEnabledState()
        controlFragment?.updateEnabledState()
        interceptLayout.setInterception(!config.isCurrentDeviceEnabled)
    }

    fun getPresetColor(presetIndex: Int = eqManager.currentPresetIndex): Int {
        return if (!config.isCurrentDeviceEnabled) disabledColor
        else eqManager.getAssociatedPresetColorHex(presetIndex)
    }

    fun updateColor() = updateColor(getPresetColor(), false)

    fun updateColor(color: Int, cancelAnimated: Boolean) {
        if (cancelAnimated) colorAnimator?.cancel()

        currentBackgroundColor = color

        eqFragment?.updateFragmentBackgroundColors(color)
        controlFragment?.updateFragmentBackgroundColors(color)

        currentDeviceToggle?.setColor(color)
    }

    fun animateColorToNext(
        listener: Animator.AnimatorListener?, updateListener: ColorUpdateListener?
    ) = animateColorTo(getPresetColor(), listener, updateListener)


    fun animateColorTo(
        colorTo: Int, listener: Animator.AnimatorListener?, updateListener: ColorUpdateListener?
    ) {
        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentBackgroundColor, colorTo)
        colorAnimator?.apply {
            setDuration(500)
            addUpdateListener(updateListener ?: mColorUpdateListener)
            listener?.let { addListener(it) }
            start()
        }
    }

    private fun promptIfNotDefault() {
        // TODO: This should be in MusicActivity, right?
        val audioFxPackageName = requireActivity().packageName
        val musicFxPrefs = Constants.getMusicFxPrefs(activity)
        val defaultPackage =
            musicFxPrefs.getString(Constants.MUSICFX_DEFAULT_PACKAGE_KEY, audioFxPackageName)

        // Don't do anything if we are the default package
        if (defaultPackage == audioFxPackageName) return

        AlertDialog.Builder(activity).setMessage(R.string.snack_bar_not_default)
            .setNegativeButton(R.string.snack_bar_not_default_not_now) { _, _ -> requireActivity().finish() }
            .setPositiveButton(R.string.snack_bar_not_default_set) { dialog, _ ->
                val updateIntent = Intent(activity, Compatibility.Service::class.java)
                updateIntent.putExtra("defPackage", audioFxPackageName)
                updateIntent.putExtra("defName", ActivityMusic::class.java.name)
                requireActivity().startService(updateIntent)
                dialog.dismiss()
            }.setCancelable(false).create().show()
    }

    companion object {
        private val TAG: String = AudioFxFragment::class.java.simpleName

        private const val TAG_EQUALIZER = "equalizer"
        private const val TAG_CONTROLS = "controls"
    }
}