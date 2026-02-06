package com.ccko.pikxplus.ui;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.ccko.pikxplus.MainActivity;
import com.ccko.pikxplus.R;
import com.ccko.pikxplus.adapters.MediaItems;
import com.ccko.pikxplus.adapters.MediaStoreHelper;
import com.ccko.pikxplus.utils.FloatingWindowManager;
import com.ccko.pikxplus.utils.SharedViewModel;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PhotosFragment extends Fragment {

  private static final String TAG = "PhotosFragment";
  private RecyclerView recyclerView;
  private PhotosAdapter adapter;
  private List<MediaItems> mediaList = new ArrayList<>();
  private String albumId;
  private String albumName;
  private TextView albumTitle;

  private FloatingWindowManager floatingWindow;

  // preloading the Photos
  private boolean isLoadingImages = false;
  private boolean isLoadingVideos = false;
  private Thread imagesLoadingThread = null;
  private Thread videosLoadingThread = null;

  private SearchFragment.Filter currentFilter = null;
  private String folderName;
  // Short memory keys
  private static final String PREF_KEY_LAST_URI = "viewer_last_image";
  private static final String PREF_KEY_LAST_INDEX = "viewer_last_index";
  private static final String PREF_LAST_ALBUM_ID = "last_album_id";
  private static final String PREF_LAST_ALBUM_NAME = "last_album_name";
  private static final String PREF_LAST_ALBUM_RELATIVE_PATH = "last_album_relative_path";

  private SortMode currentSortMode = SortMode.DATE_DESC;
  private static final String PREF_KEY_SORT_MODE = "photos_sort_mode";
  private int currentSpanCount = 6; // Default grid columns
  private boolean isGridView = true; // true = grid, false = list
  private static final String PREF_KEY_VIEW_MODE = "photos_grid_span";

  private Thread loadingThread = null;
  private SharedViewModel sharedViewModel;

  // Keep old Photo class for backward compatibility with ViewerFragment
  public static class Photo {
    public String id;
    public String name;
    public long dateModified;
    public long size;
    public Uri uri;

    public Photo(String id, String name, long dateModified, long size, Uri uri) {
      this.id = id;
      this.name = name;
      this.dateModified = dateModified;
      this.size = size;
      this.uri = uri;
    }
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    AppCompatActivity activity = (AppCompatActivity) getActivity();
    if (activity != null && activity.getSupportActionBar() != null) {
      activity.getSupportActionBar().hide();
    }

    Bundle args = getArguments();
    if (args != null) {
      albumId = args.getString("album_id");
      albumName = args.getString("album_name");
      folderName = args.getString("folder_name");
      android.util.Log.d("PhotosFragment", "Received album ID: " + albumId);
      android.util.Log.d("PhotosFragment", "Received album name: " + albumName);
    }

    // Restore saved sort mode
    try {
      if (MainActivity.prefs != null) {
        String savedSort = MainActivity.prefs.getString(PREF_KEY_SORT_MODE, null);
        if (savedSort != null) {
          try {
            currentSortMode = SortMode.valueOf(savedSort);
          } catch (IllegalArgumentException ignored) {
          }
        }
      }
    } catch (Exception ignored) {
    }

    // Restore saved Grid span
    try {
      if (MainActivity.prefs != null) {
        int savedSpan = MainActivity.prefs.getInt(PREF_KEY_VIEW_MODE, 0);
        if (savedSpan > 0) {
          try {
            currentSpanCount = savedSpan;
          } catch (IllegalArgumentException ignored) {
          }
        }
      }
    } catch (Exception ignored) {
    }

    // Restore last selected album if no args
    if ((albumId == null || albumId.isEmpty()) && (albumName == null || albumName.isEmpty())) {
      try {
        if (MainActivity.prefs != null) {
          String savedId = MainActivity.prefs.getString(PREF_LAST_ALBUM_ID, null);
          String savedName = MainActivity.prefs.getString(PREF_LAST_ALBUM_NAME, null);
          String savedRel = MainActivity.prefs.getString(PREF_LAST_ALBUM_RELATIVE_PATH, null);

          if (savedId != null && !savedId.isEmpty()) {
            albumId = savedId;
          }
          if (savedName != null && !savedName.isEmpty()) {
            albumName = savedName;
          }
          if (savedRel != null && !savedRel.isEmpty()) {
            folderName = savedRel;
          }
        }
      } catch (Exception ignored) {
      }
    }
  }

  public void applyFilter(SearchFragment.Filter filter) {
    this.currentFilter = filter;
    loadAlbumPhotos();
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {

    AppCompatActivity activity = (AppCompatActivity) getActivity();
    if (activity != null && activity.getSupportActionBar() != null) {
      activity.getSupportActionBar().hide();
    }

    View view = inflater.inflate(R.layout.fragment_photos, container, false);
    recyclerView = view.findViewById(R.id.recyclerViewPhotos);
    GridLayoutManager layoutManager = new GridLayoutManager(getContext(), currentSpanCount);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setHasFixedSize(true);
    adapter = new PhotosAdapter();
    recyclerView.setAdapter(adapter);

    return view;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
    sharedViewModel
        .getFilter()
        .observe(
            getViewLifecycleOwner(),
            new Observer<SearchFragment.Filter>() {
              @Override
              public void onChanged(SearchFragment.Filter filter) {
                if (filter != null) {
                  applyFilter(filter);
                }
              }
            });

    boolean hasAlbumId = albumId != null && !albumId.isEmpty();
    boolean hasFolderName = folderName != null && !folderName.isEmpty();

    if (hasAlbumId || hasFolderName) {
      loadAlbumPhotos();
    } else {
      Toast.makeText(getContext(), "No album selected", Toast.LENGTH_SHORT).show();
    }

    getParentFragmentManager()
        .setFragmentResultListener(
            ViewerFragment.REQUEST_KEY,
            getViewLifecycleOwner(),
            new FragmentResultListener() {
              @Override
              public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                String uriString = bundle.getString(ViewerFragment.RESULT_URI_KEY);
                int index = bundle.getInt(ViewerFragment.RESULT_INDEX_KEY, -1);

                Log.d(TAG, "Received viewer result - URI: " + uriString + ", Index: " + index);

                // Save to SharedPreferences AND scroll immediately
                if (uriString != null && MainActivity.prefs != null) {
                  MainActivity.prefs
                      .edit()
                      .putString(PREF_KEY_LAST_URI, uriString)
                      .putInt(PREF_KEY_LAST_INDEX, index)
                      .apply();
                }

                // Scroll to the position NOW (no delay needed)
                scrollToLastViewedIfApplicable();
              }
            });
    toggleViewMode();
  }

  public void loadAlbumPhotos() {
    cancelAllLoading();
    // Don't clear the list immediately to avoid a "white flash"
    // We'll swap the data in one go on the UI thread.

    imagesLoadingThread =
        new Thread(
            () -> {
              // 1. Fetch data in background
              List<MediaItems> images =
                  MediaStoreHelper.loadImagesForAlbum(getContext(), albumId, albumName, folderName);
              List<MediaItems> videos =
                  MediaStoreHelper.loadVideosForAlbum(getContext(), albumId, albumName, folderName);

              List<MediaItems> combined = new ArrayList<>(images.size() + videos.size());
              combined.addAll(images);
              combined.addAll(videos);

              if (Thread.interrupted()) return;

              // 2. Sort in background
              sortMediaList(combined);

              if (getActivity() != null) {
                getActivity()
                    .runOnUiThread(
                        () -> {
                          mediaList.clear();
                          mediaList.addAll(combined);
                          adapter.notifyDataSetChanged();

                          // 3. Teleport to position immediately after
                          // data is bound
                          // scrollToLastViewedIfApplicable();

                          isLoadingImages = false;
                          isLoadingVideos = false;
                        });
              }
            });
    imagesLoadingThread.start();
  }

  private void cancelAllLoading() {
    if (imagesLoadingThread != null && imagesLoadingThread.isAlive()) {
      imagesLoadingThread.interrupt();
      imagesLoadingThread = null;
    }
    if (videosLoadingThread != null && videosLoadingThread.isAlive()) {
      videosLoadingThread.interrupt();
      videosLoadingThread = null;
    }
    isLoadingImages = false;
    isLoadingVideos = false;
  }

  public void setAlbumData(String albumId, String albumName, String folderName) {
    this.albumId = albumId;
    this.albumName = albumName;
    this.folderName = folderName;

    if (albumTitle != null) {
      albumTitle.setText(albumName);
    }
    loadAlbumPhotos();
  }

  private static final Comparator<MediaItems> NATURAL_NAME_COMPARATOR =
      (a, b) -> {
        if (a.name == null) return -1;
        if (b.name == null) return 1;

        String s1 = a.name;
        String s2 = b.name;

        int len1 = s1.length();
        int len2 = s2.length();
        int i = 0, j = 0;

        while (i < len1 && j < len2) {
          char c1 = s1.charAt(i);
          char c2 = s2.charAt(j);

          if (Character.isDigit(c1) && Character.isDigit(c2)) {
            while (i < len1 && s1.charAt(i) == '0') i++;
            while (j < len2 && s2.charAt(j) == '0') j++;

            int numStart1 = i;
            int numStart2 = j;
            while (i < len1 && Character.isDigit(s1.charAt(i))) i++;
            while (j < len2 && Character.isDigit(s2.charAt(j))) j++;

            String numStr1 = s1.substring(numStart1, i);
            String numStr2 = s2.substring(numStart2, j);

            BigInteger num1 = numStr1.isEmpty() ? BigInteger.ZERO : new BigInteger(numStr1);
            BigInteger num2 = numStr2.isEmpty() ? BigInteger.ZERO : new BigInteger(numStr2);

            int cmp = num1.compareTo(num2);
            if (cmp != 0) return cmp;

            if (numStr1.length() != numStr2.length()) {
              return Integer.compare(numStr2.length(), numStr1.length());
            }
          } else {
            int cmp = Character.toLowerCase(c1) - Character.toLowerCase(c2);
            if (cmp != 0) return cmp;
            i++;
            j++;
          }
        }

        return Integer.compare(len1, len2);
      };

  private enum SortMode {
    NAME_ASC,
    NAME_DESC,
    DATE_DESC,
    DATE_ASC, // Newest first (DESC) vs Oldest first (ASC)
    SIZE_DESC,
    SIZE_ASC
  }

  // Helper to handle background sorting for 21k items
  private void sortMediaList(List<MediaItems> list) {
    if (list == null || list.isEmpty()) return;
    switch (currentSortMode) {
      case NAME_ASC:
        list.sort(NATURAL_NAME_COMPARATOR);
        break;
      case NAME_DESC:
        list.sort(NATURAL_NAME_COMPARATOR.reversed());
        break;
      case DATE_DESC:
        list.sort((a, b) -> Long.compare(b.dateModified, a.dateModified));
        break;
      case DATE_ASC:
        list.sort((a, b) -> Long.compare(a.dateModified, b.dateModified));
        break;
      case SIZE_DESC:
        list.sort((a, b) -> Long.compare(b.size, a.size));
        break;
      case SIZE_ASC:
        list.sort((a, b) -> Long.compare(a.size, b.size));
        break;
    }
  }

  private void applySort() {
    if (mediaList == null || mediaList.isEmpty()) return;
    switch (currentSortMode) {
      case NAME_ASC:
        mediaList.sort(NATURAL_NAME_COMPARATOR);
        break;
      case NAME_DESC:
        mediaList.sort(NATURAL_NAME_COMPARATOR.reversed());
        break;
      case DATE_DESC:
        mediaList.sort((a, b) -> Long.compare(b.dateModified, a.dateModified));
        break;
      case DATE_ASC:
        mediaList.sort((a, b) -> Long.compare(a.dateModified, b.dateModified));
        break;
      case SIZE_DESC:
        mediaList.sort((a, b) -> Long.compare(b.size, a.size));
        break;
      case SIZE_ASC:
        mediaList.sort((a, b) -> Long.compare(a.size, b.size));
        break;
    }

    try {
      if (MainActivity.prefs != null) {
        MainActivity.prefs.edit().putString(PREF_KEY_SORT_MODE, currentSortMode.name()).apply();
        Log.d(
            TAG,
            "inside applySort - currentSort: "
                + currentSortMode.name()
                + ", PREF: "
                + PREF_KEY_SORT_MODE);
      }
    } catch (Exception ignored) {
    }

    adapter.notifyDataSetChanged();
  }

  private void scrollToLastViewedIfApplicable() {
    if (recyclerView == null || mediaList == null || mediaList.isEmpty()) return;
    if (MainActivity.prefs == null) return;

    String savedUri = MainActivity.prefs.getString(PREF_KEY_LAST_URI, null);
    int savedIndex = MainActivity.prefs.getInt(PREF_KEY_LAST_INDEX, -1);

    int matchPos = -1;
    if (savedUri != null) {
      for (int i = 0; i < mediaList.size(); i++) {
        if (savedUri.equals(mediaList.get(i).uri.toString())) {
          matchPos = i;
          break;
        }
      }
    }

    // Fallback to index if URI doesn't match
    if (matchPos < 0 && savedIndex >= 0 && savedIndex < mediaList.size()) {
      matchPos = savedIndex;
    }

    if (matchPos < 0) return;

    // The "+1" accounts for your Header item at position 0
  //  final int finalPos = matchPos + 1;
    final int finalPos = matchPos;

    recyclerView.post(
        () -> {
          RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
          if (lm instanceof GridLayoutManager) {
            // '0' offset means the item sits exactly at the top of the screen
            ((GridLayoutManager) lm).scrollToPositionWithOffset(finalPos, 0);
          } else {
            recyclerView.scrollToPosition(finalPos);
          }
        });
  }

  private void showSortPopup(View anchor) {
    if (getContext() == null) return;

    FloatingWindowManager fwm = new FloatingWindowManager(getContext(), anchor);
    // Use DP conversion to ensure width is consistent across screens
    int widthPx = (int) (250 * getResources().getDisplayMetrics().density);

    fwm.show(
        R.layout.floating_photos_sort,
        widthPx,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        20,
        Gravity.START | Gravity.CENTER_VERTICAL,
        content -> {
          // IMPORTANT: Use 'content' to find views!
          View rowName = content.findViewById(R.id.btn_sort_name);
          View rowDate = content.findViewById(R.id.btn_sort_date);
          View rowSize = content.findViewById(R.id.btn_sort_size);

          setupSortRowLogic(
              content, rowName, R.id.img_name_arrow, SortMode.NAME_ASC, SortMode.NAME_DESC, fwm);
          setupSortRowLogic(
              content, rowDate, R.id.img_date_arrow, SortMode.DATE_DESC, SortMode.DATE_ASC, fwm);
          setupSortRowLogic(
              content, rowSize, R.id.img_size_arrow, SortMode.SIZE_ASC, SortMode.SIZE_DESC, fwm);
        },
        false);
  }

  // Refactored for safety - 'content' is the inflated popup view
  private void setupSortRowLogic(
      View content, View row, int arrowId, SortMode asc, SortMode desc, FloatingWindowManager fwm) {
    ImageView arrow = content.findViewById(arrowId);

    boolean isCurrent = (currentSortMode == asc || currentSortMode == desc);
    arrow.setRotation(currentSortMode == asc ? 0 : 180);
    arrow.setAlpha(isCurrent ? 1.0f : 0f);

    row.setOnClickListener(
        v -> {
          currentSortMode = (currentSortMode == asc) ? desc : asc;

          // Instant response: sort first, then update UI
          applySort();

          // Refresh the popup UI without closing it
          fwm.updateContent(
              newContent -> {
                setupSortRowLogic(
                    newContent, newContent.findViewById(row.getId()), arrowId, asc, desc, fwm);
              });
        });
  }

  private void showViewModePopup(View anchor) {
    if (getContext() == null) return;

    FloatingWindowManager fwm = new FloatingWindowManager(getContext(), anchor);
    int widthPx = (int) (250 * getResources().getDisplayMetrics().density);

    fwm.show(
        R.layout.floating_photos_viewmode,
        widthPx,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        20,
        Gravity.END | Gravity.CENTER_VERTICAL,
        content -> {
          TextView countTxt = content.findViewById(R.id.txt_grid_count);
          countTxt.setText(String.valueOf(currentSpanCount));

          content
              .findViewById(R.id.btn_grid_plus)
              .setOnClickListener(
                  v -> {
                    if (currentSpanCount < 6) {
                      currentSpanCount++;
                      updateGridSpan(currentSpanCount);
                      countTxt.setText(String.valueOf(currentSpanCount));

                      try {
                        if (MainActivity.prefs != null) {
                          MainActivity.prefs
                              .edit()
                              .putInt(PREF_KEY_VIEW_MODE, currentSpanCount)
                              .apply();
                          Log.d(
                              TAG,
                              "in grid plusBtn listener - currentSpanCount: "
                                  + currentSpanCount
                                  + ", PREF: "
                                  + PREF_KEY_VIEW_MODE);
                        }
                      } catch (Exception ignored) {
                      }
                    }
                  });

          content
              .findViewById(R.id.btn_grid_minus)
              .setOnClickListener(
                  v -> {
                    if (currentSpanCount > 2) {
                      currentSpanCount--;
                      updateGridSpan(currentSpanCount);
                      countTxt.setText(String.valueOf(currentSpanCount));

                      try {
                        if (MainActivity.prefs != null) {
                          MainActivity.prefs
                              .edit()
                              .putInt(PREF_KEY_VIEW_MODE, currentSpanCount)
                              .apply();
                          Log.d(
                              TAG,
                              "in grid minusBtn listener - currentSpanCount: "
                                  + currentSpanCount
                                  + ", PREF: "
                                  + PREF_KEY_VIEW_MODE);
                        }
                      } catch (Exception ignored) {
                      }
                    }
                  });

          content
              .findViewById(R.id.btn_view_list)
              .setOnClickListener(
                  v -> {
                    isGridView = false;
                    currentSpanCount = 1;
                    updateGridSpan(1);
                    fwm.dismiss();
                    
                         try {
            if (MainActivity.prefs != null) {
              MainActivity.prefs.edit().putInt(PREF_KEY_VIEW_MODE, currentSpanCount).apply();
              Log.d(
                  TAG,
                  "in listBtn listener - currentSpanCount: "
                      + currentSpanCount
                      + ", PREF: "
                      + PREF_KEY_VIEW_MODE);
            }
          } catch (Exception ignored) {
          }
          
                  });
        },
        false);
  }

  private void updateGridSpan(int span) {
    isGridView = (span > 1);
    GridLayoutManager lm = (GridLayoutManager) recyclerView.getLayoutManager();
    if (lm != null) {
      lm.setSpanCount(span);
      adapter.notifyItemRangeChanged(1, adapter.getItemCount()); // Skip header, update grid
    }
  }

  private void toggleViewMode() {
    isGridView = !isGridView;
    currentSpanCount = isGridView ? 6 : 1;

    try {
      if (MainActivity.prefs != null) {
        int savedSpan = MainActivity.prefs.getInt(PREF_KEY_VIEW_MODE, 0);
        if (savedSpan > 0) {
          try {
            currentSpanCount = savedSpan;
          } catch (IllegalArgumentException ignored) {
          }
        }
      }
    } catch (Exception ignored) {
    }

    GridLayoutManager layoutManager = new GridLayoutManager(getContext(), currentSpanCount);
    layoutManager.setSpanSizeLookup(
        new GridLayoutManager.SpanSizeLookup() {
          @Override
          public int getSpanSize(int position) {
            // Header (position 0) takes up all columns (3 in grid, 1 in list)
            return (adapter.getItemViewType(position) == 0) ? currentSpanCount : 1;
          }
        });

    recyclerView.setLayoutManager(layoutManager);
    adapter.notifyDataSetChanged();
  }

  // RecyclerView Adapter
  private class PhotosAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_MEDIA = 1;

    @Override
    public int getItemViewType(int position) {
      return position == 0 ? TYPE_HEADER : TYPE_MEDIA;
    }

    @Override
    public int getItemCount() {
      return mediaList.isEmpty() ? 0 : mediaList.size() + 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      if (viewType == TYPE_HEADER) {
        View headerView =
            LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo_header, parent, false);
        return new HeaderViewHolder(headerView);
      } else {
        // media item inflation
        int layoutId = isGridView ? R.layout.item_photo_grid : R.layout.item_photo_list;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new MediaViewHolder(view);
      }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
      if (holder instanceof HeaderViewHolder) {
        // Bind first image to header
        if (!mediaList.isEmpty()) {
          MediaItems firstItem = mediaList.get(0);
          ((HeaderViewHolder) holder).bind(firstItem);
        }
      } else {
        // Media items start from position 1
        int mediaPosition = position - 1;
        if (mediaPosition < mediaList.size()) {
          MediaItems item = mediaList.get(mediaPosition);
          ((MediaViewHolder) holder).bind(item);
        }
      }
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
      ImageView headerImage;
      TextView albumTitle;
      ImageButton sortButton;
      ImageButton viewModeButton;

      public HeaderViewHolder(@NonNull View itemView) {
        super(itemView);
        headerImage = itemView.findViewById(R.id.headerImage);
        albumTitle = itemView.findViewById(R.id.albumTitle);
        sortButton = itemView.findViewById(R.id.sortButton);
        viewModeButton = itemView.findViewById(R.id.viewModeButton);

        sortButton.setOnClickListener(v -> showSortPopup(sortButton));
        viewModeButton.setOnClickListener(v -> showViewModePopup(viewModeButton));

        // DISABLED: First item is no longer clickable
        headerImage.setOnClickListener(null);
        headerImage.setClickable(false);
      }

      public void bind(MediaItems firstItem) {
        // load the first item here (duplication logic)
        Glide.with(itemView.getContext()).load(firstItem.uri).centerCrop().into(headerImage);

        if (albumName != null) {
          albumTitle.setText(albumName);
        }
      }
    }

    class MediaViewHolder extends RecyclerView.ViewHolder {
      private ImageView thumbnail;
      private TextView title;
      private TextView details;
      private View container;

      // NEW: Video-specific views
      private ImageView formatIcon;
      private TextView videoDuration;

      public MediaViewHolder(@NonNull View itemView) {
        super(itemView);
        thumbnail = itemView.findViewById(R.id.photoThumbnail);
        title = itemView.findViewById(R.id.photoTitle);
        details = itemView.findViewById(R.id.photoDetails);
        container = itemView.findViewById(R.id.photoContainer);

        // Video overlay views (only exist in layouts)
        formatIcon = itemView.findViewById(R.id.formatIcon);
        videoDuration = itemView.findViewById(R.id.videoDuration);

        // In MediaViewHolder's onClick:
        container.setOnClickListener(
            v -> {
              int position = getAdapterPosition();
              if (position == RecyclerView.NO_POSITION) return;

              // Position 0 is header, media starts at 1
              int mediaIndex = position - 1;

              ViewerFragment viewerFragment = new ViewerFragment();
              Bundle args = new Bundle();
              ArrayList<MediaItems> parcelableList = new ArrayList<>(mediaList);
              args.putParcelableArrayList("media_items", parcelableList);
              args.putInt("current_index", mediaIndex); // Use actual index
              args.putBoolean("user_picked", true);
              viewerFragment.setArguments(args);

              if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openViewer(viewerFragment);
              }
            });
      }

      public void bind(MediaItems item) {
        if (isGridView) {
          // Grid view: show only thumbnail + overlays
          if (title != null) title.setVisibility(View.GONE);
          if (details != null) details.setVisibility(View.GONE);

          // Show format icon (bottom-right)
          if (formatIcon != null) {
            formatIcon.setVisibility(View.VISIBLE);
            if (item.isVideo()) {
              formatIcon.setImageResource(R.drawable.ic_play_circle);
            } else if (item.isAnimated()) {
              formatIcon.setImageResource(R.drawable.ic_gif);
            } else {
              formatIcon.setVisibility(View.GONE);
            }
          }

          // Show video duration (bottom-left)
          if (videoDuration != null) {
            if (item.isVideo() && item.duration > 0) {
              videoDuration.setVisibility(View.VISIBLE);
              videoDuration.setText(item.getFormattedDuration());
            } else {
              videoDuration.setVisibility(View.GONE);
            }
          }
        } else {
          // List view: show all metadata
          if (title != null) {
            title.setVisibility(View.VISIBLE);
            title.setText(item.name);
          }

          if (details != null) {
            details.setVisibility(View.VISIBLE);
            String detailsText;
            if (item.isVideo()) {
              detailsText = item.getFormattedDuration() + " • " + formatSize(item.size);
            } else {
              detailsText = item.getFormattedDimensions() + " • " + formatSize(item.size);
            }
            details.setText(detailsText);
          }

          // Show format icon in list view too
          if (formatIcon != null) {
            formatIcon.setVisibility(View.VISIBLE);
            if (item.isVideo()) {
              formatIcon.setImageResource(R.drawable.ic_play_circle);
            } else if (item.isAnimated()) {
              formatIcon.setImageResource(R.drawable.ic_gif);
            } else {
              formatIcon.setImageResource(R.drawable.ic_photo);
            }
          }

          // Hide duration text in list (it's in details)
          if (videoDuration != null) {
            videoDuration.setVisibility(View.GONE);
          }
        }

        // Load thumbnail
        Glide.with(itemView.getContext())
            .load(item.uri)
            .centerCrop()
            .thumbnail(0.1f) // Load a 20% tiny version first
            .override(240, 240) // Don't decode larger than needed for the grid
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .encodeQuality(70) // Cache only the resized version
            .into(thumbnail);
      }
    }
  }

  private String formatDate(long timestamp) {
    java.util.Date date = new java.util.Date(timestamp * 1000);
    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy");
    return sdf.format(date);
  }

  public void reloadPhotos() {
    if (albumId != null && !albumId.isEmpty()) {
      loadAlbumPhotos();
    }
  }

  private String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(1024));
    String pre = ("KMGTPE").charAt(exp - 1) + "";
    return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
  }

  @Override
  public void onResume() {
    super.onResume();

    // Clear any leftover overlay when returning to photos
    if (getActivity() instanceof MainActivity) {
      ((MainActivity) getActivity()).hideUiOverlay();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    // Clear the saved state when user leaves PhotosFragment
   // if (MainActivity.prefs != null) {
      //
      // MainActivity.prefs.edit().remove(PREF_KEY_LAST_URI).remove(PREF_KEY_LAST_INDEX).apply();
   //   Log.d(TAG, "Cleared viewer state when leaving PhotosFragment");
  //  }
  }
}