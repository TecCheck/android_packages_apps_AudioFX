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
package org.lineageos.audiofx.fragment;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import org.lineageos.audiofx.Compatibility;
import org.lineageos.audiofx.Constants;
import org.lineageos.audiofx.R;
import org.lineageos.audiofx.activity.ActivityMusic;
import org.lineageos.audiofx.activity.EqualizerManager;
import org.lineageos.audiofx.activity.MasterConfigControl;
import org.lineageos.audiofx.activity.StateCallbacks;
import org.lineageos.audiofx.widget.DynamicMaterialSwitch;
import org.lineageos.audiofx.widget.InterceptableLinearLayout;

public class AudioFxFragment extends Fragment implements StateCallbacks.DeviceChangedCallback {

    public static final String TAG_EQUALIZER = "equalizer";
    public static final String TAG_CONTROLS = "controls";
    private static final String TAG = AudioFxFragment.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    // current selected index
    public int mSelectedPosition = 0;
    int mCurrentBackgroundColor;
    // whether we are in the middle of animating while switching devices
    EqualizerFragment mEqFragment;
    ControlsFragment2 mControlFragment;

    InterceptableLinearLayout mInterceptLayout;
    private ValueAnimator mColorChangeAnimator;
    private final ValueAnimator.AnimatorUpdateListener mColorUpdateListener = animation -> updateBackgroundColors((Integer) animation.getAnimatedValue(), false);
    private int mDisabledColor;
    private MasterConfigControl mConfig;
    private EqualizerManager mEqManager;

    private DynamicMaterialSwitch currentDeviceToggle = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mConfig = MasterConfigControl.getInstance(getActivity());
        mEqManager = mConfig.getEqualizerManager();

        mDisabledColor = getResources().getColor(R.color.disabled_eq);
    }

    private boolean showFragments() {
        boolean createNewFrags = true;

        final FragmentTransaction fragmentTransaction = getChildFragmentManager().beginTransaction();

        if (mEqFragment == null) {
            mEqFragment = (EqualizerFragment) getChildFragmentManager().findFragmentByTag(TAG_EQUALIZER);

            if (mEqFragment != null) {
                fragmentTransaction.show(mEqFragment);
            }
        }

        if (mControlFragment == null) {
            mControlFragment = (ControlsFragment2) getChildFragmentManager().findFragmentByTag(TAG_CONTROLS);
            if (mControlFragment != null) {
                fragmentTransaction.show(mControlFragment);
            }
        }

        if (mEqFragment != null && mControlFragment != null) {
            createNewFrags = false;
        }

        fragmentTransaction.commit();

        return createNewFrags;
    }

    @Override
    public void onResume() {
        mConfig.getCallbacks().addDeviceChangedCallback(this);
        mConfig.bindService();
        mConfig.setAutoBindToService(true);

        updateEnabledState();

        super.onResume();

        mCurrentBackgroundColor = !mConfig.isCurrentDeviceEnabled() ? mDisabledColor : mEqManager.getAssociatedPresetColorHex(mEqManager.getCurrentPresetIndex());
        updateBackgroundColors(mCurrentBackgroundColor, false);

        promptIfNotDefault();
    }

    private void promptIfNotDefault() {
        final String audioFxPackageName = getActivity().getPackageName();

        final SharedPreferences musicFxPrefs = Constants.getMusicFxPrefs(getActivity());
        final String defaultPackage = musicFxPrefs.getString(Constants.MUSICFX_DEFAULT_PACKAGE_KEY, audioFxPackageName);
        final boolean notDefault = !defaultPackage.equals(audioFxPackageName);

        if (notDefault) {
            new AlertDialog.Builder(getActivity()).setMessage(R.string.snack_bar_not_default).setNegativeButton(R.string.snack_bar_not_default_not_now, (dialog, which) -> getActivity().finish()).setPositiveButton(R.string.snack_bar_not_default_set, (dialog, which) -> {
                Intent updateIntent = new Intent(getActivity(), Compatibility.Service.class);
                updateIntent.putExtra("defPackage", audioFxPackageName);
                updateIntent.putExtra("defName", ActivityMusic.class.getName());
                getActivity().startService(updateIntent);
                dialog.dismiss();
            }).setCancelable(false).create().show();
        }
    }

    @Override
    public void onPause() {
        mConfig.setAutoBindToService(false);
        mConfig.getCallbacks().removeDeviceChangedCallback(this);
        super.onPause();
        mConfig.unbindService();
    }

    public void updateBackgroundColors(Integer color, boolean cancelAnimated) {
        if (cancelAnimated && mColorChangeAnimator != null) {
            mColorChangeAnimator.cancel();
        }
        mCurrentBackgroundColor = color;
        Log.d(TAG, "Set color: " + color);

        if (mEqFragment != null) {
            mEqFragment.updateFragmentBackgroundColors(color);
        }
        if (mControlFragment != null) {
            mControlFragment.updateFragmentBackgroundColors(color);
        }
        if (currentDeviceToggle != null) {
            Log.d(TAG, "Toggle: " + color);

            currentDeviceToggle.setColor(color);
        }
    }

    public void updateEnabledState() {
        boolean currentDeviceEnabled = mConfig.isCurrentDeviceEnabled();
        if (mEqFragment != null) {
            mEqFragment.updateEnabledState();
        }
        if (mControlFragment != null) {
            mControlFragment.updateEnabledState();
        }
        if (mInterceptLayout != null) {
            mInterceptLayout.setInterception(!currentDeviceEnabled);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        if (container == null) {
            Log.w(TAG, "container is null.");
            // no longer displaying this fragment
            return null;
        }

        View root = inflater.inflate(mConfig.hasMaxxAudio() ? R.layout.fragment_audiofx_maxxaudio : R.layout.fragment_audiofx, container, false);

        final FragmentTransaction fragmentTransaction = getChildFragmentManager().beginTransaction();

        boolean createNewFrags = true;

        if (savedInstanceState != null) {
            createNewFrags = showFragments();
        }

        if (createNewFrags) {
            fragmentTransaction.add(R.id.equalizer, mEqFragment = new EqualizerFragment(), TAG_EQUALIZER);
            fragmentTransaction.add(R.id.controls, mControlFragment = new ControlsFragment2(), TAG_CONTROLS);
        }

        fragmentTransaction.commit();


        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // view was destroyed
        final FragmentTransaction fragmentTransaction = getChildFragmentManager().beginTransaction();

        if (mEqFragment != null) {
            fragmentTransaction.remove(mEqFragment);
            mEqFragment = null;
        }
        if (mControlFragment != null) {
            fragmentTransaction.remove(mControlFragment);
            mControlFragment = null;
        }

        fragmentTransaction.commitAllowingStateLoss();

    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mInterceptLayout = view.findViewById(R.id.interceptable_layout);
    }

    public void animateBackgroundColorTo(int colorTo, Animator.AnimatorListener listener, ColorUpdateListener updateListener) {
        if (mColorChangeAnimator != null) {
            mColorChangeAnimator.cancel();
            mColorChangeAnimator = null;
        }
        mColorChangeAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), mCurrentBackgroundColor, colorTo);
        mColorChangeAnimator.setDuration(500);
        mColorChangeAnimator.addUpdateListener(updateListener != null ? updateListener : mColorUpdateListener);
        if (listener != null) {
            mColorChangeAnimator.addListener(listener);
        }
        mColorChangeAnimator.start();
    }

    @Override
    public void onDeviceChanged(AudioDeviceInfo device, boolean userChange) {
        updateEnabledState();
        getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onGlobalDeviceToggle(final boolean checked) {
        final Animator.AnimatorListener animatorListener = new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {
                if (checked) {
                    updateEnabledState();
                }
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                if (!checked) {
                    updateEnabledState();
                }
            }

            @Override
            public void onAnimationCancel(@NonNull Animator animation) {
            }

            @Override
            public void onAnimationRepeat(@NonNull Animator animation) {

            }
        };
        final int colorTo = checked ? mEqManager.getAssociatedPresetColorHex(mEqManager.getCurrentPresetIndex()) : mDisabledColor;
        animateBackgroundColorTo(colorTo, animatorListener, null);
    }

    public int getDisabledColor() {
        return mDisabledColor;
    }

    public void setCurrentDeviceToggle(DynamicMaterialSwitch currentDeviceToggle) {
        this.currentDeviceToggle = currentDeviceToggle;
    }

    public static class ColorUpdateListener implements ValueAnimator.AnimatorUpdateListener {

        final AudioFxBaseFragment mFrag;

        public ColorUpdateListener(AudioFxBaseFragment frag) {
            this.mFrag = frag;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            mFrag.setBackgroundColor((Integer) animation.getAnimatedValue(), false);
        }
    }
}
