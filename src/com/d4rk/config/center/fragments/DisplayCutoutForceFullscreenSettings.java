/**
 * Copyright (C) 2018 The LineageOS Project
 * Copyright (C) 2019 PixelExperience
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

import android.widget.Toast;

import com.d4rk.config.center.R;
import com.d4rk.config.center.preferences.AppSelectorPreferenceFragment;
import com.android.settingslib.applications.ApplicationsState;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** No Settings-owned CutoutFullscreenController (its observer has no teardown API). */
public class DisplayCutoutForceFullscreenSettings extends AppSelectorPreferenceFragment {
    private static final Set<String> HIDDEN_APPS = new HashSet<>(Arrays.asList(
            "com.android.settings", "com.android.documentsui", "com.android.fmradio",
            "com.caf.fmradio", "com.android.stk", "com.google.android.calculator",
            "com.google.android.calendar", "com.google.android.deskclock",
            "com.google.android.contacts", "com.google.android.apps.messaging",
            "com.google.android.googlequicksearchbox", "com.android.vending",
            // The pinned framework always forces Google Dialer; a toggle would be ineffective.
            "com.google.android.dialer", "com.google.android.apps.wallpaper", "com.google.android.as"));

    @Override
    public String getTitle() {
        return mContext.getString(R.string.d4rk_display_cutout_force_fullscreen_title);
    }

    @Override
    public String getPreferenceKey() {
        return "force_full_screen_cutout_apps";
    }

    @Override
    protected boolean isSelectableApp(ApplicationsState.AppEntry entry) {
        return !HIDDEN_APPS.contains(entry.info.packageName);
    }

    @Override
    protected void onSelectionChanged() {
        Toast.makeText(requireContext(), R.string.display_cutout_force_fullscreen_restart_app,
                Toast.LENGTH_SHORT).show();
    }
}
