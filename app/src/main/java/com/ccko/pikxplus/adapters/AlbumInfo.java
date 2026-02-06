package com.ccko.pikxplus.adapters;

import com.ccko.pikxplus.ui.AlbumsFragment;

import android.net.Uri;

/**
 * Enhanced album data model with separate photo/video counts and storage info.
 * Replaces the old Album inner class in AlbumsFragment.
 */
public class AlbumInfo {

	public String id;
	public String name;
	public int photoCount;
	public int videoCount;
	public Uri thumbnailUri;
	public String relativePath;
	public boolean isOnSdCard;
	public String volumeName;

	/**
	 * Constructor with all fields
	 */
	public AlbumInfo(String id, String name, int photoCount, int videoCount, Uri thumbnailUri, String relativePath,
			String volumeName) {
		this.id = id;
		this.name = name;
		this.photoCount = photoCount;
		this.videoCount = videoCount;
		this.thumbnailUri = thumbnailUri;
		this.relativePath = relativePath;
		this.volumeName = volumeName;
		this.isOnSdCard = !isInternalStorage(volumeName);
	}

	/**
	 * Get total media count (photos + videos)
	 */
	public int getTotalCount() {
		return photoCount + videoCount;
	}

	/**
	 * Check if album contains only photos
	 */
	public boolean hasOnlyPhotos() {
		return photoCount > 0 && videoCount == 0;
	}

	/**
	 * Check if album contains only videos
	 */
	public boolean hasOnlyVideos() {
		return videoCount > 0 && photoCount == 0;
	}

	/**
	 * Check if album contains mixed media
	 */
	public boolean hasMixedMedia() {
		return photoCount > 0 && videoCount > 0;
	}

	/**
	 * Helper to determine if volume name indicates internal storage
	 */
	private static boolean isInternalStorage(String volumeName) {
		return volumeName != null && volumeName.equals("external_primary");
	}

	/**
	 * Compatibility method - converts to old Album format if needed
	 * (Can be removed once full migration is complete)
	 */
	public AlbumsFragment.Album toAlbum() {
		return new AlbumsFragment.Album(id, name, getTotalCount(), thumbnailUri, relativePath);
	}
}