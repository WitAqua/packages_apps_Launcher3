/*
 * Copyright (C) WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */
package org.witaqua.launcher3.bubble;

import static android.app.Activity.RESULT_OK;
import static android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID;

import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.settings.SettingsActivity.LauncherSettingsFragment;

import java.util.List;

/**
 * Home settings of the bubble home screen: only the settings that apply to it. They share the
 * values of the classic home screen, the items of Launcher3 read them as usual.
 *
 * It also sets the widgets of the bubble home screen: picking one binds it to the Launcher3
 * widget host, and runs its configuration if any.
 */
public class BubbleSettingsFragment extends LauncherSettingsFragment {

    private static final int REQUEST_BIND_WIDGET = 1001;
    private static final int REQUEST_CONFIGURE_WIDGET = 1002;

    private static final String STATE_PENDING_KEY = "bubble_widget_pending_key";
    private static final String STATE_PENDING_ID = "bubble_widget_pending_id";

    /** The slot and the id of the widget being bound or configured. */
    @Nullable
    private String mPendingKey;
    private int mPendingId = INVALID_APPWIDGET_ID;

    @Override
    protected int getPreferencesResId() {
        return R.xml.witaqua_bubble_preferences;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        if (savedInstanceState != null) {
            mPendingKey = savedInstanceState.getString(STATE_PENDING_KEY);
            mPendingId = savedInstanceState.getInt(STATE_PENDING_ID, INVALID_APPWIDGET_ID);
        }
        for (String key : BubbleWidgets.KEYS) {
            BubbleWidgetPreference preference = findPreference(key);
            if (preference == null) {
                continue;
            }
            preference.setOnPreferenceClickListener(p -> {
                showWidgetPicker(key);
                return true;
            });
            preference.setOnClearListener(() -> setWidget(key, INVALID_APPWIDGET_ID));
            preference.update();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PENDING_KEY, mPendingKey);
        outState.putInt(STATE_PENDING_ID, mPendingId);
    }

    private void showWidgetPicker(String key) {
        Context context = requireContext();
        List<AppWidgetProviderInfo> providers = BubbleWidgets.getPickableProviders(context);
        if (providers.isEmpty()) {
            Toast.makeText(context, R.string.bubble_widget_picker_empty, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        // The dialogs of the settings theme do not clip their content, so the items of a list
        // are drawn over the title and the buttons when scrolled: the widgets are listed in a
        // list of their own, clipped to its bounds.
        ListView list = new ListView(context);
        list.setAdapter(new ProviderAdapter(context, providers));
        list.setDivider(null);
        list.setClipChildren(true);
        list.setClipToPadding(true);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.bubble_widget_picker_title)
                .setView(list)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            bindWidget(key, providers.get(position));
        });
        dialog.show();
        if (list.getParent() instanceof ViewGroup panel) {
            panel.setClipChildren(true);
        }

        // A text button, as in Material 3, instead of the outlined button of the theme.
        Button cancel = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        TypedValue background = new TypedValue();
        if (cancel != null && context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, background, true)) {
            cancel.setBackgroundResource(background.resourceId);
        }
    }

    private void bindWidget(String key, AppWidgetProviderInfo info) {
        Context context = requireContext();
        mPendingKey = key;
        mPendingId = BubbleWidgets.newHost(context).allocateAppWidgetId();
        Bundle options = BubbleWidgets.getSizeOptions(context);
        if (AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(
                mPendingId, info.getProfile(), info.provider, options)) {
            configureWidget();
            return;
        }
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mPendingId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile())
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options);
        try {
            requireActivity().startActivityForResult(intent, REQUEST_BIND_WIDGET);
        } catch (ActivityNotFoundException e) {
            cancelPendingWidget();
        }
    }

    /** Runs the configuration of the widget being bound, if it needs one. */
    private void configureWidget() {
        AppWidgetProviderInfo info =
                AppWidgetManager.getInstance(requireContext()).getAppWidgetInfo(mPendingId);
        if (info == null) {
            cancelPendingWidget();
            return;
        }
        boolean optional = (info.widgetFeatures
                & AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0;
        if (info.configure == null || optional) {
            commitPendingWidget();
            return;
        }
        try {
            BubbleWidgets.newHost(requireContext()).startAppWidgetConfigureActivityForResult(
                    requireActivity(), mPendingId, 0, REQUEST_CONFIGURE_WIDGET, null);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(requireContext(), R.string.activity_not_found, Toast.LENGTH_SHORT)
                    .show();
            cancelPendingWidget();
        }
    }

    /**
     * Called by the settings activity with the results of the activities started for binding
     * and configuring a widget.
     */
    public void onWidgetActivityResult(int requestCode, int resultCode) {
        if (mPendingId == INVALID_APPWIDGET_ID) {
            return;
        }
        if (requestCode == REQUEST_BIND_WIDGET) {
            if (resultCode == RESULT_OK) {
                configureWidget();
            } else {
                cancelPendingWidget();
            }
        } else if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            if (resultCode == RESULT_OK) {
                commitPendingWidget();
            } else {
                cancelPendingWidget();
            }
        }
    }

    private void commitPendingWidget() {
        String key = mPendingKey;
        int appWidgetId = mPendingId;
        mPendingKey = null;
        mPendingId = INVALID_APPWIDGET_ID;
        if (key != null) {
            setWidget(key, appWidgetId);
        }
    }

    private void cancelPendingWidget() {
        if (mPendingId != INVALID_APPWIDGET_ID) {
            BubbleWidgets.newHost(requireContext()).deleteAppWidgetId(mPendingId);
        }
        mPendingKey = null;
        mPendingId = INVALID_APPWIDGET_ID;
    }

    private void setWidget(String key, int appWidgetId) {
        BubbleWidgets.setWidgetId(requireContext(), key, appWidgetId);
        BubbleWidgetPreference preference = findPreference(key);
        if (preference != null) {
            preference.update();
        }
    }

    /** Lists the widgets with their preview, name and app. */
    private static class ProviderAdapter extends ArrayAdapter<AppWidgetProviderInfo> {

        private final PackageManager mPackageManager;
        private final int mDensity;

        ProviderAdapter(Context context, List<AppWidgetProviderInfo> providers) {
            super(context, R.layout.witaqua_bubble_widget_picker_item, providers);
            mPackageManager = context.getPackageManager();
            mDensity = context.getResources().getDisplayMetrics().densityDpi;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView,
                @NonNull ViewGroup parent) {
            View view = convertView != null ? convertView : LayoutInflater.from(getContext())
                    .inflate(R.layout.witaqua_bubble_widget_picker_item, parent, false);
            AppWidgetProviderInfo info = getItem(position);
            Drawable preview = info.loadPreviewImage(getContext(), mDensity);
            if (preview == null) {
                preview = info.loadIcon(getContext(), mDensity);
            }
            ((ImageView) view.findViewById(R.id.bubble_widget_preview)).setImageDrawable(preview);
            ((TextView) view.findViewById(R.id.bubble_widget_label))
                    .setText(info.loadLabel(mPackageManager));
            ((TextView) view.findViewById(R.id.bubble_widget_app))
                    .setText(BubbleWidgets.getAppLabel(mPackageManager, info));
            return view;
        }
    }
}
