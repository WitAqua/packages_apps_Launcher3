/*
 * Copyright (C) WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */
package org.witaqua.launcher3.bubble;

import static android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.SizeF;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.LauncherWidgetHolder;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.ListenableAppWidgetHost.ProviderChangedListener;

import java.util.List;

/**
 * A widget slot of the bubble home screen. It shows the widget set in the settings, hosted by the
 * Launcher3 widget host, and follows the changes of the settings.
 */
public class BubbleWidgetFrame extends FrameLayout {

    /** The widget host of Launcher, or of the preview of the home screen. */
    @Nullable
    private final LauncherWidgetHolder mWidgetHolder;
    private final String mKey;

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener =
            (prefs, key) -> {
                if (getKey().equals(key)) {
                    update();
                }
            };
    private final ProviderChangedListener mProviderListener = () -> post(this::update);

    @Nullable
    private AppWidgetHostView mWidget;
    private int mAppWidgetId = INVALID_APPWIDGET_ID;

    public BubbleWidgetFrame(Context context, String key) {
        super(context);
        mWidgetHolder = ActivityContext.lookupContext(context).getAppWidgetHolder();
        mKey = key;
    }

    private String getKey() {
        return mKey;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        LauncherPrefs.getPrefs(getContext()).registerOnSharedPreferenceChangeListener(
                mPrefListener);
        if (mWidgetHolder != null) {
            mWidgetHolder.addProviderChangeListener(mProviderListener);
        }
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        LauncherPrefs.getPrefs(getContext()).unregisterOnSharedPreferenceChangeListener(
                mPrefListener);
        if (mWidgetHolder != null) {
            mWidgetHolder.removeProviderChangeListener(mProviderListener);
        }
        super.onDetachedFromWindow();
    }

    /** Shows the widget of the slot, if it changed. */
    private void update() {
        int appWidgetId = BubbleWidgets.getWidgetId(getContext(), mKey);
        AppWidgetProviderInfo info = appWidgetId == INVALID_APPWIDGET_ID ? null
                : AppWidgetManager.getInstance(getContext()).getAppWidgetInfo(appWidgetId);
        if (appWidgetId == mAppWidgetId && (mWidget != null) == (info != null)) {
            return;
        }
        removeAllViews();
        mWidget = null;
        mAppWidgetId = appWidgetId;
        if (info == null || mWidgetHolder == null) {
            // No widget, or its app is not available.
            return;
        }
        mWidget = mWidgetHolder.createView(appWidgetId,
                LauncherAppWidgetProviderInfo.fromProviderInfo(getContext(), info));
        addView(mWidget, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        updateWidgetSize(getWidth(), getHeight());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateWidgetSize(w, h);
    }

    private void updateWidgetSize(int width, int height) {
        if (mWidget == null || width <= 0 || height <= 0) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        mWidget.updateAppWidgetSize(new Bundle(),
                List.of(new SizeF(width / density, height / density)));
    }
}
