/*
 * Copyright (C) WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */
package org.witaqua.launcher3.bubble;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemCoordinates;

import java.util.List;

/**
 * The "bubble" home screen style: every app on one page of the workspace, laid out as flowers of
 * seven bubbles scrolling vertically.
 *
 * Its items are regular workspace items stored on {@link #BUBBLE_SCREEN_ID}, so they are kept
 * apart from the classic home screen. That screen is shown as the first workspace page by
 * {@link BubbleCellPosMapper} and drawn by {@link BubbleCellLayout}.
 *
 * Grid model, three columns, a flower is three rows (row 0 is left empty):
 * <pre>
 *       [0]   [2]       row 3p + 1   (offset by half a cell)
 *     [3]  [1]  [4]     row 3p + 2   (centre bubble is [1])
 *       [5]   [6]       row 3p + 3   (offset by half a cell)
 * </pre>
 * The numbers are the index of the item in the flower, i.e. its order.
 */
public final class BubbleHome {

    public static final String KEY_HOME_SCREEN_STYLE = "pref_home_screen_style";
    private static final String STYLE_BUBBLE = "bubble";
    private static final String STYLE_CLASSIC = "classic";

    /** Workspace screen id holding the bubble items in the model. */
    public static final int BUBBLE_SCREEN_ID = 10000;
    /** Presenter screen id of the items of the other home screen style, which are not shown. */
    public static final int HIDDEN_SCREEN_ID = -1000;

    public static final int COLUMNS = 3;
    public static final int ROWS_PER_PAGE = 3;
    public static final int ITEMS_PER_PAGE = 7;
    public static final int MAX_PAGES = 100;
    public static final int MAX_ROWS = 1 + ROWS_PER_PAGE * MAX_PAGES;

    /** Diameter of a round icon relative to the cell. */
    public static final float ICON_FILL = 0.8f;

    /** Position of an item inside a flower: {row offset, column}. */
    private static final int[][] SLOTS = {
            {1, 0}, {2, 1}, {1, 1}, {2, 0}, {2, 2}, {3, 0}, {3, 1},
    };

    private BubbleHome() { }

    /** Returns true if the bubble home screen is selected. */
    public static boolean isEnabled(Context context) {
        return STYLE_BUBBLE.equals(LauncherPrefs.getPrefs(context)
                .getString(KEY_HOME_SCREEN_STYLE, STYLE_CLASSIC));
    }

    /** Selects the bubble home screen, or the classic one. */
    public static void setEnabled(Context context, boolean enabled) {
        LauncherPrefs.getPrefs(context).edit()
                .putString(KEY_HOME_SCREEN_STYLE, enabled ? STYLE_BUBBLE : STYLE_CLASSIC).apply();
    }

    /** Returns true if items of the given presenter screen are not shown. */
    public static boolean isHiddenScreen(int presenterScreenId) {
        return presenterScreenId == HIDDEN_SCREEN_ID || presenterScreenId == BUBBLE_SCREEN_ID;
    }

    /** Returns true if the row holds two bubbles, offset by half a cell. */
    public static boolean isTwoItemRow(int cellY) {
        return cellY % ROWS_PER_PAGE != 2;
    }

    /** Returns true if the cell is the centre bubble of a flower. */
    public static boolean isCentre(int cellX, int cellY) {
        return cellY % ROWS_PER_PAGE == 2 && cellX == 1;
    }

    /** Returns the order of the item in the given cell, or -1 if it is not a bubble cell. */
    public static int indexOf(int cellX, int cellY) {
        if (cellY < 1 || cellX < 0 || cellX >= COLUMNS
                || (isTwoItemRow(cellY) && cellX >= COLUMNS - 1)) {
            return -1;
        }
        int page = (cellY - 1) / ROWS_PER_PAGE;
        int rowOffset = (cellY - 1) % ROWS_PER_PAGE + 1;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            if (SLOTS[i][0] == rowOffset && SLOTS[i][1] == cellX) {
                return page * ITEMS_PER_PAGE + i;
            }
        }
        return -1;
    }

    /** Returns the cell of the item at the given order. */
    public static Point cellOf(int index) {
        int[] slot = SLOTS[index % ITEMS_PER_PAGE];
        return new Point(slot[1], index / ITEMS_PER_PAGE * ROWS_PER_PAGE + slot[0]);
    }

    /**
     * Gives a bubble to every app that has none, e.g. the apps installed while the classic home
     * screen was shown: there is no app drawer, an app without a bubble could not be opened.
     * The first time, the hotseat apps come first so that they are on the first flower.
     */
    public static void addMissingApps(Launcher launcher) {
        if (launcher.isBubbleHome()) {
            launcher.getModel().enqueueModelUpdateTask(new BubbleAppsTask());
        }
    }

    /**
     * Returns the place of an item added to the home screen, e.g. a newly installed app: after
     * the last bubble, as the bubbles are a list.
     *
     * @param pendingItems the items being added, not in the model yet
     * @return the model coordinates, or null if the item does not fit in a bubble
     */
    public static WorkspaceItemCoordinates findSpaceForNewItem(BgDataModel dataModel,
            List<ItemInfo> pendingItems, ItemInfo item) {
        if (item.spanX != 1 || item.spanY != 1) {
            return null;
        }
        int next = 0;
        synchronized (dataModel) {
            for (ItemInfo info : dataModel.itemsIdMap) {
                next = Math.max(next, nextIndex(info));
            }
        }
        for (ItemInfo info : pendingItems) {
            next = Math.max(next, nextIndex(info));
        }
        if (next >= MAX_PAGES * ITEMS_PER_PAGE) {
            return null;
        }
        Point cell = cellOf(next);
        return new WorkspaceItemCoordinates(BUBBLE_SCREEN_ID, cell.x, cell.y);
    }

    /** Returns the order after the given item if it is a bubble, or 0. */
    private static int nextIndex(ItemInfo info) {
        if (info.container != CONTAINER_DESKTOP || info.screenId != BUBBLE_SCREEN_ID) {
            return 0;
        }
        return indexOf(info.cellX, info.cellY) + 1;
    }

    /** Returns true if the view is an item of the bubble home screen. */
    public static boolean isBubble(View view) {
        return view.getParent() instanceof View container
                && container.getParent() instanceof BubbleCellLayout;
    }

    /**
     * Positions the drag view of a bubble over the icon as shown, as bubbles are scaled and
     * moved items.
     *
     * @param outPos the top left of the drag view, in drag layer coordinates
     * @return the scale of the drag view, or -1 if the view is not a bubble
     */
    public static float getDragPreviewScaleAndPosition(View view, int previewWidth,
            int previewHeight, int[] outPos) {
        if (!isBubble(view) || !(view instanceof DraggableView draggable)) {
            return -1;
        }
        Rect bounds = new Rect();
        draggable.getSourceVisualDragBounds(bounds);
        if (bounds.isEmpty()) {
            return -1;
        }
        float[] points = {bounds.left, bounds.top, bounds.right, bounds.bottom};
        Launcher.getLauncher(view.getContext()).getDragLayer()
                .getDescendantCoordRelativeToSelf(view, points);
        // The preview is drawn at the size of the bubble; its centre is put on the icon centre.
        outPos[0] = Math.round((points[0] + points[2] - previewWidth) / 2);
        outPos[1] = Math.round((points[1] + points[3] - previewHeight) / 2);
        return 1f;
    }

    /**
     * Returns where the drag view of an item dropped on the bubbles lands: on the bubble as it is
     * shown, which is scaled about its icon and moved by the page.
     *
     * @param outPos the top left of the drag view, in drag layer coordinates
     * @return the final scale of the drag view, or -1 if the view is not a bubble
     */
    public static float getDropAnimationTarget(View child, View dragView, int blurSizeOutline,
            int[] outPos) {
        if (!isBubble(child) || !(child instanceof DraggableView draggable)) {
            return -1;
        }
        ((BubbleCellLayout) child.getParent().getParent()).updateBubble(child);
        Rect bounds = new Rect();
        draggable.getWorkspaceVisualDragBounds(bounds);
        int previewSize = dragView.getMeasuredWidth() - blurSizeOutline;
        if (bounds.isEmpty() || previewSize <= 0) {
            return -1;
        }
        float[] points = {bounds.left, bounds.top, bounds.right, bounds.bottom};
        Launcher.getLauncher(child.getContext()).getDragLayer()
                .getDescendantCoordRelativeToSelf(child, points);
        // The drag view is scaled about its centre, which is the centre of its icon.
        outPos[0] = Math.round((points[0] + points[2] - dragView.getMeasuredWidth()) / 2);
        outPos[1] = Math.round((points[1] + points[3] - dragView.getMeasuredHeight()) / 2);
        return Math.abs(points[2] - points[0]) / previewSize;
    }

    /** Clips the drag preview of a bubble icon to its round shape. */
    public static void clipDragPreview(View view, Canvas canvas, Rect iconBounds) {
        if (view instanceof BubbleTextView && isBubble(view)) {
            Path path = new Path();
            path.addOval(iconBounds.left, iconBounds.top, iconBounds.right, iconBounds.bottom,
                    Path.Direction.CW);
            canvas.clipPath(path);
        }
    }

    /**
     * Returns the bounds of the icon of a bubble as shown, in drag layer coordinates.
     *
     * @return false if the icon is not a bubble
     */
    public static boolean getBubbleIconBounds(BubbleTextView icon, Rect outBounds) {
        if (!isBubble(icon)) {
            return false;
        }
        icon.getIconBounds(outBounds);
        float[] points = {outBounds.left, outBounds.top, outBounds.right, outBounds.bottom};
        Launcher.getLauncher(icon.getContext()).getDragLayer()
                .getDescendantCoordRelativeToSelf(icon, points);
        outBounds.set(Math.round(Math.min(points[0], points[2])),
                Math.round(Math.min(points[1], points[3])),
                Math.round(Math.max(points[0], points[2])),
                Math.round(Math.max(points[1], points[3])));
        return true;
    }

    /**
     * Returns the horizontal inset of a widget in a row of the given cell size: the widgets are
     * as wide as the rows of three bubbles, as they are seen.
     */
    public static int getWidgetInset(int cellSize) {
        return Math.round(cellSize * (1 - ICON_FILL * ICON_VISIBLE_AREA_FACTOR) / 2);
    }

    /** Returns the page (flower) of the item in the given row. */
    public static int pageOf(int cellY) {
        return Math.max(0, cellY - 1) / ROWS_PER_PAGE;
    }
}
