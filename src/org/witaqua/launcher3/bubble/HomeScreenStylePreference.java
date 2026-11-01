/*
 * Copyright (C) WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */
package org.witaqua.launcher3.bubble;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RadioButton;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.launcher3.R;

/** A home screen style: its illustration at the start, its name and a radio button. */
public class HomeScreenStylePreference extends Preference {

    private boolean mChecked;

    public HomeScreenStylePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
        setLayoutResource(R.layout.witaqua_home_screen_style_option);
    }

    /** Sets whether this style is the selected one. */
    public void setChecked(boolean checked) {
        if (mChecked != checked) {
            mChecked = checked;
            notifyChanged();
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        RadioButton radio = (RadioButton) holder.findViewById(R.id.home_screen_style_radio);
        if (radio != null) {
            radio.setChecked(mChecked);
        }
        holder.itemView.setSelected(mChecked);
    }
}
