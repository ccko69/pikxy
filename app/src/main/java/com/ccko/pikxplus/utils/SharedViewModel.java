package com.ccko.pikxplus.utils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ccko.pikxplus.ui.SearchFragment;

public class SharedViewModel extends ViewModel {
	private final MutableLiveData<SearchFragment.Filter> filterLive = new MutableLiveData<>();

	public void setFilter(SearchFragment.Filter filter) {
		filterLive.setValue(filter);
	}

	public LiveData<SearchFragment.Filter> getFilter() {
		return filterLive;
	}
}