/*
 * Copyright (C) WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */
package org.witaqua.launcher3.bubble;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID;

import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.model.data.ItemInfo;

/**
 * Shows the bubble screen as the first workspace page, and hides the classic home screen.
 */
public class BubbleCellPosMapper extends CellPosMapper {

    public BubbleCellPosMapper(boolean hasVerticalHotseat, int numOfHotseat) {
        super(hasVerticalHotseat, numOfHotseat);
    }

    @Override
    public CellPos mapModelToPresenter(ItemInfo info) {
        if (info.container != CONTAINER_DESKTOP) {
            return super.mapModelToPresenter(info);
        }
        return new CellPos(info.cellX, info.cellY,
                info.screenId == BubbleHome.BUBBLE_SCREEN_ID
                        ? FIRST_SCREEN_ID : BubbleHome.HIDDEN_SCREEN_ID);
    }

    @Override
    public CellPos mapPresenterToModel(int presenterX, int presenterY, int presenterScreen,
            int container) {
        if (container == CONTAINER_DESKTOP && presenterScreen == FIRST_SCREEN_ID) {
            return new CellPos(presenterX, presenterY, BubbleHome.BUBBLE_SCREEN_ID);
        }
        return super.mapPresenterToModel(presenterX, presenterY, presenterScreen, container);
    }
}
