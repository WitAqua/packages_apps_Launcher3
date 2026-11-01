/*
 * Copyright (C) WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */
package org.witaqua.launcher3.bubble;

import android.os.Bundle;

import com.android.launcher3.R;
import com.android.launcher3.settings.SettingsActivity.LauncherSettingsFragment;

/** The home screen styles, shown with an illustration and chosen with radio buttons. */
public class HomeScreenStyleFragment extends LauncherSettingsFragment {

    private static final String KEY_CLASSIC = "home_screen_style_classic";
    private static final String KEY_BUBBLE = "home_screen_style_bubble";

    private HomeScreenStylePreference mClassic;
    private HomeScreenStylePreference mBubble;

    @Override
    protected int getPreferencesResId() {
        return R.xml.witaqua_home_screen_style_preferences;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        mClassic = findPreference(KEY_CLASSIC);
        mBubble = findPreference(KEY_BUBBLE);
        mClassic.setOnPreferenceClickListener(p -> select(false));
        mBubble.setOnPreferenceClickListener(p -> select(true));
        updateChecked();
    }

    private boolean select(boolean bubble) {
        if (BubbleHome.isEnabled(requireContext()) != bubble) {
            BubbleHome.setEnabled(requireContext(), bubble);
        }
        updateChecked();
        return true;
    }

    private void updateChecked() {
        boolean bubble = BubbleHome.isEnabled(requireContext());
        mClassic.setChecked(!bubble);
        mBubble.setChecked(bubble);
    }
}
