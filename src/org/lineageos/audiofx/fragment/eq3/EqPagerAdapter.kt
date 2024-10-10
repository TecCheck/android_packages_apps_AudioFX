package org.lineageos.audiofx.fragment.eq3

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.lineageos.audiofx.R
import org.lineageos.audiofx.activity.EqualizerManager

class EqPagerAdapter(private val equalizerManager: EqualizerManager) :
    RecyclerView.Adapter<EqPagerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title = view as TextView
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.preset_adapter_row_2, viewGroup, false)
        )
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        Log.d("EQPA", "onBindViewHolder $position")
        viewHolder.title.text = equalizerManager.getLocalizedPresetName(position)
    }

    override fun getItemCount(): Int {
        Log.d(TAG, "Items: ${equalizerManager.presetCount}")
        return equalizerManager.presetCount
    }

    companion object {
        private val TAG = EqPagerAdapter::class.simpleName
    }
}