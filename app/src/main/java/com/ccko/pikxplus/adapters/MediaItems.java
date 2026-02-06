package com.ccko.pikxplus.adapters;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import com.ccko.pikxplus.ui.PhotosFragment;

/**
* Unified data model for both images and videos.
* Replaces the old Photo class with extended capabilities.
*/
public class MediaItems implements Parcelable { // <-- ADD THIS

	public enum MediaType {
		IMAGE, VIDEO, ANIMATED_IMAGE // GIF or animated WebP
	}

	// Core fields (common to all types)
	public String id;
	public String name;
	public long dateModified;
	public long size;
	public Uri uri;
	public MediaType type;

	// Dimension fields (for all media)
	public int width;
	public int height;

	// Video-specific fields
	public long duration; // milliseconds (0 for images)

	// Album/folder info
	public String bucketId;
	public String bucketName;
	public String relativePath;

	// Storage location
	public boolean isOnSdCard;
	public String volumeName; // "external_primary", "external_sd", etc.

	/**
	* Constructor for images
	*/
	public MediaItems(String id, String name, long dateModified, long size, Uri uri, int width, int height,
			MediaType type) {
		this.id = id;
		this.name = name;
		this.dateModified = dateModified;
		this.size = size;
		this.uri = uri;
		this.width = width;
		this.height = height;
		this.type = type;
		this.duration = 0; // images have no duration
	}

	/**
	* Constructor for videos
	*/
	public MediaItems(String id, String name, long dateModified, long size, Uri uri, int width, int height,
			long duration) {
		this.id = id;
		this.name = name;
		this.dateModified = dateModified;
		this.size = size;
		this.uri = uri;
		this.width = width;
		this.height = height;
		this.duration = duration;
		this.type = MediaType.VIDEO;
	}

	/**
	* Full constructor with all fields
	*/
	public MediaItems(String id, String name, long dateModified, long size, Uri uri, MediaType type, int width,
			int height, long duration, String bucketId, String bucketName, String relativePath, String volumeName) {
		this.id = id;
		this.name = name;
		this.dateModified = dateModified;
		this.size = size;
		this.uri = uri;
		this.type = type;
		this.width = width;
		this.height = height;
		this.duration = duration;
		this.bucketId = bucketId;
		this.bucketName = bucketName;
		this.relativePath = relativePath;
		this.volumeName = volumeName;
		this.isOnSdCard = !isInternalStorage(volumeName);
	}

	// ===== ADD THIS CONSTRUCTOR =====
	/**
	* Parcel constructor - reads data back from Parcel
	*/
	protected MediaItems(Parcel in) {
		id = in.readString();
		name = in.readString();
		dateModified = in.readLong();
		size = in.readLong();
		uri = in.readParcelable(Uri.class.getClassLoader());
		int tmpType = in.readInt();
		type = tmpType == -1 ? null : MediaType.values()[tmpType];
		width = in.readInt();
		height = in.readInt();
		duration = in.readLong();
		bucketId = in.readString();
		bucketName = in.readString();
		relativePath = in.readString();
		volumeName = in.readString();
		isOnSdCard = in.readByte() != 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(id);
		dest.writeString(name);
		dest.writeLong(dateModified);
		dest.writeLong(size);
		dest.writeParcelable(uri, flags);
		dest.writeInt(type == null ? -1 : type.ordinal());
		dest.writeInt(width);
		dest.writeInt(height);
		dest.writeLong(duration);
		dest.writeString(bucketId);
		dest.writeString(bucketName);
		dest.writeString(relativePath);
		dest.writeString(volumeName);
		dest.writeByte((byte) (isOnSdCard ? 1 : 0));
	}

	@Override
	public int describeContents() {
		return 0;
	}

	public static final Creator<MediaItems> CREATOR = new Creator<MediaItems>() {
		@Override
		public MediaItems createFromParcel(Parcel in) {
			return new MediaItems(in);
		}

		@Override
		public MediaItems[] newArray(int size) {
			return new MediaItems[size];
		}
	};

	/**
	* Check if this is a video item
	*/
	public boolean isVideo() {
		return type == MediaType.VIDEO;
	}

	/**
	* Check if this is an animated image
	*/
	public boolean isAnimated() {
		return type == MediaType.ANIMATED_IMAGE;
	}

	/**
	* Check if this is a regular (static) image
	*/
	public boolean isStaticImage() {
		return type == MediaType.IMAGE;
	}

	/**
	* Get formatted duration string for videos
	* Returns empty string for images
	*/
	public String getFormattedDuration() {
		if (!isVideo() || duration <= 0) {
			return "";
		}

		long totalSeconds = duration / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;

		if (hours > 0) {
			return String.format("%d:%02d:%02d", hours, minutes, seconds);
		} else {
			return String.format("%d:%02d", minutes, seconds);
		}
	}

	/**
	* Get formatted dimensions string
	* Example: "1920x1080"
	*/
	public String getFormattedDimensions() {
		if (width > 0 && height > 0) {
			return width + "x" + height;
		}
		return "";
	}

	/**
	* Helper to determine if volume name indicates internal storage
	*/
	private static boolean isInternalStorage(String volumeName) {
		return volumeName != null && volumeName.equals("external_primary");
	}

	/**
	* Compatibility method - converts to old Photo format if needed
	* (Can be removed once full migration is complete)
	*/
	public PhotosFragment.Photo toPhoto() {
		return new PhotosFragment.Photo(id, name, dateModified, size, uri);
	}
}