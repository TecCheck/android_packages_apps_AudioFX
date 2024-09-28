package org.lineageos.audiofx.fragment

import android.animation.Animator
import android.os.Bundle
import androidx.fragment.app.Fragment
import org.lineageos.audiofx.activity.MasterConfigControl
import org.lineageos.audiofx.fragment.AudioFxFragment.ColorUpdateListener

open class AudioFxBaseFragment2 : Fragment() {

    protected lateinit var config: MasterConfigControl
    protected val eqManager get() = config.equalizerManager

    private var parent: AudioFxFragment2? = null

    protected val disabledColor get() = parent?.disabledColor
    protected val currentBackgroundColor get() = parent?.currentBackgroundColor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        parent = parentFragment as AudioFxFragment2?
        config = MasterConfigControl.getInstance(activity)
    }

    protected fun getPresetColor(presetIndex: Int = eqManager.currentPresetIndex): Int? {
        return parent?.getPresetColor(presetIndex)
    }

    fun animateBackgroundColorTo(
        colorTo: Int, listener: Animator.AnimatorListener?, updateListener: ColorUpdateListener?
    ) = parent?.animateColorTo(colorTo, listener, updateListener)

    fun animateBackgroundColorToNext(
        listener: Animator.AnimatorListener?, updateListener: ColorUpdateListener?
    ) = parent?.animateColorToNext(listener, updateListener)

    /**
     * Call to change the color and propagate it up to the activity, which will call {@link
     * #updateFragmentBackgroundColors(int)}
     *
     * @param color
     */
    fun setBackgroundColor(color: Int, cancelAnimated: Boolean) =
        parent?.updateColor(color, cancelAnimated)

    /**
     * For sub class fragments to override and apply the color
     *
     * @param color the new color to apply to any colored elements
     */
    open fun updateFragmentBackgroundColors(color: Int) {}

    /**
     * For sub class fragments to override when they might need to update their enabled states
     */
    open fun updateEnabledState() {}
}