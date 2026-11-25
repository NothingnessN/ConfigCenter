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

package com.d4rk.config.center;

import android.os.Bundle;
import android.text.TextUtils;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.gestures.GesturesSettingPreferenceController;

public class ConfigCenter extends SettingsPreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.config_center);

        if (!getResources().getBoolean(com.android.internal.R.bool.config_supportSmartPixels)) {
            removePreference("battery_category");
        }
        if (new GesturesSettingPreferenceController(requireContext()).getAvailabilityStatus()
                != BasePreferenceController.AVAILABLE) {
            removePreference("gesture_settings");
        }
        // Match the existing cutout consumer's device-availability condition.
        if (TextUtils.isEmpty(getString(
                com.android.internal.R.string.config_mainBuiltInDisplayCutout))) {
            removePreference("screen_category");
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
