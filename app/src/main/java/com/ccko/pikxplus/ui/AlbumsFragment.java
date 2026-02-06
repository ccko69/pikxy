package com.ccko.pikxplus.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.ccko.pikxplus.R;
import com.ccko.pikxplus.adapters.AlbumInfo;
import com.ccko.pikxplus.adapters.MediaStoreHelper;
import java.util.ArrayList;
import java.util.List;

public class AlbumsFragment extends Fragment {

  private static final String TAG = "AlbumsFragment";
  private RecyclerView recyclerView;
  private AlbumsAdapter adapter;
  private List<AlbumInfo> albumsList = new ArrayList<>();
  private OnAlbumSelectedListener listener;

  // Keep old Album class for backward compatibility during migration
  public static class Album {
    public String id;
    public String name;
    public int count;
    public Uri thumbnailUri;
    public String relativePath;

    public Album(
        String id, String name, int count, android.net.Uri thumbnailUri, String relativePath) {
      this.id = id;
      this.name = name;
      this.count = count;
      this.thumbnailUri = thumbnailUri;
      this.relativePath = relativePath;
    }
  }

  public interface OnAlbumSelectedListener {
    void onAlbumSelected(Album album);
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    try {
      listener = (OnAlbumSelectedListener) context;
    } catch (ClassCastException e) {
      throw new ClassCastException(context.toString() + " must implement OnAlbumSelectedListener");
    }
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_albums, container, false);

    recyclerView = view.findViewById(R.id.recyclerViewAlbums);
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

    adapter = new AlbumsAdapter();
    recyclerView.setAdapter(adapter);

    // Hide toolbar
    AppCompatActivity activity = (AppCompatActivity) getActivity();
    if (activity != null && activity.getSupportActionBar() != null) {
      activity.getSupportActionBar().hide();
    }
    return view;
  }

  // RecyclerView Adapter
  private class AlbumsAdapter extends RecyclerView.Adapter<AlbumsAdapter.AlbumViewHolder> {

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View view =
          LayoutInflater.from(parent.getContext()).inflate(R.layout.item_album, parent, false);
      return new AlbumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
      AlbumInfo album = albumsList.get(position);
      holder.bind(album);
    }

    @Override
    public int getItemCount() {
      return albumsList.size();
    }

    class AlbumViewHolder extends RecyclerView.ViewHolder {
      private ImageView thumbnail;
      private TextView albumName;
      private TextView photoCount;
      private ImageView photoFlag;
      private ImageView videoFlag;
      private TextView videoCount;
      private ImageView storageIcon;
      private View container;

      public AlbumViewHolder(@NonNull View itemView) {
        super(itemView);
        thumbnail = itemView.findViewById(R.id.albumThumbnail);
        albumName = itemView.findViewById(R.id.albumName);
        photoCount = itemView.findViewById(R.id.photoCount);
        videoCount = itemView.findViewById(R.id.videoCount);
        photoFlag = itemView.findViewById(R.id.photoIcon);
        videoFlag = itemView.findViewById(R.id.videoIcon);
        storageIcon = itemView.findViewById(R.id.storageIcon);
        container = itemView.findViewById(R.id.albumContainer);
      }

      public void bind(AlbumInfo album) {
        // Album name (top-left)
        albumName.setText(album.name);

        // Photo count (bottom-left)
        if (album.photoCount > 0) {
          photoCount.setVisibility(View.VISIBLE);
          photoFlag.setVisibility(View.VISIBLE);
          photoCount.setText(String.valueOf(album.photoCount));
        } else {
          photoCount.setVisibility(View.GONE);
          photoFlag.setVisibility(View.GONE);
        }

        // Video count (bottom-left, next to photo count)
        if (album.videoCount > 0) {
          videoCount.setVisibility(View.VISIBLE);
          videoFlag.setVisibility(View.VISIBLE);
          videoCount.setText(String.valueOf(album.videoCount));
        } else {
          videoCount.setVisibility(View.GONE);
          videoFlag.setVisibility(View.GONE);
        }

        // Storage location icon (bottom-right)
        if (album.isOnSdCard) {
          storageIcon.setImageResource(R.drawable.ic_sd_card);
          storageIcon.setVisibility(View.VISIBLE);
        } else {
          storageIcon.setImageResource(R.drawable.ic_internal_storage);
          storageIcon.setVisibility(View.VISIBLE);
        }

        // Load thumbnail
        Glide.with(itemView.getContext())
            .load(album.thumbnailUri)
            .override(240, 240)
            .centerCrop()
            .encodeQuality(70)
            .into(thumbnail);

        // Click listener - convert to old Album format for backward compatibility
        container.setOnClickListener(
            v -> {
              if (listener != null) {
                Album oldFormat = album.toAlbum();
                listener.onAlbumSelected(oldFormat);
              }
            });
      }
    }
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    loadAlbums();
  }

  public void loadAlbums() {
    new Thread(
            () -> {
              // Use new MediaStoreHelper
              List<AlbumInfo> loadedAlbums = MediaStoreHelper.loadAlbums(getContext());

              if (getActivity() != null) {
                getActivity()
                    .runOnUiThread(
                        () -> {
                          albumsList.clear();
                          albumsList.addAll(loadedAlbums);
                          adapter.notifyDataSetChanged();

                          if (albumsList.isEmpty()) {
                            Toast.makeText(getContext(), "No albums found", Toast.LENGTH_SHORT)
                                .show();
                          }
                        });
              }
            })
        .start();
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
  }
}
