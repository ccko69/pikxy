package com.ccko.pikxplus.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Transparent overlay that sits at the top of the view hierarchy. Routes gesture events to the
 * appropriate delegate based on current mode.
 *
 * <p>Usage in MainActivity: gestureOverlay.setGestureDelegate(imageViewerHandler);
 */
public class GestureOverlayView extends FrameLayout {

  private GestureDelegate currentDelegate;
  private float topDeadzonePercent = 0.1f; // 10% from top
  private float bottomDeadzonePercent = 0.1f; // 10% from bottom

  public interface GestureDelegate {
    /**
     * Handle the touch event.
     *
     * @return true if consumed, false to pass through to underlying views
     */
    boolean onTouchEvent(MotionEvent event);

    /**
     * Check if this delegate wants to handle the touch before routing. Useful for conditional
     * handling based on event properties.
     *
     * @return true if delegate wants this touch event
     */
    boolean wantsTouch(MotionEvent event);
  }

  public GestureOverlayView(@NonNull Context context) {
    super(context);
    init();
  }

  public GestureOverlayView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public GestureOverlayView(
      @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    // Make overlay transparent and non-clickable by default
    setBackgroundColor(0x00000000);
    setClickable(false);
    setFocusable(false);
  }

  /** Set the active gesture delegate. Pass null to disable gesture routing. */
  public void setGestureDelegate(@Nullable GestureDelegate delegate) {
    this.currentDelegate = delegate;
    // Make clickable only when we have a delegate
    setClickable(delegate != null);
  }

  /** Set deadzone percentages (0.0 to 1.0) */
  public void setDeadzones(float topPercent, float bottomPercent) {
    this.topDeadzonePercent = Math.max(0f, Math.min(1f, topPercent));
    this.bottomDeadzonePercent = Math.max(0f, Math.min(1f, bottomPercent));
  }

  public void setGesturesEnabled(boolean enabled) {
    setEnabled(enabled);
  }

  // 3. REPLACE your onInterceptTouchEvent with this version
  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    if (currentDelegate == null) return false;
    return true;
  }

  /** Check if touch is in left or right hotspot zone */
  /*
  private boolean isInHotspot(MotionEvent event) {
      float x = event.getX();
      int width = getWidth();

      if (width == 0)
          return false;

      // Define hotspot zones (20% on each side)
      float hotspotWidth = width * 0.2f; // Adjust as needed

      return x < hotspotWidth || x > (width - hotspotWidth);
  }
  */
  @Override
  public boolean onTouchEvent(MotionEvent event) {

    // If no delegate, pass through
    if (currentDelegate == null) {
      return false;
    }

    // Check deadzones on ACTION_DOWN
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN && isInDeadzone(event)) {
      return false;
    }

    // Route to delegate
    return currentDelegate.onTouchEvent(event);
  }

  /** Check if touch is in top or bottom deadzone */
  private boolean isInDeadzone(MotionEvent event) {
    float y = event.getY();
    int height = getHeight();

    if (height == 0) return false;

    float topLimit = height * topDeadzonePercent;
    float bottomLimit = height * (1f - bottomDeadzonePercent);

    return y < topLimit || y > bottomLimit;
  }

  /** Get current delegate (useful for debugging) */
  @Nullable
  public GestureDelegate getCurrentDelegate() {
    return currentDelegate;
  }

  /** Clear delegate and reset to pass-through mode */
  public void clearDelegate() {
    setGestureDelegate(null);
  }
}
