/*
 * Copyright (C) WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */
package org.witaqua.launcher3.bubble;

import static org.witaqua.launcher3.bubble.BubbleHome.COLUMNS;
import static org.witaqua.launcher3.bubble.BubbleHome.ICON_FILL;
import static org.witaqua.launcher3.bubble.BubbleHome.ITEMS_PER_PAGE;
import static org.witaqua.launcher3.bubble.BubbleHome.MAX_ROWS;
import static org.witaqua.launcher3.bubble.BubbleHome.ROWS_PER_PAGE;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.OverScroller;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.util.MultiTranslateDelegate;

import java.util.function.Consumer;

/**
 * Workspace page of the bubble home screen.
 *
 * Cells are square, {@link #VISIBLE_ROWS} rows are centred in the page so that the current
 * flower is shown with one row of the previous and of the next one. The page scrolls vertically
 * one flower at a time; the bubbles of the flowers being left or entered shrink, blur and are
 * pulled towards their flower.
 */
public class BubbleCellLayout extends CellLayout {

    private static final int VISIBLE_ROWS = 5;
    private static final int SNAP_DURATION_MS = 350;

    // Scales of the bubbles.
    private static final float MIN_SCALE = 0.6f;
    private static final float CENTER_MIN_SCALE = 0.9f;
    private static final float CENTER_MAX_SCALE = 1.138f;
    private static final float CENTER_TRANSLATION_Y = 0.75f;

    /** Blur of the icons a flower away, relative to the cell. */
    private static final float MAX_BLUR = 0.04f;
    /** Alpha of the icons a flower away, when blurs are turned off on the device. */
    private static final float DIMMED_ALPHA = 0.5f;

    /** Translation channel of the bubble layout; it is only used by taskbar items otherwise. */
    private static final int TRANSLATE_INDEX = MultiTranslateDelegate.INDEX_TASKBAR_PINNING_ANIM;

    private static final String KEY_PAGE = "bubble_home_page";

    /** Distance to the centre of a bubble, relative to a cell, within which a drop makes a folder. */
    private static final float FOLDER_CREATION_RADIUS = 0.25f;

    /** Height of the top and bottom areas that scroll the flowers while dragging. */
    private static final float EDGE_SCROLL_AREA = 0.12f;
    private static final int EDGE_SCROLL_DELAY_MS = 500;
    private static final int EDGE_SCROLL_REPEAT_MS = 800;

    /** Null in the preview of the home screen, which is not touched. */
    private final Launcher mLauncher;
    private final OverScroller mScroller;
    private final int mTouchSlop;
    private final Rect mTmpRect = new Rect();

    private int mCellSize;
    private int mTopOffset;
    /** Vertical centre of an icon in its cell, as laid out by Launcher3. */
    private float mIconCenterY;
    /** Scroll position, 0 on the first flower. */
    private int mScrollPos;
    private int mCurrentPage;
    private int mNextPage = -1;

    private int mDownScrollPos;
    private int mCurrentMaxScroll;
    private float mDownX;
    private float mDownY;
    private float mLastY;
    private float mLastYRemainder;
    private boolean mScrolling;
    private boolean mLongPressed;

    private final Runnable mLongPress = this::onLongPressEmpty;
    private final Runnable mEdgeScroll = this::onEdgeScroll;
    private int mEdgeDirection;

    private final GestureDetector mDoubleTapDetector;

    private final Consumer<Boolean> mBlurEnabledListener = this::setBlurEnabled;
    private boolean mBlurEnabled = true;

    /** Widgets shown above and below the current flower, over the neighbouring ones. */
    private final BubbleWidgetFrame mTopWidget;
    private final BubbleWidgetFrame mBottomWidget;

    public BubbleCellLayout(Context context) {
        super(context, (AttributeSet) null);
        mLauncher = mActivity instanceof Launcher launcher ? launcher : null;
        setHapticFeedbackEnabled(false);
        mBorderSpace = new Point();
        setGridSize(COLUMNS, MAX_ROWS);
        mScroller = new OverScroller(context);
        mTouchSlop = ViewConfiguration.get(context).getScaledPagingTouchSlop();
        mCurrentPage = LauncherPrefs.getPrefs(context).getInt(KEY_PAGE, 0);
        mDoubleTapDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent event) {
                        if (mScrolling || !LauncherPrefs.SLEEP_GESTURE.get(getContext())) {
                            return false;
                        }
                        mLauncher.onSleepEvent(event);
                        return true;
                    }
                });
        mDoubleTapDetector.setIsLongpressEnabled(false);

        mTopWidget = new BubbleWidgetFrame(context, BubbleWidgets.KEY_TOP);
        mBottomWidget = new BubbleWidgetFrame(context, BubbleWidgets.KEY_BOTTOM);
        addView(mTopWidget);
        addView(mBottomWidget);
    }

    private int pageDistance() {
        return Math.max(1, ROWS_PER_PAGE * mCellSize);
    }

    private float scrollPages() {
        return mScrollPos / (float) pageDistance();
    }

    /** Last flower that can be reached: the one of the last place an item can take. */
    private int getMaxPage() {
        return BubbleHome.pageOf(BubbleHome.cellOf(getLastIndex()).y);
    }

    /**
     * Returns the last order an item can take. While dragging, it is the place after the other
     * bubbles, as they are a list: the dragged bubble is the invisible one, or an item from
     * elsewhere.
     */
    private int getLastIndex() {
        boolean dragging = mLauncher != null && mLauncher.getDragController().isDragging();
        ViewGroup container = getShortcutsAndWidgets();
        int count = 0;
        int last = 0;
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            View child = container.getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            last = Math.max(last, BubbleHome.indexOf(lp.getCellX(), lp.getCellY()));
            if (!dragging || child.getVisibility() == VISIBLE) {
                count++;
            }
        }
        return Math.max(last, dragging ? count : count - 1);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int height = MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom();
        int cell = width / COLUMNS;
        mBorderSpace.set(0, 0);
        if (cell != mCellSize) {
            mCellSize = cell;
            setCellDimensions(cell, cell);
        }
        mTopOffset = (height - VISIBLE_ROWS * cell) / 2;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // The content scrolls: the items view holds every possible flower, or the touches
        // outside of the first screen would not reach it.
        View container = getShortcutsAndWidgets();
        int contentHeight = MAX_ROWS * mCellSize;
        if (container.getHeight() != contentHeight) {
            container.layout(container.getLeft(), container.getTop(), container.getRight(),
                    container.getTop() + contentHeight);
        }
        // Layouts happen while scrolling too: only settle on a flower when idle.
        if (mNextPage == -1 && !mScrolling && mScroller.isFinished()) {
            mCurrentPage = Math.min(mCurrentPage, getMaxPage());
            mScrollPos = mCurrentPage * pageDistance();
        }
        applyScroll();
        layoutWidgets(b - t);
    }

    /**
     * Lays the widgets out above and below the current flower, at most a bubble high: they are
     * where the neighbouring flowers are shown.
     */
    private void layoutWidgets(int height) {
        int centerY = getFlowerCenterY();
        int flowerHalfHeight = Math.round(ROWS_PER_PAGE * mCellSize / 2f);
        layoutWidget(mTopWidget, 0, centerY - flowerHalfHeight);
        layoutWidget(mBottomWidget, centerY + flowerHalfHeight, height);
    }

    private void layoutWidget(View widget, int top, int bottom) {
        int height = Math.max(0, Math.min(bottom - top, mCellSize));
        // Centred in the area between the flower and the edge of the page, and as wide as the
        // rows of three bubbles.
        top = (top + bottom - height) / 2;
        View container = getShortcutsAndWidgets();
        int inset = BubbleHome.getWidgetInset(mCellSize);
        int left = container.getLeft() + inset;
        int right = container.getLeft() + COLUMNS * mCellSize - inset;
        widget.measure(MeasureSpec.makeMeasureSpec(Math.max(0, right - left), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        widget.layout(left, top, right, top + height);
    }

    /** Returns the centre of the current flower once settled, in the page. */
    private int getFlowerCenterY() {
        return getShortcutsAndWidgets().getTop() + mTopOffset + 2 * mCellSize
                + Math.round(mIconCenterY) - Math.round(mIconCenterY - mCellSize / 2f);
    }

    private void applyScroll() {
        updateIconMetrics();
        // Launcher3 lays the icon out above the centre of its cell (room for the label): the
        // whole content is moved instead of each icon, so that the visual centre of a cell is
        // where Launcher3 expects it when dragging.
        scrollTo(0, mScrollPos - mTopOffset + Math.round(mIconCenterY - mCellSize / 2f));
        // The widgets do not scroll.
        mTopWidget.setTranslationY(getScrollY());
        mBottomWidget.setTranslationY(getScrollY());
        updateBackgroundBounds();
        updateChildren();
    }

    /**
     * The page is outlined while dragging: the outline is the square of the current flower, it
     * does not scroll with the bubbles.
     */
    private void updateBackgroundBounds() {
        // CellLayout lays the background out on the width of the page.
        Rect bounds = mBackground.getBounds();
        int size = bounds.width();
        // Centred on the centre bubble of the current flower, as it is shown once settled.
        int centerY = getScrollY() + getFlowerCenterY();
        mBackground.setBounds(bounds.left, centerY - size / 2, bounds.right,
                centerY - size / 2 + size);
    }

    private void updateIconMetrics() {
        final ViewGroup container = getShortcutsAndWidgets();
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            if (container.getChildAt(i) instanceof BubbleTextView icon) {
                icon.getIconBounds(mTmpRect);
                if (mTmpRect.width() > 0) {
                    mIconCenterY = mTmpRect.exactCenterY();
                    return;
                }
            }
        }
        mIconCenterY = mCellSize / 2f;
    }

    // ----- Paging.

    /** Scrolls back to the first flower. */
    public void snapToDefaultPage(boolean animate) {
        mNextPage = 0;
        mScroller.startScroll(0, mScrollPos, 0, -mScrollPos, animate ? SNAP_DURATION_MS : 0);
        postInvalidateOnAnimation();
    }

    private void snapToPage(int delta) {
        mNextPage = Math.max(0, Math.min(getMaxPage(), mCurrentPage + delta));
        mScroller.startScroll(0, mScrollPos, 0, mNextPage * pageDistance() - mScrollPos,
                SNAP_DURATION_MS);
        postInvalidateOnAnimation();
    }

    /** Moves to the neighbouring flower once the finger travelled an eighth of a flower. */
    private void snapAfterDrag() {
        mScrolling = false;
        int threshold = pageDistance() / 8;
        if (mScrollPos - mDownScrollPos > threshold) {
            snapToPage(1);
        } else if (mDownScrollPos - mScrollPos > threshold) {
            snapToPage(-1);
        } else {
            snapToPage(0);
        }
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScroller.computeScrollOffset()) {
            mScrollPos = mScroller.getCurrY();
            applyScroll();
            postInvalidateOnAnimation();
            return;
        }
        if (mNextPage != -1) {
            mScrollPos = mNextPage * pageDistance();
            if (mCurrentPage != mNextPage) {
                mCurrentPage = mNextPage;
                // Kept so that the flower is restored, even after Launcher was killed.
                final SharedPreferences.Editor editor = LauncherPrefs.getPrefs(getContext())
                        .edit().putInt(KEY_PAGE, mCurrentPage);
                post(editor::apply);
            }
            mNextPage = -1;
            applyScroll();
        }
    }

    // ----- Bubbles.

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Other parts of Launcher3 animate the items too (new app bounce, drops...): the
        // bubble layout is applied on top of them.
        updateChildren();
        super.dispatchDraw(canvas);
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        requestLayout();
    }

    /** Lays the bubbles out for the current scroll position. */
    private void updateChildren() {
        if (mCellSize == 0) {
            return;
        }
        final float scrollPages = scrollPages();
        final ViewGroup container = getShortcutsAndWidgets();
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            View child = container.getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            int cellY = lp.useTmpCoords ? lp.getTmpCellY() : lp.getCellY();
            if (Math.abs(BubbleHome.pageOf(cellY) - scrollPages) <= 2) {
                updateChild(child, scrollPages);
            }
        }
    }

    /** Applies the bubble layout to an item, before it is shown at its position. */
    void updateBubble(View child) {
        if (mCellSize > 0) {
            updateChild(child, scrollPages());
        }
    }

    private void updateChild(View child, float scrollPages) {
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
        // While reordering, the bubbles are moved to temporary cells.
        int cellX = lp.useTmpCoords ? lp.getTmpCellX() : lp.getCellX();
        int cellY = lp.useTmpCoords ? lp.getTmpCellY() : lp.getCellY();
        if (BubbleHome.indexOf(cellX, cellY) < 0) {
            return;
        }
        final float cell = mCellSize;
        final int page = BubbleHome.pageOf(cellY);
        final boolean twoItemRow = BubbleHome.isTwoItemRow(cellY);
        final boolean centre = BubbleHome.isCentre(cellX, cellY);
        final float away = Math.min(1f, Math.abs(scrollPages - page));

        // Flowers being left or entered: side bubbles are pulled in, the centre one moves to the
        // flower it belongs to.
        float shift = cell / 2 * away;
        float tx = 0;
        float ty = 0;
        if (twoItemRow) {
            tx += cellX == 0 ? -shift : shift;
        } else if (!centre) {
            tx += cellX == 0 ? shift : -shift;
        } else {
            float dy = CENTER_TRANSLATION_Y * cell * away;
            ty = scrollPages > page ? dy : -dy;
        }
        float fullScale = centre ? CENTER_MAX_SCALE : 1f;
        float minScale = centre ? CENTER_MIN_SCALE : MIN_SCALE;
        float scale = (1f - away) * fullScale + away * minScale;

        // The icon fills the bubble and is centred in the cell.
        float iconSize;
        if (child instanceof FolderIcon folder) {
            folder.setTextVisible(false);
            folder.getPreviewBounds(mTmpRect);
            iconSize = 2f * folder.getFolderBackground().getRadius();
        } else if (child instanceof BubbleTextView icon) {
            icon.getIconBounds(mTmpRect);
            iconSize = mTmpRect.width();
            // Bubbles have no label and a round icon.
            icon.setBubbleStyle(true);
        } else {
            return;
        }
        if (iconSize <= 0) {
            return;
        }
        tx += cell / 2 - mTmpRect.exactCenterX();
        ty += mIconCenterY - mTmpRect.exactCenterY();
        scale *= cell * ICON_FILL / iconSize;

        child.setPivotX(mTmpRect.exactCenterX());
        child.setPivotY(mTmpRect.exactCenterY());
        // The reorder animations of Launcher3 scale the items too, on top of the bubble scale.
        if (child instanceof BubbleTextView icon) {
            icon.setBubbleScale(scale);
        } else if (child instanceof FolderIcon folder) {
            folder.setBubbleScale(scale);
        }
        if (child instanceof Reorderable reorderable) {
            reorderable.getTranslateDelegate().setTranslation(TRANSLATE_INDEX, tx, ty);
        } else {
            child.setTranslationX(tx);
            child.setTranslationY(ty);
        }

        // The icons a flower away are cross-faded to a blurred version. When blurs are turned
        // off on the device, they fade instead, so that they do not look like the current
        // flower. The effect is kept as a tag: blur radius, or minus the alpha.
        float effect = mBlurEnabled
                ? Math.round(MAX_BLUR * cell * away)
                : -Math.round((1f - (1f - DIMMED_ALPHA) * away) * 100) / 100f;
        Float lastEffect = (Float) child.getTag(R.id.bubble_blur_radius);
        if (lastEffect == null || lastEffect != effect) {
            child.setTag(R.id.bubble_blur_radius, effect);
            child.setRenderEffect(createEffect(effect));
        }
    }

    private static RenderEffect createEffect(float effect) {
        if (effect > 0) {
            return RenderEffect.createBlurEffect(effect, effect, Shader.TileMode.DECAL);
        }
        if (effect < 0 && effect > -1) {
            ColorMatrix alpha = new ColorMatrix();
            alpha.setScale(1, 1, 1, -effect);
            return RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(alpha));
        }
        return null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getContext().getSystemService(WindowManager.class).addCrossWindowBlurEnabledListener(
                getContext().getMainExecutor(), mBlurEnabledListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        getContext().getSystemService(WindowManager.class)
                .removeCrossWindowBlurEnabledListener(mBlurEnabledListener);
        super.onDetachedFromWindow();
    }

    private void setBlurEnabled(boolean enabled) {
        if (mBlurEnabled != enabled) {
            mBlurEnabled = enabled;
            invalidate();
        }
    }

    // ----- Drag and drop

    @Override
    protected int getTranslationXForCell(int cellX, int cellY) {
        return getTranslationXForRow(cellY);
    }

    /** Rows of two bubbles are offset by half a cell. */
    int getTranslationXForRow(int cellY) {
        return BubbleHome.isTwoItemRow(cellY) ? mCellSize / 2 : 0;
    }

    /** The bubble a dragged item will land on lights up. */
    @Override
    protected void drawDropLocation(Canvas canvas, CellLayoutLayoutParams params, RectF bounds,
            Paint paint) {
        int cellX = params.getCellX();
        int cellY = params.getCellY();
        if (BubbleHome.indexOf(cellX, cellY) < 0) {
            return;
        }
        cellToRect(cellX, cellY, 1, 1, mTmpRect);
        float radius = mCellSize * ICON_FILL / 2
                * (BubbleHome.isCentre(cellX, cellY) ? CENTER_MAX_SCALE : 1f);
        float cx = mTmpRect.exactCenterX();
        float cy = mTmpRect.top + mIconCenterY;
        int strokeAlpha = paint.getAlpha();
        Paint.Style style = paint.getStyle();
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(strokeAlpha / 3);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(style);
        paint.setAlpha(strokeAlpha);
        canvas.drawCircle(cx, cy, radius, paint);
    }

    /** Only the cells of the flowers around the current one can be dropped on. */
    @Override
    protected int[] findNearestArea(int relativeXPos, int relativeYPos, int minSpanX,
            int minSpanY, int spanX, int spanY, boolean ignoreOccupied, int[] result,
            int[] resultSpan) {
        final int[] bestXY = result != null ? result : new int[2];
        bestXY[0] = bestXY[1] = -1;
        if (resultSpan != null) {
            resultSpan[0] = resultSpan[1] = 1;
        }
        if (spanX != 1 || spanY != 1) {
            // Only icons and folders fit in bubbles.
            return bestXY;
        }
        int index = nearestIndex(relativeXPos, relativeYPos, ignoreOccupied);
        if (index >= 0) {
            Point cell = BubbleHome.cellOf(index);
            bestXY[0] = cell.x;
            bestXY[1] = cell.y;
        }
        return bestXY;
    }

    /**
     * Returns the order of the bubble nearest to the given point (in content coordinates) on
     * the current flower, or -1.
     */
    int nearestIndex(int x, int y, boolean includeOccupied) {
        final int page = touchablePage();
        final int end = Math.min((page + 1) * ITEMS_PER_PAGE, getLastIndex() + 1);
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int index = page * ITEMS_PER_PAGE; index < end; index++) {
            Point cell = BubbleHome.cellOf(index);
            if (!includeOccupied && isOccupied(cell.x, cell.y)) {
                continue;
            }
            cellToRect(cell.x, cell.y, 1, 1, mTmpRect);
            double distance = Math.hypot(x - mTmpRect.exactCenterX(),
                    y - (mTmpRect.top + mIconCenterY));
            if (distance < bestDistance) {
                best = index;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Override
    public BubbleReorderAlgorithm createReorderAlgorithm() {
        return new BubbleReorderAlgorithm(this);
    }

    /** Dropping near the centre of a bubble makes a folder. */
    @Override
    public float getFolderCreationRadius(int[] targetCell) {
        return mCellSize * FOLDER_CREATION_RADIUS;
    }

    /** Resting anywhere else in a cell pushes the bubbles to make room. */
    @Override
    public float getReorderRadius(int[] targetCell, int spanX, int spanY) {
        return mCellSize;
    }

    /**
     * Called while an item is dragged over the bubbles, with the drag layer y of the item:
     * resting near the top or bottom edge scrolls to the previous or next flower.
     */
    public void onDragOver(int dragLayerY) {
        int height = mLauncher.getDragLayer().getHeight();
        int area = (int) (height * EDGE_SCROLL_AREA);
        int direction = dragLayerY < area ? -1 : dragLayerY > height - area ? 1 : 0;
        if (direction != mEdgeDirection) {
            mEdgeDirection = direction;
            removeCallbacks(mEdgeScroll);
            if (direction != 0) {
                postDelayed(mEdgeScroll, EDGE_SCROLL_DELAY_MS);
            }
        }
    }

    /** Called when the dragged item leaves the bubbles, e.g. for a drop target. */
    public void onDragExit() {
        mEdgeDirection = 0;
        removeCallbacks(mEdgeScroll);
    }

    private void onEdgeScroll() {
        if (mEdgeDirection == 0 || !mLauncher.getDragController().isDragging()) {
            mEdgeDirection = 0;
            return;
        }
        snapToPage(mEdgeDirection);
        postDelayed(mEdgeScroll, EDGE_SCROLL_REPEAT_MS);
    }

    // ----- Touch

    /** Returns the page bubbles can be touched on: the one mostly shown. */
    private int touchablePage() {
        return Math.round(scrollPages());
    }

    @Override
    public void enableHardwareLayer(boolean hasLayer) {
        // A layer only holds what is inside the view, the bubbles of the other flowers are
        // drawn outside of it as the page scrolls.
        super.enableHardwareLayer(false);
    }

    /** Bubbles of the neighbouring flowers are only a preview, they cannot be touched. */
    boolean isTouchable(View child) {
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
        return BubbleHome.pageOf(lp.getCellY()) == touchablePage();
    }

    @Override
    protected ShortcutAndWidgetContainer createShortcutsAndWidgetContainer(Context context,
            int containerType) {
        return new BubbleShortcutAndWidgetContainer(context, containerType);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (super.onInterceptTouchEvent(ev)) {
            return true;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                onDown(ev);
                return mScrolling;
            case MotionEvent.ACTION_MOVE:
                return startScrollIfNeeded(ev);
            default:
                return false;
        }
    }

    private void onDown(MotionEvent ev) {
        mScrolling = false;
        mLongPressed = false;
        if (!mScroller.isFinished() || mNextPage != -1) {
            // Touching while snapping catches the flowers where they are: the finger now scrolls
            // them, and the bubbles cannot be pressed as they are moving.
            mScroller.abortAnimation();
            if (mNextPage != -1) {
                mCurrentPage = mNextPage;
                mNextPage = -1;
            }
            mScrolling = true;
        }
        mDownScrollPos = mScrollPos;
        mCurrentMaxScroll = pageDistance() * (mCurrentPage + 1);
        mDownX = ev.getX();
        mDownY = mLastY = ev.getY();
        mLastYRemainder = 0;
    }

    private boolean startScrollIfNeeded(MotionEvent ev) {
        if (mScrolling) {
            return true;
        }
        float dx = ev.getX() - mDownX;
        float dy = ev.getY() - mDownY;
        if (Math.abs(dy) <= mTouchSlop || Math.abs(dy) < Math.abs(dx)) {
            return false;
        }
        mScrolling = true;
        mLastY = ev.getY();
        removeCallbacks(mLongPress);
        getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // The touches on empty space are not left to the workspace, which handles the double tap
        // otherwise.
        mDoubleTapDetector.onTouchEvent(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Touches on empty space scroll the bubbles and long pressing shows the options.
                onDown(ev);
                postDelayed(mLongPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!startScrollIfNeeded(ev)) {
                    return true;
                }
                scrollBy(ev.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(mLongPress);
                if (mScrolling) {
                    snapAfterDrag();
                }
                return true;
            default:
                return true;
        }
    }

    /** Follows the finger, at most one flower further. */
    private void scrollBy(float y) {
        float dy = (mLastY + mLastYRemainder) - y;
        if (Math.abs(dy) < 1f) {
            return;
        }
        int target = (int) dy + mScrollPos;
        if (dy < 0 && target < 0) {
            dy = -mScrollPos;
        }
        if (dy > 0 && target > mCurrentMaxScroll) {
            dy = mCurrentMaxScroll - mScrollPos;
        }
        int max = getMaxPage() * pageDistance();
        if (dy > 0 && target > max) {
            dy = max - mScrollPos;
        }
        int step = (int) dy;
        mScrollPos += step;
        mLastY = y;
        mLastYRemainder = dy - step;
        applyScroll();
    }

    private void onLongPressEmpty() {
        if (mScrolling) {
            return;
        }
        mLongPressed = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        float[] point = {mDownX, mDownY};
        mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(this, point);
        mLauncher.showDefaultOptions(point[0], point[1]);
    }
}
