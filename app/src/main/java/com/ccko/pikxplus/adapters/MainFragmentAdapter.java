package com.ccko.pikxplus.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.ccko.pikxplus.ui.AlbumsFragment;
import com.ccko.pikxplus.ui.PhotosFragment;
import com.ccko.pikxplus.ui.SearchFragment;
import com.ccko.pikxplus.ui.StorageFragment;

public class MainFragmentAdapter extends FragmentStateAdapter {

	public static final int POSITION_STORAGE = 0;
	public static final int POSITION_ALBUMS = 1;
	public static final int POSITION_PHOTOS = 2;
	public static final int POSITION_SEARCH = 3;
	public static final int POSITION_MORE = 4;

	public MainFragmentAdapter(@NonNull FragmentActivity fragmentActivity) {
		super(fragmentActivity);
	}

	@NonNull
	@Override
	public Fragment createFragment(int position) {
		switch (position) {
		case POSITION_STORAGE:
			// For personal use, storage screen is same as albums
			return new StorageFragment();
		case POSITION_ALBUMS:
			return new AlbumsFragment();
		case POSITION_PHOTOS:
			return new PhotosFragment();
		case POSITION_SEARCH:
			return new SearchFragment();
		case POSITION_MORE:
			// Placeholder for now
			return new AlbumsFragment(); // Or create a MoreFragment later
		default:
			return new AlbumsFragment();
		}
	}

	@Override
	public int getItemCount() {
		return 5; // Total number of tabs
	}
}