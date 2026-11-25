/*
 * Copyright (C) 2013 The CyanogenMod Project
 *               2017 The LineageOS Project
 *               2018 The PixelExperience Project
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

package com.d4rk.config.center.fragments;

import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.d4rk.config.center.R;
import com.d4rk.config.center.preferences.ButtonSettingsUtils;
import com.d4rk.config.center.preferences.CustomDialogPreference;

import lineageos.providers.LineageSettings;

public class ButtonBacklightBrightness extends CustomDialogPreference<AlertDialog> implements
        SeekBar.OnSeekBarChangeListener {
    private static final int BUTTON_BRIGHTNESS_TOGGLE_MODE_ONLY = 1;
    private static final int DEFAULT_BUTTON_TIMEOUT = 5;
    private static final int KEYBOARD_BRIGHTNESS_TOGGLE_MODE_ONLY = 1;

    private ButtonBrightnessControl mButtonBrightness;
    private BrightnessControl mKeyboardBrightness;
    private BrightnessControl mActiveControl;

    private ViewGroup mTimeoutContainer;
    private SeekBar mTimeoutBar;
    private TextView mTimeoutValue;

    private ContentResolver mResolver;
    private Bundle mUnboundState;

    public ButtonBacklightBrightness(Context context, AttributeSet attrs) {
        super(context, attrs);

        mResolver = context.getContentResolver();

        setDialogLayoutResource(R.layout.button_backlight);

        if (ButtonSettingsUtils.hasKeyboardBacklightSupport(context)) {
            final boolean isSingleValue = KEYBOARD_BRIGHTNESS_TOGGLE_MODE_ONLY ==
                    context.getResources().getInteger(org.lineageos.platform.internal.R.integer
                            .config_deviceSupportsKeyboardBrightnessControl);
            mKeyboardBrightness = new BrightnessControl(
                    LineageSettings.Secure.KEYBOARD_BRIGHTNESS, isSingleValue,
                    context.getResources().getFloat(org.lineageos.platform.internal.R.dimen
                            .config_keyboardBrightnessSettingDefaultFloat));
            mActiveControl = mKeyboardBrightness;
        }
        if (ButtonSettingsUtils.hasButtonBacklightSupport(context)) {
            final boolean isSingleValue = BUTTON_BRIGHTNESS_TOGGLE_MODE_ONLY ==
                    context.getResources().getInteger(org.lineageos.platform.internal.R.integer
                            .config_deviceSupportsButtonBrightnessControl);

            float defaultBrightness = context.getResources().getFloat(
                    org.lineageos.platform.internal.R.dimen
                            .config_buttonBrightnessSettingDefaultFloat);

            mButtonBrightness = new ButtonBrightnessControl(
                    LineageSettings.Secure.BUTTON_BRIGHTNESS,
                    LineageSettings.System.BUTTON_BACKLIGHT_ONLY_WHEN_PRESSED,
                    isSingleValue, defaultBrightness);
            mActiveControl = mButtonBrightness;
        }

        updateSummary();
    }

    @Override
    protected void onDialogShown() {
        updateBrightnessPreview();
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder, DialogInterface.OnClickListener listener) {
        super.onPrepareDialogBuilder(builder, listener);
        builder.setNeutralButton(R.string.reset,
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
    }

    @Override
    protected boolean onDismissDialog(AlertDialog dialog, int which) {
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            mTimeoutBar.setProgress(DEFAULT_BUTTON_TIMEOUT);
            if (mButtonBrightness != null) {
                mButtonBrightness.reset();
            }
            if (mKeyboardBrightness != null) {
                mKeyboardBrightness.reset();
            }
            return false;
        }
        return true;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        mUnboundState = null;

        mTimeoutContainer = (ViewGroup) view.findViewById(R.id.timeout_container);
        mTimeoutBar = (SeekBar) view.findViewById(R.id.timeout_seekbar);
        mTimeoutValue = (TextView) view.findViewById(R.id.timeout_value);
        mTimeoutBar.setMax(30);
        mTimeoutBar.setOnSeekBarChangeListener(this);
        mTimeoutBar.setProgress(getTimeout());
        handleTimeoutUpdate(mTimeoutBar.getProgress());

        ViewGroup buttonContainer = (ViewGroup) view.findViewById(R.id.button_container);
        if (mButtonBrightness != null) {
            mButtonBrightness.init(buttonContainer);
        } else {
            buttonContainer.setVisibility(View.GONE);
            mTimeoutContainer.setVisibility(View.GONE);
        }

        ViewGroup keyboardContainer = (ViewGroup) view.findViewById(R.id.keyboard_container);
        if (mKeyboardBrightness != null) {
            mKeyboardBrightness.init(keyboardContainer);
        } else {
            keyboardContainer.setVisibility(View.GONE);
        }

        if (mButtonBrightness == null || mKeyboardBrightness == null) {
            view.findViewById(R.id.button_keyboard_divider).setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (!positiveResult) {
            return;
        }
        if (mTimeoutBar == null && mUnboundState == null) return;

        if (mButtonBrightness != null) {
            applyTimeout(mTimeoutBar != null ? mTimeoutBar.getProgress()
                    : mUnboundState.getInt("timeout", getTimeout()));
            mButtonBrightness.applyBrightness(mUnboundState, "button");
            boolean onlyWhenPressed = mButtonBrightness.mOnlyWhenPressedCheckBox != null
                    ? mButtonBrightness.mOnlyWhenPressedCheckBox.isChecked()
                    : mUnboundState.getBoolean("only_when_pressed", false);
            mButtonBrightness.setOnlyWhenPressedEnabled(onlyWhenPressed);
        }
        if (mKeyboardBrightness != null) {
            mKeyboardBrightness.applyBrightness(mUnboundState, "keyboard");
        }

        updateSummary();
    }

    @Override
    protected void onSaveDialogState(Bundle state) {
        if (mTimeoutBar == null) return;
        state.putInt("timeout", mTimeoutBar.getProgress());
        if (mButtonBrightness != null) {
            state.putFloat("button", mButtonBrightness.getBrightness(false));
            state.putBoolean("only_when_pressed",
                    mButtonBrightness.mOnlyWhenPressedCheckBox.isChecked());
        }
        if (mKeyboardBrightness != null) {
            state.putFloat("keyboard", mKeyboardBrightness.getBrightness(false));
        }
    }

    @Override
    protected void onRestoreDialogState(Bundle state) {
        mTimeoutBar.setProgress(state.getInt("timeout", getTimeout()));
        if (mButtonBrightness != null) {
            mButtonBrightness.setBrightness(state.getFloat("button",
                    mButtonBrightness.getBrightness(true)));
            mButtonBrightness.mOnlyWhenPressedCheckBox.setChecked(state.getBoolean(
                    "only_when_pressed", mButtonBrightness.isOnlyWhenPressedEnabled()));
        }
        if (mKeyboardBrightness != null) {
            mKeyboardBrightness.setBrightness(state.getFloat("keyboard",
                    mKeyboardBrightness.getBrightness(true)));
        }
    }

    @Override
    protected void onUnbindDialogView() {
        if (mTimeoutBar != null) {
            mUnboundState = new Bundle();
            onSaveDialogState(mUnboundState);
        }
        if (mTimeoutBar != null) mTimeoutBar.setOnSeekBarChangeListener(null);
        if (mButtonBrightness != null) mButtonBrightness.unbind();
        if (mKeyboardBrightness != null) mKeyboardBrightness.unbind();
        mTimeoutBar = null;
        mTimeoutContainer = null;
        mTimeoutValue = null;
    }

    public void updateSummary() {
        if (mButtonBrightness != null) {
            float buttonBrightness = mButtonBrightness.getBrightness(true);
            int timeout = getTimeout();

            if (buttonBrightness == 0.0f) {
                setSummary(R.string.backlight_summary_disabled);
            } else if (timeout == 0) {
                setSummary(R.string.backlight_timeout_unlimited);
            } else {
                setSummary(getContext().getString(R.string.backlight_summary_enabled_with_timeout,
                        getTimeoutString(timeout)));
            }
        } else if (mKeyboardBrightness != null &&
                mKeyboardBrightness.getBrightness(true) != 0.0f) {
            setSummary(R.string.backlight_summary_enabled);
        } else {
            setSummary(R.string.backlight_summary_disabled);
        }
    }

    private String getTimeoutString(int timeout) {
        return getContext().getResources().getQuantityString(
                R.plurals.backlight_timeout_time, timeout, timeout);
    }

    private int getTimeout() {
        int timeout = LineageSettings.Secure.getIntForUser(mResolver,
                LineageSettings.Secure.BUTTON_BACKLIGHT_TIMEOUT,
                DEFAULT_BUTTON_TIMEOUT * 1000, UserHandle.myUserId()) / 1000;
        return Math.max(0, Math.min(30, timeout));
    }

    private void applyTimeout(int timeout) {
        LineageSettings.Secure.putIntForUser(mResolver,
                LineageSettings.Secure.BUTTON_BACKLIGHT_TIMEOUT,
                Math.max(0, Math.min(30, timeout)) * 1000, UserHandle.myUserId());
    }

    private void updateBrightnessPreview() {
        if (getDialog() == null || getDialog().getWindow() == null) {
            return;
        }
        Window window = getDialog().getWindow();
        LayoutParams params = window.getAttributes();
        if (mActiveControl != null) {
            params.buttonBrightness = mActiveControl.getBrightness(false);
        } else {
            params.buttonBrightness = -1.0f;
        }
        window.setAttributes(params);
    }

    private void updateTimeoutEnabledState() {
        if (mTimeoutContainer == null) return;
        float buttonBrightness = mButtonBrightness != null
                ? mButtonBrightness.getBrightness(false) : 0.0f;
        int count = mTimeoutContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            mTimeoutContainer.getChildAt(i).setEnabled(buttonBrightness != 0.0f);
        }
    }

    private void handleTimeoutUpdate(int timeout) {
        if (timeout == 0) {
            mTimeoutValue.setText(R.string.backlight_timeout_unlimited);
        } else {
            mTimeoutValue.setText(getTimeoutString(timeout));
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        handleTimeoutUpdate(progress);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // Do nothing here
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // Persist only after confirmation. Cancelling never changes the hardware state.
    }

    private class BrightnessControl implements
            SeekBar.OnSeekBarChangeListener, CheckBox.OnCheckedChangeListener {
        private String mSetting;
        protected boolean mIsSingleValue;
        private float mDefaultBrightness;
        protected CheckBox mCheckBox;
        protected SeekBar mSeekBar;
        private TextView mValue;

        public BrightnessControl(String setting, boolean singleValue, float defaultBrightness) {
            mSetting = setting;
            mIsSingleValue = singleValue;
            mDefaultBrightness = defaultBrightness;
        }

        public void init(ViewGroup container) {
            float brightness = getBrightness(true);

            if (mIsSingleValue) {
                container.findViewById(R.id.seekbar_container).setVisibility(View.GONE);
                mCheckBox = (CheckBox) container.findViewById(R.id.backlight_switch);
                mCheckBox.setChecked(brightness != 0.0f);
                mCheckBox.setOnCheckedChangeListener(this);
            } else {
                container.findViewById(R.id.checkbox_container).setVisibility(View.GONE);
                mSeekBar = (SeekBar) container.findViewById(R.id.seekbar);
                mValue = (TextView) container.findViewById(R.id.value);

                mSeekBar.setMax(100);
                mSeekBar.setProgress((int)(brightness * 100.0f));
                mSeekBar.setOnSeekBarChangeListener(this);
            }

            handleBrightnessUpdate((int)(brightness * 100.0f));
        }

        public void unbind() {
            if (mCheckBox != null) mCheckBox.setOnCheckedChangeListener(null);
            if (mSeekBar != null) mSeekBar.setOnSeekBarChangeListener(null);
            mCheckBox = null;
            mSeekBar = null;
            mValue = null;
        }

        public float getBrightness(boolean persisted) {
            if (mCheckBox != null && !persisted) {
                return mCheckBox.isChecked() ? mDefaultBrightness : 0.0f;
            } else if (mSeekBar != null && !persisted) {
                return mSeekBar.getProgress() / 100.0f;
            }
            float value = LineageSettings.Secure.getFloatForUser(
                    mResolver, mSetting, mDefaultBrightness, UserHandle.myUserId());
            return !Float.isFinite(value) ? mDefaultBrightness
                    : Math.max(0.0f, Math.min(1.0f, value));
        }

        public void applyBrightness(Bundle state, String key) {
            float value = state == null ? getBrightness(false)
                    : state.getFloat(key, getBrightness(true));
            if (!Float.isFinite(value)) value = mDefaultBrightness;
            LineageSettings.Secure.putFloatForUser(mResolver, mSetting,
                    Math.max(0.0f, Math.min(1.0f, value)), UserHandle.myUserId());
        }

        /* Behaviors when it's a seekbar */
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            handleBrightnessUpdate(progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mActiveControl = this;
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // Do nothing here
        }

        /* Behaviors when it's a plain checkbox */
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            mActiveControl = this;
            updateBrightnessPreview();
            updateTimeoutEnabledState();
        }

        public void setBrightness(float value) {
            if (mIsSingleValue) {
                mCheckBox.setChecked(value != 0.0f);
            } else {
                mSeekBar.setProgress((int)(value * 100.0f));
            }
        }

        public void reset() {
            setBrightness(mDefaultBrightness);
        }

        private void handleBrightnessUpdate(int brightness) {
            updateBrightnessPreview();
            if (mValue != null) {
                mValue.setText(String.format("%d%%", brightness));
            }
            updateTimeoutEnabledState();
        }
    }

    private class ButtonBrightnessControl extends BrightnessControl {
        private String mOnlyWhenPressedSetting;
        private CheckBox mOnlyWhenPressedCheckBox;

        public ButtonBrightnessControl(String brightnessSetting, String onlyWhenPressedSetting,
                boolean singleValue, float defaultBrightness) {
            super(brightnessSetting, singleValue, defaultBrightness);
            mOnlyWhenPressedSetting = onlyWhenPressedSetting;
        }

        @Override
        public void init(ViewGroup container) {
            super.init(container);

            mOnlyWhenPressedCheckBox =
                    (CheckBox) container.findViewById(R.id.backlight_only_when_pressed_switch);
            mOnlyWhenPressedCheckBox.setChecked(isOnlyWhenPressedEnabled());
            mOnlyWhenPressedCheckBox.setOnCheckedChangeListener(this);
            updateOnlyWhenPressedCheckBoxState();
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            super.onCheckedChanged(buttonView, isChecked);
            updateOnlyWhenPressedCheckBoxState();
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            super.onProgressChanged(seekBar, progress, fromUser);
            updateOnlyWhenPressedCheckBoxState();
        }

        private void updateOnlyWhenPressedCheckBoxState(){
            if (mOnlyWhenPressedCheckBox == null) return;
            if (mIsSingleValue) {
                mOnlyWhenPressedCheckBox.setEnabled(mCheckBox.isChecked());
            }else if (!mIsSingleValue) {
                mOnlyWhenPressedCheckBox.setEnabled(mSeekBar.getProgress() != 0);
            }
        }

        public boolean isOnlyWhenPressedEnabled() {
            return LineageSettings.System.getIntForUser(mResolver,
                    mOnlyWhenPressedSetting, 0, UserHandle.myUserId()) == 1;
        }

        public void setOnlyWhenPressedEnabled(boolean enabled) {
            LineageSettings.System.putIntForUser(mResolver,
                    mOnlyWhenPressedSetting, enabled ? 1 : 0, UserHandle.myUserId());
        }

        @Override
        public void reset() {
            super.reset();
            mOnlyWhenPressedCheckBox.setChecked(false);
        }

        @Override
        public void unbind() {
            super.unbind();
            if (mOnlyWhenPressedCheckBox != null) {
                mOnlyWhenPressedCheckBox.setOnCheckedChangeListener(null);
                mOnlyWhenPressedCheckBox = null;
            }
        }
    }
}
