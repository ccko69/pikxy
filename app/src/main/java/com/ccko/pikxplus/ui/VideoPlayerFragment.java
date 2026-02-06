package com.ccko.pikxplus.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.ccko.pikxplus.MainActivity;
import com.ccko.pikxplus.R;
import com.ccko.pikxplus.adapters.MediaItems;
import com.ccko.pikxplus.features.GestureAndSlideShow;
import com.ccko.pikxplus.utils.FloatingWindowManager;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen video player with ExoPlayer. Opened when user taps play button on video thumbnail in
 * ViewerFragment.
 */
public class VideoPlayerFragment extends Fragment {

  private static final String TAG = "VideoPlayerFragment";

  // Views
  private StyledPlayerView playerView;
  private VideoPlayerOverlay videoControls;

  // ExoPlayer
  private ExoPlayer exoPlayer;

  // Gesture handler
  private GestureAndSlideShow.VideoPlayerGestureHandler videoGestureHandler;

  // Audio/brightness
  private AudioManager audioManager;
  private int maxVolume;
  private float currentBrightness = -1f;
  // Anchors for gestures
  private int volumeAnchor = -1;
  private float brightnessAnchor = -1f;

  // Progress updater
  private Handler controlsHandler = new Handler(Looper.getMainLooper());
  private Runnable progressUpdater;
  private boolean progressUpdatesPaused = false;
  private boolean wasPlayingBeforeSeek = false;

  // Data
  private List<MediaItems> mediaItems;
  private int currentIndex = 0;

  // floating windows
  private FloatingWindowManager floatingWindow;
  private FloatingWindowManager volumeOverlay;
  private FloatingWindowManager brightnessOverlay;
  private PopupWindow speedPopup;

  // rotation
  private int originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

  private int rotationSteps = 0;
  private static final int ROTATION_DURATION = 500;

  // Persistent zoom/pan state
  private float currentScale = 1.0f;
  private float translationX = 0f;
  private float translationY = 0f;

  // Clamp constants
  private static final float MAX_SCALE = 20.0f;
  private static final float MIN_SCALE_DEFAULT = 1.0f; // baseline
  private static final float MIN_SCALE_FALLBACK = 0.25f; // for large videos
  private static final float PAN_LIMIT_RATIO = 0.5f; // half screen

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {

    View view = inflater.inflate(R.layout.fragment_video_player, container, false);

    // Find views
    playerView = view.findViewById(R.id.videoPlayerView);
    videoControls = view.findViewById(R.id.videoControls);

    // Audio manager for volume control
    audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
    maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

    // Initialize floating window managers
    floatingWindow = new FloatingWindowManager(requireContext(), view);
    volumeOverlay = new FloatingWindowManager(requireContext(), view);
    brightnessOverlay = new FloatingWindowManager(requireContext(), view);

    // Set dismiss callback for main floating window
    floatingWindow.setOnDismissCallback(
        () -> {
          if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setGestureDelegate(videoGestureHandler);
          }
        });

    // Initialize video gesture handler
    videoGestureHandler =
        new GestureAndSlideShow.VideoPlayerGestureHandler(
            requireContext(),
            new GestureAndSlideShow.VideoPlayerGestureHandler.HostCallback() {
              @Override
              public void onSingleTap() {
                toggleVideoControls();
              }

              @Override
              public void onDoubleTap() {}

              @Override
              public void onSeek(long deltaMs) {
                if (exoPlayer != null) {
                  long newPos = exoPlayer.getCurrentPosition() + deltaMs;
                  newPos = Math.max(0, Math.min(newPos, exoPlayer.getDuration()));
                  exoPlayer.seekTo(newPos);
                }
                progressUpdatesPaused = false;
              }

              @Override
              public void onVolumeChange(float delta) {
                int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int newVol = currentVol + (int) (delta * maxVolume);
                newVol = Math.max(0, Math.min(newVol, maxVolume));
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0);
                // Show volume overlay with current level
                showVolumeOverlay(newVol);
              }

              @Override
              public void onBrightnessChange(float delta) {
                WindowManager.LayoutParams layoutParams =
                    requireActivity().getWindow().getAttributes();
                if (currentBrightness < 0) {
                  currentBrightness = layoutParams.screenBrightness;
                  if (currentBrightness < 0) currentBrightness = 0.5f;
                }
                currentBrightness += delta;
                currentBrightness = Math.max(0.01f, Math.min(currentBrightness, 1.0f));
                layoutParams.screenBrightness = currentBrightness;
                requireActivity().getWindow().setAttributes(layoutParams);
                // Show brightness overlay with current level
                showBrightnessOverlay(currentBrightness);
              }

              @Override
              public void onPinchZoom(float scaleFactor) {
                applyScale(scaleFactor);
              }

              @Override
              public void onPan(float dx, float dy) {
                applyPan(dx, dy);
              }

              @Override
              public long getPlayerPosition() {
                return exoPlayer != null ? exoPlayer.getCurrentPosition() : 0L;
              }

              @Override
              public long getPlayerDuration() {
                if (exoPlayer == null) return -1L;
                long d = exoPlayer.getDuration();
                return d <= 0 ? -1L : d;
              }

              @Override
              public void onSeekPreview(long previewPositionMs) {
                progressUpdatesPaused = true;
                if (videoControls != null) {
                  videoControls.updatePreviewPosition(previewPositionMs, getPlayerDuration());
                }
                if (exoPlayer != null) {
                  exoPlayer.seekTo(previewPositionMs);
                }
              }

              @Override
              public void pausePlayerProgressUpdates() {
                progressUpdatesPaused = true;
                if (exoPlayer != null && exoPlayer.isPlaying()) {
                  exoPlayer.pause();
                  wasPlayingBeforeSeek = true;
                }
              }

              @Override
              public void resumePlayerProgressUpdates() {
                progressUpdatesPaused = false;
                if (exoPlayer != null && wasPlayingBeforeSeek) {
                  exoPlayer.play();
                  wasPlayingBeforeSeek = false;
                }
              }
            });

    // Setup video controls listeners
    setupVideoControls();

    return view;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    videoControls = view.findViewById(R.id.videoControls);
    // Register gesture handler with MainActivity
    if (getActivity() instanceof MainActivity) {
      ((MainActivity) getActivity()).setGestureDelegate(videoGestureHandler);
    }

    // Get arguments
    Bundle args = getArguments();
    if (args != null) {
      mediaItems = args.getParcelableArrayList("media_items");
      currentIndex = args.getInt("current_index", 0);

      if (mediaItems != null && currentIndex >= 0 && currentIndex < mediaItems.size()) {
        MediaItems item = mediaItems.get(currentIndex);
        if (item.isVideo()) {
          playVideo(item);
        } else {
          Toast.makeText(getContext(), "Not a video!", Toast.LENGTH_SHORT).show();
          requireActivity().onBackPressed();
        }
      } else {
        Toast.makeText(getContext(), "Invalid video", Toast.LENGTH_SHORT).show();
        requireActivity().onBackPressed();
      }
    }
  }

  private void setupVideoControls() {
    videoControls.setControlListener(
        new VideoPlayerOverlay.ControlListener() {
          @Override
          public void onBackPressed() {
            requireActivity().onBackPressed();
          }

          @Override
          public void onPlayPauseToggle() {
            if (exoPlayer != null) {
              if (exoPlayer.isPlaying()) {
                exoPlayer.pause();
              } else {
                exoPlayer.play();
              }
            }
          }

          @Override
          public void onSeekTo(long positionMs) {
            if (exoPlayer != null) {
              exoPlayer.seekTo(positionMs);
            }
          }

          @Override
          public void onMuteToggle() {
            if (exoPlayer != null) {
              float newVolume = exoPlayer.getVolume() > 0 ? 0f : 1f;
              exoPlayer.setVolume(newVolume);
              videoControls.setMuted(newVolume == 0);
            }
          }

          @Override
          public void onPreviousVideo() {
            loadPreviousVideo();
          }

          @Override
          public void onNextVideo() {
            loadNextVideo();
          }

          @Override
          public void onLoopToggle() {
            if (exoPlayer != null) {
              boolean looping = exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ONE;
              exoPlayer.setRepeatMode(looping ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ONE);
              videoControls.setLooping(!looping);
            }
          }

          @Override
          public void onLockToggle() {
            // Controls already handle showing/hiding
          }

          @Override
          public void onOrientationToggle() {
            ((MainActivity) getActivity()).toggleOrientation();
          }

          @Override
          public void onPlaylistOpen() {
            showPlaylistWindow();
          }

          @Override
          public void onShuffleToggle() {
            Toast.makeText(getContext(), "Shuffle: Coming soon", Toast.LENGTH_SHORT).show();
          }

          @Override
          public void onSpeedChange() {
            showSpeedWindow();
          }

          @Override
          public void onRatioChange() {
            if (playerView != null) {
              int currentMode = playerView.getResizeMode();
              int nextMode;

              currentScale = 1.0f;
              translationX = 0f;
              translationY = 0f;
              playerView.setScaleX(1.0f);
              playerView.setScaleY(1.0f);
              playerView.setTranslationX(0f);
              playerView.setTranslationY(0f);

              if (currentMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                nextMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                Toast.makeText(getContext(), "Crop", Toast.LENGTH_SHORT).show();
              } else if (currentMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                nextMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                Toast.makeText(getContext(), "Stretch", Toast.LENGTH_SHORT).show();
              } else {

                nextMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                Toast.makeText(getContext(), "Fit", Toast.LENGTH_SHORT).show();
              }

              playerView.setResizeMode(nextMode);
            }
          }

          @Override
          public void onMoreOptions() {
            Toast.makeText(getContext(), "More options: Coming soon", Toast.LENGTH_SHORT).show();
          }
        });
  }

  private String formatTime(long ms) {
    long totalSeconds = ms / 1000;
    long minutes = totalSeconds / 60;
    long seconds = totalSeconds % 60;
    return String.format("%02d:%02d", minutes, seconds);
  }

  private void playVideo(MediaItems item) {
    // Initialize ExoPlayer if needed
    if (exoPlayer == null) {
      exoPlayer = new ExoPlayer.Builder(requireContext()).build();
      playerView.setPlayer(exoPlayer);

      // Listen for player state changes
      exoPlayer.addListener(
          new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
              if (playbackState == Player.STATE_ENDED) {
                // Video finished - could auto-advance
                loadNextVideo();
              }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
              videoControls.updatePlayPauseButton(isPlaying);
              if (isPlaying) {
                startProgressUpdater();
              } else {
                stopProgressUpdater();
              }
            }
          });
    }

    // Load video
    MediaItem mediaItem = MediaItem.fromUri(item.uri);
    exoPlayer.setMediaItem(mediaItem);
    exoPlayer.prepare();
    exoPlayer.play();

    // Update UI
    videoControls.setVideoTitle(item.name);
    videoControls.show();
  }

  private void loadNextVideo() {
    if (mediaItems == null || currentIndex >= mediaItems.size() - 1) return;

    // Find next video in list
    for (int i = currentIndex + 1; i < mediaItems.size(); i++) {
      if (mediaItems.get(i).isVideo()) {
        currentIndex = i;
        playVideo(mediaItems.get(i));
        return;
      }
    }

    Toast.makeText(getContext(), "No more videos", Toast.LENGTH_SHORT).show();
  }

  private void loadPreviousVideo() {
    if (mediaItems == null || currentIndex <= 0) return;

    // Find previous video in list
    for (int i = currentIndex - 1; i >= 0; i--) {
      if (mediaItems.get(i).isVideo()) {
        currentIndex = i;
        playVideo(mediaItems.get(i));
        return;
      }
    }

    Toast.makeText(getContext(), "No previous videos", Toast.LENGTH_SHORT).show();
  }

  // gesture - onTap
  private void toggleVideoControls() {

    if (videoControls.isShowing()) {

      videoControls.hide();

      // edge to edge
      Activity a = getActivity();
      if (a instanceof MainActivity) {
        ((MainActivity) a).setViewerModeEnabled(true);
      }

    } else {
      videoControls.show();
    }
  }

  // gesture - pinch to zoom
  private void applyScale(float scaleFactor) {
    // Update scale
    currentScale *= scaleFactor;

    // Choose min scale: 1.0x normally; allow 0.25x fallback for big videos
    float minScale = shouldAllowQuarterScaleFallback() ? MIN_SCALE_FALLBACK : MIN_SCALE_DEFAULT;

    // Clamp
    if (currentScale < minScale) currentScale = minScale;
    if (currentScale > MAX_SCALE) currentScale = MAX_SCALE;

    // Apply
    playerView.setScaleX(currentScale);
    playerView.setScaleY(currentScale);

    // Re-apply translation to preserve pan
    playerView.setTranslationX(translationX);
    playerView.setTranslationY(translationY);
  }

  private boolean shouldAllowQuarterScaleFallback() {
    // Heuristic: if video is larger than player bounds, allow 0.25x
    // You can refine using actual video size if available.
    View v = playerView;
    return v != null && (v.getWidth() > 0 && v.getHeight() > 0);
  }

  // gesture - panning
  public void applyPan(float dx, float dy) {
    translationX += dx;
    translationY += dy;
    clampPan();
    playerView.setTranslationX(translationX);
    playerView.setTranslationY(translationY);
  }

  public float getCurrentScale() {
    return this.currentScale;
  }

  private void clampPan() {
    // Limit pan to half the screen in each axis
    DisplayMetrics dm = getResources().getDisplayMetrics();
    float maxX = dm.widthPixels * PAN_LIMIT_RATIO;
    float maxY = dm.heightPixels * PAN_LIMIT_RATIO;

    if (translationX > maxX) translationX = maxX;
    if (translationX < -maxX) translationX = -maxX;
    if (translationY > maxY) translationY = maxY;
    if (translationY < -maxY) translationY = -maxY;
  }

  private void startProgressUpdater() {
    if (progressUpdater == null) {
      progressUpdater =
          new Runnable() {
            @Override
            public void run() {
              // Fixed: No longer using the redundant updateSeekBar method
              if (!progressUpdatesPaused && exoPlayer != null && videoControls != null) {
                videoControls.updateProgress(
                    exoPlayer.getCurrentPosition(), exoPlayer.getDuration());
              }
              controlsHandler.postDelayed(this, 500);
            }
          };
    }
    controlsHandler.removeCallbacks(progressUpdater);
    controlsHandler.post(progressUpdater);
  }

  private void stopProgressUpdater() {
    if (progressUpdater != null) {
      controlsHandler.removeCallbacks(progressUpdater);
      progressUpdater = null;
    }
  }

  // Show playlist window
  private void showPlaylistWindow() {
    Log.d("VideoPlayer", "showPlaylistWindow called");
    if (floatingWindow.isShowing()) {
      floatingWindow.dismiss();
      return;
    }
    floatingWindow.showTyped(
        FloatingWindowManager.TYPE_PLAYLIST,
        R.layout.floating_playlist,
        content -> setupPlaylistLogic(content));
  }

  // Setup playlist logic
  private void setupPlaylistLogic(View container) {
    RecyclerView recyclerView = container.findViewById(R.id.playlistRecyclerView);
    TextView countText = container.findViewById(R.id.playlistCount);

    if (mediaItems == null || mediaItems.isEmpty()) {
      countText.setText("0 videos");
      return;
    }

    // Filter only videos
    List<MediaItems> videos = new ArrayList<>();
    for (MediaItems item : mediaItems) {
      if (item.isVideo()) {
        videos.add(item);
      }
    }

    countText.setText(videos.size() + " video" + (videos.size() != 1 ? "s" : ""));

    // Setup RecyclerView
    recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
    PlaylistAdapter adapter = new PlaylistAdapter(videos, currentIndex);
    recyclerView.setAdapter(adapter);

    // Scroll to current video
    recyclerView.scrollToPosition(Math.max(0, currentIndex));
  }

  // Playlist adapter
  private class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.VideoViewHolder> {
    private List<MediaItems> videos;
    private int currentPlayingIndex;

    public PlaylistAdapter(List<MediaItems> videos, int currentIndex) {
      this.videos = videos;
      this.currentPlayingIndex = currentIndex;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View view =
          LayoutInflater.from(parent.getContext())
              .inflate(R.layout.item_playlist_video, parent, false);
      return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
      MediaItems video = videos.get(position);
      holder.bind(video, position == currentPlayingIndex);
    }

    @Override
    public int getItemCount() {
      return videos.size();
    }

    class VideoViewHolder extends RecyclerView.ViewHolder {
      ImageView thumbnail;
      TextView name;
      TextView size;
      ImageView playingIndicator;

      public VideoViewHolder(@NonNull View itemView) {
        super(itemView);
        thumbnail = itemView.findViewById(R.id.videoThumbnail);
        name = itemView.findViewById(R.id.videoName);
        size = itemView.findViewById(R.id.videoSize);
        playingIndicator = itemView.findViewById(R.id.playingIndicator);

        itemView.setOnClickListener(
            v -> {
              int position = getAdapterPosition();
              if (position != RecyclerView.NO_POSITION) {
                // Find actual index in mediaItems
                int actualIndex = findActualIndex(videos.get(position));
                if (actualIndex >= 0) {
                  currentIndex = actualIndex;
                  playVideo(videos.get(position));
                  floatingWindow.dismiss();
                }
              }
            });
      }

      public void bind(MediaItems video, boolean isPlaying) {
        name.setText(video.name);
        size.setText(formatSize(video.size));
        playingIndicator.setVisibility(isPlaying ? View.VISIBLE : View.GONE);

        // Load thumbnail
        com.bumptech.glide.Glide.with(itemView.getContext())
            .load(video.uri)
            .override(160, 90)
            .centerCrop()
            .into(thumbnail);
      }
    }

    private int findActualIndex(MediaItems video) {
      if (mediaItems == null) return -1;
      for (int i = 0; i < mediaItems.size(); i++) {
        if (mediaItems.get(i).uri.equals(video.uri)) {
          return i;
        }
      }
      return -1;
    }
  }

  // gesture - Show volume overlay
  private void showVolumeOverlay(int volume) {
    int percentage = (int) ((volume / (float) maxVolume) * 100);

    volumeOverlay.showOrUpdate(
        FloatingWindowManager.TYPE_VOLUME,
        R.layout.floating_volume,
        content -> {
          SeekBar seekBar = content.findViewById(R.id.volumeSeekBar);
          TextView text = content.findViewById(R.id.volumeText);
          ImageView icon = content.findViewById(R.id.volumeIcon);

          // Update UI to show current volume
          seekBar.setMax(100);
          seekBar.setProgress(percentage);
          seekBar.setEnabled(true); // read-only = false
          text.setText(percentage + "%");

          // Update icon based on level
          if (percentage == 0) {
            icon.setImageResource(R.drawable.ic_volume_off);
          } else {
            icon.setImageResource(R.drawable.ic_volume_on);
          }
        });
  }

  // gesture - Show brightness overlay
  private void showBrightnessOverlay(float brightness) {
    int percentage = (int) (brightness * 100);

    brightnessOverlay.showOrUpdate(
        FloatingWindowManager.TYPE_BRIGHTNESS,
        R.layout.floating_brightness,
        content -> {
          SeekBar seekBar = content.findViewById(R.id.brightnessSeekBar);
          TextView text = content.findViewById(R.id.brightnessText);

          // Update UI to show current brightness
          seekBar.setMax(100);
          seekBar.setProgress(percentage);
          seekBar.setEnabled(true); // read-only = false
          text.setText(percentage + "%");
        });
  }

  // gesture - Show timer overlay
  private void showTimerOverlay(float brightness) {

    brightnessOverlay.showOrUpdate(
        FloatingWindowManager.TYPE_TIMER,
        R.layout.floating_timer,
        content -> {
          View floatBar = content.findViewById(R.id.floatBar);
          TextView currentTime = content.findViewById(R.id.FcurrentTime);
          TextView totalTime = content.findViewById(R.id.FtotalTime);
        });
  }

  // HELPER METHOD
  private String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(1024));
    String pre = ("KMGTPE").charAt(exp - 1) + "";
    return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
  }

  private void showSpeedWindow() {
    if (floatingWindow.isShowing()) {
      floatingWindow.dismiss();
      return;
    }

    floatingWindow.showTyped(
        FloatingWindowManager.TYPE_SPEED,
        R.layout.floating_speed,
        content -> setupSpeedWindowLogic(content));
  }

  // Apply rotation to imageView
  private void applyRotationAnimated() {
    if (playerView == null) return;

    float targetAngle = rotationSteps * 90f;
    float currentAngle = playerView.getRotation();

    // 1. Animate rotation
    ObjectAnimator rotationAnim =
        ObjectAnimator.ofFloat(playerView, "rotation", currentAngle, targetAngle);
    rotationAnim.setDuration(ROTATION_DURATION); // 500
    rotationAnim.setInterpolator(new DecelerateInterpolator());
    rotationAnim.start();

    // 2. ANIMATE CLAMP (runs in parallel with rotation)
    animateClamp();
  }

  // Smooth animated clamp
  private void animateClamp() {
    View parent = (View) playerView.getParent();
    if (parent == null || parent.getWidth() == 0 || parent.getHeight() == 0) {
      // Parent not ready → retry after layout
      parent.post(this::animateClamp);
      return;
    }

    // Get current dimensions
    ViewGroup.LayoutParams lp = playerView.getLayoutParams();
    int startWidth = lp.width;
    int startHeight = lp.height;

    // Calculate target dimensions
    int pw = parent.getWidth();
    int ph = parent.getHeight();
    boolean swap = (rotationSteps % 2) != 0;
    int targetWidth = swap ? ph : pw;
    int targetHeight = swap ? pw : ph;

    // Skip if no change needed
    if (startWidth == targetWidth && startHeight == targetHeight) return;

    // Animate width/height change over ROTATION_DURATION
    ValueAnimator layoutAnim = ValueAnimator.ofFloat(0f, 1f);
    layoutAnim.setDuration(ROTATION_DURATION);
    layoutAnim.setInterpolator(new DecelerateInterpolator());
    layoutAnim.addUpdateListener(
        animation -> {
          float fraction = animation.getAnimatedFraction();
          int animatedWidth = (int) (startWidth + (targetWidth - startWidth) * fraction);
          int animatedHeight = (int) (startHeight + (targetHeight - startHeight) * fraction);

          ViewGroup.LayoutParams animLp = playerView.getLayoutParams();
          animLp.width = animatedWidth;
          animLp.height = animatedHeight;
          playerView.setLayoutParams(animLp);
        });
    layoutAnim.start();
  }

  // ======================

  private void setupSpeedWindowLogic(View container) {
    SeekBar speedSeekBar = container.findViewById(R.id.speedSeekBar);
    TextView speedValueText = container.findViewById(R.id.speedValueText);
    ImageButton minusBtn = container.findViewById(R.id.speedMinusBtn);
    ImageButton plusBtn = container.findViewById(R.id.speedPlusBtn);

    final int MIN_PROGRESS = 0; // 0 -> 0.5x
    final int MAX_PROGRESS = 150; // 150 -> 2.0x
    final int STEP = 5; // 25 -> 0.25x step

    speedSeekBar.setMax(MAX_PROGRESS);

    // helpers
    final java.util.function.IntFunction<Float> progressToSpeed = p -> 0.5f + (p / 100f);
    final java.util.function.Function<Float, Integer> speedToProgress =
        s -> Math.round((s - 0.5f) * 100f);

    // apply speed and update UI
    Runnable applySpeedFromProgress =
        () -> {
          int progress = speedSeekBar.getProgress();
          // snap to STEP
          int snapped = Math.round(progress / (float) STEP) * STEP;
          if (snapped != progress) speedSeekBar.setProgress(snapped);
          float newSpeed = progressToSpeed.apply(speedSeekBar.getProgress());

          // update text
          speedValueText.setText(String.format(Locale.getDefault(), "%.2fx", newSpeed));

          // apply to player
          if (exoPlayer != null) {
            try {
              exoPlayer.setPlaybackParameters(
                  new com.google.android.exoplayer2.PlaybackParameters(newSpeed));
            } catch (Exception ignored) {
            }
          }

          // update overlay button state
          if (videoControls != null) {
            videoControls.setSpeedActive(Math.abs(newSpeed - 1.0f) > 0.001f);
          }
        };

    // initialize from player
    float currentSpeed = 1.0f;
    if (exoPlayer != null) {
      try {
        currentSpeed = exoPlayer.getPlaybackParameters().speed;
      } catch (Exception ignored) {
      }
    }
    int initialProgress = speedToProgress.apply(currentSpeed);
    initialProgress = Math.max(MIN_PROGRESS, Math.min(MAX_PROGRESS, initialProgress));
    initialProgress = Math.round(initialProgress / (float) STEP) * STEP;
    speedSeekBar.setProgress(initialProgress);
    applySpeedFromProgress.run();

    // SeekBar listener
    speedSeekBar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // always apply so programmatic changes also take effect
            applySpeedFromProgress.run();
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {
            stopProgressUpdater();
          }

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {
            startProgressUpdater();
          }
        });

    // +/- buttons: change progress by STEP and apply immediately
    minusBtn.setOnClickListener(
        v -> {
          int p = speedSeekBar.getProgress() - STEP;
          p = Math.max(MIN_PROGRESS, p);
          speedSeekBar.setProgress(p);
          applySpeedFromProgress.run();
        });

    plusBtn.setOnClickListener(
        v -> {
          int p = speedSeekBar.getProgress() + STEP;
          p = Math.min(MAX_PROGRESS, p);
          speedSeekBar.setProgress(p);
          applySpeedFromProgress.run();
        });
  }

  @Override
  public void onResume() {
    super.onResume();
    if (getActivity() instanceof MainActivity) {
      ((MainActivity) getActivity()).setGestureDelegate(videoGestureHandler);
    }
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    // Setup video controls listeners
  }

  @Override
  public void onPause() {
    super.onPause();

    // Pause video
    if (exoPlayer != null) {
      exoPlayer.pause();
      // TODO: Save playback position to prefs
    }

    if (getActivity() instanceof MainActivity) {
      ((MainActivity) getActivity()).hideUiOverlay();
    }

    // Unregister gesture handler
    if (getActivity() instanceof MainActivity) {
      ((MainActivity) getActivity()).setGestureDelegate(null);
    }
  }

  // UPDATE onDestroyView() to cleanup floating windows
  @Override
  public void onDestroyView() {
    super.onDestroyView();

    stopProgressUpdater();

    if (getActivity() instanceof MainActivity) {
      ((MainActivity) getActivity()).hideUiOverlay();
    }

    // Dismiss all floating windows
    if (floatingWindow != null) {
      floatingWindow.dismiss();
    }
    if (volumeOverlay != null) {
      volumeOverlay.dismiss();
    }
    if (brightnessOverlay != null) {
      brightnessOverlay.dismiss();
    }

    if (playerView != null) {
      playerView.clearAnimation(); // Cancel rotation animation
      // Cancel clamp animation (if any)
      playerView.animate().cancel();
    }

    // Release player
    if (exoPlayer != null) {
      exoPlayer.release();
      exoPlayer = null;
    }

    // Cleanup gesture handler
    if (videoGestureHandler != null) {
      videoGestureHandler.cleanup();
    }

    // Restore brightness
    if (currentBrightness >= 0) {
      WindowManager.LayoutParams layoutParams = requireActivity().getWindow().getAttributes();
      layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
      requireActivity().getWindow().setAttributes(layoutParams);
    }
  }
}
