/*
		Copyright 2014 Giovanni Di Gregorio.

		Licensed under the Apache License, Version 2.0 (the "License");
		you may not use this file except in compliance with the License.
		You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

		Unless required by applicable law or agreed to in writing, software
		distributed under the License is distributed on an "AS IS" BASIS,
		WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
		See the License for the specific language governing permissions and
		limitations under the License.

		Modified by Aditya Karnam, Sept 17, 2019
 */

package com.tmantman.nativecamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.apache.cordova.camera.FileHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;

import android.Manifest;
import org.apache.cordova.PermissionHelper;

import static org.apache.cordova.camera.CameraLauncher.calculateSampleSize;

/**
 * This class launches the camera view, allows the user to take a picture,
 * closes the camera view, and returns the captured image. When the camera view
 * is closed, the screen displayed before the camera view was shown is
 * redisplayed.
 */
public class NativeCameraLauncher extends CordovaPlugin {

	private static final String LOG_TAG = "NativeCameraLauncher";

	private static final int DATA_URL = 0;              // Return base64 encoded string
	private static final int FILE_URI = 1;              // Return file uri (content://media/external/images/media/2 for Android)
	private static final int NATIVE_URI = 2;                    // On Android, this is the same as FILE_URI

	private static final int PHOTOLIBRARY = 0;          // Choose image from picture library (same as SAVEDPHOTOALBUM for Android)
	private static final int CAMERA = 1;                // Take picture from camera
	private static final int SAVEDPHOTOALBUM = 2;       // Choose image from picture library (same as PHOTOLIBRARY for Android)

	private static final int PICTURE = 0;               // allow selection of still pictures only. DEFAULT. Will return format specified via DestinationType
	private static final int VIDEO = 1;                 // allow selection of video only, ONLY RETURNS URL
	private static final int ALLMEDIA = 2;              // allow selection from all media types

	private static final int JPEG = 0;                  // Take a picture of type JPEG
	private static final int PNG = 1;                   // Take a picture of type PNG
	private static final String GET_PICTURE = "Get Picture";
	private static final String GET_VIDEO = "Get Video";
	private static final String GET_All = "Get All";

	public static final int PERMISSION_DENIED_ERROR = 20;
	public static final int TAKE_PIC_SEC = 0;
	public static final int SAVE_TO_ALBUM_SEC = 1;

	private static final String JPEG_TYPE = "jpg";
	private static final String PNG_TYPE = "png";
	private static final String TIME_FORMAT = "yyyyMMdd_HHmmss";
	private static final String JPEG_MIME_TYPE = "image/jpeg";
	private static final String JPEG_EXTENSION = "." + JPEG_TYPE;
	private static final String PNG_EXTENSION = "." + PNG_TYPE;

	private int mQuality;
	private int targetWidth;
	private int targetHeight;
	private int srcType;
	private int destType;
	private int encodingType;
	private int mediaType;                  // What type of media to retrieve
	private boolean correctOrientation;     // Should the pictures orientation be corrected
	private boolean saveToPhotoAlbum;       // Should the picture be saved to the device's photo album
	private boolean orientationCorrected;   // Has the picture's orientation been corrected
	private boolean allowEdit;              // Should we allow the user to crop the image.
	private Uri imageUri;
	private File photo;
	private static final String _DATA = "_data";
	private CallbackContext callbackContext;
	private String date = null;
	private ExifHelper exifData;            // Exif data from source

	public NativeCameraLauncher() {
	}

	void failPicture(String reason) {
		callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, reason));
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		PluginResult.Status status = PluginResult.Status.OK;
		String result = "";
		this.callbackContext = callbackContext;
		try {
			if (action.equals("takePicture")) {
				this.targetHeight = 0;
				this.targetWidth = 0;
				this.mQuality = 50;
				this.mQuality = args.getInt(0);
				this.destType = args.getInt(1);
				this.srcType = args.getInt(2);
				this.targetWidth = args.getInt(3);
				this.targetHeight = args.getInt(4);
				this.encodingType = args.getInt(5);
				this.mediaType = args.getInt(6);
				this.allowEdit = args.getBoolean(7);
				this.correctOrientation = args.getBoolean(8);
				this.saveToPhotoAlbum = args.getBoolean(9);
				this.takePicture();
				PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
				r.setKeepCallback(true);
				callbackContext.sendPluginResult(r);
				return true;
			}
			return false;
		} catch (JSONException e) {
			e.printStackTrace();
			callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));
			return true;
		}
	}

	public void takePicture() {
		// Camera
		if (this.srcType == 1) {
			// Save the number of images currently on disk for later
			Intent intent = new Intent(this.cordova.getActivity().getApplicationContext(), com.tmantman.nativecamera.CameraActivity.class);
			this.photo = createCaptureFile();
			this.imageUri = Uri.fromFile(photo);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, this.imageUri);
			this.cordova.startActivityForResult((CordovaPlugin) this, intent, 1);
		}
		else if ((this.srcType == 0) || (this.srcType == 2)) {
			// FIXME: Stop always requesting the permission
			if(!PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
				PermissionHelper.requestPermission(this, SAVE_TO_ALBUM_SEC, Manifest.permission.READ_EXTERNAL_STORAGE);
			} else {
				this.getImage(this.srcType, this.destType, this.encodingType);
			}			
		}
	}

	private File createCaptureFile() {
		File oldFile = new File(getTempDirectoryPath(this.cordova.getActivity().getApplicationContext()), "Pic-" + this.date + ".jpg");
		if(oldFile.exists())
			oldFile.delete();
		Calendar c = Calendar.getInstance();
		this.date = "" + c.get(Calendar.DAY_OF_MONTH)
					+ c.get(Calendar.MONTH)
					+ c.get(Calendar.YEAR)
					+ c.get(Calendar.HOUR_OF_DAY)
					+ c.get(Calendar.MINUTE)
					+ c.get(Calendar.SECOND);
		File photo = new File(getTempDirectoryPath(this.cordova.getActivity().getApplicationContext()), "Pic-" + this.date + ".jpg");
		return photo;
	}


	/**
	 * Get image from photo library.
	 *
	 * @param srcType           The album to get image from.
	 * @param returnType        Set the type of image to return.
	 * @param encodingType
	 */
	// TODO: Images selected from SDCARD don't display correctly, but from CAMERA ALBUM do!
	// TODO: Images from kitkat filechooser not going into crop function
	public void getImage(int srcType, int returnType, int encodingType) {
		Intent intent = new Intent();
		String title = GET_PICTURE;
		// TODO only pictures
		intent.setType("image/*");
		intent.setAction(Intent.ACTION_GET_CONTENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		if (this.cordova != null) {
			this.cordova.startActivityForResult((CordovaPlugin) this, Intent.createChooser(intent,
					new String(title)), (srcType + 1) * 16 + returnType + 1);
		}
	}


	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// If image available
		if (resultCode == Activity.RESULT_OK) {
			if (srcType == CAMERA) {
				int rotate = 0;
				try {
					String uriString = this.imageUri.toString();
					String fileLocation = FileHelper.getRealPath(uriString, this.cordova);
					String mimeType = FileHelper.getMimeType(uriString, this.cordova);
					Bitmap bitmap = null;
					try {
						bitmap = getScaledAndRotatedBitmap(uriString);
					} catch (IOException e) {
						e.printStackTrace();
					}
					if (bitmap == null) {
						LOG.d(LOG_TAG, "I either have a null image path or bitmap");
						this.failPicture("Unable to create bitmap!");
						return;
					}


					String modifiedPath = this.outputModifiedBitmap(bitmap, this.imageUri);
					Log.i(LOG_TAG, "Modified Image Path: " + modifiedPath);

					ExifHelper exif = new ExifHelper();
					exif.createInFile(this.imageUri.getPath());
					exif.readExifData();
					rotate = exif.getOrientation();
					Log.i(LOG_TAG, "Uncompressed image rotation value: " + rotate);

					exif.resetOrientation();
					exif.createOutFile(this.imageUri.getPath());
					exif.writeExifData();

					JSONObject returnObject = new JSONObject();
					returnObject.put("url", this.imageUri.toString());
					returnObject.put("rotation", rotate);

					Log.i(LOG_TAG, "Return data: " + returnObject.toString());

					PluginResult result = new PluginResult(PluginResult.Status.OK, returnObject);

					Log.i(LOG_TAG, "Final Exif orientation value: " + exif.getOrientation());

					// Send Uri back to JavaScript for viewing image
					this.callbackContext.sendPluginResult(result);

				} catch (IOException e) {
					e.printStackTrace();
					this.failPicture("Error capturing image.");
				} catch (JSONException e) {
					e.printStackTrace();
					this.failPicture("Error capturing image.");
				}
			}
			// If retrieving photo from library
			else if ((srcType == PHOTOLIBRARY) || (srcType == SAVEDPHOTOALBUM)) {
				if (resultCode == Activity.RESULT_OK && intent != null) {
					final Intent i = intent;
					final int finalDestType = destType;
					cordova.getThreadPool().execute(new Runnable() {
						public void run() {
							processResultFromGallery(finalDestType, i);
						}
					});
				} else if (resultCode == Activity.RESULT_CANCELED) {
					this.failPicture("No Image Selected");
				} else {
					this.failPicture("Selection did not complete!");
				}
			}
		}

		// If cancelled
		else if (resultCode == Activity.RESULT_CANCELED) {
			this.failPicture("Camera cancelled.");
		}

		// If something else
		else {
			this.failPicture("Did not complete!");
		}
	}

    /**
     * Applies all needed transformation to the image received from the gallery.
     *
     * @param destType In which form should we return the image
     * @param intent   An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     */
    private void processResultFromGallery(int destType, Intent intent) {
        Uri uri = intent.getData();
        if (uri == null) {
            this.failPicture("null data from photo library");
            return;
        }
		String fileLocation = FileHelper.getRealPath(uri, this.cordova);
		LOG.d(LOG_TAG, "File location is: " + fileLocation);
        String uriString = uri.toString();
		String mimeType = FileHelper.getMimeType(uriString, this.cordova);
		Bitmap bitmap = null;
		try {
			bitmap = getScaledAndRotatedBitmap(uriString);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (bitmap == null) {
			LOG.d(LOG_TAG, "I either have a null image path or bitmap");
			this.failPicture("Unable to create bitmap!");
			return;
		}


		// If sending filename back
		if (destType == FILE_URI || destType == NATIVE_URI) {
			// Did we modify the image?
			if ( (this.targetHeight > 0 && this.targetWidth > 0) ||
					(this.correctOrientation && this.orientationCorrected) ||
					!mimeType.equalsIgnoreCase(getMimetypeForFormat(encodingType)))
			{
				try {
					String modifiedPath = this.outputModifiedBitmap(bitmap, uri);
					// The modified image is cached by the app in order to get around this and not have to delete you
					// application cache I'm adding the current system time to the end of the file url.
					this.callbackContext.success("file://" + modifiedPath + "?" + System.currentTimeMillis());

				} catch (Exception e) {
					e.printStackTrace();
					this.failPicture("Error retrieving image.");
				}
			} else {
				this.callbackContext.success(fileLocation);
			}
		}
		if (bitmap != null) {
			bitmap.recycle();
			bitmap = null;
		}
		System.gc();
    }

	/**
	 * Converts output image format int value to string value of mime type.
	 * @param outputFormat int Output format of camera API.
	 *                     Must be value of either JPEG or PNG constant
	 * @return String String value of mime type or empty string if mime type is not supported
	 */
	private String getMimetypeForFormat(int outputFormat) {
		if (outputFormat == JPEG) return JPEG_MIME_TYPE;
		return "";
	}

	private String getTempDirectoryPath() {
		File cache = null;

		// SD Card Mounted
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			cache = cordova.getActivity().getExternalCacheDir();
		}
		// Use internal storage
		else {
			cache = cordova.getActivity().getCacheDir();
		}

		// Create the cache directory if it doesn't exist
		cache.mkdirs();
		return cache.getAbsolutePath();
	}

	private String outputModifiedBitmap(Bitmap bitmap, Uri uri) throws IOException {
		// Some content: URIs do not map to file paths (e.g. picasa).
		String realPath = FileHelper.getRealPath(uri, this.cordova);

		// Get filename from uri
		String fileName = realPath != null ?
				realPath.substring(realPath.lastIndexOf('/') + 1) :
				"modified." + (this.encodingType == JPEG ? JPEG_TYPE : PNG_TYPE);

		String timeStamp = new SimpleDateFormat(TIME_FORMAT).format(new Date());
		//String fileName = "IMG_" + timeStamp + (this.encodingType == JPEG ? ".jpg" : ".png");
		String modifiedPath = getTempDirectoryPath() + "/" + fileName;

		OutputStream os = new FileOutputStream(modifiedPath);
		CompressFormat compressFormat = this.encodingType == JPEG ?
				CompressFormat.JPEG :
				CompressFormat.PNG;

		bitmap.compress(compressFormat, this.mQuality, os);
		os.close();

		if (exifData != null && this.encodingType == JPEG) {
			try {
				if (this.correctOrientation && this.orientationCorrected) {
					exifData.resetOrientation();
				}
				exifData.createOutFile(modifiedPath);
				exifData.writeExifData();
				exifData = null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return modifiedPath;
	}


	private String getTempDirectoryPath(Context ctx) {
		File cache = null;

		// SD Card Mounted
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			cache = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath()
					+ "/Android/data/"
					+ ctx.getPackageName() + "/cache/");
		}
		// Use internal storage
		else {
			cache = ctx.getCacheDir();
		}

		// Create the cache directory if it doesn't exist
		if (!cache.exists()) {
			cache.mkdirs();
		}

		return cache.getAbsolutePath();
	}

	@Override
	public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
		this.callbackContext = callbackContext;

		this.mQuality = state.getInt("mQuality");
		this.targetWidth = state.getInt("targetWidth");
		this.targetHeight = state.getInt("targetHeight");

		this.imageUri = state.getParcelable("imageUri");
		this.photo = (File) state.getSerializable("photo");

		this.date = state.getString("date");

		super.onRestoreStateForActivityResult(state, callbackContext);
	}

	@Override
	public Bundle onSaveInstanceState() {

		Bundle state = new Bundle();
		state.putInt("mQuality", mQuality);
		state.putInt("targetWidth", targetWidth);
		state.putInt("targetHeight", targetHeight);
		state.putString("date", date);
		state.putParcelable("imageUri", imageUri);
		state.putSerializable("photo", photo);

		return state;
	}

	/**
	 * Return a scaled and rotated bitmap based on the target width and height
	 *
	 * @param imageUrl
	 * @return
	 * @throws IOException
	 */
	private Bitmap getScaledAndRotatedBitmap(String imageUrl) throws IOException {
		// If no new width or height were specified, and orientation is not needed return the original bitmap
		if (this.targetWidth <= 0 && this.targetHeight <= 0 && !(this.correctOrientation)) {
			InputStream fileStream = null;
			Bitmap image = null;
			try {
				fileStream = FileHelper.getInputStreamFromUriString(imageUrl, cordova);
				image = BitmapFactory.decodeStream(fileStream);
			}  catch (OutOfMemoryError e) {
				callbackContext.error(e.getLocalizedMessage());
			} catch (Exception e){
				callbackContext.error(e.getLocalizedMessage());
			}
			finally {
				if (fileStream != null) {
					try {
						fileStream.close();
					} catch (IOException e) {
						LOG.d(LOG_TAG, "Exception while closing file input stream.");
					}
				}
			}
			return image;
		}


        /*  Copy the inputstream to a temporary file on the device.
            We then use this temporary file to determine the width/height/orientation.
            This is the only way to determine the orientation of the photo coming from 3rd party providers (Google Drive, Dropbox,etc)
            This also ensures we create a scaled bitmap with the correct orientation

             We delete the temporary file once we are done
         */
		File localFile = null;
		Uri galleryUri = null;
		int rotate = 0;
		try {
			InputStream fileStream = FileHelper.getInputStreamFromUriString(imageUrl, cordova);
			if (fileStream != null) {
				// Generate a temporary file
				String timeStamp = new SimpleDateFormat(TIME_FORMAT).format(new Date());
				String fileName = "IMG_" + timeStamp + (this.encodingType == JPEG ? JPEG_EXTENSION : PNG_EXTENSION);
				localFile = new File(getTempDirectoryPath() + fileName);
				galleryUri = Uri.fromFile(localFile);
				writeUncompressedImage(fileStream, galleryUri);
				try {
					String mimeType = FileHelper.getMimeType(imageUrl.toString(), cordova);
					if (JPEG_MIME_TYPE.equalsIgnoreCase(mimeType)) {
						//  ExifInterface doesn't like the file:// prefix
						String filePath = galleryUri.toString().replace("file://", "");
						// read exifData of source
						ExifHelper exifData = new ExifHelper();
						exifData.createInFile(filePath);
						exifData.readExifData();
						// Use ExifInterface to pull rotation information
						if (this.correctOrientation) {
							ExifInterface exif = new ExifInterface(filePath);
							rotate = exifToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED));
						}
					}
				} catch (Exception oe) {
					LOG.w(LOG_TAG,"Unable to read Exif data: "+ oe.toString());
					rotate = 0;
				}
			}
		}
		catch (Exception e)
		{
			LOG.e(LOG_TAG,"Exception while getting input stream: "+ e.toString());
			return null;
		}



		try {
			// figure out the original width and height of the image
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			InputStream fileStream = null;
			try {
				fileStream = FileHelper.getInputStreamFromUriString(galleryUri.toString(), cordova);
				BitmapFactory.decodeStream(fileStream, null, options);
			} finally {
				if (fileStream != null) {
					try {
						fileStream.close();
					} catch (IOException e) {
						LOG.d(LOG_TAG, "Exception while closing file input stream.");
					}
				}
			}


			//CB-2292: WTF? Why is the width null?
			if (options.outWidth == 0 || options.outHeight == 0) {
				return null;
			}

			// User didn't specify output dimensions, but they need orientation
			if (this.targetWidth <= 0 && this.targetHeight <= 0) {
				this.targetWidth = options.outWidth;
				this.targetHeight = options.outHeight;
			}

			// Setup target width/height based on orientation
			int rotatedWidth, rotatedHeight;
			boolean rotated= false;
			if (rotate == 90 || rotate == 270) {
				rotatedWidth = options.outHeight;
				rotatedHeight = options.outWidth;
				rotated = true;
			} else {
				rotatedWidth = options.outWidth;
				rotatedHeight = options.outHeight;
			}

			// determine the correct aspect ratio
			int[] widthHeight = calculateAspectRatio(rotatedWidth, rotatedHeight);


			// Load in the smallest bitmap possible that is closest to the size we want
			options.inJustDecodeBounds = false;
			options.inSampleSize = calculateSampleSize(rotatedWidth, rotatedHeight,  widthHeight[0], widthHeight[1]);
			Bitmap unscaledBitmap = null;
			try {
				fileStream = FileHelper.getInputStreamFromUriString(galleryUri.toString(), cordova);
				unscaledBitmap = BitmapFactory.decodeStream(fileStream, null, options);
			} finally {
				if (fileStream != null) {
					try {
						fileStream.close();
					} catch (IOException e) {
						LOG.d(LOG_TAG, "Exception while closing file input stream.");
					}
				}
			}
			if (unscaledBitmap == null) {
				return null;
			}

			int scaledWidth = (!rotated) ? widthHeight[0] : widthHeight[1];
			int scaledHeight = (!rotated) ? widthHeight[1] : widthHeight[0];

			Bitmap scaledBitmap = Bitmap.createScaledBitmap(unscaledBitmap, scaledWidth, scaledHeight, true);
			if (scaledBitmap != unscaledBitmap) {
				unscaledBitmap.recycle();
				unscaledBitmap = null;
			}
			if (this.correctOrientation && (rotate != 0)) {
				Matrix matrix = new Matrix();
				matrix.setRotate(rotate);
				try {
					scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
					this.orientationCorrected = true;
				} catch (OutOfMemoryError oom) {
					this.orientationCorrected = false;
				}
			}
			return scaledBitmap;
		}
		finally {
			// delete the temporary copy
			if (localFile != null) {
				localFile.delete();
			}
		}

	}

	/**
	 * Maintain the aspect ratio so the resulting image does not look smooshed
	 *
	 * @param origWidth
	 * @param origHeight
	 * @return
	 */
	public int[] calculateAspectRatio(int origWidth, int origHeight) {
		int newWidth = this.targetWidth;
		int newHeight = this.targetHeight;

		// If no new width or height were specified return the original bitmap
		if (newWidth <= 0 && newHeight <= 0) {
			newWidth = origWidth;
			newHeight = origHeight;
		}
		// Only the width was specified
		else if (newWidth > 0 && newHeight <= 0) {
			newHeight = (int)((double)(newWidth / (double)origWidth) * origHeight);
		}
		// only the height was specified
		else if (newWidth <= 0 && newHeight > 0) {
			newWidth = (int)((double)(newHeight / (double)origHeight) * origWidth);
		}
		// If the user specified both a positive width and height
		// (potentially different aspect ratio) then the width or height is
		// scaled so that the image fits while maintaining aspect ratio.
		// Alternatively, the specified width and height could have been
		// kept and Bitmap.SCALE_TO_FIT specified when scaling, but this
		// would result in whitespace in the new image.
		else {
			double newRatio = newWidth / (double) newHeight;
			double origRatio = origWidth / (double) origHeight;

			if (origRatio > newRatio) {
				newHeight = (newWidth * origHeight) / origWidth;
			} else if (origRatio < newRatio) {
				newWidth = (newHeight * origWidth) / origHeight;
			}
		}

		int[] retval = new int[2];
		retval[0] = newWidth;
		retval[1] = newHeight;
		return retval;
	}

	/**
	 * Write an inputstream to local disk
	 *
	 * @param fis - The InputStream to write
	 * @param dest - Destination on disk to write to
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void writeUncompressedImage(InputStream fis, Uri dest) throws FileNotFoundException,
			IOException {
		OutputStream os = null;
		try {
			os = this.cordova.getActivity().getContentResolver().openOutputStream(dest);
			byte[] buffer = new byte[4096];
			int len;
			while ((len = fis.read(buffer)) != -1) {
				os.write(buffer, 0, len);
			}
			os.flush();
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					LOG.d(LOG_TAG, "Exception while closing output stream.");
				}
			}
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					LOG.d(LOG_TAG, "Exception while closing file input stream.");
				}
			}
		}
	}
	/**
	 * In the special case where the default width, height and quality are unchanged
	 * we just write the file out to disk saving the expensive Bitmap.compress function.
	 *
	 * @param src
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void writeUncompressedImage(Uri src, Uri dest) throws FileNotFoundException,
			IOException {

		FileInputStream fis = new FileInputStream(FileHelper.stripFileProtocol(src.toString()));
		writeUncompressedImage(fis, dest);

	}

	private int exifToDegrees(int exifOrientation) {
		if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
			return 90;
		} else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
			return 180;
		} else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
			return 270;
		} else {
			return 0;
		}
	}
}
