package com.ccko.pikxplus;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import com.ccko.pikxplus.adapters.MainFragmentAdapter;
import com.ccko.pikxplus.ui.AlbumsFragment;
import com.ccko.pikxplus.ui.GestureOverlayView;
import com.ccko.pikxplus.ui.PhotosFragment;
import com.ccko.pikxplus.ui.ViewerFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity
    implements AlbumsFragment.OnAlbumSelectedListener, ViewerFragment.OnImageDeletedListener {

  private static final int STORAGE_PERMISSION_CODE = 1001;
  private BottomNavigationView bottomNavigationView;
  public androidx.viewpager2.widget.ViewPager2 viewPager;
  private MainFragmentAdapter pagerAdapter;
  private boolean isViewerActive = false;
  private FrameLayout uiOverlay;
  private View currentOverlayView = null;
  // NEW: Gesture overlay
  private GestureOverlayView gestureOverlay;

  public static SharedPreferences prefs;
  private static final String PREF_LAST_ALBUM_ID = "last_album_id";
  private static final String PREF_LAST_ALBUM_NAME = "last_album_name";
  private static final String PREF_LAST_ALBUM_RELATIVE_PATH = "last_album_relative_path";
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main); 

    // Enable edge-to-edge
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

    // Initialize views FIRST
    bottomNavigationView = findViewById(R.id.bottomNavigation);
    viewPager = findViewById(R.id.viewPager);
    gestureOverlay = findViewById(R.id.gestureOverlay);
    uiOverlay = findViewById(R.id.uiOverlay);

    // Correct root view
    View root = findViewById(android.R.id.content);

    // Apply insets AFTER views exist
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

          // Bottom nav respects bottom inset
          bottomNavigationView.setPadding(
              bottomNavigationView.getPaddingLeft(),
              bottomNavigationView.getPaddingTop(),
              bottomNavigationView.getPaddingRight(),
              systemBars.bottom);

          // ViewPager respects top + bottom
          viewPager.setPadding(0, systemBars.top, 0, 0);
          return WindowInsetsCompat.CONSUMED;
        });

    checkStoragePermission();
    prefs = getSharedPreferences("pikxplus_prefs", MODE_PRIVATE);

    // Initialize gesture overlay
    gestureOverlay.setDeadzones(0.10f, 0.10f); // 10% top and bottom

    // Setup ViewPager2
    pagerAdapter = new MainFragmentAdapter(this);
    viewPager.setAdapter(pagerAdapter);
    viewPager.setUserInputEnabled(true);
    viewPager.setCurrentItem(MainFragmentAdapter.POSITION_ALBUMS, false);

    // Setup bottom navigation
    bottomNavigationView.setSelectedItemId(R.id.nav_albums);

    setupViewPagerWithBottomNav();
  }

  private void checkStoragePermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (!Environment.isExternalStorageManager()) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(
                "This app requires All Files Access to manage your Images. Please enable it in settings.")
            .setPositiveButton(
                "Grant",
                (dialog, which) -> {
                  try {
                    Intent intent =
                        new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(
                        Uri.parse(
                            String.format("package:%s", getApplicationContext().getPackageName())));
                    startActivityForResult(intent, STORAGE_PERMISSION_CODE);
                  } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, STORAGE_PERMISSION_CODE);
                  }
                })
            .setNegativeButton("Exit", (dialog, which) -> finish())
            .setCancelable(false)
            .show();
      } else {
        loadAlbumsIfPresent();
      }
    } else {
      // Legacy permissions for Android 10 and below
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
          != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            this,
            new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
            STORAGE_PERMISSION_CODE);
      } else {
        loadAlbumsIfPresent();
      }
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == STORAGE_PERMISSION_CODE) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (Environment.isExternalStorageManager()) {
          // This triggers a full Activity restart
          recreate();
        } else {
          // Loop back to the dialog because they were stubborn
          checkStoragePermission();
        }
      }
    }
  }

  // Helper
  private void loadAlbumsIfPresent() {
    Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
    if (currentFragment instanceof AlbumsFragment) {
      ((AlbumsFragment) currentFragment).loadAlbums();
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode == STORAGE_PERMISSION_CODE) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        Fragment currentFragment =
            getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (currentFragment instanceof AlbumsFragment) {
          ((AlbumsFragment) currentFragment).loadAlbums();
        }
      } else {
        Toast.makeText(this, "Storage permission is required to view photos", Toast.LENGTH_LONG)
            .show();

        if (!ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
          Toast.makeText(
                  this, "Please grant permission in Settings to use this app", Toast.LENGTH_LONG)
              .show();
          finish();
        } else {
          new Handler(Looper.getMainLooper())
              .postDelayed(
                  () ->
                      ActivityCompat.requestPermissions(
                          MainActivity.this,
                          new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                          STORAGE_PERMISSION_CODE),
                  1000);
        }
      }
    }
  }

  private void setupViewPagerWithBottomNav() {
    viewPager.registerOnPageChangeCallback(
        new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
          @Override
          public void onPageSelected(int position) {
            super.onPageSelected(position);

            switch (position) {
              case MainFragmentAdapter.POSITION_STORAGE:
                bottomNavigationView.setSelectedItemId(R.id.nav_storage);
                break;
              case MainFragmentAdapter.POSITION_ALBUMS:
                bottomNavigationView.setSelectedItemId(R.id.nav_albums);
                break;
              case MainFragmentAdapter.POSITION_PHOTOS:
                bottomNavigationView.setSelectedItemId(R.id.nav_photos);
                break;
              case MainFragmentAdapter.POSITION_SEARCH:
                bottomNavigationView.setSelectedItemId(R.id.nav_search);
                break;
              case MainFragmentAdapter.POSITION_MORE:
                bottomNavigationView.setSelectedItemId(R.id.nav_more);
                break;
            }
          }
        });

    bottomNavigationView.setOnNavigationItemSelectedListener(
        item -> {
          int position = -1;
          int itemId = item.getItemId();

          if (itemId == R.id.nav_storage) {
            position = MainFragmentAdapter.POSITION_STORAGE;
          } else if (itemId == R.id.nav_albums) {
            position = MainFragmentAdapter.POSITION_ALBUMS;
          } else if (itemId == R.id.nav_photos) {
            position = MainFragmentAdapter.POSITION_PHOTOS;
          } else if (itemId == R.id.nav_search) {
            position = MainFragmentAdapter.POSITION_SEARCH;
          } else if (itemId == R.id.nav_more) {
            Toast.makeText(MainActivity.this, "There is Nothing More!", Toast.LENGTH_SHORT).show();
            return true;
          }

          if (position != -1) {
            viewPager.setCurrentItem(position, true);
          }
          return true;
        });
  }

  @Override
  public void onAlbumSelected(AlbumsFragment.Album album) {
    try {
      prefs
          .edit()
          .putString(PREF_LAST_ALBUM_ID, album.id)
          .putString(PREF_LAST_ALBUM_NAME, album.name)
          .putString(
              PREF_LAST_ALBUM_RELATIVE_PATH, album.relativePath == null ? "" : album.relativePath)
          .apply();
    } catch (Exception ignored) {
    }

    Fragment fragment =
        getSupportFragmentManager().findFragmentByTag("f" + MainFragmentAdapter.POSITION_PHOTOS);
    if (fragment instanceof PhotosFragment) {
      ((PhotosFragment) fragment).setAlbumData(album.id, album.name, album.relativePath);
    }

    viewPager.setCurrentItem(MainFragmentAdapter.POSITION_PHOTOS, true);
  }

  // Method to open ViewerFragment (overlay mode) - NOW CONTROLS GESTURE ROUTING
  public void openViewer(Fragment fragment) {
    isViewerActive = true;
    setViewerMode(true);

    viewPager.setUserInputEnabled(false);
    findViewById(R.id.fragmentContainer).setVisibility(View.VISIBLE);
    viewPager.setVisibility(View.GONE);

    // Works for ANY fragment now (ViewerFragment OR VideoPlayerFragment)
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.fragmentContainer, fragment)
        .addToBackStack(null)
        .commit();
  }

  // Method to close ViewerFragment - RESTORES GESTURE ROUTING
  public void closeViewer() {
    isViewerActive = false;
    setViewerMode(false);

    // Re-enable ViewPager swiping
    viewPager.setUserInputEnabled(true);

    // Hide fragmentContainer and show ViewPager
    findViewById(R.id.fragmentContainer).setVisibility(View.GONE);
    viewPager.setVisibility(View.VISIBLE);

    // NEW: Clear gesture delegate (return to ViewPager control)
    if (gestureOverlay != null) {
      gestureOverlay.clearDelegate();
    }

    hideUiOverlay();
    getSupportFragmentManager().popBackStack();
  }

  // NEW: Public method for fragments to register gesture handlers
  public void setGestureDelegate(GestureOverlayView.GestureDelegate delegate) {
    if (gestureOverlay != null) {
      gestureOverlay.setGestureDelegate(delegate);
    }
  }

  private void animateViewerOverlay(boolean show) {
    View overlay = findViewById(R.id.fragmentContainer);

    if (show) {
      overlay.setAlpha(0f);
      overlay.setVisibility(View.VISIBLE);
      overlay
          .animate()
          .alpha(1f)
          .setDuration(0)
          .setInterpolator(new AccelerateDecelerateInterpolator())
          .start();
    } else {
      overlay
          .animate()
          .alpha(0f)
          .setDuration(500)
          .setInterpolator(new AccelerateDecelerateInterpolator())
          .withEndAction(() -> overlay.setVisibility(View.GONE))
          .start();
    }
  }

  private void animateBottomNav(boolean show) {
    // cancel any running animation
    bottomNavigationView.animate().cancel();

    // ensure translation is reset so previous slide doesn't interfere
    bottomNavigationView.setTranslationY(0f);

    if (show) {
      // prepare for fade in
      bottomNavigationView.setAlpha(0f);
      bottomNavigationView.setVisibility(View.VISIBLE);
      bottomNavigationView.setClickable(false); // disable clicks during animation

      // optional: enable hardware layer for smoother animation on older devices
      bottomNavigationView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

      bottomNavigationView
          .animate()
          .alpha(1f)
          .setDuration(500) // try 200-400ms
          .setInterpolator(new DecelerateInterpolator())
          .withEndAction(
              () -> {
                bottomNavigationView.setClickable(true);
                bottomNavigationView.setLayerType(View.LAYER_TYPE_NONE, null);
              })
          .start();
    } else {
      // fade out
      bottomNavigationView.setClickable(false);
      bottomNavigationView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

      bottomNavigationView
          .animate()
          .alpha(0f)
          .setDuration(500) // try 200-400ms
          .setInterpolator(new AccelerateInterpolator())
          .withEndAction(
              () -> {
                bottomNavigationView.setVisibility(View.GONE);
                bottomNavigationView.setClickable(true);
                bottomNavigationView.setLayerType(View.LAYER_TYPE_NONE, null);
              })
          .start();
    }
  }

  /** Add a view to the UI overlay (above gesture overlay) */
  public void showUiOverlay(View overlayView) {
    if (uiOverlay == null) return;

    // Remove any existing overlay
    hideUiOverlay();

    // Add the new overlay
    currentOverlayView = overlayView;
    uiOverlay.addView(overlayView);
    uiOverlay.setVisibility(View.VISIBLE);
  }

  public void hideUiOverlay() {
    if (uiOverlay == null) return;

    uiOverlay.removeAllViews();
    uiOverlay.setVisibility(View.GONE);
    currentOverlayView = null;
  }

  public void clearUiOverlay() {
    hideUiOverlay();
  }

  // seet true inside onResume to fullscreen and fulse inside onPause to cancel.
  public void setViewerModeEnabled(boolean enabled) {
    setViewerMode(enabled);
  }

  // hide/show system bar and toolbars
  private void setViewerMode(boolean enabled) {
    Window window = getWindow();
    WindowInsetsControllerCompat controller =
        WindowCompat.getInsetsController(window, window.getDecorView());

    if (enabled) {
      // --- Immersive mode ---
      WindowCompat.setDecorFitsSystemWindows(window, false);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(lp);
      }

      // Transparent bars for immersive feel
      window.setStatusBarColor(Color.TRANSPARENT);
      window.setNavigationBarColor(Color.TRANSPARENT);

      if (controller != null) {
        // Hide both bars
        controller.hide(
            WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
        // Immersive swipe behavior
        controller.setSystemBarsBehavior(
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      }
      animateBottomNav(false);
      animateViewerOverlay(true);
    } else {
      // --- Normal mode: give bars back ---
      WindowCompat.setDecorFitsSystemWindows(window, true);
      int primary = Color.parseColor("#FF000000");
      window.setStatusBarColor(primary);
      window.setNavigationBarColor(primary);
      if (controller != null) {
        controller.show(
            WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
      }

      animateBottomNav(true);
      animateViewerOverlay(false);
    }
  }

  public void toggleOrientation() {
    int current = getRequestedOrientation();

    if (current == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        || current == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {

      // Portrait → sensor-based landscape
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

    } else {
      // Any landscape → portrait
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
  }

  public void onImageDeleted(int deletedIndex) {
    Fragment fragment =
        getSupportFragmentManager().findFragmentByTag("f" + MainFragmentAdapter.POSITION_PHOTOS);
    if (fragment instanceof PhotosFragment) {
      ((PhotosFragment) fragment).loadAlbumPhotos();
    }
  }

  @Override
  public void onBackPressed() {
    if (isViewerActive) {
      closeViewer();
      getSupportFragmentManager().popBackStack();
    } else {
      int currentPosition = viewPager.getCurrentItem();

      if (currentPosition == MainFragmentAdapter.POSITION_PHOTOS) {
        viewPager.setCurrentItem(MainFragmentAdapter.POSITION_ALBUMS, true);
        hideUiOverlay(); // Clear overlay when going back to albums
      } else if (currentPosition == MainFragmentAdapter.POSITION_ALBUMS
          || currentPosition == MainFragmentAdapter.POSITION_STORAGE) {
        super.onBackPressed();
        hideUiOverlay();
      } else {
        viewPager.setCurrentItem(MainFragmentAdapter.POSITION_ALBUMS, true);
        hideUiOverlay();
      }
    }
  }
}
