/*
 * Copyright (C) WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.folder;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import com.android.launcher3.util.BlurBackgroundHelper;

/**
 * Fullscreen blurred backdrop shown behind a folder while WitAqua's fullscreen
 * folder mode (pref_folder_fullscreen_blur) is enabled. The blur itself is drawn
 * by {@link BlurBackgroundHelper}; this view only hosts the drawing while the
 * folder is open and fades in/out alongside the folder's open/close animations.
 * It never consumes touches, so outside-tap-to-close keeps working.
 *
 * <p>The view is laid out with a custom position covering the whole drag layer
 * (see Folder.showFullscreenBlurView), so the backdrop also covers the status
 * bar and navigation bar areas.
 */
public class FolderFullscreenBlurView extends View {

    private final BlurBackgroundHelper mBlurHelper;

    public FolderFullscreenBlurView(Context context, BlurBackgroundHelper blurHelper) {
        super(context);
        mBlurHelper = blurHelper;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mBlurHelper.drawFullscreenFolderBlur(canvas, this);
    }
}
