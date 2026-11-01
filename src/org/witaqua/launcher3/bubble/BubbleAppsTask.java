/*
 * Copyright (C) WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */
package org.witaqua.launcher3.bubble;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Point;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.ModelTaskController;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ItemInfoMatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds a bubble for every app that has none, after the last bubble, including the app icons out
 * of the bubbles. It runs on the model thread, after the other model tasks, so that an app is
 * never added twice.
 */
public class BubbleAppsTask implements ModelUpdateTask {

    @Override
    public void execute(@NonNull ModelTaskController taskController,
            @NonNull BgDataModel dataModel, @NonNull AllAppsList apps) {
        Context context = taskController.getContext();
        if (!BubbleHome.isEnabled(context)) {
            return;
        }
        List<ItemInfo> added = new ArrayList<>();
        IntSet misplaced = new IntSet();
        synchronized (dataModel) {
            // The apps that have a bubble, alone or in a folder, and the place after the last
            // bubble.
            Set<ComponentKey> shown = new HashSet<>();
            int next = 0;
            List<ItemInfo> hotseat = new ArrayList<>();
            for (ItemInfo info : dataModel.itemsIdMap) {
                if (info.container == CONTAINER_HOTSEAT) {
                    hotseat.add(info);
                }
                if (info.container != CONTAINER_DESKTOP
                        || info.screenId != BubbleHome.BUBBLE_SCREEN_ID) {
                    continue;
                }
                int index = BubbleHome.indexOf(info.cellX, info.cellY);
                if (index < 0 && info.itemType == ITEM_TYPE_APPLICATION) {
                    // An app icon out of the bubbles, e.g. placed there by the classic home
                    // screen: it is not shown, it gets a new bubble.
                    misplaced.add(info.id);
                    continue;
                }
                next = Math.max(next, index + 1);
                List<ItemInfo> contents = info instanceof CollectionInfo collection
                        ? collection.getContents() : List.of(info);
                for (ItemInfo item : contents) {
                    ComponentKey key = getAppKey(item);
                    if (key != null) {
                        shown.add(key);
                    }
                }
            }

            // The apps without bubble, in alphabetical order. When the bubbles are created, the
            // hotseat apps come first.
            Set<AppInfo> missing = new LinkedHashSet<>();
            if (next == 0) {
                hotseat.sort(Comparator.comparingInt(info -> info.screenId));
                for (ItemInfo info : hotseat) {
                    List<ItemInfo> contents = info instanceof CollectionInfo collection
                            ? collection.getContents() : List.of(info);
                    for (ItemInfo item : contents) {
                        ComponentKey key = getAppKey(item);
                        AppInfo app = key == null ? null : findApp(apps, key);
                        if (app != null && !shown.contains(key)) {
                            missing.add(app);
                        }
                    }
                }
            }
            List<AppInfo> sorted = new ArrayList<>(apps.data);
            sorted.sort((a, b) -> String.valueOf(a.title)
                    .compareToIgnoreCase(String.valueOf(b.title)));
            UserCache userCache = UserCache.getInstance(context);
            for (AppInfo app : sorted) {
                // Like the apps added to the home screen when installed, the apps of the private
                // space are not shown.
                if (!shown.contains(new ComponentKey(app.componentName, app.user))
                        && !userCache.getUserInfo(app.user).isPrivate()) {
                    missing.add(app);
                }
            }

            for (AppInfo app : missing) {
                if (next >= BubbleHome.MAX_PAGES * BubbleHome.ITEMS_PER_PAGE) {
                    break;
                }
                WorkspaceItemInfo item = app.makeWorkspaceItem(context);
                Point cell = BubbleHome.cellOf(next++);
                item.container = CONTAINER_DESKTOP;
                item.screenId = BubbleHome.BUBBLE_SCREEN_ID;
                item.cellX = cell.x;
                item.cellY = cell.y;
                added.add(item);
            }
            if (!misplaced.isEmpty()) {
                taskController.deleteAndBindComponentsRemoved(
                        ItemInfoMatcher.ofItemIds(misplaced), "Out of the bubbles");
            }
            if (!added.isEmpty()) {
                taskController.getModelWriter().addItemsToDatabase(added);
            }
        }
        if (!added.isEmpty()) {
            taskController.scheduleCallbackTask(cb -> cb.bindItemsAdded(added));
        }
    }

    /** Returns the app opened by the item, if it is an app icon. */
    private static ComponentKey getAppKey(ItemInfo item) {
        ComponentName component = item.getTargetComponent();
        return item.itemType == ITEM_TYPE_APPLICATION && component != null
                ? new ComponentKey(component, item.user) : null;
    }

    private static AppInfo findApp(AllAppsList apps, ComponentKey key) {
        for (AppInfo app : apps.data) {
            if (key.equals(new ComponentKey(app.componentName, app.user))) {
                return app;
            }
        }
        return null;
    }
}
