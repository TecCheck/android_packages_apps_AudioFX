package org.lineageos.audiofx.fragment

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.Dialog
import android.media.AudioDeviceInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import org.lineageos.audiofx.R
import org.lineageos.audiofx.activity.StateCallbacks.DeviceChangedCallback
import org.lineageos.audiofx.activity.StateCallbacks.EqUpdatedCallback
import org.lineageos.audiofx.eq.EqContainerView
import org.lineageos.audiofx.eq.EqSwipeController
import org.lineageos.audiofx.fragment.AudioFxFragment.ColorUpdateListener
import org.lineageos.audiofx.preset.InfinitePagerAdapter
import org.lineageos.audiofx.preset.InfiniteViewPager
import org.lineageos.audiofx.preset.PresetPagerAdapter
import org.lineageos.audiofx.viewpagerindicator.CirclePageIndicator

class EqualizerFragment2 : AudioFxBaseFragment2(), DeviceChangedCallback, EqUpdatedCallback {

    private lateinit var mSwipeInterceptor: EqSwipeController
    private lateinit var mEqContainer: EqContainerView
    private lateinit var mPresetPager: InfiniteViewPager
    private lateinit var mFakePager: ViewPager
    private lateinit var mPresetPageIndicator: CirclePageIndicator

    private lateinit var mDataAdapter: PresetPagerAdapter
    private lateinit var mInfiniteAdapter: InfinitePagerAdapter

    /*
     * this array can hold on to arrays which store preset levels,
     * so modifying values in here should only be done with extreme care
     */
    private lateinit var mSelectedPositionBands: FloatArray
    private var mSelectedPosition: Int = 0
    private var mCurrentRealPage: Int = 0

    // whether we are in the middle of animating while switching devices
    private var mDeviceChanging: Boolean = false
    private var mAnimatingToRealPageTarget = -1
    private val mArgbEval = ArgbEvaluator()

    // TODO: This whole ViewPager situation needs to change
    private val mViewPageChangeListener: OnPageChangeListener = object : OnPageChangeListener {
        var mState: Int = 0
        var mLastOffset: Float = 0f
        var mJustGotToCustomAndSettling: Boolean = false

        override fun onPageScrolled(
            newPosition: Int, positionOffset: Float, positionOffsetPixels: Int
        ) {
            var newPosition = newPosition
            var positionOffset = positionOffset
            if (DEBUG_VIEWPAGER) {
                Log.i(TAG, "onPageScrolled($newPosition, $positionOffset, $positionOffsetPixels)")
            }
            val colorFrom: Int
            val colorTo: Int

            if (newPosition == mAnimatingToRealPageTarget && eqManager.isAnimatingToCustom) {
                if (DEBUG_VIEWPAGER) Log.w(
                    TAG, "settling var set to true"
                )
                mJustGotToCustomAndSettling = true
                mAnimatingToRealPageTarget = -1
            }

            newPosition %= mDataAdapter.count

            if (eqManager.isAnimatingToCustom || mDeviceChanging) {
                if (DEBUG_VIEWPAGER) {
                    Log.i(
                        TAG,
                        "ignoring onPageScrolled because animating to custom or device is changing"
                    )
                }
                return
            }

            var toPos: Int
            if (mLastOffset - positionOffset > 0.8) { // this is needed for flings
                //Log.e(TAG, "OFFSET DIFF > 0.8! Setting selected position from: " +
                // mSelectedPosition + " to " + newPosition);
                mSelectedPosition = newPosition

                // mSelectedPositionBands will be reset by setPreset() below calling back
                // to onPresetChanged()
                eqManager.setPreset(mSelectedPosition)
            }

            if ((newPosition < mSelectedPosition || (newPosition == mDataAdapter.count - 1) && mSelectedPosition == 0)) {
                // scrolling left <<<<<
                positionOffset = (1 - positionOffset)
                //Log.v(TAG, "<<<<<< positionOffset: " + positionOffset + " (last offset:
                // " + mLastOffset + ")");
                toPos = newPosition
                colorTo = eqManager.getAssociatedPresetColorHex(toPos)
            } else {
                // scrolling right >>>>>
                //Log.v(TAG, ">>>>>>> positionOffset: " + positionOffset + " (last
                // offset: " + mLastOffset + ")");
                toPos = newPosition + 1 % mDataAdapter.count
                if (toPos >= mDataAdapter.count) {
                    toPos = 0
                }

                colorTo = eqManager.getAssociatedPresetColorHex(toPos)
            }

            if (!mDeviceChanging && config.isCurrentDeviceEnabled) {
                colorFrom = eqManager.getAssociatedPresetColorHex(mSelectedPosition)
                setBackgroundColor(
                    (mArgbEval.evaluate(positionOffset, colorFrom, colorTo) as Int), true
                )
            }

            if (mSelectedPositionBands == null) {
                mSelectedPositionBands = eqManager.getPresetLevels(mSelectedPosition)
            }
            // get current bands
            val finalPresetLevels: FloatArray = eqManager.getPresetLevels(toPos)

            val N: Int = eqManager.numBands
            for (i in 0 until N) { // animate bands
                val delta = finalPresetLevels[i] - mSelectedPositionBands[i]
                val newBandLevel = mSelectedPositionBands[i] + (delta * positionOffset)
                //if (DEBUG_VIEWPAGER) Log.d(TAG, i + ", delta: " + delta + ",
                // newBandLevel: " + newBandLevel);
                eqManager.setLevel(i, newBandLevel, true)
            }
            mLastOffset = positionOffset
        }

        override fun onPageSelected(position: Int) {
            var position = position
            if (DEBUG_VIEWPAGER) Log.i(
                TAG, "onPageSelected($position)"
            )
            mCurrentRealPage = position
            position %= mDataAdapter.count
            if (DEBUG_VIEWPAGER) Log.e(
                TAG, "onPageSelected($position)"
            )
            mFakePager.currentItem = position
            mSelectedPosition = position
            if (!mDeviceChanging) {
                mSelectedPositionBands = eqManager.getPresetLevels(mSelectedPosition)
            }
        }


        override fun onPageScrollStateChanged(newState: Int) {
            mState = newState
            if (mDeviceChanging) { // avoid setting unwanted presets during custom
                // animations
                return
            }
            if (DEBUG_VIEWPAGER) {
                Log.w(TAG, "onPageScrollStateChanged(${stateToString(newState)})")
            }

            if (mJustGotToCustomAndSettling && mState == ViewPager.SCROLL_STATE_IDLE) {
                if (DEBUG_VIEWPAGER) {
                    Log.w(TAG, "onPageScrollChanged() setting animating to custom = false")
                }
                mJustGotToCustomAndSettling = false
                eqManager.isChangingPresets = false
                eqManager.setAnimatingToCustom(false)
            } else {
                if (mState == ViewPager.SCROLL_STATE_IDLE) {
                    animateBackgroundColorTo(getPresetColor(mSelectedPosition) ?: 0, null, null)
                    eqManager.isChangingPresets = false
                    eqManager.setPreset(mSelectedPosition)
                } else {
                    // not idle
                    eqManager.isChangingPresets = true
                }
            }
        }

        private fun stateToString(state: Int): String {
            return when (state) {
                0 -> "STATE_IDLE"
                1 -> "STATE_DRAGGING"
                2 -> "STATE_SETTLING"
                else -> "STATE_WUT"
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.equalizer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mSelectedPositionBands = eqManager.getPersistedPresetLevels(eqManager.currentPresetIndex)
        mSelectedPosition = eqManager.currentPresetIndex

        mSwipeInterceptor = view.findViewById(R.id.swipe_interceptor)
        mEqContainer = view.findViewById(R.id.eq_container)
        mPresetPager = view.findViewById(R.id.pager)
        mPresetPageIndicator = view.findViewById(R.id.indicator)
        mFakePager = view.findViewById(R.id.fake_pager)

        val save = mEqContainer.findViewById<View>(R.id.save)
        save?.setOnClickListener {
            val newId: Int = eqManager.addPresetFromCustom()
            mInfiniteAdapter.notifyDataSetChanged()
            mDataAdapter.notifyDataSetChanged()
            mPresetPageIndicator.notifyDataSetChanged()
            jumpToPreset(newId)
        }

        val rename = mEqContainer.findViewById<View>(R.id.rename)
        rename?.setOnClickListener {
            if (eqManager.isUserPreset()) {
                openRenameDialog()
            }
        }

        val remove = mEqContainer.findViewById<View>(R.id.remove)
        remove?.setOnClickListener {
            removeCurrentCustomPreset(true)
        }

        mDataAdapter = PresetPagerAdapter(activity)
        mInfiniteAdapter = InfinitePagerAdapter(mDataAdapter)

        mPresetPager.setAdapter(mInfiniteAdapter)
        mPresetPager.setOnPageChangeListener(mViewPageChangeListener)

        mFakePager.setAdapter(mDataAdapter)
        mCurrentRealPage = mPresetPager.currentItem

        // eat all events
        mPresetPageIndicator.setOnTouchListener { _, _ -> true }
        mPresetPageIndicator.setSnap(true)

        mPresetPageIndicator.setViewPager(mFakePager, 0)
        mPresetPageIndicator.setCurrentItem(mSelectedPosition)

        mFakePager.setCurrentItem(mSelectedPosition)
        mPresetPager.setCurrentItem(mSelectedPosition)
    }

    override fun onPause() {
        mEqContainer.stopListening()
        config.callbacks.removeDeviceChangedCallback(this)
        config.callbacks.removeEqUpdatedCallback(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        mEqContainer.startListening()
        config.callbacks.addEqUpdatedCallback(this)
        config.callbacks.addDeviceChangedCallback(this)
        mPresetPageIndicator.notifyDataSetChanged()
        mDataAdapter.notifyDataSetChanged()
    }

    override fun updateFragmentBackgroundColors(color: Int) {
        mSwipeInterceptor.setBackgroundColor(color)
    }

    // BEGIN State callbacks
    override fun onDeviceChanged(device: AudioDeviceInfo?, userChange: Boolean) {
        var diff: Int = eqManager.currentPresetIndex - mSelectedPosition
        val samePage = diff == 0
        diff += mDataAdapter.count
        if (DEBUG) {
            Log.d(TAG, "diff: $diff")
        }
        mCurrentRealPage = mPresetPager.currentItem

        if (DEBUG) Log.d(TAG, "mCurrentRealPage Before: $mCurrentRealPage")
        val newPage = mCurrentRealPage + diff
        if (DEBUG) Log.d(TAG, "mCurrentRealPage After: $newPage")

        mSelectedPositionBands = eqManager.getPresetLevels(mSelectedPosition)
        val targetBandLevels: FloatArray = eqManager.getPresetLevels(eqManager.currentPresetIndex)

        val animatorListener: Animator.AnimatorListener = object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                eqManager.isChangingPresets = true
                mDeviceChanging = true

                if (!samePage) {
                    mPresetPager.setCurrentItemAbsolute(newPage)
                }
            }

            override fun onAnimationEnd(animation: Animator) {
                eqManager.isChangingPresets = false

                mSelectedPosition = eqManager.currentPresetIndex
                mSelectedPositionBands = eqManager.getPresetLevels(mSelectedPosition)

                mDeviceChanging = false
            }

            override fun onAnimationCancel(animation: Animator) {}

            override fun onAnimationRepeat(animation: Animator) {}
        }

        /*
        val animatorUpdateListener: ColorUpdateListener = object : ColorUpdateListener(this) {
            override fun onAnimationUpdate(animator: ValueAnimator) {
                super.onAnimationUpdate(animator)

                for (i in 0 until eqManager.numBands) { // animate bands
                    val delta = targetBandLevels[i] - mSelectedPositionBands[i]
                    val newBandLevel =
                        (mSelectedPositionBands[i] + (delta * animator.animatedFraction))
                    //if (DEBUG_VIEWPAGER) Log.d(TAG, i + ", delta: " + delta + ", newBandLevel:
                    // " + newBandLevel);
                    eqManager.setLevel(i, newBandLevel, true)
                }
            }
        }

         */

        //animateBackgroundColorToNext(animatorListener, animatorUpdateListener)
        animateBackgroundColorToNext(animatorListener, null)
    }

    override fun onGlobalDeviceToggle(on: Boolean) {
        if (on) return
        mFakePager.setCurrentItem(mFakePager.currentItem, true)
    }


    override fun onBandLevelChange(band: Int, dB: Float, fromSystem: Boolean) {
        // call backs we get when bands are changing, check if the user is physically touching them
        // and set the preset to "custom" and do proper animations.
        if (!fromSystem) { // from user
            if (!eqManager.isCustomPreset // not on custom already
                && !eqManager.isUserPreset() // or not on a user preset
                && !eqManager.isAnimatingToCustom
            ) { // and animation hasn't started
                if (DEBUG) Log.w(TAG, "met conditions to start an animation to custom trigger")
                // view pager is infinite, so we can't set the item to 0. find NEXT 0
                eqManager.setAnimatingToCustom(true)

                val newIndex: Int = eqManager.copyToCustom()

                mInfiniteAdapter.notifyDataSetChanged()
                mDataAdapter.notifyDataSetChanged()
                // do background transition manually as viewpager can't handle this bg change

                val listener: Animator.AnimatorListener = object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {
                        var diff = newIndex - (mCurrentRealPage % mDataAdapter.count)
                        diff += mDataAdapter.count
                        val newPage = mCurrentRealPage + diff

                        mAnimatingToRealPageTarget = newPage
                        mPresetPager.setCurrentItemAbsolute(newPage)
                    }

                    override fun onAnimationEnd(animation: Animator) {}

                    override fun onAnimationCancel(animation: Animator) {}

                    override fun onAnimationRepeat(animation: Animator) {}
                }
                animateBackgroundColorTo(getPresetColor(newIndex) ?: 0, listener, null)
            }
            mSelectedPositionBands[band] = dB
        }
    }

    override fun onPresetChanged(newPresetIndex: Int) {}

    override fun onPresetsChanged() {
        mDataAdapter.notifyDataSetChanged()
    }

    // END State callbacks

    private fun jumpToPreset(index: Int) {
        var diff = index - (mCurrentRealPage % mDataAdapter.count)
        // double it, short (e.g. 1 hop) distances sometimes bug out??
        diff += mDataAdapter.count
        val newPage = mCurrentRealPage + diff
        mPresetPager.setCurrentItemAbsolute(newPage, false)
    }

    private fun removeCurrentCustomPreset(showWarning: Boolean) {
        if (showWarning) {
            AlertDialog.Builder(activity).setMessage(
                String.format(
                    getString(R.string.remove_custom_preset_warning_message),
                    eqManager.currentPreset.name
                )
            ).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ -> removeCurrentCustomPreset(false) }
                .create().show()
            return
        }

        if (eqManager.removePreset(eqManager.currentPresetIndex)) {
            mInfiniteAdapter.notifyDataSetChanged()
            mDataAdapter.notifyDataSetChanged()
            mPresetPageIndicator.notifyDataSetChanged()
            jumpToPreset(mSelectedPosition - 1)
        }
    }

    private fun openRenameDialog() {
        val newName = EditText(activity)
        newName.setText(eqManager.currentPreset.name)

        val dialog = AlertDialog.Builder(activity).setTitle(R.string.rename).setView(newName)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                eqManager.renameCurrentPreset(newName.text.toString())
                val viewWithTag = mPresetPager.findViewWithTag<TextView>(eqManager.currentPreset)
                viewWithTag.text = newName.text.toString()
                mDataAdapter.notifyDataSetChanged()
                mPresetPager.invalidate()
            }.setNegativeButton(android.R.string.cancel, null).create()

        // disable ok button if text is empty
        newName.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun afterTextChanged(s: Editable) {
                dialog.getButton(Dialog.BUTTON_POSITIVE).isEnabled = s.isNotEmpty()
            }
        })

        dialog.show()
    }

    companion object {
        private val TAG: String = EqualizerFragment::class.java.simpleName
        private const val DEBUG: Boolean = false
        private const val DEBUG_VIEWPAGER: Boolean = false
    }
}