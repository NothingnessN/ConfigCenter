/*
 * Copyright (C) 2020 Havoc-OS
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

import static com.d4rk.config.center.preferences.DeviceKeysConstants.*;

import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.gestures.SystemNavigationPreferenceController;
import com.d4rk.config.center.R;
import com.d4rk.config.center.preferences.ButtonSettingsUtils;
import com.d4rk.config.center.preferences.CustomDialogPreference;

import lineageos.providers.LineageSettings;

public class Buttons extends SettingsPreferenceFragment {
    private SwitchPreference mHomeAnswerCall;
    private ButtonBacklightBrightness mBacklight;
    private SystemNavigationPreferenceController mNavigationController;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.config_center_buttons);

        final Context context = requireContext();
        final int keys = ButtonSettingsUtils.getDeviceKeys(context);
        final int wakeKeys = ButtonSettingsUtils.getDeviceWakeKeys(context);
        pruneKeyCategory("home_key", "home_wake_screen", KEY_MASK_HOME, keys, wakeKeys);
        pruneKeyCategory("back_key", "back_wake_screen", KEY_MASK_BACK, keys, wakeKeys);
        pruneKeyCategory("menu_key", "menu_wake_screen", KEY_MASK_MENU, keys, wakeKeys);
        pruneKeyCategory("assist_key", "assist_wake_screen", KEY_MASK_ASSIST, keys, wakeKeys);
        pruneKeyCategory("app_switch_key", "app_switch_wake_screen",
                KEY_MASK_APP_SWITCH, keys, wakeKeys);
        pruneKeyCategory("camera_key", "camera_wake_screen", KEY_MASK_CAMERA, keys, wakeKeys);

        if ((wakeKeys & KEY_MASK_CAMERA) == 0 || getResources().getBoolean(
                org.lineageos.platform.internal.R.bool.config_singleStageCameraKey)) {
            removePreference("camera_sleep_on_release");
        }
        if ((wakeKeys & KEY_MASK_VOLUME) == 0) {
            removePreference("volume_wake_screen");
            removePreference("volume_answer_call");
        }

        mHomeAnswerCall = findPreference("home_answer_call");
        if (mHomeAnswerCall != null) {
            // Preserve the original native 1=do nothing, 2=answer call values.
            mHomeAnswerCall.setOnPreferenceChangeListener((preference, newValue) ->
                    LineageSettings.Secure.putIntForUser(getContentResolver(),
                            LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR,
                            (Boolean) newValue
                                    ? LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR_ANSWER
                                    : LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR_DO_NOTHING,
                            UserHandle.myUserId()));
        }

        mBacklight = findPreference("button_backlight");
        if (!ButtonSettingsUtils.hasButtonBacklightSupport(context)
                && !ButtonSettingsUtils.hasKeyboardBacklightSupport(context)) {
            removePreference("button_backlight");
            mBacklight = null;
        }

        // Force-on is useful only on devices without a default software navbar.
        if (getResources().getBoolean(com.android.internal.R.bool.config_showNavigationBar)
                || (keys & (KEY_MASK_HOME | KEY_MASK_BACK)) != (KEY_MASK_HOME | KEY_MASK_BACK)) {
            removePreference("force_show_navbar");
        }
        mNavigationController = new SystemNavigationPreferenceController(
                context, "gesture_system_navigation");
        if (mNavigationController.getAvailabilityStatus() != BasePreferenceController.AVAILABLE) {
            removePreference("gesture_system_navigation");
        }
        removeEmptyCategories();
    }

    private void pruneKeyCategory(String category, String wakeKey, int mask,
            int keys, int wakeKeys) {
        if ((keys & mask) == 0) {
            removePreference(category);
        } else if ((wakeKeys & mask) == 0) {
            removePreference(wakeKey);
        }
    }

    private void removeEmptyCategories() {
        final PreferenceScreen screen = getPreferenceScreen();
        for (int i = screen.getPreferenceCount() - 1; i >= 0; i--) {
            final Preference preference = screen.getPreference(i);
            if (preference instanceof PreferenceCategory
                    && ((PreferenceCategory) preference).getPreferenceCount() == 0) {
                screen.removePreference(preference);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mHomeAnswerCall != null) {
            mHomeAnswerCall.setChecked(LineageSettings.Secure.getIntForUser(
                    getContentResolver(), LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR,
                    LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR_DEFAULT,
                    UserHandle.myUserId())
                    == LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR_ANSWER);
        }
        if (mBacklight != null) {
            mBacklight.updateSummary();
        }
        final Preference navigation = findPreference("gesture_system_navigation");
        if (navigation != null) {
            navigation.setSummary(mNavigationController.getSummary());
        }
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (!(preference instanceof CustomDialogPreference)) {
            super.onDisplayPreferenceDialog(preference);
            return;
        }
        if (getParentFragmentManager().findFragmentByTag("dialog_preference") != null) {
            return;
        }
        final DialogFragment dialog = CustomDialogPreference.CustomPreferenceDialogFragment
                .newInstance(preference.getKey());
        dialog.setTargetFragment(this, 0);
        dialog.show(getParentFragmentManager(), "dialog_preference");
        onDialogShowing();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
