/*
 * Copyright (C) WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */
package org.witaqua.launcher3.bubble;

import android.graphics.Point;
import android.view.View;

import com.android.launcher3.celllayout.ItemConfiguration;
import com.android.launcher3.celllayout.ReorderAlgorithm;
import com.android.launcher3.celllayout.ReorderParameters;
import com.android.launcher3.util.CellAndSpan;

import java.util.ArrayList;
import java.util.List;

/**
 * Reorders the bubbles as a list: the dragged bubble is inserted where it is dropped and the
 * following ones move by one place.
 */
public class BubbleReorderAlgorithm extends ReorderAlgorithm {

    private final BubbleCellLayout mCellLayout;

    public BubbleReorderAlgorithm(BubbleCellLayout cellLayout) {
        super(cellLayout);
        mCellLayout = cellLayout;
    }

    @Override
    public ItemConfiguration calculateReorder(ReorderParameters reorderParameters) {
        ItemConfiguration solution = reorderParameters.getSolution();
        if (reorderParameters.getSpanX() != 1 || reorderParameters.getSpanY() != 1) {
            solution.isSolution = false;
            return solution;
        }
        int target = mCellLayout.nearestIndex(reorderParameters.getPixelX(),
                reorderParameters.getPixelY(), true /* includeOccupied */);
        if (target < 0) {
            solution.isSolution = false;
            return solution;
        }

        // The other bubbles, in order.
        View dragView = reorderParameters.getDragView();
        List<View> others = new ArrayList<>();
        for (View view : solution.map.keySet()) {
            if (view != dragView) {
                others.add(view);
            }
        }
        others.sort((a, b) -> Integer.compare(indexOf(solution.map.get(a)),
                indexOf(solution.map.get(b))));
        target = Math.min(target, others.size());

        for (int i = 0; i < others.size(); i++) {
            Point cell = BubbleHome.cellOf(i < target ? i : i + 1);
            CellAndSpan cellAndSpan = solution.map.get(others.get(i));
            cellAndSpan.cellX = cell.x;
            cellAndSpan.cellY = cell.y;
        }
        Point cell = BubbleHome.cellOf(target);
        solution.cellX = cell.x;
        solution.cellY = cell.y;
        solution.spanX = 1;
        solution.spanY = 1;
        solution.isSolution = true;
        return solution;
    }

    private static int indexOf(CellAndSpan cellAndSpan) {
        int index = BubbleHome.indexOf(cellAndSpan.cellX, cellAndSpan.cellY);
        return index < 0 ? Integer.MAX_VALUE : index;
    }
}
