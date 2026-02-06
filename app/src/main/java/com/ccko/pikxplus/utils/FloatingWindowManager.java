package com.ccko.pikxplus.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupWindow;
import com.ccko.pikxplus.MainActivity;

/**
 * Unified manager for all floating windows/popups in the app. Handles: speed control, playlist,
 * volume/brightness overlays, settings, etc.
 */
public class FloatingWindowManager {

  private PopupWindow currentPopup;
  private Context context;
  private View anchorView;
  private Runnable onDismissCallback;
  private Handler autoHideHandler = new Handler(Looper.getMainLooper());
  private Runnable autoHideRunnable;

  // Window type constants
  public static final int TYPE_SPEED = 0;
  public static final int TYPE_PLAYLIST = 1;
  public static final int TYPE_VOLUME = 2;
  public static final int TYPE_BRIGHTNESS = 3;
  public static final int TYPE_SETTINGS = 4;
  public static final int TYPE_TIMER = 5;

  public FloatingWindowManager(Context context, View anchorView) {
    this.context = context;
    this.anchorView = anchorView;
  }

  /** Show a floating window with preset type configurations */
  public void showTyped(int type, int layoutResId, SetupCallback setupCallback) {
    DisplayMetrics metrics = getDisplayMetrics();

    switch (type) {
      case TYPE_SPEED:
        show(
            layoutResId,
            dpToPx(300),
            dpToPx(60),
            dpToPx(100),
            Gravity.TOP | Gravity.CENTER_HORIZONTAL,
            setupCallback,
            false);
        break;

      case TYPE_PLAYLIST:
        int playlistWidth = (int) (metrics.widthPixels * 0.65f);
        int playlistHeight = (int) (metrics.heightPixels * 0.80f);
        show(
            layoutResId,
            playlistWidth,
            playlistHeight,
            dpToPx(80),
            Gravity.TOP | Gravity.END,
            setupCallback,
            false);
        break;

      case TYPE_VOLUME:
        // Thin vertical bar on right side
        show(
            layoutResId,
            dpToPx(40),
            dpToPx(250),
            dpToPx(20),
            Gravity.CENTER_VERTICAL | Gravity.START,
            setupCallback,
            true);
        break;

      case TYPE_BRIGHTNESS:
        // Thin vertical bar on left side
        show(
            layoutResId,
            dpToPx(40),
            dpToPx(250),
            dpToPx(20),
            Gravity.CENTER_VERTICAL | Gravity.END,
            setupCallback,
            true);
        break;

      case TYPE_SETTINGS:
        // Large centered window
        int settingsWidth = (int) (metrics.widthPixels * 0.60f);
        int settingsHeight = (int) (metrics.heightPixels * 0.75f);
        show(layoutResId, settingsWidth, settingsHeight, 0, Gravity.CENTER, setupCallback, false);
        break;

      case TYPE_TIMER:
        // Thin horizontal bar on Center
        int timerWidth = (int) (metrics.widthPixels * 1f);
      //  int timerHeight = (int) (metrics.heightPixels * 0.75f);
        show(layoutResId, timerWidth, dpToPx(40), 0, Gravity.CENTER, setupCallback, true);
        break;
    }
  }

  /**
   * Show a floating window with custom dimensions and position
   *
   * @param autoHide If true, window will auto-dismiss after 2 seconds
   */
  public void show(
      int layoutResId,
      int width,
      int height,
      int yOffset,
      int gravity,
      SetupCallback setupCallback,
      boolean autoHide) {

    // Dismiss existing popup
    dismiss();

    // Inflate content
    View content = LayoutInflater.from(context).inflate(layoutResId, null);
    content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

    // Let caller setup the view
    if (setupCallback != null) {
      setupCallback.setup(content);
    }

    // Create popup
    currentPopup = new PopupWindow(content, width, height, true);
    currentPopup.setOutsideTouchable(true);
    currentPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    currentPopup.setClippingEnabled(false);

    // Disable gestures while popup visible (except for auto-hide overlays)
    if (!autoHide) {
      disableGestures();
    }

    currentPopup.setOnDismissListener(
        () -> {
          cancelAutoHide();
          if (!autoHide) {
            restoreGestures();
          }
          if (onDismissCallback != null) {
            onDismissCallback.run();
          }
        });

    // Show with specified gravity
    currentPopup.showAtLocation(anchorView.getRootView(), gravity, 0, yOffset);

    // Schedule auto-hide if requested
    if (autoHide) {
      scheduleAutoHide(4000); // 4 seconds
    }
  }

  /** Update existing popup content without dismissing */
  public void updateContent(SetupCallback setupCallback) {
    if (currentPopup != null && currentPopup.isShowing()) {
      View content = currentPopup.getContentView();
      if (content != null && setupCallback != null) {
        setupCallback.setup(content);

        // Reset auto-hide timer on update
        if (autoHideRunnable != null) {
          scheduleAutoHide(4000);
        }
      }
    }
  }

  /** Show or update if already showing (useful for volume/brightness) */
  public void showOrUpdate(int type, int layoutResId, SetupCallback setupCallback) {
    if (isShowing()) {
      updateContent(setupCallback);
    } else {
      showTyped(type, layoutResId, setupCallback);
    }
  }

  public void dismiss() {
    cancelAutoHide();
    if (currentPopup != null && currentPopup.isShowing()) {
      currentPopup.dismiss();
      currentPopup = null;
    }
  }

  public boolean isShowing() {
    return currentPopup != null && currentPopup.isShowing();
  }

  public void setOnDismissCallback(Runnable callback) {
    this.onDismissCallback = callback;
  }

  /** Schedule auto-hide after specified delay */
  private void scheduleAutoHide(long delayMs) {
    cancelAutoHide();
    autoHideRunnable = this::dismiss;
    autoHideHandler.postDelayed(autoHideRunnable, delayMs);
  }

  /** Cancel scheduled auto-hide */
  private void cancelAutoHide() {
    if (autoHideRunnable != null) {
      autoHideHandler.removeCallbacks(autoHideRunnable);
      autoHideRunnable = null;
    }
  }

  /** Reset auto-hide timer (call this when user interacts with overlay) */
  public void resetAutoHideTimer() {
    if (autoHideRunnable != null) {
      scheduleAutoHide(4000);
    }
  }

  private void disableGestures() {
    if (context instanceof MainActivity) {
      ((MainActivity) context).setGestureDelegate(null);
    }
  }

  private void restoreGestures() {
    if (onDismissCallback != null) {
      onDismissCallback.run();
    }
  }

  public DisplayMetrics getDisplayMetrics() {
    DisplayMetrics metrics = new DisplayMetrics();
    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    if (wm != null) {
      wm.getDefaultDisplay().getMetrics(metrics);
    }
    return metrics; 
  }

  public int dpToPx(int dp) {
    float density = context.getResources().getDisplayMetrics().density;
    return Math.round(dp * density);
  }

  public interface SetupCallback {
    void setup(View content);
  }
}
