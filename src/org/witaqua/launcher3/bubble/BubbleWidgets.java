/*
 * Copyright (C) WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */
package org.witaqua.launcher3.bubble;

import static android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.util.DisplayMetrics;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.widget.LauncherWidgetHolder;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Widgets of the bubble home screen: one at the top and one at the bottom of the screen, shown
 * over the neighbouring flowers. They are one bubble high at most.
 *
 * The widgets are hosted by the Launcher3 widget host, their ids are kept in the preferences.
 */
public final class BubbleWidgets {

    public static final String KEY_TOP = "pref_bubble_widget_top";
    public static final String KEY_BOTTOM = "pref_bubble_widget_bottom";
    public static final String[] KEYS = {KEY_TOP, KEY_BOTTOM};

    private BubbleWidgets() { }

    /** Returns the id of the widget of the given slot, or {@code INVALID_APPWIDGET_ID}. */
    public static int getWidgetId(Context context, String key) {
        return LauncherPrefs.getPrefs(context).getInt(key, INVALID_APPWIDGET_ID);
    }

    /** Returns the ids of the widgets of every slot, which must not be deleted as unused. */
    public static IntSet getWidgetIds(Context context) {
        IntSet ids = new IntSet();
        for (String key : KEYS) {
            int id = getWidgetId(context, key);
            if (id != INVALID_APPWIDGET_ID) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Sets the widget of the given slot, deleting the previous one. */
    public static void setWidgetId(Context context, String key, int appWidgetId) {
        int previous = getWidgetId(context, key);
        if (previous != INVALID_APPWIDGET_ID && previous != appWidgetId) {
            newHost(context).deleteAppWidgetId(previous);
        }
        if (appWidgetId == INVALID_APPWIDGET_ID) {
            LauncherPrefs.getPrefs(context).edit().remove(key).apply();
        } else {
            LauncherPrefs.getPrefs(context).edit().putInt(key, appWidgetId).apply();
        }
    }

    /** Returns a host to allocate and delete the ids of the widgets of Launcher. */
    public static AppWidgetHost newHost(Context context) {
        return new AppWidgetHost(context, LauncherWidgetHolder.APPWIDGET_HOST_ID);
    }

    /** Returns the size of a bubble, in pixels: the screen is three bubbles wide. */
    public static int getCellSize(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return Math.min(metrics.widthPixels, metrics.heightPixels) / BubbleHome.COLUMNS;
    }

    /** Returns the widgets that fit in a slot: one bubble high at most, sorted by app. */
    public static List<AppWidgetProviderInfo> getPickableProviders(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int maxWidth = Math.min(metrics.widthPixels, metrics.heightPixels);
        int maxHeight = getCellSize(context);
        List<AppWidgetProviderInfo> providers = new ArrayList<>();
        for (AppWidgetProviderInfo info : AppWidgetManager.getInstance(context)
                .getInstalledProvidersForProfile(Process.myUserHandle())) {
            boolean resizeX = (info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0;
            boolean resizeY = (info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0;
            int minWidth = resizeX && info.minResizeWidth > 0
                    ? Math.min(info.minWidth, info.minResizeWidth) : info.minWidth;
            int minHeight = resizeY && info.minResizeHeight > 0
                    ? Math.min(info.minHeight, info.minResizeHeight) : info.minHeight;
            if (minWidth <= maxWidth && minHeight <= maxHeight) {
                providers.add(info);
            }
        }
        PackageManager pm = context.getPackageManager();
        Collator collator = Collator.getInstance();
        providers.sort(Comparator.<AppWidgetProviderInfo, String>comparing(
                        info -> getAppLabel(pm, info), collator)
                .thenComparing(info -> info.loadLabel(pm), collator));
        return providers;
    }

    /** Returns the name of the app of a widget. */
    public static String getAppLabel(PackageManager pm, AppWidgetProviderInfo info) {
        try {
            return String.valueOf(pm.getApplicationLabel(
                    pm.getApplicationInfo(info.provider.getPackageName(), 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return info.provider.getPackageName();
        }
    }

    /** Returns the size options of a slot, for binding a widget. */
    public static Bundle getSizeOptions(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int cellSize = getCellSize(context);
        int width = Math.round((Math.min(metrics.widthPixels, metrics.heightPixels)
                - 2 * BubbleHome.getWidgetInset(cellSize)) / metrics.density);
        int height = Math.round(cellSize / metrics.density);
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height);
        return options;
    }
}
