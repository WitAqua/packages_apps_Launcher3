/*
 * Copyright (C) WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */
package org.witaqua.launcher3.bubble;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.core.view.WindowCompat;
import androidx.fragment.app.FragmentActivity;

import com.android.launcher3.R;
import com.android.settingslib.widget.SettingsThemeHelper;

/** Lets the user choose the style of the home screen: classic or bubble. */
public class HomeScreenStyleActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        setActionBar(findViewById(R.id.action_bar));
        getActionBar().setDisplayHomeAsUpEnabled(true);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, new HomeScreenStyleFragment())
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();
        if (SettingsThemeHelper.isExpressiveTheme(this)) {
            theme.applyStyle(
                    com.android.settingslib.widget.theme.R.style.Theme_SubSettingsBase_Expressive,
                    true);
        }
        return theme;
    }
}
