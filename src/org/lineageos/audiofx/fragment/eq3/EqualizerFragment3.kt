package org.lineageos.audiofx.fragment.eq3

import android.os.Bundle
import android.view.View
import android.util.Log
import androidx.viewpager2.widget.ViewPager2
import org.lineageos.audiofx.R
import org.lineageos.audiofx.fragment.AudioFxBaseFragment2

class EqualizerFragment3 : AudioFxBaseFragment2(R.layout.equalizer3) {

    private lateinit var pager: ViewPager2
    private lateinit var pagerAdapter: EqPagerAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("EQF3", "onViewCreated")
        pagerAdapter = EqPagerAdapter(eqManager)
        pager = view.findViewById(R.id.pager2)
        pager.adapter = pagerAdapter
    }

}