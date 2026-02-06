package com.ccko.pikxplus.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.InputStream;

public class ImageLoader {

  // Maximum dimensions for different use cases
  public static final int MAX_THUMBNAIL = 400;
  public static final int MAX_PREVIEW = 2048; // 2K for preview
  public static final int MAX_FULL = 4096; // 4K for zoom
/*
  // For thumbnails (400x400 max)
  Bitmap thumbnail = ImageLoader.loadWithLimit(context, uri, ImageLoader.MAX_THUMBNAIL);
  // For preview (2K max)
  Bitmap preview = ImageLoader.loadWithLimit(context, uri, ImageLoader.MAX_PREVIEW);
  // For full viewer with zoom (4K max)
  Bitmap fullImage = ImageLoader.loadWithLimit(context, uri, ImageLoader.MAX_FULL);
*/
  public static Bitmap loadWithLimit(Context context, Uri uri, int maxDimension) {
    try {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;

      try (InputStream is = context.getContentResolver().openInputStream(uri)) {
        if (is == null) return null;
        BitmapFactory.decodeStream(is, null, options);
      }

      int width = options.outWidth;
      int height = options.outHeight;

      // YOUR SIMPLE CONDITION
      if (width > maxDimension || height > maxDimension) {
        // Scale down proportionally
        float scale = Math.min((float) maxDimension / width, (float) maxDimension / height);
        width = (int) (width * scale);
        height = (int) (height * scale);
      }

      // Calculate sample size for these dimensions
      options.inSampleSize = calculateInSampleSize(options, width, height);
      options.inJustDecodeBounds = false;
      options.inPreferredConfig = Bitmap.Config.RGB_565;

      try (InputStream is = context.getContentResolver().openInputStream(uri)) {
        if (is == null) return null;
        return BitmapFactory.decodeStream(is, null, options);
      }
    } catch (Exception e) {
      return null;
    }
  }

  private static int calculateInSampleSize(
      BitmapFactory.Options options, int reqWidth, int reqHeight) {
    final int height = options.outHeight;
    final int width = options.outWidth;
    int inSampleSize = 1;

    if (height > reqHeight || width > reqWidth) {
      final int halfHeight = height / 2;
      final int halfWidth = width / 2;

      while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
        inSampleSize *= 2;
      }
    }
    return inSampleSize;
  }
}
/*
public class ImageLoader {

  public static Bitmap decodeSampledBitmapFromUri(
      Context context, Uri uri, int reqWidth, int reqHeight) {
    try {
      // First decode with inJustDecodeBounds=true to check dimensions
      final BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;

      try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
        if (inputStream == null) return null;
        BitmapFactory.decodeStream(inputStream, null, options);
      }

      // Calculate inSampleSize
      options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

      // Decode bitmap with inSampleSize set
      options.inJustDecodeBounds = false;
      options.inPreferredConfig = Bitmap.Config.RGB_565; // Lower memory usage

      try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
        if (inputStream == null) return null;
        return BitmapFactory.decodeStream(inputStream, null, options);
      }
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  private static int calculateInSampleSize(
      BitmapFactory.Options options, int reqWidth, int reqHeight) {
    final int height = options.outHeight;
    final int width = options.outWidth;
    int inSampleSize = 1;

    if (height > reqHeight || width > reqWidth) {
      final int halfHeight = height / 2;
      final int halfWidth = width / 2;

      while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
        inSampleSize *= 2;
      }
    }
    return inSampleSize;
  }
}
*/
