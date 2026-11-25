/*
* Copyright (C) 2018 The Pixel Experience Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.d4rk.config.center.preferences;

import android.content.Context;

import static com.d4rk.config.center.preferences.DeviceKeysConstants.*;

public class ButtonSettingsUtils {
    public static int getDeviceKeys(Context context) {
        return context.getResources().getInteger(
                org.lineageos.platform.internal.R.integer.config_deviceHardwareKeys);
    }

    public static int getDeviceWakeKeys(Context context) {
        return context.getResources().getInteger(
                org.lineageos.platform.internal.R.integer.config_deviceHardwareWakeKeys);
    }

    /* returns whether the device has home key or not. */
    public static boolean hasHomeKey(Context context) {
        return (getDeviceKeys(context) & KEY_MASK_HOME) != 0;
    }

    /* returns whether the device has back key or not. */
    public static boolean hasBackKey(Context context) {
        return (getDeviceKeys(context) & KEY_MASK_BACK) != 0;
    }

    /* returns whether the device has menu key or not. */
    public static boolean hasMenuKey(Context context) {
        return (getDeviceKeys(context) & KEY_MASK_MENU) != 0;
    }

    /* returns whether the device has assist key or not. */
    public static boolean hasAssistKey(Context context) {
        return (getDeviceKeys(context) & KEY_MASK_ASSIST) != 0;
    }

    /* returns whether the device has app switch key or not. */
    public static boolean hasAppSwitchKey(Context context) {
        return (getDeviceKeys(context) & KEY_MASK_APP_SWITCH) != 0;
    }

    /* returns whether the device supports button backlight adjusment or not. */
    public static boolean hasButtonBacklightSupport(Context context) {
        final boolean buttonBrightnessControlSupported = context.getResources().getInteger(
                org.lineageos.platform.internal.R.integer
                        .config_deviceSupportsButtonBrightnessControl) != 0;

        // All hardware keys besides volume and camera can possibly have a backlight
        return buttonBrightnessControlSupported
                && (hasHomeKey(context) || hasBackKey(context) || hasMenuKey(context)
                || hasAssistKey(context) || hasAppSwitchKey(context));
    }

    /* returns whether the device supports keyboard backlight adjusment or not. */
    public static boolean hasKeyboardBacklightSupport(Context context) {
        return context.getResources().getInteger(org.lineageos.platform.internal.R.integer
                .config_deviceSupportsKeyboardBrightnessControl) != 0;
    }

}
