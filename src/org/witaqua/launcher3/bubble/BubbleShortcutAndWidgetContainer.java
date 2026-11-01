/*
 * Copyright (C) WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */
package org.witaqua.launcher3.bubble;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.folder.FolderIcon;

/**
 * Items of the bubble page. Bubbles are scaled icons, so their views are much bigger than what is
 * shown and overlap: touches go to the bubble whose round icon is under the finger, and touches
 * between bubbles are left to the page.
 */
public class BubbleShortcutAndWidgetContainer extends ShortcutAndWidgetContainer {

    private final Rect mTmpRect = new Rect();
    private final float[] mTmpPoint = new float[2];
    private final Matrix mTmpMatrix = new Matrix();
    private View mTouchTarget;

    public BubbleShortcutAndWidgetContainer(Context context, int containerType) {
        super(context, containerType);
    }

    @Override
    public void setupLp(View child) {
        super.setupLp(child);
        offsetTwoItemRow(child);
    }

    @Override
    public void measureChild(View child) {
        super.measureChild(child);
        offsetTwoItemRow(child);
    }

    /**
     * Rows of two bubbles are offset by half a cell: it is part of the layout of the items, so
     * that every animation of Launcher3 (reorder, drop, folders) moves them where they are shown.
     */
    private void offsetTwoItemRow(View child) {
        if (!(getParent() instanceof BubbleCellLayout page)) {
            return;
        }
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
        if (!lp.isLockedToGrid) {
            return;
        }
        int cellY = lp.useTmpCoords ? lp.getTmpCellY() : lp.getCellY();
        lp.x += page.getTranslationXForRow(cellY);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mTouchTarget = findBubbleAt(ev.getX(), ev.getY());
        }
        View target = mTouchTarget;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mTouchTarget = null;
        }
        if (target == null) {
            return false;
        }
        MotionEvent event = MotionEvent.obtain(ev);
        event.offsetLocation(getScrollX() - target.getLeft(), getScrollY() - target.getTop());
        if (!target.getMatrix().isIdentity()) {
            target.getMatrix().invert(mTmpMatrix);
            event.transform(mTmpMatrix);
        }
        boolean handled = target.dispatchTouchEvent(event);
        event.recycle();
        return handled;
    }

    /** Returns the touchable bubble whose round icon contains the point, or null. */
    private View findBubbleAt(float x, float y) {
        if (!(getParent() instanceof BubbleCellLayout page)) {
            return null;
        }
        View best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child.getVisibility() != VISIBLE || !page.isTouchable(child)) {
                continue;
            }
            if (child instanceof FolderIcon folder) {
                folder.getPreviewBounds(mTmpRect);
            } else if (child instanceof BubbleTextView icon) {
                icon.getIconBounds(mTmpRect);
            } else {
                continue;
            }
            mTmpPoint[0] = mTmpRect.exactCenterX();
            mTmpPoint[1] = mTmpRect.exactCenterY();
            child.getMatrix().mapPoints(mTmpPoint);
            float dx = x + getScrollX() - child.getLeft() - mTmpPoint[0];
            float dy = y + getScrollY() - child.getTop() - mTmpPoint[1];
            float radius = mTmpRect.width() * child.getScaleX() / 2;
            float distance = dx * dx + dy * dy;
            if (distance <= radius * radius && distance < bestDistance) {
                best = child;
                bestDistance = distance;
            }
        }
        return best;
    }
}
