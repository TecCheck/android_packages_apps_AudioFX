package org.lineageos.audiofx.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import org.lineageos.audiofx.R
import org.lineageos.audiofx.knobs.KnobCommander
import org.lineageos.audiofx.knobs.KnobContainer
import org.lineageos.audiofx.widget.DynamicMaterialSwitch

class ControlsFragment2 : AudioFxBaseFragment2() {

    private var knobCommander: KnobCommander? = null
    private var knobContainer: KnobContainer? = null
    private var reverbSwitch: DynamicMaterialSwitch? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        knobCommander = KnobCommander.getInstance(activity)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val layout = if (config.hasMaxxAudio()) R.layout.controls_maxx_audio
        else R.layout.controls_generic

        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        knobContainer = view.findViewById(R.id.knob_container)

        val switchId = if (config.hasMaxxAudio()) R.id.maxx_volume_switch else R.id.reverb_switch
        reverbSwitch = view.findViewById(switchId)
        reverbSwitch?.setOnCheckedChangeListener(this::onReverbCheckChanged)

        currentBackgroundColor?.let { updateFragmentBackgroundColors(it) }
    }

    override fun onPause() {
        config.callbacks.removeDeviceChangedCallback(knobContainer)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        config.callbacks.addDeviceChangedCallback(knobContainer)
    }

    override fun updateFragmentBackgroundColors(color: Int) {
        knobContainer?.updateKnobHighlights(color)
        reverbSwitch?.setColor(color)
    }

    private fun onReverbCheckChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        if (config.hasMaxxAudio()) {
            config.maxxVolumeEnabled
            config.setMaxxVolumeEnabled(isChecked)
        } else {
            config.reverbEnabled
            config.setReverbEnabled(isChecked)
        }
    }
}