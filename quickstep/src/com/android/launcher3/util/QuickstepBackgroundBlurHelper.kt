/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.util

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.view.View
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.launcher3.Flags.blurOnMoreSurfaces
import com.android.launcher3.R
import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.folder.Folder
import com.android.launcher3.graphics.PathWrapper
import com.android.launcher3.util.WindowBlurState.WINDOW_BLUR_STATE
import com.android.launcher3.views.ActivityContext
import javax.inject.Inject
import javax.inject.Named

/**
 * Quickstep implementation of the helper class that creates and updates the blur drawable used
 * for folders and the homescreen popup.
 */
@ActivityContextSingleton
class QuickstepBackgroundBlurHelper
@Inject
constructor(
    private val activityContext: ActivityContext,
    @Named(WINDOW_BLUR_STATE) private val blurState: ListenableRef<Boolean>,
) : BlurBackgroundHelper() {

    private val folderBlurRadius = activityContext.asContext().resources.getDimension(
        R.dimen.folder_blur_radius
    )

    private val cornerRadius = Themes.getDialogCornerRadius(activityContext.asContext())
    private val workspaceBlurRenderNode = RenderNode("workspaceBlur")
    private val workspaceBlurRenderNodeOutline = Outline()
    private val bounds = Rect()
    private val folderBlurDrawable: BackgroundBlurDrawable? by lazy {
        if (!isBlurEnabled()) null
        else
            activityContext.dragLayer.getViewRootImpl()
                .createBackgroundBlurDrawable()?.apply {
                    setBlurRadius(folderBlurRadius.toInt())
                    setVisible(false, false)
                }
    }

    override fun prepareToOpenFolder(folder: Folder) {
        if (!isBlurEnabled()) {
            return
        }

        recordWorkspaceSnapshot(folder)
    }

    // WitAqua: the fullscreen folder backdrop does not depend on the
    // blur_on_more_surfaces flag; the workspace snapshot blur works whenever a
    // snapshot can be recorded, and the cross-window part additionally requires
    // cross-window blur to be enabled on the device.
    override fun prepareFullscreenFolderBlur(folder: Folder) {
        fullscreenBlurProgress = 0f
        recordWorkspaceSnapshot(folder)
    }

    // WitAqua: blur strength of the fullscreen folder backdrop, animated by
    // the folder in sync with its open/close animation (0 = no blur,
    // 1 = full blur). Both blur layers stay at a fixed full radius and the
    // progress only cross-fades them, so the blur result is cached and
    // replayed each frame instead of being recomputed at a new radius
    // (which dropped frames).
    private var fullscreenBlurProgress = 0f

    private val fullscreenBlurEffect: RenderEffect by lazy {
        RenderEffect.createBlurEffect(
            folderBlurRadius, folderBlurRadius, Shader.TileMode.CLAMP)
    }

    override fun setFullscreenBlurProgress(progress: Float) {
        fullscreenBlurProgress = progress
    }

    override fun drawFullscreenFolderBlur(canvas: Canvas, view: View) {
        drawFullscreenCrossWindowBlur(canvas, view)
        drawFullscreenWorkspaceBlur(canvas)
    }

    // WitAqua: no extra tint is drawn over the status bar / navigation bar
    // bands. An earlier version of the backdrop darkened them by 20% to
    // match a dark layer the workspace snapshot then contained; with the
    // cross-fade backdrop the main area no longer darkens, and the tint
    // only made the bands visibly darker than the rest of the backdrop
    // (measured step of about x0.82 at the status bar boundary).

    private fun recordWorkspaceSnapshot(folder: Folder) {
        val folderIcon = folder.folderIcon
        val folderNameVisibility: Int = folderIcon.folderName.visibility
        val isIconVisible = folderIcon.iconVisible

        folderIcon.setTextVisible(false)
        folderIcon.setIconVisible(false)

        val dragLayer = activityContext.dragLayer
        val canvas =
            workspaceBlurRenderNode.beginRecording(dragLayer.getWidth(), dragLayer.getHeight())
        dragLayer.draw(canvas)
        workspaceBlurRenderNode.endRecording()
        workspaceBlurRenderNode.setPosition(0, 0, dragLayer.getWidth(), dragLayer.getHeight())

        folderIcon.folderName.visibility = folderNameVisibility
        folderIcon.setIconVisible(isIconVisible)
    }

    private var fullscreenBlurDrawable: BackgroundBlurDrawable? = null

    private fun getFullscreenBlurDrawable(): BackgroundBlurDrawable? {
        if (!blurState.value) {
            return null
        }
        if (fullscreenBlurDrawable == null) {
            fullscreenBlurDrawable =
                activityContext.dragLayer
                    .getViewRootImpl()
                    ?.createBackgroundBlurDrawable()
                    ?.apply {
                        setBlurRadius(folderBlurRadius.toInt())
                        setVisible(false, false)
                    }
        }
        return fullscreenBlurDrawable
    }

    private fun drawFullscreenCrossWindowBlur(canvas: Canvas, view: View) {
        val d = getFullscreenBlurDrawable() ?: return

        d.setVisible(true, false)
        d.setAlpha((fullscreenBlurProgress * 255).toInt())
        d.setBounds(0, 0, view.width, view.height)
        d.setCornerRadius(0f)
        d.draw(canvas)
    }

    private fun drawFullscreenWorkspaceBlur(canvas: Canvas) {
        if (!workspaceBlurRenderNode.hasDisplayList()) {
            return
        }
        workspaceBlurRenderNode.translationX = 0f
        workspaceBlurRenderNode.translationY = 0f
        workspaceBlurRenderNode.setClipToOutline(false)
        // Cross-fade between the sharp snapshot and the fully blurred one.
        // The fixed-radius blur is computed once and cached by the render
        // pipeline; only the layer alphas change per frame.
        val progress = fullscreenBlurProgress
        if (progress < 1f) {
            workspaceBlurRenderNode.setRenderEffect(null)
            workspaceBlurRenderNode.setAlpha(1f - progress)
            canvas.drawRenderNode(workspaceBlurRenderNode)
        }
        if (progress > 0f) {
            workspaceBlurRenderNode.setRenderEffect(fullscreenBlurEffect)
            workspaceBlurRenderNode.setAlpha(progress)
            canvas.drawRenderNode(workspaceBlurRenderNode)
        }
        workspaceBlurRenderNode.setAlpha(1f)
    }

    override fun drawFolderBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {
        if (!isBlurEnabled()) {
            return
        }

        drawCrossWindowBlur(canvas, pathWrapper, view)
        drawWorkspaceBlur(canvas, pathWrapper?.path, view)
    }

    private fun drawWorkspaceBlur(canvas: Canvas, path: Path?, view: View) {
        if (!workspaceBlurRenderNode.hasDisplayList()) {
            return
        }
        workspaceBlurRenderNode.translationX = -view.left.toFloat()
        workspaceBlurRenderNode.translationY = -view.top.toFloat()

        if (path != null && !path.isEmpty) {
            workspaceBlurRenderNodeOutline.setPath(path)
            workspaceBlurRenderNodeOutline.mPath.offset(view.left.toFloat(), view.top.toFloat())
        } else {
            workspaceBlurRenderNodeOutline.setRoundRect(
                view.left,
                view.top,
                view.right,
                view.bottom,
                cornerRadius,
            )
        }
        workspaceBlurRenderNode.setOutline(workspaceBlurRenderNodeOutline)
        workspaceBlurRenderNode.setClipToOutline(true)

        workspaceBlurRenderNode.setRenderEffect(
            RenderEffect.createBlurEffect(
                folderBlurRadius,
                folderBlurRadius,
                Shader.TileMode.CLAMP
            )
        )
        canvas.drawRenderNode(workspaceBlurRenderNode)
    }

    private fun drawCrossWindowBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {
        val d = folderBlurDrawable ?: return

        d.setVisible(true, false)
        if (pathWrapper != null) {
            pathWrapper.bounds.roundOut(bounds)
            d.bounds = bounds
            d.setCornerRadius(pathWrapper.cornerRadius)
        } else {
            d.setBounds(0, 0, view.width, view.height)
            d.setCornerRadius(cornerRadius)
        }
        d.draw(canvas)
    }

    override fun folderCloseComplete() {
        fullscreenBlurProgress = 0f
        if (workspaceBlurRenderNode.hasDisplayList()) {
            workspaceBlurRenderNode.discardDisplayList()
        }
        folderBlurDrawable?.setVisible(false, false)
        fullscreenBlurDrawable?.setVisible(false, false)
    }

    override fun isBlurEnabled(): Boolean {
        return blurOnMoreSurfaces() && blurState.value
    }
}
