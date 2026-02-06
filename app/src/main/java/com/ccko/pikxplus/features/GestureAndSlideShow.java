package com.ccko.pikxplus.features;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import androidx.core.view.GestureDetectorCompat;
import com.ccko.pikxplus.ui.GestureOverlayView;
import com.ccko.pikxplus.ui.VideoPlayerFragment;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.Nullable;
import com.ccko.pikxplus.ui.VideoPlayerFragment;

/**
 * Library of specialized gesture handlers for different app modes. Each handler implements
 * GestureOverlayView.GestureDelegate.
 */
public class GestureAndSlideShow {

  /**
   * Specialized gesture handler for Image Viewer mode. Handles: pinch zoom, double-tap zoom, pan,
   * swipe-to-close, next/prev navigation.
   */
  public static class ImageViewerGestureHandler implements GestureOverlayView.GestureDelegate {

    private final GestureDetectorCompat gestureDetector;
    private final ScaleGestureDetector scaleGestureDetector;
    private final Handler uiHandler;
    private final HostCallback host;
    private final Context ctx;
    private final SlideShow slideShow;
    private final WeakReference<Activity> activityRef;

    // Gesture state flags
    public volatile boolean isSwiping = false;
    public volatile boolean isScaling = false;
    public volatile boolean isPanning = false;
    public volatile boolean isHotspotTouch = false;
    public volatile boolean isDoubleTapping = false;

    public interface HostCallback {
      void onDoubleTap(float x, float y);

      void onLongPress();

      void onPan(float distanceX, float distanceY);

      void onScale(float scaleFactor, float focusX, float focusY);

      void onScaleEnd();

      void onRequestClose();

      void onNextImageRequested();

      void onPreviousImageRequested();

      void onSlideShowStopped();

      void onSlideShowNext();

      void onLeftHotspotTapped();

      void onRightHotspotTapped();
    }

    public ImageViewerGestureHandler(
        Context context, HostCallback hostCallback, @Nullable Activity activity) {
      this.ctx = context;
      this.host = hostCallback;
      this.activityRef = activity != null ? new WeakReference<>(activity) : null;
      this.uiHandler = new Handler(Looper.getMainLooper());

      this.gestureDetector = new GestureDetectorCompat(context, new ImageGestureListener());
      this.scaleGestureDetector = new ScaleGestureDetector(context, new ImageScaleListener());

      this.slideShow =
          new SlideShow(
              new SlideShow.Callback() {
                @Override
                public void onNextImage() {
                  uiHandler.post(
                      () -> {
                        if (host != null) host.onSlideShowNext();
                      });
                }

                @Override
                public void onSlideShowStopped() {
                  uiHandler.post(
                      () -> {
                        if (host != null) host.onSlideShowStopped();
                      });
                }
              },
              activity);
    }

    // Add hotspot zone detection
    private boolean isInLeftHotspot(float x, float y) {
      DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
      int screenWidth = metrics.widthPixels;
      float hotspotWidth = screenWidth * 0.08f; // Left 20% is hotspot
      return x < hotspotWidth;
    }

    private boolean isInRightHotspot(float x, float y) {
      DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
      int screenWidth = metrics.widthPixels;
      float hotspotWidth = screenWidth * 0.08f; // Right 20% is hotspot
      return x > (screenWidth - hotspotWidth);
    }

    @Override
    public boolean wantsTouch(MotionEvent event) {
      float x = event.getX();
      float y = event.getY();

      // If touch is in hotspot, gesture handler wants it (to handle it specially)
      if (isInLeftHotspot(x, y) || isInRightHotspot(x, y)) {
        return true;
      }

      // Otherwise, default behavior
      return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      float x = event.getX();
      float y = event.getY();
      int action = event.getActionMasked();

      if (action == MotionEvent.ACTION_DOWN) {
        isHotspotTouch = isInLeftHotspot(x, y) || isInRightHotspot(x, y);
      }

      if (isHotspotTouch) {
        if (action == MotionEvent.ACTION_UP) {
          // ADD THE CONDITION HERE:
          // Check if any gesture is currently active
          if (!isPanning) {
            if (isInLeftHotspot(x, y)) {
              if (host != null) host.onLeftHotspotTapped();
            } else if (isInRightHotspot(x, y)) {
              if (host != null) host.onRightHotspotTapped();
            }
          }
          isHotspotTouch = false;
          return true;
        }
        return true; // Consume all hotspot touches
      }

      // Original gesture handling
      if (event.getPointerCount() > 1) {
        gestureDetector.setIsLongpressEnabled(false);
      } else {
        gestureDetector.setIsLongpressEnabled(true);
      }

      boolean scaleHandled = scaleGestureDetector.onTouchEvent(event);
      boolean gestureHandled = gestureDetector.onTouchEvent(event);

      return scaleHandled || gestureHandled;
    }

    // SlideShow controls
    public void startSlideShow(long intervalMs) {
      if (slideShow != null && !slideShow.isActive()) {
        slideShow.start(intervalMs);
      }
    }

    public void stopSlideShow() {
      if (slideShow != null && slideShow.isActive()) {
        slideShow.stop();
      }
    }

    public boolean isSlideShowActive() {
      return slideShow != null && slideShow.isActive();
    }

    // Cleanup
    public void cleanup() {
      stopSlideShow();
      if (uiHandler != null) {
        uiHandler.removeCallbacksAndMessages(null);
      }
    }

    // Inner gesture listener for images
    private class ImageGestureListener extends GestureDetector.SimpleOnGestureListener {
      private static final int SWIPE_THRESHOLD = 100;
      private static final int SWIPE_VELOCITY_THRESHOLD = 100;
      private static final float TOP_IGNORE_RATIO = 0.10f;

      @Override
      public boolean onDoubleTap(MotionEvent e) {
        isDoubleTapping = true;
        uiHandler.postDelayed(() -> isDoubleTapping = false, 300);
        if (host != null) host.onDoubleTap(e.getX(), e.getY());
        return true;
      }

      @Override
      public void onLongPress(MotionEvent e) {
        if (host != null) host.onLongPress();
      }

      @Override
      public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        isPanning = true;
		isScaling = true;
        uiHandler.postDelayed(() -> isPanning = false, 150);
        if (host != null) host.onPan(distanceX, distanceY);
        return true;
      }

      @Override
      public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        try {
        //  if (e1.getPointerCount() > 1 || e2.getPointerCount() > 1) return false;

           int effectiveThreshold = 150;
          int velocityThreshold = 800;

          float startX = e1.getX();
          float startY = e1.getY();
          float endX = e2.getX();
          float endY = e2.getY();

          float diffX = endX - startX;
          float diffY = endY - startY;

          // Ignore gestures in top area
          float topIgnoreLimit =
              ctx.getResources().getDisplayMetrics().heightPixels * TOP_IGNORE_RATIO;
          if (startY <= topIgnoreLimit) {
            return false;
          }
/*
          if (isPanning == true) {
            effectiveThreshold = 300;
            velocityThreshold = 1500;
          } else {
            effectiveThreshold = 100;
            velocityThreshold = 900;
          }
*/
          // Vertical swipe-to-close (downwards)
          if (Math.abs(diffY) > Math.abs(diffX)) {
            if (diffY > effectiveThreshold && Math.abs(velocityY) > velocityThreshold) {
              isSwiping = true;
              uiHandler.postDelayed(() -> isSwiping = false, 200);
              if (host != null) host.onRequestClose();
              return true;
            }
          }

          // Horizontal navigation (left/right)
          if (Math.abs(diffX) > Math.abs(diffY)) {
            if (Math.abs(diffX) > effectiveThreshold && Math.abs(velocityX) > velocityThreshold) {
              isSwiping = true;
              uiHandler.postDelayed(() -> isSwiping = false, 200);

              if (diffX > 0) {
                if (host != null) host.onPreviousImageRequested();
              } else {
                if (host != null) host.onNextImageRequested();
              }
              return true;
            }
          }
        } catch (Exception ex) {
          ex.printStackTrace();
        }
        return false;
      }
    }

    private class ImageScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
      @Override
      public boolean onScaleBegin(ScaleGestureDetector detector) {
        isScaling = true;
        return true;
      }

      @Override
      public boolean onScale(ScaleGestureDetector detector) {
        if (host != null) {
			
          host.onScale(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
        }
        return true;
      }

      @Override
      public void onScaleEnd(ScaleGestureDetector detector) {
        isScaling = false;
        if (host != null) host.onScaleEnd();
      }
    }
  }

  /** SlideShow helper class (unchanged, kept for compatibility) */
  public static class SlideShow {
    public interface Callback {
      void onNextImage();

      void onSlideShowStopped();
    }

    private final Callback cb;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean active = new AtomicBoolean(false);
    private long intervalMs = 3000L;
    private final WeakReference<Activity> activityRef;

    private final Runnable tick =
        new Runnable() {
          @Override
          public void run() {
            if (!active.get()) return;
            if (cb != null) cb.onNextImage();
            handler.postDelayed(this, intervalMs);
          }
        };

    public SlideShow(Callback callback, @Nullable Activity activity) {
      this.cb = callback;
      this.activityRef = activity != null ? new WeakReference<>(activity) : null;
    }

    public void start(long ms) {
      this.intervalMs = Math.max(100L, ms);

      if (activityRef != null) {
        Activity a = activityRef.get();
        if (a != null) {
          a.runOnUiThread(
              () -> a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        }
      }

      if (active.compareAndSet(false, true)) {
        handler.postDelayed(tick, this.intervalMs);
      }
    }

    public void stop() {
      if (active.compareAndSet(true, false)) {
        handler.removeCallbacks(tick);

        if (activityRef != null) {
          Activity a = activityRef.get();
          if (a != null) {
            a.runOnUiThread(
                () -> a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
          }
        }

        if (cb != null) cb.onSlideShowStopped();
      }
    }

    public boolean isActive() {
      return active.get();
    }

    public static void fadeTransition(View imageView, Context context, Runnable loadNextImage) {
      Animation fadeOut = AnimationUtils.loadAnimation(context, android.R.anim.fade_out);
      fadeOut.setAnimationListener(
          new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
              loadNextImage.run();
              Animation fadeIn = AnimationUtils.loadAnimation(context, android.R.anim.fade_in);
              imageView.startAnimation(fadeIn);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
          });
      imageView.startAnimation(fadeOut);
    }
  }

  // ============================================
  //  VideoPlayerGestureHandler
  // ============================================

  /**
   * Specialized gesture handler for Video Player mode. Handles: horizontal swipe → seek, vertical
   * right → volume, vertical left → brightness Also: single tap → toggle controls, double tap →
   * placeholder, pinch zoom → placeholder
   */
  public static class VideoPlayerGestureHandler implements GestureOverlayView.GestureDelegate {

    private final GestureDetectorCompat gestureDetector;
    private final ScaleGestureDetector scaleGestureDetector;
    private final Handler uiHandler;
    private final HostCallback host;
    private final Context ctx;

    // Gesture state flags
    public volatile boolean isSeeking = false;
    public volatile boolean isAdjustingVolume = false;
    public volatile boolean isAdjustingBrightness = false;
    public volatile boolean isScaling = false;

    // Seek accumulator
    private float lastSeekX = 0;
    private long gestureStartPos = 0L; // Anchor for seeking

    private long seekDeltaMs = 0;

    private static final long SEEK_STEP_MS = 1000; // 10 seconds per swipe

    private float lastMidX = Float.NaN;
    private float lastMidY = Float.NaN;

    public interface HostCallback {
      void onSingleTap(); // Toggle controls

      // void onLongPress();

      void onDoubleTap(); // Placeholder

      void onSeek(long deltaMs); // ±10s seek

      void onSeekPreview(long previewPositionMs); // NEW: update UI preview only

      void onVolumeChange(float delta); // 0.0 to 1.0

      void onBrightnessChange(float delta); // 0.0 to 1.0

      void onPinchZoom(float scaleFactor);

      void onPan(float dx, float dy); // two-finger pan deltas

      long getPlayerPosition(); // returns current position in ms

      long getPlayerDuration(); // returns duration in ms (or -1 if unknown)

      void pausePlayerProgressUpdates();

      void resumePlayerProgressUpdates();
    }

    public VideoPlayerGestureHandler(Context context, HostCallback hostCallback) {
      this.ctx = context;
      this.host = hostCallback;
      this.uiHandler = new Handler(Looper.getMainLooper());

      this.gestureDetector = new GestureDetectorCompat(context, new VideoGestureListener());
      this.scaleGestureDetector = new ScaleGestureDetector(context, new VideoScaleListener());
    }

    // active Mode
    private enum ActiveGesture {
      NONE,
      SCALE,
      SEEK,
      VOLUME,
      BRIGHTNESS,
      PAN
    }

    private ActiveGesture active = ActiveGesture.NONE;

    private void setActive(ActiveGesture g) {
      active = g;
    }

    private boolean isActive(ActiveGesture g) {
      return active == g;
    }

    private void clearActive() {
      active = ActiveGesture.NONE;
    }

    @Override
    public boolean wantsTouch(MotionEvent event) {
      // Video player always wants touch events when active
      return true;
    }

    public boolean onDown(MotionEvent e) {
      return true;
	  
    }

    private int pId0 = -1, pId1 = -1;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_POINTER_DOWN:
          if (event.getPointerCount() == 2) {
            pId0 = event.getPointerId(0);
            pId1 = event.getPointerId(1);
            // LOCK to PAN: This prevents onScroll from triggering Seek/Volume
            setActive(ActiveGesture.PAN);
          }
          break;

        case MotionEvent.ACTION_MOVE:
          // Handle the Pan logic here if active is PAN
          if (isActive(ActiveGesture.PAN) && event.getPointerCount() >= 2) {
            handleTwoFingerPan(event);
          }
          break;

        case MotionEvent.ACTION_POINTER_UP:
          // If one finger lifts, stop Panning/Scaling
          if (isActive(ActiveGesture.PAN) || isActive(ActiveGesture.SCALE)) {
            clearActive();
            isScaling = false;
            lastMidX = Float.NaN;
            lastMidY = Float.NaN;
          }
          pId0 = -1;
          pId1 = -1;
          break;
      }

      boolean scaleHandled = scaleGestureDetector.onTouchEvent(event);
      boolean gestureHandled = false;

      // IMPORTANT: Only allow Scroll (Seek/Vol) if we are NOT Panning or Scaling
      if (!isActive(ActiveGesture.SCALE) && !isActive(ActiveGesture.PAN)) {
        gestureHandled = gestureDetector.onTouchEvent(event);
      }

      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        finalizeSeekIfNeeded();
        resetFlags();
        clearActive();
      }

      return scaleHandled || gestureHandled;
    }

    private void finalizeSeekIfNeeded() {
      if (isSeeking && host != null) {
        host.onSeek(seekDeltaMs);
        host.resumePlayerProgressUpdates();
      }
    }

    private void resetFlags() {
      isSeeking = false;
      isAdjustingVolume = false;
      isAdjustingBrightness = false;
      seekDeltaMs = 0;
    }

    private void handleTwoFingerPan(MotionEvent event) {
      // Find the correct index for our tracked IDs
      int idx0 = event.findPointerIndex(pId0);
      int idx1 = event.findPointerIndex(pId1);

      if (idx0 != -1 && idx1 != -1) {
        float midX = (event.getX(idx0) + event.getX(idx1)) / 2f;
        float midY = (event.getY(idx0) + event.getY(idx1)) / 2f;

        if (!Float.isNaN(lastMidX) && !Float.isNaN(lastMidY)) {
          float dx = midX - lastMidX;
          float dy = midY - lastMidY;

          // Use the host callback you already have set up
          if (host != null) {
            host.onPan(dx, dy);
          }
        }

        lastMidX = midX;
        lastMidY = midY;
      }
    }

    public void cleanup() {
      if (uiHandler != null) {
        uiHandler.removeCallbacksAndMessages(null);
      }
    }

    private class VideoGestureListener extends GestureDetector.SimpleOnGestureListener {
      private static final float VOLUME_SENSITIVITY =
          0.0027f; // Adjust for smoother/coarser control
      private static final float BRIGHTNESS_SENSITIVITY = 0.001f;

      private final int touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();

      private boolean gestureDirectionLocked = false;

      @Override
      public boolean onDown(MotionEvent e) {
        gestureDirectionLocked = false;
        isSeeking = false;
        isAdjustingVolume = false;
        isAdjustingBrightness = false;

        // Capture start position for the "Anchor"
        if (host != null) {
          gestureStartPos = host.getPlayerPosition();
          seekDeltaMs = 0;
        }
        return true;
      }

      @Override
      public boolean onSingleTapConfirmed(MotionEvent e) {
        if (host != null) host.onSingleTap();
        return true;
      }

      /**
       * @Override public void onLongPress(MotionEvent e) { if (host != null) host.onLongPress(); }
       */
      @Override
      public boolean onDoubleTap(MotionEvent e) {
        if (host != null) {
          int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
          float tapX = e.getX();
          // Decide direction based on tap position
          long skipMs = 10_000; // 10 seconds
          if (tapX < screenWidth / 2f) {
            // Left side → rewind
            host.onSeek(-skipMs);
          } else {
            // Right side → forward
            host.onSeek(skipMs);
          }
        }
        return true;
      }

      @Override
      public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        // LOCK: If two fingers are touching, it's a Pan/Zoom, NOT a Seek/Volume/Brightness
        if (e2.getPointerCount() > 1) {
          // Return false so we don't accidentally set ActiveGesture.SEEK or VOLUME
          return false;
        }

        if (active == ActiveGesture.NONE) {
          float totalDeltaX = e2.getX() - e1.getX();
          float totalDeltaY = e2.getY() - e1.getY();

          if (Math.abs(totalDeltaX) > touchSlop || Math.abs(totalDeltaY) > touchSlop) {
            if (Math.abs(totalDeltaX) > Math.abs(totalDeltaY)) {
              setActive(ActiveGesture.SEEK);
              if (host != null) host.pausePlayerProgressUpdates();
            } else {
              int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
              setActive(
                  e1.getX() > screenWidth / 2 ? ActiveGesture.VOLUME : ActiveGesture.BRIGHTNESS);
            }
          }
        }

        // Standard execution for one-finger locked gestures
        if (isActive(ActiveGesture.SEEK)) handleSeekGesture(-distanceX);
        else if (isActive(ActiveGesture.VOLUME)) handleVolumeGesture(-distanceY);
        else if (isActive(ActiveGesture.BRIGHTNESS)) handleBrightnessGesture(-distanceY);

        return true;
      }

      // When finger is lifted, reset everything and apply final seek
      public void onUp(MotionEvent e) {
        if (isSeeking && host != null) {
        //  host.onSeek(seekDeltaMs);
          host.resumePlayerProgressUpdates();
        }
        isSeeking = false;
        isAdjustingVolume = false;
        isAdjustingBrightness = false;
        gestureDirectionLocked = false;
      }

      // duration logic
      private void handleSeekGesture(float deltaX) {
        isSeeking = true;
        int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;

        // Get video duration
        long duration = -1L;
        if (host != null) {
          duration = host.getPlayerDuration();
        }

        if (duration <= 0) {
          // Fallback to fixed duration (e.g., 5 min default)
          duration = 300000; // 5 minutes
        }

        // Map screen movement to percentage of total duration
        float seekRatio = deltaX / screenWidth;
   //     long deltaMs = (long) (seekRatio * duration * SEEK_STEP_MS);

        // For better control: full swipe = ~10% of video
        // SEEK_SENSITIVITY = 0.1f means 10% per full screen swipe
        long deltaMs = (long) (seekRatio * duration * 0.1f);

        seekDeltaMs += deltaMs;
				
				long previewPos = gestureStartPos + seekDeltaMs;

                if (previewPos < 0) previewPos = 0;
             if (duration > 0 && previewPos > duration) previewPos = duration;

                if (host != null) {
                    host.onSeekPreview(previewPos);
                }
      }

      private void handleVolumeGesture(float incrementalY) {
        isAdjustingVolume = true;
        // distanceY is positive when scrolling DOWN, so negative = swipe UP
        float volumeDelta = -incrementalY * VOLUME_SENSITIVITY;

        if (host != null) host.onVolumeChange(volumeDelta);
      }

      private void handleBrightnessGesture(float incrementalY) {
        isAdjustingBrightness = true;
        float brightnessDelta = -incrementalY * BRIGHTNESS_SENSITIVITY;

        if (host != null) host.onBrightnessChange(brightnessDelta);
      }
    }

    private class VideoScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
      @Override
      public boolean onScaleBegin(ScaleGestureDetector detector) {
        isScaling = true;
        lastMidX = Float.NaN;
        lastMidY = Float.NaN;
        return true;
      }

      @Override
      public boolean onScale(ScaleGestureDetector detector) {
        if (host != null) host.onPinchZoom(detector.getScaleFactor());
        return true;
      }

      @Override
      public void onScaleEnd(ScaleGestureDetector detector) {
        isScaling = false;
        lastMidX = Float.NaN;
        lastMidY = Float.NaN;
      }
    }
  }
}
