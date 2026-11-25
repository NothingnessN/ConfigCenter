/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.d4rk.config.center.preferences;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;

/** Settings-hosted dialog with fragment-owned state and a non-dismissing reset button. */
public class CustomDialogPreference<T extends AlertDialog> extends DialogPreference {
    private CustomPreferenceDialogFragment mFragment;

    public CustomDialogPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CustomDialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CustomDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomDialogPreference(Context context) {
        super(context);
    }

    @SuppressWarnings("unchecked")
    public T getDialog() {
        return mFragment != null && mFragment.getDialog() instanceof AlertDialog
                ? (T) mFragment.getDialog() : null;
    }

    protected void onPrepareDialogBuilder(AlertDialog.Builder builder,
            DialogInterface.OnClickListener listener) {}

    protected void onBindDialogView(View view) {}

    protected void onDialogShown() {}

    protected void onDialogClosed(boolean positiveResult) {}

    protected boolean onDismissDialog(T dialog, int which) {
        return true;
    }

    protected void onSaveDialogState(Bundle outState) {}

    protected void onRestoreDialogState(Bundle savedState) {}

    protected void onUnbindDialogView() {}

    public static class CustomPreferenceDialogFragment extends PreferenceDialogFragmentCompat {
        private static final String STATE = "d4rk_dialog_state";
        private Bundle mRestoredState;

        public static CustomPreferenceDialogFragment newInstance(String key) {
            CustomPreferenceDialogFragment fragment = new CustomPreferenceDialogFragment();
            Bundle args = new Bundle();
            args.putString(ARG_KEY, key);
            fragment.setArguments(args);
            return fragment;
        }

        private CustomDialogPreference<AlertDialog> mPreference;

        @Override
        @SuppressWarnings("unchecked")
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mPreference = (CustomDialogPreference<AlertDialog>) getPreference();
            mPreference.mFragment = this;
            mRestoredState = savedInstanceState == null ? null
                    : savedInstanceState.getBundle(STATE);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            mPreference.mFragment = this;
            return super.onCreateDialog(savedInstanceState);
        }

        @Override
        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);
            mPreference.onPrepareDialogBuilder(builder, this);
        }

        @Override
        protected void onBindDialogView(View view) {
            super.onBindDialogView(view);
            mPreference.onBindDialogView(view);
            if (mRestoredState != null) {
                mPreference.onRestoreDialogState(mRestoredState);
                mRestoredState = null;
            }
        }

        @Override
        public void onStart() {
            super.onStart();
            AlertDialog dialog = (AlertDialog) requireDialog();
            for (int which : new int[] {DialogInterface.BUTTON_POSITIVE,
                    DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_NEUTRAL}) {
                if (dialog.getButton(which) != null) {
                    dialog.getButton(which).setOnClickListener(view -> {
                        if (mPreference.onDismissDialog(dialog, which)) {
                            onClick(dialog, which);
                            dismiss();
                        }
                    });
                }
            }
            mPreference.onDialogShown();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            Bundle state = new Bundle();
            mPreference.onSaveDialogState(state);
            outState.putBundle(STATE, state);
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            mPreference.onDialogClosed(positiveResult);
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            mPreference.onUnbindDialogView();
            if (mPreference.mFragment == this) {
                mPreference.mFragment = null;
            }
        }
    }
}
