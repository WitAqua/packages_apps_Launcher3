/*
 * Copyright (C) WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */
package org.witaqua.launcher3.bubble;

import static android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.launcher3.R;

/**
 * A widget slot of the bubble home screen: shows the widget placed in it, and a button at its
 * end to remove it.
 */
public class BubbleWidgetPreference extends Preference {

    @Nullable
    private Runnable mOnClear;
    private boolean mHasWidget;

    public BubbleWidgetPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
        setWidgetLayoutResource(R.layout.witaqua_bubble_widget_clear);
    }

    /** Sets what is done when the remove button is clicked. */
    public void setOnClearListener(@Nullable Runnable onClear) {
        mOnClear = onClear;
    }

    /** Shows the widget of the slot. */
    public void update() {
        Context context = getContext();
        int appWidgetId = BubbleWidgets.getWidgetId(context, getKey());
        AppWidgetProviderInfo info = appWidgetId == INVALID_APPWIDGET_ID ? null
                : AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId);
        mHasWidget = appWidgetId != INVALID_APPWIDGET_ID;
        setSummary(info != null ? info.loadLabel(context.getPackageManager())
                : context.getString(R.string.bubble_widget_none));
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View clear = holder.findViewById(R.id.bubble_widget_clear);
        if (clear != null) {
            clear.setVisibility(mHasWidget ? View.VISIBLE : View.GONE);
            clear.setOnClickListener(v -> {
                if (mOnClear != null) {
                    mOnClear.run();
                }
            });
        }
    }
}
