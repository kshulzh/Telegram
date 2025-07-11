package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Bitmaps;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

public class MyBlurredLinearLayout extends LinearLayout {
    private final Path path = new Path();
    private final RectF rectF = new RectF();
    public boolean blurBackground = false;
    Theme.ResourcesProvider resourcesProvider;
    public MyBlurredLinearLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
    }
    public List<InfoIcon> allInfoIcons = new ArrayList<>(15);
    public List<InfoIcon> selected = new ArrayList<>(4);

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int childCount = getChildCount();
        if (childCount == 0) return;
        int space = dp(8);

        int totalSpace = space * (childCount - 1);
        int elementWidth = ((right - left - space*2) - totalSpace) / childCount;
        int elementHeight = bottom - top - space*2;

        for (int i = 0; i < childCount; i++) {
            int start = i * (elementWidth + space) + space;
            getChildAt(i).layout(
                    start,
                    space,
                    start + elementWidth,
                    elementHeight + space
            );
            getChildAt(i).invalidate();
        }
    }

    public void drawBlur(RenderNode contentNode, Canvas canvas) {
        if (blurBackground && Build.VERSION.SDK_INT >= 31) {
            RenderNode blurNode = new RenderNode("blur");
            blurNode.setRenderEffect(RenderEffect.createBlurEffect(60f, 60f,
                    Shader.TileMode.CLAMP));
            int left = getLeft();
            int top = getTop();
            int width = getWidth();
            int height = getHeight();
            int right = getRight();
            int bottom = getBottom();
            blurNode.setPosition(
                    left,
                    top,
                    right,
                    bottom
            );
            blurNode.setTranslationY(getTranslationY());
            blurNode.setTranslationX(getTranslationX());

            Canvas blurCanvas = blurNode.beginRecording(width, height);
            rectF.set(0, 0, width, height);
            path.rewind();
            path.addRoundRect(rectF, dp(0f), dp(0f), Path.Direction.CW);
            blurCanvas.clipPath(path);
            blurCanvas.translate(-left, -top - getTranslationY());
            blurCanvas.drawRenderNode(contentNode);

            // Draw InfoIcon components (ImageView and TextView) on top of blurred background

            blurNode.endRecording();
            canvas.drawRenderNode(blurNode);

        }
        for (InfoIcon infoIcon : selected) {
            infoIcon.drawBlur(contentNode, canvas);
        }
    }

    public void setProgress(float progress) {
        for (InfoIcon infoIcon : allInfoIcons) {
            infoIcon.setProgress(progress);
        }
    }

    public InfoIcon addInfoIcon(int drawableRes, int text) {
        InfoIcon infoIcon = new InfoIcon(drawableRes, text);
        addInfoIcon(infoIcon);
        return infoIcon;
    }

    public void addInfoIcon(InfoIcon infoIcon) {
        allInfoIcons.add(infoIcon);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        for (InfoIcon infoIcon : selected) {
            infoIcon.invalidate();
        }
    }

    @Override
    public void setScaleY(float scaleY) {
        super.setScaleY(scaleY);
        for (InfoIcon infoIcon : allInfoIcons) {
            infoIcon.setScaleY(scaleY);
        }
    }

    public void refresh() {
        int i = 0;
        selected.clear();
        for (int j =0 ; j<allInfoIcons.size(); j++) {
            if (allInfoIcons.get(j).isVisible) {
                if (i > 3) {
                    continue;
                }
                if (getChildAt(i)!=allInfoIcons.get(j)) {
                    addView(allInfoIcons.get(j), i);
                }
                selected.add(allInfoIcons.get(j));
                i++;
            } else {
                removeView(allInfoIcons.get(j));

            }
        }

        invalidate();
    }

    public class InfoIcon extends LinearLayout {
        private final Path path = new Path();
        private final RectF rectF = new RectF();
        public ImageView imageView;
        public TextView textView;
        private boolean isDrawn = false;
        public boolean isVisible = false;

        public InfoIcon(int drawableRes, int text) {
            super(MyBlurredLinearLayout.this.getContext());
            imageView = new ImageView(MyBlurredLinearLayout.this.getContext());
            imageView.setImageResource(drawableRes);
            textView = new TextView(MyBlurredLinearLayout.this.getContext());
            textView.setText(text);
            textView.setTextSize(14);
            textView.setTextColor(Theme.getColor(Theme.key_profile_title, resourcesProvider));
            textView.setGravity(Gravity.CENTER);
            textView.setTypeface(AndroidUtilities.bold());
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);

            this.addView(imageView, LayoutHelper.createFrame(dp(8), dp(8), Gravity.CENTER, 0, 0, 0, 0));
            this.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (isDrawn) {
                super.onDraw(canvas);
            }
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            if (isDrawn) {
                super.dispatchDraw(canvas);
            }
        }

        public void drawBlur(RenderNode contentNode, Canvas canvas) {
            float left = getLeft() + MyBlurredLinearLayout.this.getLeft();
            float top = getTop() + MyBlurredLinearLayout.this.getTop() + MyBlurredLinearLayout.this.getTranslationY();
            float widthOriginal =getWidth();
            float heightOriginal =getHeight();
            int width =(int)(getWidth() * getScaleX());
            int height =(int) (getHeight() * getScaleY());
            if (width <= 0 || height <= 0) {
                return;
            }
            Bitmap bitmap = Bitmaps.createBitmap((int)widthOriginal, (int)heightOriginal, Bitmap.Config.ARGB_8888);
            Canvas bitmapCanvas = new Canvas(bitmap);
            if (Build.VERSION.SDK_INT >= 31 && !blurBackground) {
                RenderNode blurNode = new RenderNode("blur");
                blurNode.setRenderEffect(RenderEffect.createBlurEffect(15f, 15f,
                        Shader.TileMode.CLAMP));
                blurNode.setPosition(
                        MyBlurredLinearLayout.this.getLeft() + getLeft(),
                        MyBlurredLinearLayout.this.getTop() + getTop(),
                        MyBlurredLinearLayout.this.getRight() + getRight(),
                        MyBlurredLinearLayout.this.getBottom() + getBottom()
                );
                blurNode.setTranslationY(MyBlurredLinearLayout.this.getTranslationY());
                blurNode.setTranslationX(MyBlurredLinearLayout.this.getTranslationX());

                Canvas blurCanvas = blurNode.beginRecording(getWidth(), getHeight());
                rectF.set(0, 0, width, height);
                path.rewind();
                path.addRoundRect(rectF, dp(16f), dp(16f), Path.Direction.CW);
                blurCanvas.clipPath(path);
                blurCanvas.translate(-left, -top);
                blurCanvas.drawRenderNode(contentNode);

                // Draw InfoIcon components (ImageView and TextView) on top of blurred background

                blurNode.endRecording();
                canvas.drawRenderNode(blurNode);
            }
            GradientDrawable background = new GradientDrawable();
            background.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            background.setBounds(0, 0, (int)widthOriginal, (int)heightOriginal);
            background.setAlpha(40);// Background color
            background.setCornerRadius(dp(16f));
            background.draw(bitmapCanvas);
            forceDraw(bitmapCanvas);

            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            canvas.drawBitmap(scaledBitmap, left, top, null);
        }

        void forceDraw(Canvas canvas) {
            isDrawn = true;
            draw(canvas);
            isDrawn = false;
        }

        public void setProgress(float progress) {
            setScaleY(1-progress);
            setAlpha(1-progress);
            imageView.setScaleX(1-progress);
            imageView.setScaleY(1-progress);
            textView.setScaleY(1-progress);
            textView.setScaleX(1-progress);
        }
    }
}
