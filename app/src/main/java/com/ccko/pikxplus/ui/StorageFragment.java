package com.ccko.pikxplus.ui;

import android.app.Activity;
import android.os.Build;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowCompat;
import com.ccko.pikxplus.MainActivity;
import com.ccko.pikxplus.R;
import androidx.fragment.app.Fragment;
import com.ccko.pikxplus.features.GestureAndSlideShow;
import com.ccko.pikxplus.features.OpticalIllusionView;

public class StorageFragment extends Fragment {

	private OpticalIllusionView illusionView;
	private GestureAndSlideShow.ImageViewerGestureHandler gestureHandler;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		// If you already have a layout, add the view into a container there.
		// For simplicity, create the view and return it as the fragment root.
		illusionView = new OpticalIllusionView(requireContext());
		// Optionally set layout params if adding to an existing layout
		illusionView.setLayoutParams(
				new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		return illusionView;
	}

	@Override
	public void onResume() {
		super.onResume();
		Activity a = getActivity();
		if (a instanceof MainActivity) {
			((MainActivity) a).setViewerModeEnabled(true);
		}
		if (illusionView != null)
			illusionView.start();

	}

	@Override
	public void onPause() {
		super.onPause();

		Activity a = getActivity();
		if (a instanceof MainActivity) {
			((MainActivity) a).setViewerModeEnabled(false);
		}
		// stop the illusion if you haven't already
		if (illusionView != null)
			illusionView.stop();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

	}
}