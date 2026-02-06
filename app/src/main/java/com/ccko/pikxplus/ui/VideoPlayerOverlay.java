package com.ccko.pikxplus.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ccko.pikxplus.MainActivity;
import com.ccko.pikxplus.R;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.material.slider.Slider;

/** Custom video player controls overlay. Handles all UI elements for video playback. */
public class VideoPlayerOverlay extends FrameLayout {

  // Top bar buttons
  private ImageButton backButton;
  private TextView videoTitle;
  private ImageButton orientationButton; // Placeholder
  private ImageButton playlistButton;
  private ImageButton loopButton;
  private ImageButton shuffleButton; // Placeholder
  private ImageButton speedButton;
  private ImageButton lockButton;
  private ImageButton moreButton; // Placeholder

  // Bottom bar buttons
  private ImageButton muteButton;
  private ImageButton previousButton;
  private ImageButton playPauseButton;
  private ImageButton nextButton;
  private ImageButton ratioButton;

  // Seek bar
  private SeekBar seekBar;
  //  private Slider vidSliderBar;
  private TextView currentTime;
  private TextView totalTime;

  private TextView FcurrentTime;
  private TextView FtotalTime;

  // Container views
  private View topBar;
  private View bottomBar;
  //public View floatBar;
  // State
  private boolean isUiLocked = false;
  private boolean isMuted = false;
  private boolean isLooping = false;
  private float currentSpeed = 1.0f;

  // Callbacks
  private ControlListener listener;

  public interface ControlListener {
    void onBackPressed();

    void onPlayPauseToggle();

    void onSeekTo(long positionMs);

    void onMuteToggle();

    void onPreviousVideo();

    void onNextVideo();

    void onLoopToggle();

    void onLockToggle();

    // Placeholder callbacks
    void onOrientationToggle();

    void onPlaylistOpen();

    void onShuffleToggle();

    void onSpeedChange();

    void onRatioChange();

    void onMoreOptions();
  }

  public VideoPlayerOverlay(@NonNull Context context) {
    super(context);
    init(context);
  }

  public VideoPlayerOverlay(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  private void init(Context context) {
    LayoutInflater.from(context).inflate(R.layout.video_player_overlay, this, true);

    // Find views
    topBar = findViewById(R.id.videoTopBar);
    bottomBar = findViewById(R.id.videoBottomBar);
   // floatBar = findViewById(R.id.floatBar);

    // Top bar
    backButton = findViewById(R.id.videoBackButton);
    videoTitle = findViewById(R.id.videoTitle);
    orientationButton = findViewById(R.id.orientationButton);
    playlistButton = findViewById(R.id.playlistButton);
    loopButton = findViewById(R.id.loopButton);
    shuffleButton = findViewById(R.id.shuffleButton);
    speedButton = findViewById(R.id.speedButton);
    lockButton = findViewById(R.id.lockButton);
    moreButton = findViewById(R.id.moreButton);

    // Bottom bar
    muteButton = findViewById(R.id.muteButton);
    previousButton = findViewById(R.id.previousButton);
    playPauseButton = findViewById(R.id.playPauseButton);
    nextButton = findViewById(R.id.nextButton);
    ratioButton = findViewById(R.id.ratioButton);

    // Seek bar
    // vidSliderBar = findViewById(R.id.vidSliderBar);
    seekBar = findViewById(R.id.videoSeekBar);
    currentTime = findViewById(R.id.currentTime);
    totalTime = findViewById(R.id.totalTime);
    FcurrentTime = findViewById(R.id.FcurrentTime);
    FtotalTime = findViewById(R.id.FtotalTime);
    setupListeners();
    updateButtonStates();
  }

  private void setupListeners() {
    backButton.setOnClickListener(
        v -> {
          if (listener != null) listener.onBackPressed();
        });

    playPauseButton.setOnClickListener(
        v -> {
          if (!isUiLocked && listener != null) listener.onPlayPauseToggle();
        });

    muteButton.setOnClickListener(
        v -> {
          if (!isUiLocked && listener != null) {
            isMuted = !isMuted;
            listener.onMuteToggle();
            updateButtonStates();
          }
        });

    previousButton.setOnClickListener(
        v -> {
          if (!isUiLocked && listener != null) listener.onPreviousVideo();
        });

    nextButton.setOnClickListener(
        v -> {
          if (!isUiLocked && listener != null) listener.onNextVideo();
        });

    loopButton.setOnClickListener(
        v -> {
          if (!isUiLocked && listener != null) {
            isLooping = !isLooping;
            listener.onLoopToggle();
            updateButtonStates();
          }
        });

    lockButton.setOnClickListener(
        v -> {
          isUiLocked = !isUiLocked;
          if (listener != null) listener.onLockToggle();
          updateButtonStates();
        });

    // Placeholder buttons
    orientationButton.setOnClickListener(
        v -> {
          if (!isUiLocked && listener != null) listener.onOrientationToggle();
        });

    playlistButton.setOnClickListener(
        v -> {
          if (!isUiLocked && listener != null) listener.onPlaylistOpen();
        });

    shuffleButton.setOnClickListener(
        v -> {
          if (!isUiLocked && listener != null) listener.onShuffleToggle();
        });

    speedButton.setOnClickListener(
        v -> {
          if (!isUiLocked && listener != null) listener.onSpeedChange();
        });

    ratioButton.setOnClickListener(
        v -> {
          if (!isUiLocked && listener != null) listener.onRatioChange();
        });

    moreButton.setOnClickListener(
        v -> {
          if (!isUiLocked && listener != null) listener.onMoreOptions();
        });

    // Seek bar
    //	seekBar.addOnChangeListener

    seekBar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && !isUiLocked) {
              currentTime.setText(formatTime(progress));
            //  FcurrentTime.setText(formatTime(progress));
            }
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {
            // Pause auto-hide while seeking
          }

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {
            if (!isUiLocked && listener != null) {
              listener.onSeekTo(seekBar.getProgress());
            }
          }
        });

    /*
      vidSliderBar.addOnChangeListener(
          new Slider.OnChangeListener() {
            @Override
            public void onValueChange(Slider slider, float value, boolean fromUser) {
              if (fromUser && !isUiLocked) {
                currentTime.setText(formatTime((long) value));
                FcurrentTime.setText(formatTime((long) value));
              }
            }

              public void onStartTrackingTouch(Slider slider) {
                // Pause auto-hide while seeking
              }

              public void onStopTrackingTouch(Slider slider) {
                if (!isUiLocked && listener != null) {
                  listener.onSeekTo((int) slider.getValue());
                }
              }

            public void onSliderTouch(Slider slider, boolean isTouch) {
              if (isTouch) {
                // Pause auto-hide while seeking
              } else {
                // User released the slider
                if (!isUiLocked && listener != null) {
                  listener.onSeekTo((int) slider.getValue());
                }
              }
            }

          });
    */
  }

  private void updateButtonStates() {
    // Mute button
    muteButton.setImageResource(isMuted ? R.drawable.ic_volume_off : R.drawable.ic_volume_on);
    muteButton.setSelected(isMuted);
    // Loop button
    loopButton.setImageResource(isLooping ? R.drawable.ic_loop_on : R.drawable.ic_loop_off);
    loopButton.setSelected(isLooping);
    // Lock button
    lockButton.setImageResource(isUiLocked ? R.drawable.ic_lock : R.drawable.ic_unlock);
    lockButton.setSelected(isUiLocked);

    // Disable/enable buttons when locked
    int alpha = isUiLocked ? 64 : 255;
    playPauseButton.setAlpha(alpha / 255f);
    muteButton.setAlpha(alpha / 255f);
    previousButton.setAlpha(alpha / 255f);
    bottomBar.setAlpha(alpha / 255f);
    nextButton.setAlpha(alpha / 255f);
    // vidSliderBar.setEnabled(!isUiLocked);
  }

  // toolbar button checkers
  public boolean isShowing() {
    return topBar.getVisibility() == VISIBLE || bottomBar.getVisibility() == VISIBLE;
    //    || floatBar.getVisibility() == GONE;
  }

  public boolean isUiLocked() {
    return isUiLocked;
  }

  public void setUiLocked(boolean locked) {
    isUiLocked = locked;
    updateButtonStates();
  }

  public void setMuted(boolean muted) {
    isMuted = muted;
    updateButtonStates();
  }

  public void setLooping(boolean looping) {
    isLooping = looping;
    updateButtonStates();
  }

  public void setSpeedActive(boolean active) {
    if (speedButton != null) {
      speedButton.setSelected(active);
      //    speedButton.setImageAlpha(active ? 255 : 150);
    }
  }

  public void setControlListener(ControlListener listener) {
    this.listener = listener;
  }

  public void setVideoTitle(String title) {
    videoTitle.setText(title);
  }

  public void updatePlayPauseButton(boolean isPlaying) {
    playPauseButton.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
  }

  public void updateProgress(long currentMs, long durationMs) {
    if (durationMs > 0) {
      seekBar.setMax((int) durationMs);
      seekBar.setProgress((int) currentMs);

      currentTime.setText(formatTime(currentMs));
      totalTime.setText(formatTime(durationMs));

      //  FcurrentTime.setText(formatTime(currentMs));
      //  FtotalTime.setText(formatTime(durationMs));
    }
  }

  /*
    public void updateProgress(long currentMs, long durationMs) {
      if (durationMs > 0) {
        vidSliderBar.setValueFrom(0);
        vidSliderBar.setValueTo(durationMs);
        vidSliderBar.setValue(currentMs);

        currentTime.setText(formatTime(currentMs));
        totalTime.setText(formatTime(durationMs));

    //    FcurrentTime.setText(formatTime(currentMs));
    //    FtotalTime.setText(formatTime(durationMs));
      }
    }
  */
  public void show() {
    if (!isUiLocked) {
      topBar.setVisibility(VISIBLE);
      bottomBar.setVisibility(VISIBLE);

    } else {
      // When locked, only show lock button
      topBar.setVisibility(GONE);
      bottomBar.setVisibility(GONE);
      lockButton.setVisibility(VISIBLE);
    }
  }

  public void hide() {
    topBar.setVisibility(GONE);
    bottomBar.setVisibility(GONE);

    if (!isUiLocked) {
      lockButton.setVisibility(VISIBLE);
    }
  }

  public String formatTime(long milliseconds) {
    long totalSeconds = milliseconds / 1000;
    long hours = totalSeconds / 3600;
    long minutes = (totalSeconds % 3600) / 60;
    long seconds = totalSeconds % 60;

    if (hours > 0) {
      return String.format("%d:%02d:%02d", hours, minutes, seconds);
    } else {
      return String.format("%d:%02d", minutes, seconds);
    }
  }

  public void updateSeekBar(ExoPlayer player) {
    if (player != null) {
      updateProgress(player.getCurrentPosition(), player.getDuration());
    }
  }

  public void updatePreviewPosition(long previewMs, long durationMs) {
    if (durationMs > 0) {
      seekBar.setMax((int) durationMs);
      // vidSliderBar.setValueTo(durationMs);
    }
    seekBar.setProgress((int) previewMs);
    //    vidSliderBar.setValue(previewMs);
    currentTime.setText(formatTime(previewMs));
    //  FcurrentTime.setText(formatTime(previewMs));
  }

  public void enableButton(int buttonId, boolean enabled) {
    View button = findViewById(buttonId);
    if (button != null) {
      button.setEnabled(enabled);
      button.setAlpha(enabled ? 1.0f : 0.75f);
    }
  }
}
