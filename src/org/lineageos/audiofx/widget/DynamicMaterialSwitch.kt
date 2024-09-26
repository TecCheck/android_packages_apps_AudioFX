package org.lineageos.audiofx.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import com.google.android.material.materialswitch.MaterialSwitch

class DynamicMaterialSwitch(
    context: Context,
    attributeSet: AttributeSet? = null
) : MaterialSwitch(context, attributeSet) {

    init {
        // Set fresh color state lists because we want every switch to have a separate one
        trackTintList = resources.getColorStateList(
            com.google.android.material.R.color.mtrl_switch_track_tint,
            context.theme
        )
        thumbTintList = resources.getColorStateList(
            com.google.android.material.R.color.mtrl_switch_thumb_tint,
            context.theme
        )
    }

    fun setColor(color: Int) {
        // This is cursed, but I didn't know how else to make it look right
        // At some time it will break
        val hsv = floatArrayOf(0f, 0f, 0f)

        Color.colorToHSV(color, hsv)
        hsv[1] = 0.25f
        hsv[2] = 0.99f
        val colorTrack = Color.HSVToColor(hsv)

        Color.colorToHSV(color, hsv)
        hsv[1] = 0.72f
        hsv[2] = 0.44f
        val colorThumb = Color.HSVToColor(hsv)

        trackTintList?.colors[2] = colorTrack
        thumbTintList?.colors[2] = colorThumb
        thumbTintList?.colors[3] = colorThumb

        invalidate()
    }
}
