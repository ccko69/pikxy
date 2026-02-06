package com.ccko.pikxplus.ui;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.view.animation.DecelerateInterpolator;
import android.animation.ValueAnimator;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.Glide;
import com.ccko.pikxplus.MainActivity;
import com.ccko.pikxplus.R;
import com.ccko.pikxplus.adapters.ImageLoader;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.os.Build;
import androidx.annotation.RequiresApi;

import android.graphics.drawable.Drawable;
import androidx.fragment.app.FragmentActivity;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import com.ccko.pikxplus.features.GestureAndSlideShow;
// import com.ccko.pikxplus.ui.matrix.ImageMatrixController;

// video player IMPORTS
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.ccko.pikxplus.adapters.MediaItems;
import android.media.AudioManager;

public class ViewerFragment extends Fragment {

  private static final String TAG = "ViewerFragment";
  private ImageView imageView;
  private TextView imageIndexText;
  private TextView imageNameText;
  private View topBar;
  private View bottomBar;
  private ImageButton backButton;
  private ImageButton deleteButton;
  private ImageButton infoButton;
  private ImageButton rotateButton;
  private ImageButton menuButton;
  //   private View leftHotspot;
  //   private View rightHotspot;
  private View rootView;

  private boolean isUiVisible = true;

  // Image navigation
  private List<Uri> imageUris;
  private int currentIndex = 0;
  private float scaleFactor = 1.0f;
  private Matrix imageMatrix = new Matrix();
  //	private ImageMatrixController matrixController;

  private GestureAndSlideShow.ImageViewerGestureHandler gestureHandler;
  private boolean isSlideShowRunning = false;

  private float fitScale = 1.0f; // Base fit scale (computed per image)
  private float minTempScale = 0.7f; // Allow temporary zoom-out to this during gesture
  // Callback interface

  // NEW FIELDS FOR VIDEO SUPPORT
  private List<MediaItems> mediaItems; // Changed from List<Uri> imageUris
  private boolean isVideoMode = false;
  private ExoPlayer exoPlayer;
  private StyledPlayerView playerView;
  private VideoPlayerOverlay videoControls;
  private GestureAndSlideShow.VideoPlayerGestureHandler videoGestureHandler;
  private boolean controlsVisible = true;
  private Handler controlsHandler = new Handler(Looper.getMainLooper());
  private Runnable hideControlsRunnable;

  // Volume/Brightness state
  private AudioManager audioManager;
  private int maxVolume;
  private float currentBrightness = -1f;

  // short memory
  private static final String PREF_KEY_LAST_URI = "viewer_last_image";
  private static final String PREF_KEY_LAST_INDEX = "viewer_last_index";
  private static final String STATE_KEY_URI = "state_last_image_uri";
  private static final String STATE_KEY_INDEX = "state_last_image_index";
  private boolean userPickedImage = false; // set true when user explicitly selects a thumbnail

  // Add this constant at the top of ViewerFragment class:
  public static final String REQUEST_KEY = "viewer_result";
  public static final String RESULT_URI_KEY = "last_viewed_uri";
  public static final String RESULT_INDEX_KEY = "last_viewed_index";

  // rotation fields
  private int rotationSteps = 0;
  private static final int ROTATION_DURATION = 500;

  public interface OnImageDeletedListener {
    void onImageDeleted(int deletedIndex);
  }

  private OnImageDeletedListener deleteListener;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    try {
      deleteListener = (OnImageDeletedListener) context;
    } catch (ClassCastException e) {
      throw new ClassCastException(context.toString() + " must implement OnImageDeletedListener");
    }
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {

    rootView = inflater.inflate(R.layout.fragment_viewer, container, false);
    // Initialize views
    imageView = rootView.findViewById(R.id.imageView);
    imageIndexText = rootView.findViewById(R.id.imageIndex);
    imageNameText = rootView.findViewById(R.id.imageName);
    topBar = rootView.findViewById(R.id.topBar);
    bottomBar = rootView.findViewById(R.id.bottomBar);
    backButton = rootView.findViewById(R.id.backButton);
    deleteButton = rootView.findViewById(R.id.deleteButton);
    infoButton = rootView.findViewById(R.id.infoButton);
    menuButton = rootView.findViewById(R.id.menuButton);
    rotateButton = rootView.findViewById(R.id.rotateButton);
    // matrixController = new ImageMatrixController(imageView);
    // gesture and slideshow wiring
    // CHANGED: Initialize the new gesture handler
    gestureHandler =
        new GestureAndSlideShow.ImageViewerGestureHandler(
            requireContext(),
            new GestureAndSlideShow.ImageViewerGestureHandler.HostCallback() {
              @Override
              public void onDoubleTap(float x, float y) {
                if (imageView != null) {
                  imageView.post(
                      () -> {
                        if (scaleFactor > 1.1f) {
                          Matrix target = computeInitialMatrix(imageView);
                          clampMatrixForMatrix(target);
                          animateToMatrix(target, 1.0f);
                        } else {
                          Matrix targetMatrix = new Matrix(imageMatrix);
                          float zoom = 2f;
                          targetMatrix.postScale(zoom, zoom, x, y);
                          float targetScale = scaleFactor * zoom;
                          clampMatrixForMatrix(targetMatrix);
                          animateToMatrix(targetMatrix, targetScale);
                        }
                      });
                }
              }

              @Override
              public void onSlideShowNext() {
                showNextImageWithFade();
              }

              @Override
              public void onSlideShowStopped() {
                showUiElements();
                isSlideShowRunning = false;
              }

              @Override
              public void onLongPress() {
                if (imageView != null) {
                  toggleUiVisibility();
                  if (isSlideShowRunning) {
                    stopSlideShow();
                  }
                }
              }

              @Override
              public void onPan(float distanceX, float distanceY) {
                if (imageView != null) {
                  imageView.post(
                      () -> {
                        if (scaleFactor > 1.01f) {
                          imageMatrix.postTranslate(-distanceX, -distanceY);
                          clampMatrix();
                          imageView.setImageMatrix(imageMatrix);
                        }
                      });
                }
              }

              @Override
              public void onScale(float scaleDelta, float focusX, float focusY) {
                if (imageView != null) {
                  imageView.post(
                      () -> {
                        float newScale = scaleFactor * scaleDelta;
                        newScale = Math.max(minTempScale, Math.min(newScale, 30.0f));
                        if (newScale != scaleFactor) {
                          float appliedDelta = newScale / scaleFactor;
                          scaleFactor = newScale;
                          imageMatrix.postScale(appliedDelta, appliedDelta, focusX, focusY);
                          clampMatrix();
                          imageView.setImageMatrix(imageMatrix);
                        }
                      });
                }
              }

              @Override
              public void onScaleEnd() {
                if (imageView != null) {
                  imageView.post(
                      () -> {
                        if (scaleFactor < 1.0f) {
                          animateScaleTo(1.0f);
                        }
                      });
                }
              }

              @Override
              public void onRequestClose() {
                if (imageView != null) {
                  imageView.post(
                      () -> {
                        final float ZOOM_CLOSE_THRESHOLD = 1.02f;
                        boolean currentlyScalingOrPanning =
                            gestureHandler != null && (gestureHandler.isPanning);
                        boolean zoomedIn = scaleFactor > ZOOM_CLOSE_THRESHOLD;

                        if (currentlyScalingOrPanning || zoomedIn) {
                          imageView.animate().translationY(0).setDuration(100).start();
                          return;
                        }

                        imageView
                            .animate()
                            .translationY(imageView.getHeight())
                            .setDuration(150)
                            .withEndAction(
                                () -> {
                                  if (getActivity() instanceof MainActivity) {
                                    getActivity().onBackPressed();
                                  }
                                })
                            .start();
                      });
                }
              }

              @Override
              public void onNextImageRequested() {
                if (imageView != null) {
                  imageView.post(
                      () -> {
                        if (mediaItems == null || mediaItems.isEmpty()) // CHANGED
                        return;
                        if (currentIndex < mediaItems.size() - 1) { // CHANGED
                          imageView
                              .animate()
                              .translationX(-imageView.getWidth())
                              .setDuration(150)
                              .withEndAction(
                                  () -> {
                                    loadMediaAtIndex(currentIndex + 1); // CHANGED
                                    imageView.setTranslationX(imageView.getWidth());
                                    imageView.animate().translationX(0).setDuration(150).start();
                                  })
                              .start();
                        }
                      });
                }
              }

              @Override
              public void onPreviousImageRequested() {
                if (imageView != null) {
                  imageView.post(
                      () -> {
                        if (mediaItems == null || mediaItems.isEmpty()) // CHANGED
                        return;
                        if (currentIndex > 0) {
                          imageView
                              .animate()
                              .translationX(imageView.getWidth())
                              .setDuration(150)
                              .withEndAction(
                                  () -> {
                                    loadMediaAtIndex(currentIndex - 1); // CHANGED
                                    imageView.setTranslationX(-imageView.getWidth());
                                    imageView.animate().translationX(0).setDuration(150).start();
                                  })
                              .start();
                        }
                      });
                }
              }

              @Override
              public void onLeftHotspotTapped() {
                if (currentIndex > 0) {

                  final float ZOOM_CLOSE_THRESHOLD = 1.02f;
                  boolean currentlyScalingOrPanning =
                      gestureHandler != null && (gestureHandler.isPanning);
                  boolean zoomedIn = scaleFactor > ZOOM_CLOSE_THRESHOLD;

                  if (currentlyScalingOrPanning || zoomedIn) {
                    imageView.animate().translationX(0).setDuration(150).start();
                    return;
                  }
                  imageView
                      .animate()
                      .translationX(imageView.getWidth())
                      .setDuration(150)
                      .withEndAction(
                          () -> {
                            loadMediaAtIndex(currentIndex - 1);
                            imageView.setTranslationX(-imageView.getWidth());
                            imageView.animate().translationX(0).setDuration(150).start();
                          })
                      .start();
                }
              }

              @Override
              public void onRightHotspotTapped() {
                if (currentIndex < mediaItems.size() - 1) {

                  final float ZOOM_CLOSE_THRESHOLD = 1.02f;
                  boolean currentlyScalingOrPanning =
                      gestureHandler != null && (gestureHandler.isPanning);
                  boolean zoomedIn = scaleFactor > ZOOM_CLOSE_THRESHOLD;

                  if (currentlyScalingOrPanning || zoomedIn) {
                    imageView.animate().translationX(0).setDuration(150).start();
                    return;
                  }
                  imageView
                      .animate()
                      .translationX(-imageView.getWidth())
                      .setDuration(150)
                      .withEndAction(
                          () -> {
                            loadMediaAtIndex(currentIndex + 1);
                            imageView.setTranslationX(imageView.getWidth());
                            imageView.animate().translationX(0).setDuration(150).start();
                          })
                      .start();
                }
              }
            },
            requireActivity());

    // Setup listeners
    backButton.setOnClickListener(
        v -> {
          if (getActivity() instanceof MainActivity) {
            getActivity().onBackPressed();
          }
        });
    deleteButton.setOnClickListener(v -> confirmDeleteImage());
    infoButton.setOnClickListener(v -> showImageInfo());

    rotateButton.setOnClickListener(
        v -> {
          ((MainActivity) getActivity()).toggleOrientation();

          if (imageView == null) return;

          imageView.post(
              () -> {
                Handler handler = new Handler(Looper.getMainLooper());

                // center pivot for zoom
                float x = imageView.getWidth() / 2f;
                float y = imageView.getHeight() / 2f;

                Runnable doubleTapLogic =
                    () -> {
                      if (scaleFactor > 1.1f) {
                        Matrix target = computeInitialMatrix(imageView);
                        clampMatrixForMatrix(target);
                        animateToMatrix(target, 1.0f);
                      } else {
                        Matrix targetMatrix = new Matrix(imageMatrix);
                        float zoom = 2.5f;
                        targetMatrix.postScale(zoom, zoom, x, y);
                        float targetScale = scaleFactor * zoom;
                        clampMatrixForMatrix(targetMatrix);
                        animateToMatrix(targetMatrix, targetScale);
                      }
                    };

                // first simulated double tap
                handler.postDelayed(doubleTapLogic, 60);

                // second simulated double tap
                handler.postDelayed(doubleTapLogic, 560);

                // final safety clamp
                handler.postDelayed(
                    () -> {
                      if (scaleFactor < 1.0f) {
                        animateScaleTo(1.0f);
                      }
                    },
                    570);
              });
        });

    // Menu button for slideshow
    menuButton.setOnClickListener(
        v -> {
          PopupMenu menu = new PopupMenu(requireContext(), menuButton);
          if (!gestureHandler.isSlideShowActive()) {
            menu.getMenu().add("Slideshow  »");
          } else {
            menu.getMenu().add("Slideshow  ∎");
          }
          menu.setOnMenuItemClickListener(
              item -> {
                if (item.getTitle().equals("Slideshow  »")) {
                  askForSlideShowInterval();
                } else {
                  stopSlideShow();
                }
                return true;
              });
          menu.show();
        });

    // rootView.setOnClickListener(v -> toggleUiVisibility());

    // auto-hide UI onCreate
    hideUiElements();
    return rootView;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    // calling the viewer: read args, allow optional "user_picked" flag
    Bundle args = getArguments();
    if (args != null) {
      // NEW: Receive MediaItem list (contains both images and videos)
      mediaItems = args.getParcelableArrayList("media_items");
      int incomingIndex = args.getInt("current_index", 0);
      userPickedImage = args.getBoolean("user_picked", false);

      if (mediaItems == null || mediaItems.isEmpty()) {
        Toast.makeText(getContext(), "No media to display", Toast.LENGTH_SHORT).show();
        requireActivity().onBackPressed();
        return;
      }

      currentIndex = Math.max(0, Math.min(incomingIndex, mediaItems.size() - 1));
      restoreViewerStateIfNeeded(savedInstanceState);
      loadMediaAtIndex(currentIndex);
    } else {
      Toast.makeText(getContext(), "No media to display", Toast.LENGTH_SHORT).show();
      requireActivity().onBackPressed();
    }
  }

  // helpers for short memory
  private void saveViewerState(@Nullable Uri imageUri, int index) {
    if (imageUri == null) {
      Log.d(TAG, "Cannot save state - URI is null");
      return;
    }
    if (MainActivity.prefs == null) {
      Log.e(TAG, "Cannot save state - SharedPreferences is null!");
      return;
    }
    try {
      // Use apply() for async saving during swipes
      MainActivity.prefs
          .edit()
          .putString(PREF_KEY_LAST_URI, imageUri.toString())
          .putInt(PREF_KEY_LAST_INDEX, index)
          .apply();
      Log.d(TAG, "Saved viewer state - URI: " + imageUri + ", Index: " + index);
    } catch (Exception e) {
      Log.e(TAG, "Error saving viewer state", e);
    }
  }

  private void clearViewerState() {
    try {
      MainActivity.prefs.edit().remove(PREF_KEY_LAST_URI).remove(PREF_KEY_LAST_INDEX).apply();
    } catch (Exception ignored) {
    }
  }

  private void restoreViewerStateIfNeeded(@Nullable Bundle savedInstanceState) {
    // 1) If user explicitly picked an image in this session, do not override
    if (imageUris == null || imageUris.isEmpty()) return; // defensive guard
    if (userPickedImage) return;

    // 2) Try savedInstanceState first (fast rotation restore)
    if (savedInstanceState != null) {
      String uriStr = savedInstanceState.getString(STATE_KEY_URI, null);
      int idx = savedInstanceState.getInt(STATE_KEY_INDEX, -1);
      if (uriStr != null && idx >= 0 && idx < imageUris.size()) {
        // try to match URI in current list
        for (int i = 0; i < imageUris.size(); i++) {
          Uri u = imageUris.get(i);
          if (u != null && u.toString().equals(uriStr)) {
            currentIndex = i;
            return;
          }
        }
        // fallback to index if valid
        if (idx >= 0 && idx < imageUris.size()) {
          currentIndex = idx;
          return;
        }
      }
    }

    // 3) Then try SharedPreferences (survives process death)
    String savedUri = MainActivity.prefs.getString(PREF_KEY_LAST_URI, null);
    int savedIndex = MainActivity.prefs.getInt(PREF_KEY_LAST_INDEX, -1);

    if (savedUri != null) {
      int matchIndex = -1;
      for (int i = 0; i < imageUris.size(); i++) {
        Uri u = imageUris.get(i);
        if (u != null && u.toString().equals(savedUri)) {
          matchIndex = i;
          break;
        }
      }
      if (matchIndex >= 0) {
        currentIndex = matchIndex;
        return;
      }
    }

    // 4) fallback to saved index if valid, else keep default 0
    if (savedIndex >= 0 && savedIndex < imageUris.size()) {
      currentIndex = savedIndex;
    } else {
      currentIndex = Math.max(0, Math.min(currentIndex, imageUris.size() - 1));
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    if (imageUris != null && currentIndex >= 0 && currentIndex < imageUris.size()) {
      Uri u = imageUris.get(currentIndex);
      if (u != null) {
        outState.putString(STATE_KEY_URI, u.toString());
        outState.putInt(STATE_KEY_INDEX, currentIndex);
      }
    }
  } // short memory end

  private boolean hotspotAllowed() {
    if (gestureHandler != null) {
      return !gestureHandler.isSwiping
          && !gestureHandler.isScaling
          && !gestureHandler.isPanning
          && !gestureHandler.isDoubleTapping
          && scaleFactor <= 1.01f;
    }
    return scaleFactor <= 1.01f;
  }

  // animation handler for slideshow
  private void showNextImageWithFade() {
    if (currentIndex < mediaItems.size() - 1) {
      imageView
          .animate()
          //  .translationX(-imageView.getWidth())
          .alpha(0f) // fade out while sliding out
          .setDuration(500)
          .withEndAction(
              () -> {
                loadMediaAtIndex(currentIndex + 1);

                // reset position and alpha before sliding back in
                // imageView.setTranslationX(imageView.getWidth());
                imageView.setAlpha(0f);

                imageView
                    .animate()
                    //  .translationX(0)
                    .alpha(1f) // fade back in
                    .setDuration(250)
                    .start();
              })
          .start();
    }
  }

  private void askForSlideShowInterval() {
    // Inflate our new master layout
    View dialogView =
        LayoutInflater.from(requireContext()).inflate(R.layout.alert_dialog_base, null);
    TextView titleView = dialogView.findViewById(R.id.dialog_title);
    EditText input = dialogView.findViewById(R.id.dialog_input);

    // Setup local info
    titleView.setText("Second per Slide");
    int lastSec = MainActivity.prefs.getInt("slideshow_delay", 5);
    input.setText(String.valueOf(lastSec));
    input.requestFocus();

    AlertDialog dialog =
        new AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("▶", (d, i) -> performSlideshowStart(input, lastSec))
            .setNegativeButton("X", null)
            .create();

    // KEYBOARD CONFIRMATION: This is the "Confirm from keyboard" part
    input.setOnEditorActionListener(
        (v, actionId, event) -> {
          if (actionId == EditorInfo.IME_ACTION_DONE) {
            performSlideshowStart(input, lastSec);
            dialog.dismiss(); // Manually dismiss since we aren't clicking the button
            return true;
          }
          return false;
        });

    if (dialog.getWindow() != null) {
      dialog
          .getWindow()
          .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
      // Apply the 50% width rule here
      dialog.show();
      int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.7);
      dialog.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
      dialog
          .getWindow()
          .setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // Layout handles color
    }

    // Style buttons
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#4CAF50"));
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#F44336"));
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setBackgroundColor(Color.parseColor("#CC000000"));
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackgroundColor(Color.parseColor("#CC000000"));
    // Focus the input
    input.requestFocus();
    input.selectAll();
  }

  // Helper to avoid repeating logic for button vs keyboard
  private void performSlideshowStart(EditText input, int lastSec) {
    String txt = input.getText().toString().trim();
    int sec = txt.isEmpty() ? lastSec : Integer.parseInt(txt);
    MainActivity.prefs.edit().putInt("slideshow_delay", sec).apply();
    startSlideShow(sec);
  }

  // CHANGED: Update slideshow methods to use new handler
  private void startSlideShow(int sec) {
    isSlideShowRunning = true;
    hideUiElements();
    long intervalMs = Math.max(500, sec * 1000L);
    gestureHandler.startSlideShow(intervalMs);
  }

  private void stopSlideShow() {
    isSlideShowRunning = false;
    gestureHandler.stopSlideShow();
  }

  // main image loader. (need to make a var to hold showing image's id/address for when activity
  // gets reset.)
  private void loadImageAtIndex(int index) {
    if (mediaItems == null || index < 0 || index >= mediaItems.size()) return;

    MediaItems item = mediaItems.get(index);
    currentIndex = index;

    // For images, use the new loadImage() method
    if (!item.isVideo()) {
      loadImage(item);
    } else {
      // For videos, show thumbnail
      showVideoThumbnail(item);
    }
  }

  private void preloadNearbyImages() {
    if (mediaItems == null || mediaItems.isEmpty()) return;

    int start = Math.max(0, currentIndex - 2);
    int end = Math.min(mediaItems.size() - 1, currentIndex + 4);

    for (int i = start; i <= end; i++) {
      if (i == currentIndex) continue;

      MediaItems item = mediaItems.get(i);
      // Only preload images, not videos
      if (!item.isVideo()) {
        // this warms the disk cache with a File; fast and cheap
        Glide.with(this)
            .asFile()
            .load(item.uri)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .preload();
      }
    }
  }

  // Helper method to detect animated webp files
  private boolean isAnimatedWebp(byte[] header) {
    try {
      // Check RIFF header
      if (header.length < 12
          || header[0] != 'R'
          || header[1] != 'I'
          || header[2] != 'F'
          || header[3] != 'F'
          || header[8] != 'W'
          || header[9] != 'E'
          || header[10] != 'B'
          || header[11] != 'P') {
        return false;
      }

      // For complete detection we would need to parse chunks, but this is a good heuristic
      // Animated webp files typically have VP8X chunk with animation flag
      return true; // Simplified for now - most webp files on Android 11 can be animated
    } catch (Exception e) {
      return false;
    }
  }

  // helper for zoom out animation snap back
  private void animateScaleTo(float targetScale) {
    final Matrix startMatrix = new Matrix(imageMatrix); // Snapshot current
    final Matrix endMatrix = computeInitialMatrix(imageView); // Target fit (centered)

    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f); // Progress 0-1
    animator.setDuration(500);
    animator.setInterpolator(new AccelerateDecelerateInterpolator());
    animator.addUpdateListener(
        animation -> {
          float progress = (float) animation.getAnimatedValue();

          // Interpolate matrix values
          float[] startValues = new float[9];
          float[] endValues = new float[9];
          float[] interpolated = new float[9];
          startMatrix.getValues(startValues);
          endMatrix.getValues(endValues);
          for (int i = 0; i < 9; i++) {
            interpolated[i] = startValues[i] + progress * (endValues[i] - startValues[i]);
          }

          imageMatrix.setValues(interpolated);
          imageView.setImageMatrix(imageMatrix);

          // Update scaleFactor for consistency (lerp from current to 1.0f)
          scaleFactor = scaleFactor + progress * (targetScale - scaleFactor);
        });
    animator.start();
  }

  // helper for double tap animation zoom in and out
  private void animateToMatrix(Matrix targetMatrix, float targetScale) {
    final Matrix startMatrix = new Matrix(imageMatrix); // Snapshot current

    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration(500);
    animator.setInterpolator(new AccelerateDecelerateInterpolator());
    animator.addUpdateListener(
        animation -> {
          float progress = (float) animation.getAnimatedValue();

          // Interpolate matrix
          float[] startValues = new float[9];
          float[] endValues = new float[9];
          float[] interpolated = new float[9];
          startMatrix.getValues(startValues);
          targetMatrix.getValues(endValues);
          for (int i = 0; i < 9; i++) {
            interpolated[i] = startValues[i] + progress * (endValues[i] - startValues[i]);
          }

          imageMatrix.setValues(interpolated);
          imageView.setImageMatrix(imageMatrix);

          // Interpolate scaleFactor
          scaleFactor = scaleFactor + progress * (targetScale - scaleFactor);
        });
    animator.start();
  }

  private Matrix computeInitialMatrix(ImageView imgView) {
    Drawable drawable = imgView.getDrawable();
    if (drawable == null) {
      return new Matrix();
    }

    int drawWidth = drawable.getIntrinsicWidth();
    int drawHeight = drawable.getIntrinsicHeight();
    int viewWidth = imgView.getWidth();
    int viewHeight = imgView.getHeight();

    if (drawWidth <= 0 || drawHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
      return new Matrix();
    }

    float scale = Math.min((float) viewWidth / drawWidth, (float) viewHeight / drawHeight);
    Matrix matrix = new Matrix();
    matrix.setScale(scale, scale);

    float dx = (viewWidth - drawWidth * scale) / 2f;
    float dy = (viewHeight - drawHeight * scale) / 2f;
    matrix.postTranslate(dx, dy);
    fitScale = scale; // Store the base fit scale
    return matrix;
  }

  private void clampMatrix() {
    Drawable drawable = imageView.getDrawable();
    if (drawable == null) return;

    float[] values = new float[9];
    imageMatrix.getValues(values);
    float scaleX = values[Matrix.MSCALE_X];
    float transX = values[Matrix.MTRANS_X];
    float transY = values[Matrix.MTRANS_Y];

    int drawWidth = drawable.getIntrinsicWidth();
    int drawHeight = drawable.getIntrinsicHeight();
    int viewWidth = imageView.getWidth();
    int viewHeight = imageView.getHeight();

    // Scaled dimensions
    float scaledWidth = drawWidth * scaleX;
    float scaledHeight = drawHeight * scaleX; // Assume uniform scale

    // Clamp X: Don't pan left if left edge is visible, etc.
    if (scaledWidth > viewWidth) {
      // Can pan horizontally
      float minTransX = viewWidth - scaledWidth;
      float maxTransX = 0f;
      transX = Math.max(minTransX, Math.min(transX, maxTransX));
    } else { // Center if smaller
      transX = (viewWidth - scaledWidth) / 2f;
    }

    // Clamp Y (same logic)
    if (scaledHeight > viewHeight) {
      float minTransY = viewHeight - scaledHeight;
      float maxTransY = 0f;
      transY = Math.max(minTransY, Math.min(transY, maxTransY));
    } else {
      transY = (viewHeight - scaledHeight) / 2f;
    }

    // Apply clamped values
    values[Matrix.MTRANS_X] = transX;
    values[Matrix.MTRANS_Y] = transY;
    imageMatrix.setValues(values);
  }

  // helper for double tap clamp
  private RectF getDisplayRect(Matrix matrix) {
    Drawable d = imageView.getDrawable();
    if (d == null) return null;

    RectF rect = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
    matrix.mapRect(rect);
    return rect;
  }

  private void clampMatrixForMatrix(Matrix m) {
    RectF r = getDisplayRect(m);
    if (r == null) return;

    int viewW = imageView.getWidth();
    int viewH = imageView.getHeight();
    if (viewW == 0 || viewH == 0) return;

    float deltaX = 0f;
    float deltaY = 0f;

    // Horizontal clamp / center
    if (r.width() <= viewW) {
      // center horizontally
      deltaX = (viewW - r.width()) * 0.5f - r.left;
    } else {
      if (r.left > 0) {
        deltaX = -r.left;
      } else if (r.right < viewW) {
        deltaX = viewW - r.right;
      }
    }

    // Vertical clamp / center
    if (r.height() <= viewH) {
      // center vertically
      deltaY = (viewH - r.height()) * 0.5f - r.top;
    } else {
      if (r.top > 0) {
        deltaY = -r.top;
      } else if (r.bottom < viewH) {
        deltaY = viewH - r.bottom;
      }
    }

    if (Math.abs(deltaX) > 0.5f || Math.abs(deltaY) > 0.5f) {
      m.postTranslate(deltaX, deltaY);
    }
  }

  private String getImageNameFromUri(Context context, Uri uri) {
    try {
      String[] projection = {MediaStore.Images.Media.DISPLAY_NAME};
      try (android.database.Cursor cursor =
          context.getContentResolver().query(uri, projection, null, null, null)) {
        if (cursor != null && cursor.moveToFirst()) {
          int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
          return cursor.getString(nameIndex);
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "Error getting image name", e);
    }
    return "Unknown";
  }

  private void updateIndexDisplay() {
    if (mediaItems != null && !mediaItems.isEmpty()) {
      imageIndexText.setText((currentIndex + 1) + " / " + mediaItems.size());
    } else if (imageUris != null && !imageUris.isEmpty()) {
      // Fallback for legacy code
      imageIndexText.setText((currentIndex + 1) + " / " + imageUris.size());
    } else {
      imageIndexText.setText("0 / 0"); // Or empty string
    }
  }

  // =======
  // Ui Visibility
  private void toggleUiVisibility() {
    if (isSlideShowRunning) return; // lock UI hidden

    if (isUiVisible) {
      hideUiElements();

      // edge to edge
      Activity a = getActivity();
      if (a instanceof MainActivity) {
        ((MainActivity) a).setViewerModeEnabled(true);
      }
    } else {
      showUiElements();
    }
  }

  private void hideUiElements() {
    // Animate out
    topBar
        .animate()
        .translationY(-topBar.getHeight())
        .setInterpolator(new AccelerateDecelerateInterpolator())
        .setDuration(400)
        .start();
    bottomBar
        .animate()
        .translationY(bottomBar.getHeight())
        .setInterpolator(new AccelerateDecelerateInterpolator())
        .setDuration(400)
        .start();

    topBar.setVisibility(View.INVISIBLE);
    bottomBar.setVisibility(View.INVISIBLE);
    isUiVisible = false;
  }

  private void showUiElements() {
    // Animate in
    topBar
        .animate()
        .translationY(0)
        .setInterpolator(new AccelerateDecelerateInterpolator())
        .setDuration(300)
        .start();
    bottomBar
        .animate()
        .translationY(0)
        .setInterpolator(new AccelerateDecelerateInterpolator())
        .setDuration(300)
        .start();

    topBar.setVisibility(View.VISIBLE);
    bottomBar.setVisibility(View.VISIBLE);
    isUiVisible = true;
  }

  // =========== Bottons
  // Button - Delete.
  private void confirmDeleteImage() {
    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
        .setTitle("Delete Image")
        .setMessage(
            "Are you sure you want to permanently delete this image? This action cannot be undone.")
        .setPositiveButton(
            "Delete",
            (dialog, which) -> {
              Toast.makeText(getContext(), "Image deleted", Toast.LENGTH_SHORT).show();
              deleteCurrentImage();
            })
        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
        .show();
  }

  private void deleteCurrentImage() {
    if (mediaItems == null || currentIndex < 0 || currentIndex >= mediaItems.size()) return;

    MediaItems item = mediaItems.get(currentIndex);
    boolean deleted = deleteImageFromStorage(getContext(), item.uri);

    if (deleted) {
      mediaItems.remove(currentIndex);
      if (deleteListener != null) {
        deleteListener.onImageDeleted(currentIndex);
      }

      if (mediaItems.isEmpty()) {
        requireActivity().onBackPressed();
      } else {
        // Adjust index if needed
        if (currentIndex >= mediaItems.size()) {
          currentIndex = mediaItems.size() - 1;
        }
        loadMediaAtIndex(currentIndex); // Changed from loadImageAtIndex()
        updateIndexDisplay();
      }
    } else {
      Toast.makeText(getContext(), "Failed to delete media", Toast.LENGTH_SHORT).show();
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.Q)
  private boolean deleteImageFromStorage(Context context, Uri imageUri) {
    try {
      ContentResolver resolver = context.getContentResolver();
      ContentValues values = new ContentValues();
      values.put(MediaStore.Images.Media.IS_PENDING, 0);
      resolver.update(imageUri, values, null, null);

      return resolver.delete(imageUri, null, null) > 0;
    } catch (Exception e) {
      Log.e(TAG, "Error deleting image", e);
      return false;
    }
  }

  // button - Item info
  private void showImageInfo() {
    if (mediaItems == null || currentIndex < 0 || currentIndex >= mediaItems.size()) return;

    MediaItems item = mediaItems.get(currentIndex);
    StringBuilder info = new StringBuilder();

    try {
      String[] projection;
      String mediaTable;

      if (item.isVideo()) {
        projection =
            new String[] {
              MediaStore.Video.Media.DISPLAY_NAME,
              MediaStore.Video.Media.SIZE,
              MediaStore.Video.Media.DATE_MODIFIED,
              MediaStore.Video.Media.WIDTH,
              MediaStore.Video.Media.HEIGHT,
              MediaStore.Video.Media.DURATION
            };
        mediaTable = "Video";
      } else {
        projection =
            new String[] {
              MediaStore.Images.Media.DISPLAY_NAME,
              MediaStore.Images.Media.SIZE,
              MediaStore.Images.Media.DATE_MODIFIED,
              MediaStore.Images.Media.WIDTH,
              MediaStore.Images.Media.HEIGHT
            };
        mediaTable = "Image";
      }

      try (android.database.Cursor cursor =
          getContext().getContentResolver().query(item.uri, projection, null, null, null)) {
        if (cursor != null && cursor.moveToFirst()) {
          int nameIndex =
              cursor.getColumnIndexOrThrow(
                  item.isVideo()
                      ? MediaStore.Video.Media.DISPLAY_NAME
                      : MediaStore.Images.Media.DISPLAY_NAME);
          int sizeIndex =
              cursor.getColumnIndexOrThrow(
                  item.isVideo() ? MediaStore.Video.Media.SIZE : MediaStore.Images.Media.SIZE);
          int dateIndex =
              cursor.getColumnIndexOrThrow(
                  item.isVideo()
                      ? MediaStore.Video.Media.DATE_MODIFIED
                      : MediaStore.Images.Media.DATE_MODIFIED);
          int widthIndex =
              cursor.getColumnIndexOrThrow(
                  item.isVideo() ? MediaStore.Video.Media.WIDTH : MediaStore.Images.Media.WIDTH);
          int heightIndex =
              cursor.getColumnIndexOrThrow(
                  item.isVideo() ? MediaStore.Video.Media.HEIGHT : MediaStore.Images.Media.HEIGHT);

          String name = cursor.getString(nameIndex);
          long size = cursor.getLong(sizeIndex);
          long dateModified = cursor.getLong(dateIndex);
          int width = cursor.getInt(widthIndex);
          int height = cursor.getInt(heightIndex);

          info.append("Type: ").append(mediaTable).append("\n");
          info.append("Name: ").append(name).append("\n");
          info.append("Size: ").append(formatSize(size)).append("\n");
          info.append("Dimensions: ").append(width).append("x").append(height).append("\n");
          info.append("Modified: ").append(formatDate(dateModified));

          if (item.isVideo()) {
            int durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
            long duration = cursor.getLong(durationIndex);
            info.append("\nDuration: ").append(formatDuration(duration));
          }
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "Error getting media info", e);
      info.append("Error loading media info");
    }

    // Show info in Toast for now (simple implementation for phone IDE)
    Toast.makeText(getContext(), info.toString(), Toast.LENGTH_LONG).show();
  }

  private String formatDuration(long milliseconds) {
    if (milliseconds <= 0) return "0:00";

    long seconds = milliseconds / 1000;
    long minutes = seconds / 60;
    long hours = minutes / 60;

    if (hours > 0) {
      return String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60);
    } else {
      return String.format("%d:%02d", minutes, seconds % 60);
    }
  }

  private String formatDate(long timestamp) {
    java.util.Date date = new java.util.Date(timestamp * 1000);
    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm");
    return sdf.format(date);
  }

  private String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(1024));
    String pre = ("KMGTPE").charAt(exp - 1) + "";
    return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
  }

  // NEW METHOD: Load media (image or video) at index
  private void loadMediaAtIndex(int index) {
    if (mediaItems == null || index < 0 || index >= mediaItems.size()) return;

    MediaItems item = mediaItems.get(index);
    currentIndex = index;

    // Save to SharedPreferences
    saveViewerState(item.uri, currentIndex);

    // ALSO send immediate result to PhotosFragment
    sendResultToPhotosFragment(item.uri, currentIndex);

    if (item.isVideo()) {
      // Show video thumbnail with play button
      showVideoThumbnail(item);
    } else {
      // Hide play button for images
      hidePlayButtonOverlay();
      loadImage(item);
    }
  }

  // Add this method:
  private void sendResultToPhotosFragment(Uri uri, int index) {
    Bundle result = new Bundle();
    result.putString(RESULT_URI_KEY, uri.toString());
    result.putInt(RESULT_INDEX_KEY, index);

    // Use Fragment Result API to send to parent
    getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
  }

  // NEW METHOD: Load a single image
  private void loadImage(MediaItems item) {
    Log.d("ImageDebug", "Loading image: " + item.name);
    // Ensure we're in image mode
    isVideoMode = false;
    imageView.setVisibility(View.VISIBLE);

    // Hide play button overlay for images
    hidePlayButtonOverlay();

    // Switch gesture handler to image mode
    if (getActivity() instanceof MainActivity) {
      ((MainActivity) getActivity()).setGestureDelegate(gestureHandler);
    }

    // Reset zoom
    scaleFactor = 1.0f;

    // Clear previous drawable safely
    if (imageView.getDrawable() != null) {
      imageView.getDrawable().setCallback(null);
      imageView.setImageDrawable(null);
    }

    imageView.setScaleType(ImageView.ScaleType.MATRIX);
    imageMatrix = new Matrix();

    // Save viewer state for this image
    saveViewerState(item.uri, currentIndex);

    // Background load: use Glide to fetch cached file
    new Thread(
            () -> {
              WeakReference<ImageView> imageViewRef = new WeakReference<>(imageView);
              WeakReference<TextView> nameTextViewRef = new WeakReference<>(imageNameText);
              WeakReference<FragmentActivity> activityRef =
                  new WeakReference<>((FragmentActivity) getActivity());

              com.bumptech.glide.request.FutureTarget<File> future = null;
              try {
                Context context = getContext();
                if (context == null) return;

                // 1) Ask Glide for a local File (this will use cache if available):
                future =
                    Glide.with(context)
                        .asFile()
                        .load(item.uri)
                        // my favorite option -->	.override(?)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .submit();

                // Wait for file (timeout optional)
                File file = null;
                try {
                  file = future.get(5, TimeUnit.SECONDS);
                } catch (Exception ex) {
                  // fallback: null file -> we'll try direct stream from content
                  // resolver
                  file = null;
                }

                // 2) Decide how to load the image: prefer file when available
                Context ctx = context;
                Drawable drawable = null;

                if (file != null && file.exists()) {
                  // If file is present, use ImageDecoder (API >= 28) or Bitmap
                  // decode
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                      ImageDecoder.Source src = ImageDecoder.createSource(file);
                      drawable =
                          ImageDecoder.decodeDrawable(
                              src,
                              (decoder, info, source) -> {
                                // Get original size
                                int width = info.getSize().getWidth();
                                int height = info.getSize().getHeight();

                                // Apply 4K limit
                                // TODO: add a tile loading object instead of hard code limit.
                                if (width > 4096 || height > 4096) {
                                  float scale = Math.min(4096f / width, 4096f / height);
                                  int targetWidth = (int) (width * scale);
                                  int targetHeight = (int) (height * scale);

                                  decoder.setTargetSize(targetWidth, targetHeight);
                                }
                              });
                    } catch (Throwable t) {
                      drawable = null;
                    }
                  }
                  if (drawable == null) {
                    // Use ImageLoader to decode bitmap from file
                    Bitmap bm =
                        ImageLoader.loadWithLimit(ctx, Uri.fromFile(file), ImageLoader.MAX_FULL);
                  }

                } else {
                  // No file from Glide: detect via MIME and use stream
                  String mimeType = ctx.getContentResolver().getType(item.uri);
                  boolean isGif = mimeType != null && mimeType.equals("image/gif");
                  boolean isWebp = mimeType != null && mimeType.contains("webp");
                  boolean isAnimatedWebp = false;

                  if (isWebp) {
                    try (InputStream is = ctx.getContentResolver().openInputStream(item.uri)) {
                      byte[] header = new byte[12];
                      if (is != null && is.read(header) == 12) {
                        isAnimatedWebp = isAnimatedWebp(header);
                      }
                    } catch (Exception ignored) {
                    }
                  }

                  if (isGif || isAnimatedWebp) {
                    // Use framework decoder from InputStream
                    try (InputStream is = ctx.getContentResolver().openInputStream(item.uri)) {
                      if (is != null) {
                        drawable = Drawable.createFromStream(is, null);
                      }
                    } catch (Exception e) {
                      Log.e(TAG, "stream animated load failed", e);
                    }
                  } else {
                    // static image fallback via ImageLoader
                    Bitmap bm =
                        ImageLoader.loadWithLimit(ctx, Uri.fromFile(file), ImageLoader.MAX_FULL);
                    if (bm != null) {
                      drawable =
                          new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bm);
                    }
                  }
                }

                final Drawable finalDrawable = drawable;
                final FragmentActivity activity = activityRef.get();

                if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                  activity.runOnUiThread(
                      () -> {
                        ImageView imgView = imageViewRef.get();
                        TextView nameView = nameTextViewRef.get();

                        if (imgView == null || nameView == null) return;

                        if (finalDrawable != null) {
                          imgView.setImageDrawable(finalDrawable);

                          // If drawable animatable, start it
                          if (finalDrawable instanceof Animatable) {
                            try {
                              ((Animatable) finalDrawable).start();
                            } catch (Throwable ignored) {
                            }
                          }
                        } else {
                          imgView.setImageResource(R.drawable.ic_broken_image);
                        }

                        // Apply computed initial fit matrix
                        imageMatrix = computeInitialMatrix(imgView);
                        imgView.setImageMatrix(imageMatrix);
                        imgView.setScaleType(ImageView.ScaleType.MATRIX);

                        // Update UI
                        updateIndexDisplay();
                        nameView.setText(item.name);

                        // Preload nearby images
                        preloadNearbyImages();
                      });
                }
              } catch (Exception e) {
                Log.e(TAG, "Error loading image", e);
                FragmentActivity activity = activityRef.get();
                if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                  activity.runOnUiThread(
                      () -> {
                        ImageView imgView = imageViewRef.get();
                        if (imgView != null) {
                          imgView.setImageResource(R.drawable.ic_broken_image);
                        }
                      });
                }
              } finally {
                // Make sure to clear Glide future
                if (future != null) {
                  try {
                    Glide.with(this).clear(future);
                  } catch (Exception ignored) {
                  }
                }
              }
            })
        .start();
  }

  // Show video thumbnail with play button
  private void showVideoThumbnail(MediaItems item) {
    isVideoMode = false;
    imageView.setVisibility(View.VISIBLE);

    // Reset zoom
    scaleFactor = 1.0f;
    imageView.setScaleType(ImageView.ScaleType.MATRIX);
    imageMatrix = new Matrix();

    // KEEP gesture active!
    if (getActivity() instanceof MainActivity) {
      ((MainActivity) getActivity()).setGestureDelegate(gestureHandler);
    }

    // Load video thumbnail in background
    new Thread(
            () -> {
              try {
                Context context = getContext();
                if (context == null) return;

                Bitmap bitmap = Glide.with(context).asBitmap().load(item.uri).submit().get();

                if (getActivity() != null && !getActivity().isFinishing()) {
                  getActivity()
                      .runOnUiThread(
                          () -> {
                            if (imageView != null && bitmap != null) {
                              imageView.setImageBitmap(bitmap);
                              imageMatrix = computeInitialMatrix(imageView);
                              imageView.setImageMatrix(imageMatrix);
                              imageView.setScaleType(ImageView.ScaleType.MATRIX);

                              // Show play button overlay
                              showPlayButtonOverlay();
                            }
                          });
                }
              } catch (Exception e) {
                Log.e(TAG, "Error loading video thumbnail", e);
              }
            })
        .start();

    updateIndexDisplay();
    imageNameText.setText(item.name);
  }

  private View playButtonOverlayView;

  private void showPlayButtonOverlay() {
    if (getActivity() == null || !(getActivity() instanceof MainActivity)) return;

    MainActivity activity = (MainActivity) getActivity();

    // Only create if needed
    if (playButtonOverlayView == null) {
      playButtonOverlayView =
          LayoutInflater.from(requireContext()).inflate(R.layout.overlay_play_button, null);

      ImageView playButton = playButtonOverlayView.findViewById(R.id.playButtonOverlay);
      playButton.setOnClickListener(
          v -> {
            Log.d(TAG, "Play button clicked!");
            openVideoPlayer(currentIndex);

            // Hide the play button when video starts
            hidePlayButtonOverlay();
          });
    }

    // Show it through MainActivity
    activity.showUiOverlay(playButtonOverlayView);
  }

  private void hidePlayButtonOverlay() {
    if (getActivity() instanceof MainActivity) {
      ((MainActivity) getActivity()).hideUiOverlay();
    }
  }

  private void openVideoPlayer(int videoIndex) {
    VideoPlayerFragment videoPlayerFragment = new VideoPlayerFragment();
    Bundle args = new Bundle();

    // Pass all media items and current index
    ArrayList<MediaItems> parcelableList = new ArrayList<>(mediaItems);
    args.putParcelableArrayList("media_items", parcelableList);
    args.putInt("current_index", videoIndex);
    videoPlayerFragment.setArguments(args);

    // Open in full screen overlay
    if (getActivity() instanceof MainActivity) {
      ((MainActivity) getActivity()).openViewer(videoPlayerFragment);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    hideUiElements();
    // Image mode - register gesture handler
    if (getActivity() instanceof MainActivity) {
      ((MainActivity) getActivity()).setGestureDelegate(gestureHandler);
    }
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    // Reset to fit on rotation
    if (imageView != null && imageView.getDrawable() != null) {
      imageMatrix = computeInitialMatrix(imageView);
      imageView.setImageMatrix(imageMatrix);
      imageView.setScaleType(ImageView.ScaleType.MATRIX);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    stopSlideShow();
    hidePlayButtonOverlay();
    if (getActivity() instanceof MainActivity) {
      ((MainActivity) getActivity()).setGestureDelegate(null);
    }

    // FINAL save - use commit() for immediate write
    if (mediaItems != null && currentIndex >= 0 && currentIndex < mediaItems.size()) {
      MediaItems item = mediaItems.get(currentIndex);
      try {
        MainActivity.prefs
            .edit()
            .putString(PREF_KEY_LAST_URI, item.uri.toString())
            .putInt(PREF_KEY_LAST_INDEX, currentIndex)
            .commit(); // Use commit() for immediate write
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    // Clean up
    if (playButtonOverlayView != null) {
      hidePlayButtonOverlay();
      playButtonOverlayView = null;
    }

    //	restoreSystemBars();

    // Cleanup gesture handler
    if (gestureHandler != null) {
      gestureHandler.cleanup();
      gestureHandler = null;
    }

    // Clear image view
    if (imageView != null) {
      if (imageView.getDrawable() != null) {
        imageView.getDrawable().setCallback(null);
        imageView.setImageDrawable(null);
      }
      com.bumptech.glide.Glide.with(this).clear(imageView);
      imageView = null;
    }

    // CRITICAL: Ensure gesture delegate is cleared
    if (getActivity() instanceof MainActivity) {
      ((MainActivity) getActivity()).setGestureDelegate(null);
    }
  }
}
