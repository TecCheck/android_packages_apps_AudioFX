package org.lineageos.audiofx.fragment.eq3

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import org.lineageos.audiofx.R
import org.lineageos.audiofx.eq.EqContainerView
import org.lineageos.audiofx.eq.EqSwipeController
import org.lineageos.audiofx.preset.InfiniteViewPager
import kotlin.math.abs

class SwipeInterceptor(
    context: Context, attributeSet: AttributeSet? = null
) : LinearLayout(context, attributeSet) {

    private lateinit var mControls: ViewGroup
    private lateinit var mPager: InfiniteViewPager
    private lateinit var mEq: EqContainerView

    override fun onFinishInflate() {
        super.onFinishInflate()
        mEq = findViewById(R.id.eq_container)
        mPager = findViewById(R.id.pager)
        mControls = findViewById(R.id.eq_controls)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex
        val action = event.actionMasked
        val pointerId = event.getPointerId(index)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mDownPositionX = event.rawX
                mDownPositionY = event.rawY
                mDownTime = System.currentTimeMillis()
                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain()
                } else {
                    mVelocityTracker.clear()
                }
                mVelocityTracker.addMovement(event)
            }

            MotionEvent.ACTION_MOVE -> if (mVelocityTracker != null) {
                mVelocityTracker.addMovement(event)
                mVelocityTracker.computeCurrentVelocity(1000)
                val xVelocity: Float = mVelocityTracker.getXVelocity(pointerId)

                val deltaX: Float = mDownPositionX - event.rawX
                val deltaY: Float = mDownPositionY - event.rawY
                val distanceSquared = deltaX * deltaX + deltaY * deltaY

                val viewConfiguration = ViewConfiguration.get(
                    context
                )
                val touchSlop = viewConfiguration.scaledTouchSlop

                if ((!mBarActive && !mEqManager.isChangingPresets()
                            && !mEqManager.isEqualizerLocked()) && abs(xVelocity.toDouble()) < EqSwipeController.X_VELOCITY_THRESH && System.currentTimeMillis() - mDownTime > EqSwipeController.MINIMUM_TIME_HOLD_TIME
                ) {
                    if (distanceSquared < touchSlop * touchSlop) {
                        mBarActive = true
                        mBar = mEq.startTouchingBarUnder(event)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle()
                    mVelocityTracker = null
                }

                if (mBarActive) {
                    // reset state?
                    if (mBar != null) {
                        mEq.stopBarInteraction(mBar)
                        mBar.endInteraction()
                    }
                }
                mBar = null
                mBarActive = false
            }
        }
        return if (mBarActive && mBar != null) {
            mBar.onTouchEvent(event)
        } else {
            mPager.onTouchEvent(event)
        }
    }
}