package com.ccko.pikxplus.adapters;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.ccko.pikxplus.adapters.AlbumInfo;
import com.ccko.pikxplus.adapters.MediaItems;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized helper for querying MediaStore for both images and videos.
 * Keeps your existing logic while organizing it cleanly.
 */
public class MediaStoreHelper {

	private static final String TAG = "MediaStoreHelper";

	/**
	 * Load all albums with separate photo and video counts.
	 * Based on your existing loadAlbumsFromMediaStore() logic.
	 */
	public static List<AlbumInfo> loadAlbums(Context context) {
		List<AlbumInfo> albums = new ArrayList<>();
		Map<String, AlbumInfo> albumMap = new LinkedHashMap<>();

		// Query images first
		loadImageAlbums(context, albumMap);

		// Then query videos and merge counts
		loadVideoAlbums(context, albumMap);

		// Create "All Media" album if there's content
		int totalPhotos = 0;
		int totalVideos = 0;
		Uri latestThumbnail = null;

		for (AlbumInfo album : albumMap.values()) {
			totalPhotos += album.photoCount;
			totalVideos += album.videoCount;
			if (latestThumbnail == null) {
				latestThumbnail = album.thumbnailUri;
			}
		}

		if (totalPhotos + totalVideos > 0 && latestThumbnail != null) {
			AlbumInfo allMedia = new AlbumInfo("all_media", "All Media", totalPhotos, totalVideos, latestThumbnail,
					null, "external_primary");
			albums.add(allMedia);
		}

		// Add individual albums
		albums.addAll(albumMap.values());

		return albums;
	}

	/**
	 * Load image albums and populate the map
	 */
	public static void loadImageAlbums(Context context, Map<String, AlbumInfo> albumMap) {
		String[] projection = { MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
				MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH,
				MediaStore.Images.Media.VOLUME_NAME };

		try (Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
				projection, null, null, MediaStore.Images.Media.DATE_TAKEN + " DESC")) {
			if (cursor == null || cursor.getCount() == 0)
				return;

			int bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID);
			int bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
			int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
			int relativePathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH);
			int volumeNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.VOLUME_NAME);

			while (cursor.moveToNext()) {
				String bucketId = cursor.getString(bucketIdCol);
				String bucketName = cursor.getString(bucketNameCol);
				long imageId = cursor.getLong(idCol);
				String relativePath = cursor.getString(relativePathCol);
				String volumeName = cursor.getString(volumeNameCol);

				Uri thumbnailUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);

				AlbumInfo album = albumMap.get(bucketId);
				if (album == null) {
					album = new AlbumInfo(bucketId, bucketName, 1, // photoCount
							0, // videoCount
							thumbnailUri, relativePath, volumeName);
					albumMap.put(bucketId, album);
				} else {
					album.photoCount++;
					album.thumbnailUri = thumbnailUri; // Update to latest
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error loading image albums", e);
		}
	}

	/**
	 * Load video albums and merge into existing map
	 */
	public static void loadVideoAlbums(Context context, Map<String, AlbumInfo> albumMap) {
		String[] projection = { MediaStore.Video.Media.BUCKET_ID, MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
				MediaStore.Video.Media._ID, MediaStore.Video.Media.RELATIVE_PATH, MediaStore.Video.Media.VOLUME_NAME };

		try (Cursor cursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
				null, null, MediaStore.Video.Media.DATE_TAKEN + " DESC")) {
			if (cursor == null || cursor.getCount() == 0)
				return;

			int bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID);
			int bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
			int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
			int relativePathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH);
			int volumeNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.VOLUME_NAME);

			while (cursor.moveToNext()) {
				String bucketId = cursor.getString(bucketIdCol);
				String bucketName = cursor.getString(bucketNameCol);
				long videoId = cursor.getLong(idCol);
				String relativePath = cursor.getString(relativePathCol);
				String volumeName = cursor.getString(volumeNameCol);

				Uri thumbnailUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId);

				AlbumInfo album = albumMap.get(bucketId);
				if (album == null) {
					// Video-only album
					album = new AlbumInfo(bucketId, bucketName, 0, // photoCount
							1, // videoCount
							thumbnailUri, relativePath, volumeName);
					albumMap.put(bucketId, album);
				} else {
					// Album already has photos, increment video count
					album.videoCount++;
					// Keep photo thumbnail if it exists, otherwise use video
					if (album.photoCount == 0) {
						album.thumbnailUri = thumbnailUri;
					}
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error loading video albums", e);
		}
	}
    
    	/**
	 * Load media items (photos + videos) for a specific album.
	 * Based on your existing loadPhotosFromMediaStore() logic.
	 */
	public static List<MediaItems> loadMediaForAlbum(Context context, String albumId, String albumName,
			String folderName) {
		List<MediaItems> items = new ArrayList<>();

		// Load images
		items.addAll(loadImagesForAlbum(context, albumId, albumName, folderName));

		// Load videos
		items.addAll(loadVideosForAlbum(context, albumId, albumName, folderName));

		return items;
	}
    
	/**
	 * Load images for a specific album with progressive loading support
	 */
	public static List<MediaItems> loadImagesForAlbum(Context context, String albumId, String albumName,
			String folderName) {
		return loadImagesForAlbum(context, albumId, albumName, folderName, 0, 0);
	}

	/**
	 * Load images for a specific album with pagination support (for progressive loading)
	 * limit = 0 means load all, offset = starting position
	 */
	public static List<MediaItems> loadImagesForAlbum(Context context, String albumId, String albumName,
			String folderName, int limit, int offset) {
		List<MediaItems> images = new ArrayList<>();

		String[] projection = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
				MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.WIDTH,
				MediaStore.Images.Media.HEIGHT, MediaStore.Images.Media.BUCKET_ID,
				MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH,
				MediaStore.Images.Media.VOLUME_NAME, MediaStore.Images.Media.MIME_TYPE };

		String selection;
		String[] selectionArgs;

		// Handle "all_media" special case
		if (albumId != null && albumId.equals("all_media")) {
			selection = null;
			selectionArgs = null;
		} else if (albumName != null && !albumName.isEmpty()) {
			selection = MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " = ?";
			selectionArgs = new String[] { albumName };
		} else if (folderName != null && !folderName.isEmpty()) {
			selection = MediaStore.Images.Media.RELATIVE_PATH + " = ?";
			selectionArgs = new String[] { folderName };
		} else {
			selection = MediaStore.Images.Media.BUCKET_ID + " = ?";
			selectionArgs = new String[] { albumId };
		}

		try (Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
				projection, selection, selectionArgs, MediaStore.Images.Media.DATE_MODIFIED + " DESC")) {
			if (cursor == null)
				return images;

			int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
			int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
			int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
			int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
			int widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH);
			int heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT);
			int mimeTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE);
			int volumeNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.VOLUME_NAME);

			// Move to offset position if specified
			if (offset > 0 && !cursor.moveToPosition(offset)) {
				return images; // Cannot move to requested offset
			}

			int count = 0;
			while (cursor.moveToNext() && (limit == 0 || count < limit)) {
				long id = cursor.getLong(idCol);
				String name = cursor.getString(nameCol);
				long dateModified = cursor.getLong(dateCol);
				long size = cursor.getLong(sizeCol);
				int width = cursor.getInt(widthCol);
				int height = cursor.getInt(heightCol);
				String mimeType = cursor.getString(mimeTypeCol);
				String volumeName = cursor.getString(volumeNameCol);

				Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);

				// Detect animated images
				MediaItems.MediaType type = detectImageType(context, uri, mimeType);

				MediaItems item = new MediaItems(String.valueOf(id), name, dateModified, size, uri, width, height,
						type);
				item.volumeName = volumeName;
				item.isOnSdCard = !volumeName.equals("external_primary");

				images.add(item);
				count++;
			}
		} catch (Exception e) {
			Log.e(TAG, "Error loading images", e);
		}

		return images;
	}

	/**
	 * Load videos for a specific album with progressive loading support
	 */
	public static List<MediaItems> loadVideosForAlbum(Context context, String albumId, String albumName,
			String folderName) {
		return loadVideosForAlbum(context, albumId, albumName, folderName, 0, 0);
	}

	/**
	 * Load videos for a specific album with pagination support (for progressive loading)
	 * limit = 0 means load all, offset = starting position
	 */
	public static List<MediaItems> loadVideosForAlbum(Context context, String albumId, String albumName,
			String folderName, int limit, int offset) {
		List<MediaItems> videos = new ArrayList<>();

		String[] projection = { MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
				MediaStore.Video.Media.DATE_MODIFIED, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.WIDTH,
				MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.BUCKET_ID,
				MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.RELATIVE_PATH,
				MediaStore.Video.Media.VOLUME_NAME };

		String selection;
		String[] selectionArgs;

		// Handle "all_media" special case
		if (albumId != null && albumId.equals("all_media")) {
			selection = null;
			selectionArgs = null;
		} else if (albumName != null && !albumName.isEmpty()) {
			selection = MediaStore.Video.Media.BUCKET_DISPLAY_NAME + " = ?";
			selectionArgs = new String[] { albumName };
		} else if (folderName != null && !folderName.isEmpty()) {
			selection = MediaStore.Video.Media.RELATIVE_PATH + " = ?";
			selectionArgs = new String[] { folderName };
		} else {
			selection = MediaStore.Video.Media.BUCKET_ID + " = ?";
			selectionArgs = new String[] { albumId };
		}

		try (Cursor cursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
				selection, selectionArgs, MediaStore.Video.Media.DATE_MODIFIED + " DESC")) {
			if (cursor == null)
				return videos;

			int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
			int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
			int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED);
			int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
			int widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH);
			int heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT);
			int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
			int volumeNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.VOLUME_NAME);

			// Move to offset position if specified
			if (offset > 0 && !cursor.moveToPosition(offset)) {
				return videos; // Cannot move to requested offset
			}

			int count = 0;
			while (cursor.moveToNext() && (limit == 0 || count < limit)) {
				long id = cursor.getLong(idCol);
				String name = cursor.getString(nameCol);
				long dateModified = cursor.getLong(dateCol);
				long size = cursor.getLong(sizeCol);
				int width = cursor.getInt(widthCol);
				int height = cursor.getInt(heightCol);
				long duration = cursor.getLong(durationCol);
				String volumeName = cursor.getString(volumeNameCol);

				Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);

				MediaItems item = new MediaItems(String.valueOf(id), name, dateModified, size, uri, width, height,
						duration);
				item.volumeName = volumeName;
				item.isOnSdCard = !volumeName.equals("external_primary");

				videos.add(item);
				count++;
			}
		} catch (Exception e) {
			Log.e(TAG, "Error loading videos", e);
		}

		return videos;
	}

	// ... (keep the rest of the methods unchanged) ...
	/**
	 * Detect if image is animated (GIF or animated WebP)
	 * Uses your existing logic from ViewerFragment
	 */
	private static boolean isAnimatedWebp(byte[] header) {
		try {
			// Must start with RIFF and contain WEBP
			if (header.length < 12 || header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F'
					|| header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
				return false;
			}

			// Need to read more bytes to check for ANIM chunk
			// For now, this is just checking the header exists
			// TO PROPERLY DETECT: Need to read VP8X chunk at bytes 12-16
			// and check bit 1 of flags (animation flag)

			return false; // CHANGED: Default to false, only true if we find ANIM chunk
		} catch (Exception e) {
			return false;
		}
	}

	// BETTER: Replace detectImageType with this more thorough check
	private static MediaItems.MediaType detectImageType(Context context, Uri uri, String mimeType) {
		if (mimeType == null) {
			return MediaItems.MediaType.IMAGE;
		}

		// Check GIF
		if (mimeType.equals("image/gif")) {
			return MediaItems.MediaType.ANIMATED_IMAGE;
		}

		// Check animated WebP - need to read more than just header
		if (mimeType.contains("webp")) {
			try (InputStream is = context.getContentResolver().openInputStream(uri)) {
				if (is != null) {
					byte[] buffer = new byte[30]; // Read more bytes
					int bytesRead = is.read(buffer);

					if (bytesRead >= 30) {
						// Check RIFF header
						if (buffer[0] == 'R' && buffer[1] == 'I' && buffer[2] == 'F' && buffer[3] == 'F'
								&& buffer[8] == 'W' && buffer[9] == 'E' && buffer[10] == 'B' && buffer[11] == 'P') {

							// Check for VP8X chunk (extended format with animation)
							if (buffer[12] == 'V' && buffer[13] == 'P' && buffer[14] == '8' && buffer[15] == 'X') {
								// Byte 20 contains flags, bit 1 is animation flag
								byte flags = buffer[20];
								boolean hasAnimation = (flags & 0x02) != 0;

								if (hasAnimation) {
									return MediaItems.MediaType.ANIMATED_IMAGE;
								}
							}
						}
					}
				}
			} catch (Exception ignored) {
			}
		}

		return MediaItems.MediaType.IMAGE;
	}
}