package org.telegram.messenger.camera;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.GestureDetectorFixDoubleTap;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.WaveDrawable;
import org.telegram.ui.Components.ZoomControlView;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.recorder.CollageLayout;
import org.telegram.ui.Stories.recorder.CollageLayoutButton;
import org.telegram.ui.Stories.recorder.CollageLayoutView2;
import org.telegram.ui.Stories.recorder.DualCameraView;
import org.telegram.ui.Stories.recorder.FlashViews;
import org.telegram.ui.Stories.recorder.PhotoVideoSwitcherView;
import org.telegram.ui.Stories.recorder.RecordControl;
import org.telegram.ui.Stories.recorder.StoryEntry;
import org.telegram.ui.Stories.recorder.ToggleButton;
import org.telegram.ui.Stories.recorder.ToggleButton2;
import org.telegram.ui.Stories.recorder.VideoTimerView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CameraRecorder1 extends FrameLayout{
    public interface Media {
        File getFile();
    }

    public static class PhotoMedia implements Media {
        public File file;
        public Bitmap bitmap;
        @Override
        public File getFile() {
            return file;
        }

        public PhotoMedia(File file, Bitmap bitmap) {
            this.file = file;
            this.bitmap = bitmap;
        }
    }

    public static class VideoMedia implements Media {
        public File file;
        public List<Bitmap> bitmaps;
        @Override
        public File getFile() {
            return file;
        }

        public VideoMedia(File file, List<Bitmap> bitmaps) {
            this.file = file;
            this.bitmaps = bitmaps;
        }
    }
    private static CameraRecorder1 instance;
    public static CameraRecorder1 getInstance(Activity activity, int currentAccount) {
        if (instance != null && (instance.activity != activity || instance.currentAccount != currentAccount)) {
            instance.close();
            instance = null;
        }
        if (instance == null) {
            instance = new CameraRecorder1(activity, currentAccount);
        }
        return instance;
    }

    public static void destroyInstance() {
        if (instance != null) {
            instance.close();
        }
        instance = null;
    }
    private final Activity activity;
    private final int currentAccount;

    private WindowManager windowManager;
    private final WindowManager.LayoutParams windowLayoutParams;

    CameraPage cameraLayout;
    //MediaEditor mediaEditor;
    Consumer<Media> onResult = (r) -> {};
    public CameraRecorder1 setOnResult(Consumer<Media> onResult) {
        this.onResult = onResult;
        return this;
    }

    public void open() {
        setVisibility(VISIBLE);
        cameraLayout.setVisibility(VISIBLE);
        cameraLayout.open();
    }
    void close() {
        cameraLayout.close();
        setVisibility(GONE);
    }


    @SuppressLint("WrongConstant")
    public CameraRecorder1(Activity activity, int currentAccount) {
        super(activity.getApplicationContext());
        this.activity = activity;
        this.currentAccount = currentAccount;

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        if (Build.VERSION.SDK_INT >= 28) {
            windowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        windowLayoutParams.flags = (
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        );
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        }
        windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        windowManager.addView(this, windowLayoutParams);
        setFitsSystemWindows(true);
        AndroidUtilities.setLightNavigationBar(this, false);
        AndroidUtilities.setLightStatusBar(this, false);
        initViews();
    }

    void initViews() {
        initCameraPage();
    }

    void initCameraPage() {
        cameraLayout = new CameraPage(getContext())
                .setOnCloseListener(() -> {
                    close();
                }).setOnResult((r)-> {
                    close();
                    onResult.accept(r);
//                    mediaEditor.setVisibility(VISIBLE);
//                    mediaEditor.open();
//                    mediaEditor.setMedia(r);
                });
//        mediaEditor = new MediaEditor(getContext()).setOnCloseListener(()->{
//            cameraLayout.setVisibility(VISIBLE);
//            mediaEditor.setVisibility(GONE);
//            cameraLayout.open();
//
//        }).setOnResult(onResult);
        cameraLayout.setVisibility(GONE);
        addView(cameraLayout);
//        mediaEditor.setVisibility(GONE);
//        addView(mediaEditor);
    }

    class CameraPage extends FrameLayout {
        private final Theme.ResourcesProvider resourcesProvider = new DarkThemeResourceProvider();
        private GestureDetectorFixDoubleTap gestureDetector;
        private ScaleGestureDetector scaleGestureDetector;
        DualCameraView cameraView;
        ZoomControlView zoomControlView;
        private ToggleButton dualButton;
        private VideoTimerView videoTimerView;
        private ToggleButton2 flashButton;
        private CollageLayoutView2 collageLayoutView;
        private BlurringShader.BlurManager blurManager;
        private CollageLayoutButton.CollageLayoutListView collageListView;
        private CollageLayoutButton collageButton;
        FrameLayout topPanel;
        FrameLayout bottomPanel;
        FrameLayout bottomTopPanel;
        private RecordControl recordControl;
        FrameLayout bottomBottomPanel;
        private PhotoVideoSwitcherView modeSwitcherView;
        private float cameraZoom;
        private boolean isVideo = false;
        private boolean takingPhoto = false;
        private boolean takingVideo = false;
        private boolean stoppingTakingVideo = false;
        private boolean awaitingPlayer = false;
        private File outputFile;
        Runnable onCloseListener = () -> {};
        Consumer<Media> onResult = (m) -> {};
        CameraPage setOnCloseListener(Runnable onCloseListener) {
            this.onCloseListener = onCloseListener;
            return this;
        }

        CameraPage setOnResult(Consumer<Media> onResult) {
            this.onResult = onResult;
            return this;
        }
        private FlashViews.ImageViewInvertable closeButton;

        public CameraPage(@NonNull Context context) {
            super(context);
            //gestureDetector = new GestureDetectorFixDoubleTap(context, new StoryRecorder.WindowView.GestureListener());
            scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
            init();
        }
        void init() {
            createCameraView();
            initZoomView();
            initTopControlPane();
            initBottomPane();
        }
        void initZoomView() {
            zoomControlView = new ZoomControlView(getContext());
            zoomControlView.enabledTouch = false;
            zoomControlView.setAlpha(0.0f);
            addView(zoomControlView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 100 + 8));
            zoomControlView.setDelegate(zoom -> {
                if (cameraView != null) {
                    cameraView.setZoom(cameraZoom = zoom);
                }
                showZoomControls(true, true);
            });
            zoomControlView.setZoom(cameraZoom = 0, false);
        }

        void initTopControlPane() {
            topPanel = new FrameLayout(getContext());

            closeButton = new FlashViews.ImageViewInvertable(getContext());
            closeButton.setContentDescription(getString(R.string.AccDescrGoBack));
            closeButton.setScaleType(ImageView.ScaleType.CENTER);
            closeButton.setImageResource(R.drawable.msg_round_cancel_m);
            closeButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
            closeButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
            closeButton.setOnClickListener(e -> {
                onCloseListener.run();
            });
            topPanel.addView(closeButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.LEFT));

            flashButton = new ToggleButton2(getContext());
            flashButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
            flashButton.setOnClickListener(e -> {
                if (cameraView == null || awaitingPlayer) {
                    return;
                }
                String current = getCurrentFlashMode();
                String next = getNextFlashMode();
                if (current == null || current.equals(next)) {
                    return;
                }
                setCurrentFlashMode(next);
                setCameraFlashModeIcon(next, true);
            });
            flashButton.setOnLongClickListener(e -> {
                if (cameraView == null || !cameraView.isFrontface()) {
                    return false;
                }

//                checkFrontfaceFlashModes();
//                flashButton.setSelected(true);
//                flashViews.previewStart();
//                ItemOptions.makeOptions(containerView, resourcesProvider, flashButton)
//                        .addView(
//                                new SliderView(getContext(), SliderView.TYPE_WARMTH)
//                                        .setValue(flashViews.warmth)
//                                        .setOnValueChange(v -> {
//                                            //flashViews.setWarmth(v);
//                                        })
//                        )
//                        .addSpaceGap()
//                        .addView(
//                                new SliderView(getContext(), SliderView.TYPE_INTENSITY)
//                                        .setMinMax(.65f, 1f)
//                                        //.setValue(flashViews.intensity)
//                                        .setOnValueChange(v -> {
//                                           // flashViews.setIntensity(v);
//                                        })
//                        )
//                        .setOnDismiss(() -> {
////                            saveFrontFaceFlashMode();
////                            flashViews.previewEnd();
//                            flashButton.setSelected(false);
//                        })
//                        .setDimAlpha(0)
//                        .setGravity(Gravity.RIGHT)
//                        .translate(dp(46), -dp(4))
//                        .setBackgroundColor(0xbb1b1b1b)
//                        .show();
                return true;
            });
            flashButton.setVisibility(View.VISIBLE);
            topPanel.addView(flashButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT,0,0,56,0));

            dualButton = new ToggleButton(getContext(), R.drawable.media_dual_camera2_shadow, R.drawable.media_dual_camera2);
            dualButton.setOnClickListener(v -> {
                if (cameraView == null) {
                    return;
                }
                cameraView.toggleDual();
                dualButton.setValue(cameraView.isDual());

                //dualHint.hide();
                MessagesController.getGlobalMainSettings().edit().putInt("storydualhint", 2).apply();
//                if (savedDualHint.shown()) {
//                    MessagesController.getGlobalMainSettings().edit().putInt("storysvddualhint", 2).apply();
//                }
                //savedDualHint.hide();
            });
            dualButton.setVisibility(DualCameraView.dualAvailableStatic(getContext()) ? View.VISIBLE : View.GONE);
            topPanel.addView(dualButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT,0,0,56,0));

//            collageListView = new CollageLayoutButton.CollageLayoutListView(getContext(), flashViews);
//            collageListView.listView.scrollToPosition(6);
//            collageListView.setSelected(null);
//            collageListView.setOnLayoutClick(layout -> {
//                //collageLayoutView.setLayout(lastCollageLayout = layout, true);
//                collageListView.setSelected(layout);
//                if (cameraView != null) {
//                    cameraView.recordHevc = !collageLayoutView.hasLayout();
//                }
//                collageButton.setDrawable(new CollageLayoutButton.CollageLayoutDrawable(layout));
//                //setActionBarButtonVisible(collageRemoveButton, collageListView.isVisible(), true);
//                recordControl.setCollageProgress(collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f, true);
//            });
//            collageButton = new CollageLayoutButton(getContext());
//            collageButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
//            collageLayoutView = new CollageLayoutView2(getContext(), blurManager, this, resourcesProvider) {
//                @Override
//                protected void onLayoutUpdate(CollageLayout layout) {
//                    collageListView.setVisible(false, true);
//                    if (layout != null && layout.parts.size() > 1) {
//                        //collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout = layout), true);
//                        collageButton.setSelected(true, true);
//                    } else {
//                        collageButton.setSelected(false, true);
//                    }
//                    //updateActionBarButtons(true);
//                }
//            };
//            //collageLayoutView.setCancelGestures(windowView::cancelGestures);
//            collageLayoutView.setResetState(() -> {
//                //updateActionBarButtons(true);
//            });
//            addView(collageLayoutView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
//
//            collageButton.setOnClickListener(v -> {
//                //todo fix
//                if (cameraView != null && cameraView.isDual()) {
//                    cameraView.toggleDual();
//                }
//                if (!collageListView.isVisible() && !collageLayoutView.hasLayout()) {
//                    //collageLayoutView.setLayout(lastCollageLayout, true);
//                    //collageListView.setSelected(lastCollageLayout);
//                    collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout), true);
//                    collageButton.setSelected(true);
//                    if (cameraView != null) {
//                        cameraView.recordHevc = !collageLayoutView.hasLayout();
//                    }
//                }
//                collageListView.setVisible(!collageListView.isVisible(), true);
//                //updateActionBarButtons(true);
//            });
//            //collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout), false);
//            collageButton.setSelected(false);
//            collageButton.setVisibility(View.VISIBLE);
//            collageButton.setAlpha(1.0f);
//            topPanel.addView(collageButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

            videoTimerView = new VideoTimerView(getContext());
            showVideoTimer(false, false);
            topPanel.addView(videoTimerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 45, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 56, 0));

            addView(topPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));
        }

        void initBottomPane() {
            bottomPanel = new FrameLayout(getContext());

            initBottomTopPane();
            initBottomBottomPane();

            addView(bottomPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 150, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));
        }

        void initBottomTopPane() {
            bottomTopPanel = new FrameLayout(getContext());

            recordControl = new RecordControl(getContext());
            recordControl.setDelegate(recordControlDelegate);
            recordControl.startAsVideo(isVideo);
            bottomTopPanel.addView(recordControl, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

            bottomPanel.addView(bottomTopPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.TOP));
        }

        void initBottomBottomPane() {
            bottomBottomPanel = new FrameLayout(getContext());
            modeSwitcherView = new PhotoVideoSwitcherView(getContext()) {{

            }};
            modeSwitcherView.setOnSwitchModeListener(newIsVideo -> {
                isVideo = newIsVideo;
                showVideoTimer(isVideo, true);
                modeSwitcherView.switchMode(isVideo);
                recordControl.startAsVideo(isVideo);
            });
            modeSwitcherView.setOnSwitchingModeListener(t -> {
                recordControl.startAsVideoT(t);
            });
            bottomBottomPanel.addView(modeSwitcherView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

            bottomPanel.addView(bottomBottomPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.BOTTOM));
        }
        @SuppressLint("ClickableViewAccessibility")
        private void createCameraView() {
            if (cameraView != null || getContext() == null) {
                return;
            }
            cameraView = new DualCameraView(getContext(), false, false) {
                @Override
                public void onEntityDraggedTop(boolean value) {
                    //previewHighlight.show(true, value, actionBarContainer);
                }

                @Override
                public void onEntityDraggedBottom(boolean value) {
                    //previewHighlight.updateCaption(captionEdit.getText());
                    //previewHighlight.show(false, value, controlContainer);
                }

                @Override
                public void toggleDual() {
                    super.toggleDual();
                    dualButton.setValue(isDual());
                    recordControl.setDual(isDual());
                    setCameraFlashModeIcon(getCurrentFlashMode(), true);
                }

                @Override
                protected void onSavedDualCameraSuccess() {
//                    if (MessagesController.getGlobalMainSettings().getInt("storysvddualhint", 0) < 2) {
//                        AndroidUtilities.runOnUIThread(() -> {
//                            if (takingVideo || takingPhoto || cameraView == null || currentPage != PAGE_CAMERA) {
//                                return;
//                            }
//                            if (savedDualHint != null) {
//                                CharSequence text = isFrontface() ? getString(R.string.StoryCameraSavedDualBackHint) : getString(R.string.StoryCameraSavedDualFrontHint);
//                                savedDualHint.setMaxWidthPx(HintView2.cutInFancyHalf(text, savedDualHint.getTextPaint()));
//                                savedDualHint.setText(text);
//                                savedDualHint.show();
//                                MessagesController.getGlobalMainSettings().edit().putInt("storysvddualhint", MessagesController.getGlobalMainSettings().getInt("storysvddualhint", 0) + 1).apply();
//                            }
//                        }, 340);
//                    }
                    dualButton.setValue(isDual());
                }

                @Override
                protected void receivedAmplitude(double amplitude) {
                    if (recordControl != null) {
                        recordControl.setAmplitude(Utilities.clamp((float) (amplitude / WaveDrawable.MAX_AMPLITUDE), 1, 0), true);
                    }
                }
            };
            if (recordControl != null) {
                recordControl.setAmplitude(0, false);
            }
            cameraView.isStory = false;
            cameraView.setThumbDrawable(getCameraThumb());
            cameraView.initTexture();
            cameraView.setDelegate(() -> {
                String currentFlashMode = getCurrentFlashMode();
                if (TextUtils.equals(currentFlashMode, getNextFlashMode())) {
                    currentFlashMode = null;
                }
                setCameraFlashModeIcon(currentFlashMode, true);
                if (zoomControlView != null) {
                    zoomControlView.setZoom(cameraZoom = 0, false);
                }
            });
            //dualButton.setVisibility(cameraView.dualAvailable() ? View.VISIBLE : View.GONE);
            //flashButton.setTranslationX(cameraView.dualAvailable() ? -dp(46) : 0);
            addView(cameraView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            if (MessagesController.getGlobalMainSettings().getInt("storyhint2", 0) < 1) {
                //cameraHint.show();
                MessagesController.getGlobalMainSettings().edit().putInt("storyhint2", MessagesController.getGlobalMainSettings().getInt("storyhint2", 0) + 1).apply();
            }
        }
        private Drawable getCameraThumb() {
            Bitmap bitmap = null;
            try {
                File file = new File(ApplicationLoader.getFilesDirFixed(), "cthumb.jpg");
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            } catch (Throwable ignore) {}
            if (bitmap != null) {
                return new BitmapDrawable(bitmap);
            } else {
                return getContext().getResources().getDrawable(R.drawable.icplaceholder);
            }
        }

        public void open() {
            cameraView.resetCamera();
        }

        public void close() {
            cameraView.destroy(true,()->{});
        }
        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            scaleGestureDetector.onTouchEvent(ev);
            //gestureDetector.onTouchEvent(ev);
            return super.dispatchTouchEvent(ev);
        }
        @Override
        public boolean dispatchKeyEventPreIme(KeyEvent event) {
            if (event != null && event.getKeyCode()
                    == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                close();
                onCloseListener.run();
                return true;
            }
            return super.dispatchKeyEventPreIme(event);
        }
        private String getNextFlashMode() {
            if (cameraView == null || cameraView.getCameraSession() == null) {
                return null;
            }
            if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
                checkFrontfaceFlashModes();
                return frontfaceFlashModes.get(frontfaceFlashMode + 1 >= frontfaceFlashModes.size() ? 0 : frontfaceFlashMode + 1);
            }
            return cameraView.getCameraSession().getNextFlashMode();
        }

        private void setCurrentFlashMode(String mode) {
            if (cameraView == null || cameraView.getCameraSession() == null) {
                return;
            }
            if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
                int index = frontfaceFlashModes.indexOf(mode);
                if (index >= 0) {
                    frontfaceFlashMode = index;
                    MessagesController.getGlobalMainSettings().edit().putInt("frontflash", frontfaceFlashMode).apply();
                }
                return;
            }
            cameraView.getCameraSession().setCurrentFlashMode(mode);
        }
        private boolean scaling = false;
        private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {

                if (!scaling || cameraView == null || cameraView.isDualTouch()) {
                    return false;
                }
                final float deltaScaleFactor = (detector.getScaleFactor() - 1.0f) * .75f;
                cameraZoom += deltaScaleFactor;
                cameraZoom = Utilities.clamp(cameraZoom, 1, 0);
                cameraView.setZoom(cameraZoom);
                if (zoomControlView != null) {
                    zoomControlView.setZoom(cameraZoom, false);
                }
                showZoomControls(true, true);
                return true;
            }

            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                scaling = true;
                return super.onScaleBegin(detector);
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                scaling = false;
                //animateGalleryListView(false);
                animateContainerBack();
                super.onScaleEnd(detector);
            }
        }
        private ValueAnimator containerViewBackAnimator;
        private boolean applyContainerViewTranslation2 = true;
        private void animateContainerBack() {
            if (containerViewBackAnimator != null) {
                containerViewBackAnimator.cancel();
                containerViewBackAnimator = null;
            }
            applyContainerViewTranslation2 = false;
            //float y1 = containerView.getTranslationY1(), y2 = containerView.getTranslationY2(), a = containerView.getAlpha();
            containerViewBackAnimator = ValueAnimator.ofFloat(1, 0);
            containerViewBackAnimator.addUpdateListener(anm -> {
                final float t = (float) anm.getAnimatedValue();
                //containerView.setTranslationY(y1 * t);
                //containerView.setTranslationY2(y2 * t);
//            containerView.setAlpha(AndroidUtilities.lerp(a, 1f, t));
            });
            containerViewBackAnimator.setDuration(340);
            containerViewBackAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            containerViewBackAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    containerViewBackAnimator = null;
                    //containerView.setTranslationY(0);
                    //containerView.setTranslationY2(0);
                }
            });
            containerViewBackAnimator.start();
        }

        private Runnable zoomControlHideRunnable;
        private AnimatorSet zoomControlAnimation;
        private void showZoomControls(boolean show, boolean animated) {
            if (zoomControlView.getTag() != null && show || zoomControlView.getTag() == null && !show) {
                if (show) {
                    if (zoomControlHideRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable);
                    }
                    AndroidUtilities.runOnUIThread(zoomControlHideRunnable = () -> {
                        showZoomControls(false, true);
                        zoomControlHideRunnable = null;
                    }, 2000);
                }
                return;
            }
            if (zoomControlAnimation != null) {
                zoomControlAnimation.cancel();
            }
            zoomControlView.setTag(show ? 1 : null);
            zoomControlAnimation = new AnimatorSet();
            zoomControlAnimation.setDuration(180);
            if (show) {
                zoomControlView.setVisibility(View.VISIBLE);
            }
            zoomControlAnimation.playTogether(ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, show ? 1.0f : 0.0f));
            zoomControlAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!show) {
                        zoomControlView.setVisibility(View.GONE);
                    }
                    zoomControlAnimation = null;
                }
            });
            zoomControlAnimation.start();
            if (show) {
                AndroidUtilities.runOnUIThread(zoomControlHideRunnable = () -> {
                    showZoomControls(false, true);
                    zoomControlHideRunnable = null;
                }, 2000);
            }
        }
        private boolean videoTimerShown = true;
        private void showVideoTimer(boolean show, boolean animated) {
            if (videoTimerShown == show) {
                return;
            }

            videoTimerShown = show;
            if (animated) {
                videoTimerView.animate().alpha(show ? 1 : 0).setDuration(350).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> {
                    if (!show) {
                        videoTimerView.setRecording(false, false);
                    }
                }).start();
            } else {
                videoTimerView.clearAnimation();
                videoTimerView.setAlpha(show ? 1 : 0);
                if (!show) {
                    videoTimerView.setRecording(false, false);
                }
            }
        }
        private final RecordControl.Delegate recordControlDelegate = new RecordControl.Delegate() {
            @Override
            public boolean canRecordAudio() {
                return true; //requestAudioPermission();
            }

            @Override
            public void onCheckClick() {

            }

            @Override
            public void onPhotoShoot() {
                if (cameraView == null || !cameraView.isInited()) {
                //if (takingPhoto || awaitingPlayer || cameraView == null || !cameraView.isInited()) {
                    return;
                }
                //cameraHint.hide();
                if (outputFile != null) {
                    try {
                        outputFile.delete();
                    } catch (Exception ignore) {}
                    outputFile = null;
                }
                outputFile = StoryEntry.makeCacheFile(currentAccount, false);
                takingPhoto = true;
                checkFrontfaceFlashModes();
                isDark = false;
                if (cameraView.isFrontface() && frontfaceFlashMode == 1) {
                    checkIsDark();
                }
                if (useDisplayFlashlight()) {
                    //flashViews.flash(this::takePicture);
                } else {
                    takePicture(null);
                }
            }

            private void takePicture(Utilities.Callback<Runnable> done) {
                boolean savedFromTextureView = false;
                if (!useDisplayFlashlight()) {
                    cameraView.startTakePictureAnimation(true);
                }
                if (cameraView.isDual() && TextUtils.equals(cameraView.getCameraSession().getCurrentFlashMode(), Camera.Parameters.FLASH_MODE_OFF)) {
                    cameraView.pauseAsTakingPicture();
                    final Bitmap bitmap = cameraView.getTextureView().getBitmap();
                    try (FileOutputStream out = new FileOutputStream(outputFile.getAbsoluteFile())) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                        savedFromTextureView = true;
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    bitmap.recycle();
                }
                if (!savedFromTextureView) {
                    takingPhoto = CameraController.getInstance().takePicture(outputFile, true, cameraView.getCameraSessionObject(), (orientation) -> {
                        if (useDisplayFlashlight()) {
//                            try {
//                                windowView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
//                            } catch (Exception ignore) {}
                        }
                        takingPhoto = false;
                        if (outputFile == null) {
                            return;
                        }
                        int w = -1, h = -1;
                        try {
                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(outputFile.getAbsolutePath(), opts);
                            w = opts.outWidth;
                            h = opts.outHeight;
                        } catch (Exception ignore) {}

                        int rotate = orientation == -1 ? 0 : 90;
                        if (orientation == -1) {
                            if (w > h) {
                                rotate = 270;
                            }
                        } else if (h > w && rotate != 0) {
                            rotate = 0;
                        }
                        onResult.accept(new PhotoMedia(outputFile, null));
//                        outputEntry = StoryEntry.fromPhotoShoot(outputFile, rotate);
//                        if (outputEntry != null) {
//                            outputEntry.botId = botId;
//                            outputEntry.botLang = botLang;
//                        }
//                        StoryPrivacySelector.applySaved(currentAccount, outputEntry);
//                        fromGallery = false;
//
//                        if (done != null) {
//                            done.run(() -> navigateTo(PAGE_PREVIEW, true));
//                        } else {
//                            navigateTo(PAGE_PREVIEW, true);
//                        }
                    });
                } else {
                    takingPhoto = false;
                    onResult.accept(new PhotoMedia(outputFile, null));
//                    outputEntry = StoryEntry.fromPhotoShoot(outputFile, 0);
//                    if (outputEntry != null) {
//                        outputEntry.botId = botId;
//                        outputEntry.botLang = botLang;
//                    }
//                    StoryPrivacySelector.applySaved(currentAccount, outputEntry);
//                    fromGallery = false;
//
//                    if (done != null) {
//                        done.run(() -> navigateTo(PAGE_PREVIEW, true));
//                    } else {
//                        navigateTo(PAGE_PREVIEW, true);
//                    }
                }
            }

            @Override
            public void onVideoRecordStart(boolean byLongPress, Runnable whenStarted) {
                if (takingVideo || stoppingTakingVideo || awaitingPlayer || cameraView == null || cameraView.getCameraSession() == null) {
                    return;
                }
//                if (dualHint != null) {
//                    dualHint.hide();
//                }
//                if (savedDualHint != null) {
//                    savedDualHint.hide();
//                }
//                cameraHint.hide();
                takingVideo = true;
                if (outputFile != null) {
                    try {
                        outputFile.delete();
                    } catch (Exception ignore) {}
                    outputFile = null;
                }
                outputFile = StoryEntry.makeCacheFile(currentAccount, true);
                checkFrontfaceFlashModes();
                isDark = false;
                if (cameraView.isFrontface() && frontfaceFlashMode == 1) {
                    checkIsDark();
                }
                if (useDisplayFlashlight()) {
                    //flashViews.flashIn(() -> startRecording(byLongPress, whenStarted));
                } else {
                    startRecording(byLongPress, whenStarted);
                }
            }

            private void startRecording(boolean byLongPress, Runnable whenStarted) {
                if (cameraView == null) {
                    return;
                }
                CameraController.getInstance().recordVideo(cameraView.getCameraSessionObject(), outputFile, false, (thumbPath, duration) -> {
                    if (recordControl != null) {
                        recordControl.stopRecordingLoading(true);
                    }
//                    if (useDisplayFlashlight()) {
//                        flashViews.flashOut();
//                    }
                    if (outputFile == null || cameraView == null) {
                        return;
                    }

                    takingVideo = false;
                    stoppingTakingVideo = false;

                    if (duration <= 800) {
                        //animateRecording(false, true);
                        setAwakeLock(false);
                        videoTimerView.setRecording(false, true);
                        if (recordControl != null) {
                            recordControl.stopRecordingLoading(true);
                        }
                        try {
                            outputFile.delete();
                            outputFile = null;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (thumbPath != null) {
                            try {
                                new File(thumbPath).delete();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                        return;
                    }

                    showVideoTimer(false, true);
                    onResult.accept(new VideoMedia(outputFile, null));

//                    outputEntry = StoryEntry.fromVideoShoot(outputFile, thumbPath, duration);
//                    if (outputEntry != null) {
//                        outputEntry.botId = botId;
//                        outputEntry.botLang = botLang;
//                    }
//                    StoryPrivacySelector.applySaved(currentAccount, outputEntry);
//                    fromGallery = false;
//                    int width = cameraView.getVideoWidth(), height = cameraView.getVideoHeight();
//                    if (width > 0 && height > 0) {
//                        outputEntry.width = width;
//                        outputEntry.height = height;
//                        outputEntry.setupMatrix();
//                    }
//                    navigateToPreviewWithPlayerAwait(() -> {
//                        navigateTo(PAGE_PREVIEW, true);
//                    }, 0);
                }, () /* onVideoStart */ -> {
                    whenStarted.run();

                    //hintTextView.setText(getString(byLongPress ? R.string.StoryHintSwipeToZoom : R.string.StoryHintPinchToZoom), false);
                    //animateRecording(true, true);
                    setAwakeLock(true);

                    videoTimerView.setRecording(true, true);
                    showVideoTimer(true, true);
                }, cameraView, true);

                if (!isVideo) {
                    isVideo = true;
                    showVideoTimer(isVideo, true);
                    modeSwitcherView.switchMode(isVideo);
                    recordControl.startAsVideo(isVideo);
                }
            }

            @Override
            public void onVideoRecordLocked() {
                //hintTextView.setText(getString(R.string.StoryHintPinchToZoom), true);
            }

            @Override
            public void onVideoRecordPause() {

            }

            @Override
            public void onVideoRecordResume() {

            }

            @Override
            public void onVideoRecordEnd(boolean byDuration) {
                if (stoppingTakingVideo || !takingVideo) {
                    return;
                }
                stoppingTakingVideo = true;
                AndroidUtilities.runOnUIThread(() -> {
                    if (takingVideo && stoppingTakingVideo && cameraView != null) {
                        showZoomControls(false, true);
//                    animateRecording(false, true);
//                    setAwakeLock(false);
                        CameraController.getInstance().stopVideoRecording(cameraView.getCameraSessionRecording(), false, false);
                    }
                }, byDuration ? 0 : 400);
            }

            @Override
            public void onVideoDuration(long duration) {
                videoTimerView.setDuration(duration, true);
            }

            @Override
            public void onGalleryClick() {
//                if (!takingPhoto && !takingVideo && requestGalleryPermission()) {
//                    animateGalleryListView(true);
//                }
            }

            @Override
            public void onFlipClick() {
                if (cameraView == null || awaitingPlayer || takingPhoto || !cameraView.isInited()) {
                    return;
                }
//                if (savedDualHint != null) {
//                    savedDualHint.hide();
//                }
                if (useDisplayFlashlight() && frontfaceFlashModes != null && !frontfaceFlashModes.isEmpty()) {
                    final String mode = frontfaceFlashModes.get(frontfaceFlashMode);
                    SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Activity.MODE_PRIVATE);
                    sharedPreferences.edit().putString("flashMode", mode).commit();
                }
                cameraView.switchCamera();
//                saveCameraFace(cameraView.isFrontface());
//                if (useDisplayFlashlight()) {
//                    flashViews.flashIn(null);
//                } else {
//                    flashViews.flashOut();
//                }
            }

            @Override
            public void onFlipLongClick() {
                if (cameraView != null) {
                    cameraView.toggleDual();
                }
            }

            @Override
            public void onZoom(float zoom) {
                zoomControlView.setZoom(zoom, true);
                showZoomControls(false, true);
            }
        };
        private boolean useDisplayFlashlight() {
            return (takingPhoto || takingVideo) && (cameraView != null && cameraView.isFrontface()) && (frontfaceFlashMode == 2 || frontfaceFlashMode == 1 && isDark);
        }
        private void saveFrontFaceFlashMode() {
            if (frontfaceFlashMode >= 0) {
                MessagesController.getGlobalMainSettings().edit()
                        //.putFloat("frontflash_warmth", flashViews.warmth)
                        //.putFloat("frontflash_intensity", flashViews.intensity)
                        .apply();
            }
        }
        private String getCurrentFlashMode() {
            if (cameraView == null || cameraView.getCameraSession() == null) {
                return null;
            }
            if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
                checkFrontfaceFlashModes();
                return frontfaceFlashModes.get(frontfaceFlashMode);
            }
            return cameraView.getCameraSession().getCurrentFlashMode();
        }
        private int flashButtonResId;
        private void setCameraFlashModeIcon(String mode, boolean animated) {
            flashButton.clearAnimation();
            if (cameraView != null && cameraView.isDual()) {
                //if (cameraView != null && cameraView.isDual() || animatedRecording) {
                mode = null;
            }
            if (mode == null) {
                if (animated) {
                    flashButton.setVisibility(View.VISIBLE);
                    flashButton.animate().alpha(0).withEndAction(() -> {
                        flashButton.setVisibility(View.GONE);
                    }).start();
                } else {
                    flashButton.setVisibility(View.GONE);
                    flashButton.setAlpha(0f);
                }
                return;
            }
            final int resId;
            switch (mode) {
                case Camera.Parameters.FLASH_MODE_ON:
                    resId = R.drawable.media_photo_flash_on2;
                    flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashOn));
                    break;
                case Camera.Parameters.FLASH_MODE_AUTO:
                    resId = R.drawable.media_photo_flash_auto2;
                    flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashAuto));
                    break;
                default:
                case Camera.Parameters.FLASH_MODE_OFF:
                    resId = R.drawable.media_photo_flash_off2;
                    flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashOff));
                    break;
            }
            flashButton.setIcon(flashButtonResId = resId, animated && flashButtonResId != resId);
            flashButton.setVisibility(View.VISIBLE);
            if (animated) {
                flashButton.animate().alpha(1f).start();
            } else {
                flashButton.setAlpha(1f);
            }
        }
        private int frontfaceFlashMode = -1;
        private ArrayList<String> frontfaceFlashModes;
        private void checkFrontfaceFlashModes() {
            if (frontfaceFlashMode < 0) {
                frontfaceFlashMode = MessagesController.getGlobalMainSettings().getInt("frontflash", 1);
                frontfaceFlashModes = new ArrayList<>();
                frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_OFF);
                frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_AUTO);
                frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_ON);

                //flashViews.setWarmth(MessagesController.getGlobalMainSettings().getFloat("frontflash_warmth", .9f));
                //flashViews.setIntensity(MessagesController.getGlobalMainSettings().getFloat("frontflash_intensity", 1));
            }
        }
        private boolean isDark;
        private void checkIsDark() {
            if (cameraView == null || cameraView.getTextureView() == null) {
                isDark = false;
                return;
            }
            final Bitmap bitmap = cameraView.getTextureView().getBitmap();
            if (bitmap == null) {
                isDark = false;
                return;
            }
            float l = 0;
            final int sx = bitmap.getWidth() / 12;
            final int sy = bitmap.getHeight() / 12;
            for (int x = 0; x < 10; ++x) {
                for (int y = 0; y < 10; ++y) {
                    l += AndroidUtilities.computePerceivedBrightness(bitmap.getPixel((1 + x) * sx, (1 + y) * sy));
                }
            }
            l /= 100;
            bitmap.recycle();
            isDark = l < .22f;
        }

        private void setAwakeLock(boolean lock) {
//            if (lock) {
//                windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
//            } else {
//                windowLayoutParams.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
//            }
//            try {
//                windowManager.updateViewLayout(windowView, windowLayoutParams);
//            } catch (Exception e) {
//                FileLog.e(e);
//            }
        }
    }
}
