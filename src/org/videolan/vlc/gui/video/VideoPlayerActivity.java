/*****************************************************************************
 * VideoPlayerActivity.java
 *****************************************************************************
 * Copyright © 2011-2013 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.gui.video;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.Media;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.Util;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.WeakHandler;
import org.videolan.vlc.gui.CommonDialogs;
import org.videolan.vlc.gui.CommonDialogs.MenuType;
import org.videolan.vlc.gui.PreferencesActivity;
import org.videolan.vlc.gui.audio.AudioPlayerFragment;
import org.videolan.vlc.interfaces.IPlayerControl;
import org.videolan.vlc.interfaces.OnPlayerControlListener;
import org.videolan.vlc.widget.PlayerControlClassic;
import org.videolan.vlc.widget.PlayerControlWheel;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings.SettingNotFoundException;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.softwarrior.libtorrent.LibTorrent;
import com.tudelft.triblersvod.example.FilePriority;
import com.tudelft.triblersvod.example.R;
import com.tudelft.triblersvod.example.StorageModes;
import com.tudelft.triblersvod.example.TorrentState;

public class VideoPlayerActivity extends Activity implements IVideoPlayer {
	public final static String TAG = "VLC/VideoPlayerActivity";

	public final static String KEY_LATEST_CONTENT = "key_latest_content";
	public final static String KEY_LATEST_CONTENT_DIR = "key_latest_content_dir";

	// Internal intent identifier to distinguish between internal launch and
	// external intent.
	protected final static String PLAY_FROM_VIDEOGRID = "org.videolan.vlc.gui.video.PLAY_FROM_VIDEOGRID";

	private SurfaceView mSurface;
	private SurfaceHolder mSurfaceHolder;
	private FrameLayout mSurfaceFrame;
	private LibVLC mLibVLC;
	private String mLocation;

	private static final int SURFACE_BEST_FIT = 0;
	private static final int SURFACE_FIT_HORIZONTAL = 1;
	private static final int SURFACE_FIT_VERTICAL = 2;
	private static final int SURFACE_FILL = 3;
	private static final int SURFACE_16_9 = 4;
	private static final int SURFACE_4_3 = 5;
	private static final int SURFACE_ORIGINAL = 6;
	private int mCurrentSize = SURFACE_BEST_FIT;

	/** Overlay */
	private View mOverlayHeader;
	private View mOverlayLock;
	private View mOverlayOption;
	private View mOverlayProgress;
	private View mOverlayInterface;
	private static final int OVERLAY_TIMEOUT = 4000;
	private static final int OVERLAY_INFINITE = 3600000;
	private static final int FADE_OUT = 1;
	private static final int SHOW_PROGRESS = 2;
	private static final int SURFACE_SIZE = 3;
	private static final int FADE_OUT_INFO = 4;
	private boolean mDragging;
	private boolean mShowing;
	private int mUiVisibility = -1;
	private SeekBar mSeekbar;
	private TextView mTitle;
	private TextView mSysTime;
	private TextView mBattery;
	private TextView mTime;
	private TextView mLength;
	private TextView mInfo;
	private IPlayerControl mControls;
	private boolean mEnableWheelbar;
	private boolean mEnableBrightnessGesture;
	private boolean mDisplayRemainingTime = false;
	private int mScreenOrientation;
	private ImageButton mAudioTrack;
	private ImageButton mSubtitle;
	private ImageButton mLock;
	private ImageButton mSize;
	private boolean mIsLocked = false;
	private int mLastAudioTrack = -1;
	private int mLastSpuTrack = -2;

	/**
	 * For uninterrupted switching between audio and video mode
	 */
	private boolean mSwitchingView;
	private boolean mEndReached;

	// Playlist
	private int savedIndexPosition = -1;

	// size of the video
	private int mVideoHeight;
	private int mVideoWidth;
	private int mVideoVisibleHeight;
	private int mVideoVisibleWidth;
	private int mSarNum;
	private int mSarDen;

	// Volume
	private AudioManager mAudioManager;
	private int mAudioMax;

	// Touch Events
	private static final int TOUCH_NONE = 0;
	private static final int TOUCH_VOLUME = 1;
	private static final int TOUCH_BRIGHTNESS = 2;
	private static final int TOUCH_SEEK = 3;
	private int mTouchAction;
	private int mSurfaceYDisplayRange;
	private float mTouchY, mTouchX, mVol;

	// Brightness
	private boolean mIsFirstBrightnessGesture = true;

	// Tracks & Subtitles
	private Map<Integer, String> mAudioTracksList;
	private Map<Integer, String> mSubtitleTracksList;
	/**
	 * Used to store a selected subtitle; see onActivityResult. It is possible
	 * to have multiple custom subs in one session (just like desktop VLC allows
	 * you as well.)
	 */
	private ArrayList<String> mSubtitleSelectedFiles = new ArrayList<String>();

	public VideoPlayerActivity() {

	}

	// TRIBLER
	private boolean torrentFile;
	private String contentName;
	private long fileLength;
	private int[] piecePriorities;
	private int firstPieceIndex = -1;
	private int lastPieceIndex = -1;
	public boolean initCompleted;
	private int CHECKSIZE = 25000000; // TODO resolution dependent
	private int CHECKSIZEMB = CHECKSIZE / (1024 * 1024);
	private int REQUESTSIZE = 2 * CHECKSIZE;
	private String testMagnet = "magnet:?xt=urn:btih:11B53B855B1487947276FB9C95F43085EA942AB1&dn=Tears+of+Steel+mkv&xl=647889040&dl=647889040&tr=udp://tracker.publicbt.com:80/announce&tr=udp://tracker.openbittorrent.com:80/announce&tr=http://exodus.desync.com:6969/announce";
	private boolean makeMagnetOnce = false;

	private LibTorrent libTorrent() {
		return ((VLCApplication) getApplication()).getLibTorrent();
	}

	@Override
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.player);

		// TRIBLER INIT
		SharedPreferences pref = getSharedPreferences("VlcSharedPreferences",
				MODE_PRIVATE);
		findViewById(R.id.libtorrent_debug)
				.setVisibility(
						(pref.getBoolean("enable_libtorrent_debug", true) ? View.VISIBLE
								: View.GONE));
		libTorrent();

		if (Util.isICSOrLater())
			getWindow()
					.getDecorView()
					.findViewById(android.R.id.content)
					.setOnSystemUiVisibilityChangeListener(
							new OnSystemUiVisibilityChangeListener() {
								@Override
								public void onSystemUiVisibilityChange(
										int visibility) {
									if (visibility == mUiVisibility)
										return;
									setSurfaceSize(mVideoWidth, mVideoHeight,
											mVideoVisibleWidth,
											mVideoVisibleHeight, mSarNum,
											mSarDen);
									if (visibility == View.SYSTEM_UI_FLAG_VISIBLE
											&& !mShowing) {
										showOverlay();
									}
									mUiVisibility = visibility;
								}
							});

		/** initialize Views an their Events */
		mOverlayHeader = findViewById(R.id.player_overlay_header);
		mOverlayLock = findViewById(R.id.lock_overlay);
		mOverlayOption = findViewById(R.id.option_overlay);
		mOverlayProgress = findViewById(R.id.progress_overlay);
		mOverlayInterface = findViewById(R.id.interface_overlay);

		/* header */
		mTitle = (TextView) findViewById(R.id.player_overlay_title);
		mSysTime = (TextView) findViewById(R.id.player_overlay_systime);
		mBattery = (TextView) findViewById(R.id.player_overlay_battery);

		// Position and remaining time
		mTime = (TextView) findViewById(R.id.player_overlay_time);
		mTime.setOnClickListener(mRemainingTimeListener);
		mLength = (TextView) findViewById(R.id.player_overlay_length);
		mLength.setOnClickListener(mRemainingTimeListener);

		// the info textView is not on the overlay
		mInfo = (TextView) findViewById(R.id.player_overlay_info);

		mEnableWheelbar = pref.getBoolean("enable_wheel_bar", false);
		mEnableBrightnessGesture = pref.getBoolean("enable_brightness_gesture",
				true);
		mScreenOrientation = Integer
				.valueOf(pref
						.getString("screen_orientation_value", "4" /* SCREEN_ORIENTATION_SENSOR */));

		mControls = mEnableWheelbar ? new PlayerControlWheel(this)
				: new PlayerControlClassic(this);
		mControls.setOnPlayerControlListener(mPlayerControlListener);
		FrameLayout mControlContainer = (FrameLayout) findViewById(R.id.player_control);
		mControlContainer.addView((View) mControls);

		mAudioTrack = (ImageButton) findViewById(R.id.player_overlay_audio);
		mAudioTrack.setVisibility(View.GONE);
		mSubtitle = (ImageButton) findViewById(R.id.player_overlay_subtitle);
		mSubtitle.setVisibility(View.GONE);

		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				/*
				 * FIXME The setTracksAndSubtitles method probably doesn't work
				 * in case of many many Tracks and Subtitles Moreover, in a
				 * video stream, if Tracks & Subtitles change, they won't be
				 * updated
				 */
				setESTrackLists();
			}
		}, 1500);

		mLock = (ImageButton) findViewById(R.id.lock_overlay_button);
		mLock.setOnClickListener(mLockListener);

		mSize = (ImageButton) findViewById(R.id.player_overlay_size);
		mSize.setOnClickListener(mSizeListener);

		mSurface = (SurfaceView) findViewById(R.id.player_surface);
		mSurfaceHolder = mSurface.getHolder();
		mSurfaceFrame = (FrameLayout) findViewById(R.id.player_surface_frame);
		String chroma = pref.getString("chroma_format", "");
		if (Util.isGingerbreadOrLater() && chroma.equals("YV12")) {
			mSurfaceHolder.setFormat(ImageFormat.YV12);
		} else if (chroma.equals("RV16")) {
			mSurfaceHolder.setFormat(PixelFormat.RGB_565);
		} else {
			mSurfaceHolder.setFormat(PixelFormat.RGBX_8888);
		}
		mSurfaceHolder.addCallback(mSurfaceCallback);

		mSeekbar = (SeekBar) findViewById(R.id.player_overlay_seekbar);
		mSeekbar.setOnSeekBarChangeListener(mSeekListener);

		mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
		mAudioMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

		mSwitchingView = false;
		mEndReached = false;

		// Clear the resume time, since it is only used for resumes in external
		// videos.
		SharedPreferences preferences = getSharedPreferences(
				PreferencesActivity.NAME, MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();
		editor.putLong(PreferencesActivity.VIDEO_RESUME_TIME, -1);
		// Also clear the subs list, because it is supposed to be per session
		// only (like desktop VLC). We don't want the customs subtitle file
		// to persist forever with this video.
		editor.putString(PreferencesActivity.VIDEO_SUBTITLE_FILES, null);
		editor.commit();

		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_BATTERY_CHANGED);
		filter.addAction(VLCApplication.SLEEP_INTENT);
		registerReceiver(mReceiver, filter);

		try {
			mLibVLC = Util.getLibVlcInstance();
		} catch (LibVlcException e) {
			Log.d(TAG, "LibVLC initialisation failed");
			return;
		}

		EventHandler em = EventHandler.getInstance();
		em.addHandler(eventHandler);

		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

		// 100 is the value for screen_orientation_start_lock
		setRequestedOrientation(mScreenOrientation != 100 ? mScreenOrientation
				: getScreenOrientation());
	}

	@Override
	protected void onStart() {
		super.onStart();
		showOverlay();
		mSwitchingView = false;
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (mSwitchingView) {
			Log.d(TAG, "mLocation = \"" + mLocation + "\"");
			AudioServiceController.getInstance().showWithoutParse(
					savedIndexPosition);
			AudioServiceController.getInstance().unbindAudioService(this);
			AudioPlayerFragment.start(this);
			return;
		}

		long time = mLibVLC.getTime();
		long length = mLibVLC.getLength();
		// remove saved position if in the last 5 seconds
		if (length - time < 5000)
			time = 0;
		else
			time -= 5000; // go back 5 seconds, to compensate loading time

		/*
		 * Pausing here generates errors because the vout is constantly trying
		 * to refresh itself every 80ms while the surface is not accessible
		 * anymore. To workaround that, we keep the last known position in the
		 * playlist in savedIndexPosition to be able to restore it during
		 * onResume().
		 */
		mLibVLC.stop();

		mSurface.setKeepScreenOn(false);

		SharedPreferences preferences = getSharedPreferences(
				PreferencesActivity.NAME, MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();
		// Save position
		if (time >= 0) {
			if (MediaDatabase.getInstance(this).mediaItemExists(mLocation)) {
				editor.putString(PreferencesActivity.LAST_MEDIA, mLocation);
				MediaDatabase.getInstance(this).updateMedia(mLocation,
						MediaDatabase.mediaColumn.MEDIA_TIME, time);
			} else {
				// Video file not in media library, store time just for
				// onResume()
				editor.putLong(PreferencesActivity.VIDEO_RESUME_TIME, time);
			}
		}
		// Save selected subtitles
		String subtitleList_serialized = null;
		if (mSubtitleSelectedFiles.size() > 0) {
			Log.d(TAG, "Saving selected subtitle files");
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try {
				ObjectOutputStream oos = new ObjectOutputStream(bos);
				oos.writeObject(mSubtitleSelectedFiles);
				subtitleList_serialized = bos.toString();
			} catch (IOException e) {
			}
		}
		editor.putString(PreferencesActivity.VIDEO_SUBTITLE_FILES,
				subtitleList_serialized);

		editor.commit();
		AudioServiceController.getInstance().unbindAudioService(this);

		// TRIBLER
		if (!contentName.isEmpty()) {
			libTorrent().PauseTorrent(contentName);
			libTorrent().PauseSession();
		}

	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mReceiver);
		if (mLibVLC != null && !mSwitchingView) {
			mLibVLC.stop();
		}

		EventHandler em = EventHandler.getInstance();
		em.removeHandler(eventHandler);

		mAudioManager = null;

		// TRIBLER
		if (!contentName.isEmpty()) {
			libTorrent().RemoveTorrent(contentName);
			libTorrent().AbortSession();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		onTriblerResume();
	}

	// TRIBLER
	private void testMagnetMaking() {
		File location = getTorrentDownloadDir();
		Log.d(TAG, "making torrent at location: " + location);
		libTorrent().AddMagnet(location.getAbsolutePath(),
				StorageModes.ALLOCATE.ordinal(), testMagnet);
		new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				File location = getTorrentDownloadDir();
				while (!libTorrent().HasMetaData(location.getAbsolutePath(),
						testMagnet)) {
					// 31-10-2013, always has metadata before coming here
					Log.d(TAG,
							"has metadata: "
									+ libTorrent().HasMetaData(
											location.getAbsolutePath(),
											testMagnet));
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				Log.d(TAG, "Making torrent...");
				File location = getTorrentDownloadDir();
				libTorrent().MakeTorrentFromMagnet(location.getAbsolutePath(),
						testMagnet);
				Log.d(TAG, "Test done");
			}

		}.execute();
	}

	// TRIBLER
	private void onTriblerResume() {
		AudioServiceController.getInstance().bindAudioService(this);

		load();

		if (!contentName.isEmpty()) {
			Log.d(TAG, "start isn't empty");
			libTorrent().ResumeTorrent(contentName);
			libTorrent().ResumeSession();
		}
		/*
		 * if(!makeMagnetOnce) { testMagnetMaking(); makeMagnetOnce = true; }
		 */
		/*
		 * if the activity has been paused by pressing the power button,
		 * pressing it again will show the lock screen. But onResume will also
		 * be called, even if vlc-android is still in the background. To
		 * workaround that, pause playback if the lockscreen is displayed
		 */
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (mLibVLC != null && mLibVLC.isPlaying()) {
					KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
					if (km.inKeyguardRestrictedInputMode())
						mLibVLC.pause();
				}
			}
		}, 500);

		showOverlay();

		// Add any selected subtitle file from the file picker
		if (mSubtitleSelectedFiles.size() > 0) {
			for (String file : mSubtitleSelectedFiles) {
				Log.i(TAG, "Adding user-selected subtitle " + file);
				mLibVLC.addSubtitleTrack(file);
			}
			/**
			 * FIXME remove when #7540 ES are not updated mid-stream a.k.a.
			 * subtitle events is fixed
			 */
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					setESTrackLists(true);

					mHandler.postDelayed(new Runnable() {
						@Override
						public void run() {
							setESTrackLists(true);
						}
					}, 1200);
				}
			}, 1000);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (data == null)
			return;

		if (data.getDataString() == null) {
			Log.d(TAG, "Subtitle selection dialog was cancelled");
		}
		if (data.getData() == null)
			return;

		String uri = data.getData().getPath();
		if (requestCode == CommonDialogs.INTENT_SPECIFIC) {
			Log.d(TAG, "Specific subtitle file: " + uri);
		} else if (requestCode == CommonDialogs.INTENT_GENERIC) {
			Log.d(TAG, "Generic subtitle file: " + uri);
		}
		mSubtitleSelectedFiles.add(data.getData().getPath());
	}

	public static void start(Context context, String location) {
		start(context, location, null, -1, false, false);
	}

	public static void start(Context context, String location, Boolean fromStart) {
		start(context, location, null, -1, false, fromStart);
	}

	public static void start(Context context, String location, String title,
			Boolean dontParse) {
		start(context, location, title, -1, dontParse, false);
	}

	public static void start(Context context, String location, String title,
			int position, Boolean dontParse) {
		start(context, location, title, position, dontParse, false);
	}

	public static void start(Context context, String location, String title,
			int position, Boolean dontParse, Boolean fromStart) {
		Intent intent = new Intent(context, VideoPlayerActivity.class);
		intent.setAction(VideoPlayerActivity.PLAY_FROM_VIDEOGRID);
		intent.putExtra("itemLocation", location);
		intent.putExtra("itemTitle", title);
		intent.putExtra("dontParse", dontParse);
		intent.putExtra("fromStart", fromStart);
		intent.putExtra("itemPosition", position);

		if (dontParse)
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
		else {
			// Stop the currently running audio
			AudioServiceController asc = AudioServiceController.getInstance();
			asc.stop();
		}

		context.startActivity(intent);
	}

	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equalsIgnoreCase(Intent.ACTION_BATTERY_CHANGED)) {
				int batteryLevel = intent.getIntExtra("level", 0);
				if (batteryLevel >= 50)
					mBattery.setTextColor(Color.GREEN);
				else if (batteryLevel >= 30)
					mBattery.setTextColor(Color.YELLOW);
				else
					mBattery.setTextColor(Color.RED);
				mBattery.setText(String.format("%d%%", batteryLevel));
			} else if (action.equalsIgnoreCase(VLCApplication.SLEEP_INTENT)) {
				finish();
			}
		}
	};

	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		showOverlay();
		return true;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		setSurfaceSize(mVideoWidth, mVideoHeight, mVideoVisibleWidth,
				mVideoVisibleHeight, mSarNum, mSarDen);
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void setSurfaceSize(int width, int height, int visible_width,
			int visible_height, int sar_num, int sar_den) {
		if (width * height == 0)
			return;

		// store video size
		mVideoHeight = height;
		mVideoWidth = width;
		mVideoVisibleHeight = visible_height;
		mVideoVisibleWidth = visible_width;
		mSarNum = sar_num;
		mSarDen = sar_den;
		Message msg = mHandler.obtainMessage(SURFACE_SIZE);
		mHandler.sendMessage(msg);
	}

	/**
	 * Lock screen rotation
	 */
	private void lockScreen() {
		if (mScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
				setRequestedOrientation(14 /* SCREEN_ORIENTATION_LOCKED */);
			else
				setRequestedOrientation(getScreenOrientation());
		}
		showInfo(R.string.locked, 1000);
		mLock.setBackgroundResource(R.drawable.ic_lock_glow);
		mTime.setEnabled(false);
		mSeekbar.setEnabled(false);
		mLength.setEnabled(false);
		hideOverlay(true);
	}

	/**
	 * Remove screen lock
	 */
	private void unlockScreen() {
		if (mScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR)
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		showInfo(R.string.unlocked, 1000);
		mLock.setBackgroundResource(R.drawable.ic_lock);
		mTime.setEnabled(true);
		mSeekbar.setEnabled(true);
		mLength.setEnabled(true);
		mShowing = false;
		showOverlay();
	}

	/**
	 * Show text in the info view for "duration" milliseconds
	 * 
	 * @param text
	 * @param duration
	 */
	private void showInfo(String text, int duration) {
		mInfo.setVisibility(View.VISIBLE);
		mInfo.setText(text);
		mHandler.removeMessages(FADE_OUT_INFO);
		mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
	}

	private void showInfo(int textid, int duration) {
		mInfo.setVisibility(View.VISIBLE);
		mInfo.setText(textid);
		mHandler.removeMessages(FADE_OUT_INFO);
		mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
	}

	/**
	 * Show text in the info view
	 * 
	 * @param text
	 */
	private void showInfo(String text) {
		mInfo.setVisibility(View.VISIBLE);
		mInfo.setText(text);
		mHandler.removeMessages(FADE_OUT_INFO);
	}

	/**
	 * hide the info view with "delay" milliseconds delay
	 * 
	 * @param delay
	 */
	private void hideInfo(int delay) {
		mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, delay);
	}

	/**
	 * hide the info view
	 */
	private void hideInfo() {
		hideInfo(0);
	}

	private void fadeOutInfo() {
		if (mInfo.getVisibility() == View.VISIBLE)
			mInfo.startAnimation(AnimationUtils.loadAnimation(
					VideoPlayerActivity.this, android.R.anim.fade_out));
		mInfo.setVisibility(View.INVISIBLE);
	}

	/**
	 * Handle libvlc asynchronous events
	 */
	private final Handler eventHandler = new VideoPlayerEventHandler(this);

	private static class VideoPlayerEventHandler extends
			WeakHandler<VideoPlayerActivity> {
		public VideoPlayerEventHandler(VideoPlayerActivity owner) {
			super(owner);
		}

		@Override
		public void handleMessage(Message msg) {
			VideoPlayerActivity activity = getOwner();
			if (activity == null)
				return;

			switch (msg.getData().getInt("event")) {
			case EventHandler.MediaPlayerPlaying:
				Log.i(TAG, "MediaPlayerPlaying");
				// TRIBLER
				if (activity.initCompleted)
					activity.setESTracks();
				break;
			case EventHandler.MediaPlayerPaused:
				Log.i(TAG, "MediaPlayerPaused");
				break;
			case EventHandler.MediaPlayerStopped:
				Log.i(TAG, "MediaPlayerStopped");
				break;
			case EventHandler.MediaPlayerEndReached:
				Log.i(TAG, "MediaPlayerEndReached");
				activity.endReached();
				break;
			case EventHandler.MediaPlayerVout:
				activity.handleVout(msg);
				break;
			case EventHandler.MediaPlayerPositionChanged:
				// don't spam the logs
				break;
			case EventHandler.MediaPlayerEncounteredError:
				Log.i(TAG, "MediaPlayerEncounteredError");
				activity.encounteredError();
				break;
			default:
				Log.e(TAG, String.format("Event not handled (0x%x)", msg
						.getData().getInt("event")));
				break;
			}
			activity.updateOverlayPausePlay();
		}
	};

	/**
	 * Handle resize of the surface and the overlay
	 */
	private final Handler mHandler = new VideoPlayerHandler(this);

	private static class VideoPlayerHandler extends
			WeakHandler<VideoPlayerActivity> {
		public VideoPlayerHandler(VideoPlayerActivity owner) {
			super(owner);
		}

		@Override
		public void handleMessage(Message msg) {
			VideoPlayerActivity activity = getOwner();
			if (activity == null) // WeakReference could be GC'ed early
				return;

			switch (msg.what) {
			case FADE_OUT:
				activity.hideOverlay(false);
				break;
			case SHOW_PROGRESS:
				int pos = activity.setOverlayProgress();
				if (activity.canShowProgress()) {
					msg = obtainMessage(SHOW_PROGRESS);
					sendMessageDelayed(msg, 1000 - (pos % 1000));
				}
				break;
			case SURFACE_SIZE:
				activity.changeSurfaceSize();
				break;
			case FADE_OUT_INFO:
				activity.fadeOutInfo();
				break;
			}
		}
	};

	private boolean canShowProgress() {
		// TRIBLER
		return true;
		// return !mDragging && mShowing && mLibVLC.isPlaying();
	}

	private void endReached() {
		if (mLibVLC.getMediaList().expandMedia(savedIndexPosition) == 0) {
			Log.d(TAG, "Found a video playlist, expanding it");
			eventHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					load();
				}
			}, 1000);
		} else if (torrentFile) {
			mEndReached = false;
			initCompleted = false;
			// TRIBLER
		} else {
			/* Exit player when reaching the end */
			mEndReached = true;
			finish();
		}
	}

	private void encounteredError() {
		/* Encountered Error, exit player with a message */
		AlertDialog dialog = new AlertDialog.Builder(VideoPlayerActivity.this)
				.setPositiveButton(R.string.ok,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								finish();
							}
						}).setTitle(R.string.encountered_error_title)
				.setMessage(R.string.encountered_error_message).create();
		dialog.show();
	}

	private void handleVout(Message msg) {
		if (msg.getData().getInt("data") == 0 && !mEndReached) {
			/* Video track lost, open in audio mode */
			Log.i(TAG, "Video track lost, switching to audio");
			mSwitchingView = true;
			finish();
		}
	}

	private void changeSurfaceSize() {
		// get screen size
		int dw = getWindow().getDecorView().getWidth();
		int dh = getWindow().getDecorView().getHeight();

		// getWindow().getDecorView() doesn't always take orientation into
		// account, we have to correct the values
		boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
		if (dw > dh && isPortrait || dw < dh && !isPortrait) {
			int d = dw;
			dw = dh;
			dh = d;
		}

		// sanity check
		if (dw * dh == 0 || mVideoWidth * mVideoHeight == 0) {
			Log.e(TAG, "Invalid surface size");
			return;
		}

		// compute the aspect ratio
		double ar, vw;
		double density = (double) mSarNum / (double) mSarDen;
		if (density == 1.0) {
			/* No indication about the density, assuming 1:1 */
			vw = mVideoVisibleWidth;
			ar = (double) mVideoVisibleWidth / (double) mVideoVisibleHeight;
		} else {
			/* Use the specified aspect ratio */
			vw = mVideoVisibleWidth * density;
			ar = vw / mVideoVisibleHeight;
		}

		// compute the display aspect ratio
		double dar = (double) dw / (double) dh;

		switch (mCurrentSize) {
		case SURFACE_BEST_FIT:
			if (dar < ar)
				dh = (int) (dw / ar);
			else
				dw = (int) (dh * ar);
			break;
		case SURFACE_FIT_HORIZONTAL:
			dh = (int) (dw / ar);
			break;
		case SURFACE_FIT_VERTICAL:
			dw = (int) (dh * ar);
			break;
		case SURFACE_FILL:
			break;
		case SURFACE_16_9:
			ar = 16.0 / 9.0;
			if (dar < ar)
				dh = (int) (dw / ar);
			else
				dw = (int) (dh * ar);
			break;
		case SURFACE_4_3:
			ar = 4.0 / 3.0;
			if (dar < ar)
				dh = (int) (dw / ar);
			else
				dw = (int) (dh * ar);
			break;
		case SURFACE_ORIGINAL:
			dh = mVideoVisibleHeight;
			dw = (int) vw;
			break;
		}

		// force surface buffer size
		mSurfaceHolder.setFixedSize(mVideoWidth, mVideoHeight);

		// set display size
		LayoutParams lp = mSurface.getLayoutParams();
		lp.width = dw * mVideoWidth / mVideoVisibleWidth;
		lp.height = dh * mVideoHeight / mVideoVisibleHeight;
		mSurface.setLayoutParams(lp);

		// set frame size (crop if necessary)
		lp = mSurfaceFrame.getLayoutParams();
		lp.width = dw;
		lp.height = dh;
		mSurfaceFrame.setLayoutParams(lp);

		mSurface.invalidate();
	}

	/**
	 * show/hide the overlay
	 */

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (mIsLocked) {
			showOverlay();
			return false;
		}

		DisplayMetrics screen = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(screen);

		if (mSurfaceYDisplayRange == 0)
			mSurfaceYDisplayRange = Math.min(screen.widthPixels,
					screen.heightPixels);

		float y_changed = event.getRawY() - mTouchY;
		float x_changed = event.getRawX() - mTouchX;

		// coef is the gradient's move to determine a neutral zone
		float coef = Math.abs(y_changed / x_changed);
		float xgesturesize = ((x_changed / screen.xdpi) * 2.54f);

		switch (event.getAction()) {

		case MotionEvent.ACTION_DOWN:
			// Audio
			mTouchY = event.getRawY();
			mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
			mTouchAction = TOUCH_NONE;
			// Seek
			mTouchX = event.getRawX();
			break;

		case MotionEvent.ACTION_MOVE:
			// No volume/brightness action if coef < 2
			if (coef > 2) {
				// Volume (Up or Down - Right side)
				if (!mEnableBrightnessGesture
						|| mTouchX > (screen.widthPixels / 2)) {
					doVolumeTouch(y_changed);
				}
				// Brightness (Up or Down - Left side)
				if (mEnableBrightnessGesture
						&& mTouchX < (screen.widthPixels / 2)) {
					doBrightnessTouch(y_changed);
				}
				// Extend the overlay for a little while, so that it doesn't
				// disappear on the user if more adjustment is needed. This
				// is because on devices with soft navigation (e.g. Galaxy
				// Nexus), gestures can't be made without activating the UI.
				if (Util.hasNavBar())
					showOverlay();
			}
			// Seek (Right or Left move)
			doSeekTouch(coef, xgesturesize, false);
			break;

		case MotionEvent.ACTION_UP:
			// Audio or Brightness
			if (mTouchAction == TOUCH_NONE) {
				if (!mShowing) {
					showOverlay();
				} else {
					hideOverlay(true);
				}
			}
			// Seek
			doSeekTouch(coef, xgesturesize, true);
			break;
		}
		return mTouchAction != TOUCH_NONE;
	}

	private void doSeekTouch(float coef, float gesturesize, boolean seek) {
		// No seek action if coef > 0.5 and gesturesize < 1cm
		if (mEnableWheelbar || coef > 0.5 || Math.abs(gesturesize) < 1)
			return;

		if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_SEEK)
			return;
		mTouchAction = TOUCH_SEEK;

		// Always show seekbar when searching
		if (!mShowing)
			showOverlay();

		long length = mLibVLC.getLength();
		long time = mLibVLC.getTime();

		// Size of the jump, 10 minutes max (600000), with a bi-cubic
		// progression, for a 8cm gesture
		int jump = (int) (Math.signum(gesturesize) * ((600000 * Math.pow(
				(gesturesize / 8), 4)) + 3000));

		// Adjust the jump
		if ((jump > 0) && ((time + jump) > length))
			jump = (int) (length - time);
		if ((jump < 0) && ((time + jump) < 0))
			jump = (int) -time;

		// Jump !
		if (seek)
			mPlayerControlListener.onSeekTo(time + jump);

		// Show the jump's size
		showInfo(
				String.format("%s%s (%s)", jump >= 0 ? "+" : "",
						Util.millisToString(jump),
						Util.millisToString(time + jump)), 1000);
	}

	private void doVolumeTouch(float y_changed) {
		if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_VOLUME)
			return;
		int delta = -(int) ((y_changed / mSurfaceYDisplayRange) * mAudioMax);
		int vol = (int) Math.min(Math.max(mVol + delta, 0), mAudioMax);
		if (delta != 0) {
			mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
			mTouchAction = TOUCH_VOLUME;
			showInfo(
					getString(R.string.volume) + '\u00A0'
							+ Integer.toString(vol), 1000);
		}
	}

	private void initBrightnessTouch() {
		float brightnesstemp = 0.01f;
		// Initialize the layoutParams screen brightness
		try {
			brightnesstemp = android.provider.Settings.System.getInt(
					getContentResolver(),
					android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255.0f;
		} catch (SettingNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		lp.screenBrightness = brightnesstemp;
		getWindow().setAttributes(lp);
		mIsFirstBrightnessGesture = false;
	}

	private void doBrightnessTouch(float y_changed) {
		if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_BRIGHTNESS)
			return;
		if (mIsFirstBrightnessGesture)
			initBrightnessTouch();
		mTouchAction = TOUCH_BRIGHTNESS;

		// Set delta : 0.07f is arbitrary for now, it possibly will change in
		// the future
		float delta = -y_changed / mSurfaceYDisplayRange * 0.07f;

		// Estimate and adjust Brightness
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		lp.screenBrightness = Math.min(
				Math.max(lp.screenBrightness + delta, 0.01f), 1);

		// Set Brightness
		getWindow().setAttributes(lp);
		showInfo(
				getString(R.string.brightness) + '\u00A0'
						+ Math.round(lp.screenBrightness * 15), 1000);
	}

	/**
	 * handle changes of the seekbar (slicer)
	 */
	private final OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			mDragging = true;
			mLibVLC.pause();
			showOverlay(OVERLAY_INFINITE);
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			mDragging = false;
			showOverlay();
			hideInfo();
		}

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {
			if (fromUser) {
				if (mLibVLC.isSeekable() && initCompleted) {
					initCompleted = false;
					setDownloadPrioritySeekTo(progress);
				}
			}
		}
	};

	/**
    *
    */
	private final OnClickListener mAudioTrackListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			final String[] arrList = new String[mAudioTracksList.size()];
			int i = 0;
			int listPosition = 0;
			for (Map.Entry<Integer, String> entry : mAudioTracksList.entrySet()) {
				arrList[i] = entry.getValue();
				// map the track position to the list position
				if (entry.getKey() == mLibVLC.getAudioTrack())
					listPosition = i;
				i++;
			}
			AlertDialog dialog = new AlertDialog.Builder(
					VideoPlayerActivity.this)
					.setTitle(R.string.track_audio)
					.setSingleChoiceItems(arrList, listPosition,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int listPosition) {
									int trackID = -1;
									// Reverse map search...
									for (Map.Entry<Integer, String> entry : mAudioTracksList
											.entrySet()) {
										if (arrList[listPosition].equals(entry
												.getValue())) {
											trackID = entry.getKey();
											break;
										}
									}
									if (trackID < 0)
										return;

									MediaDatabase
											.getInstance(
													VideoPlayerActivity.this)
											.updateMedia(
													mLocation,
													MediaDatabase.mediaColumn.MEDIA_AUDIOTRACK,
													trackID);
									mLibVLC.setAudioTrack(trackID);
									dialog.dismiss();
								}
							}).create();
			dialog.setCanceledOnTouchOutside(true);
			dialog.setOwnerActivity(VideoPlayerActivity.this);
			dialog.show();
		}
	};

	/**
    *
    */
	private final OnClickListener mSubtitlesListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			final String[] arrList = new String[mSubtitleTracksList.size()];
			int i = 0;
			int listPosition = 0;
			for (Map.Entry<Integer, String> entry : mSubtitleTracksList
					.entrySet()) {
				arrList[i] = entry.getValue();
				// map the track position to the list position
				if (entry.getKey() == mLibVLC.getSpuTrack())
					listPosition = i;
				i++;
			}

			AlertDialog dialog = new AlertDialog.Builder(
					VideoPlayerActivity.this)
					.setTitle(R.string.track_text)
					.setSingleChoiceItems(arrList, listPosition,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int listPosition) {
									int trackID = -2;
									// Reverse map search...
									for (Map.Entry<Integer, String> entry : mSubtitleTracksList
											.entrySet()) {
										if (arrList[listPosition].equals(entry
												.getValue())) {
											trackID = entry.getKey();
											break;
										}
									}
									if (trackID < -1)
										return;

									MediaDatabase
											.getInstance(
													VideoPlayerActivity.this)
											.updateMedia(
													mLocation,
													MediaDatabase.mediaColumn.MEDIA_SPUTRACK,
													trackID);
									mLibVLC.setSpuTrack(trackID);
									dialog.dismiss();
								}
							}).create();
			dialog.setCanceledOnTouchOutside(true);
			dialog.setOwnerActivity(VideoPlayerActivity.this);
			dialog.show();
		}
	};

	/**
    *
    */
	private final OnPlayerControlListener mPlayerControlListener = new OnPlayerControlListener() {
		@Override
		public void onPlayPause() {
			if (mLibVLC.isPlaying())
				pause();
			else
				play();
			showOverlay();
		}

		@Override
		public void onSeek(int delta) {
			// unseekable stream
			if (mLibVLC.getLength() <= 0)
				return;

			long position = mLibVLC.getTime() + delta;
			if (position < 0)
				position = 0;
			mLibVLC.setTime(position);
			showOverlay();
		}

		@Override
		public void onSeekTo(long position) {
			// unseekable stream
			if (mLibVLC.getLength() <= 0)
				return;
			mLibVLC.setTime(position);
			mTime.setText(Util.millisToString(position));
		}

		@Override
		public long onWheelStart() {
			showOverlay(OVERLAY_INFINITE);
			return mLibVLC.getTime();
		}

		@Override
		public void onShowInfo(String info) {
			if (info != null)
				showInfo(info);
			else {
				hideInfo();
				showOverlay();
			}
		}
	};

	/**
     *
     */
	private final OnClickListener mLockListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (mIsLocked) {
				mIsLocked = false;
				unlockScreen();
			} else {
				mIsLocked = true;
				lockScreen();
			}
		}
	};

	/**
     *
     */
	private final OnClickListener mSizeListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (mCurrentSize < SURFACE_ORIGINAL) {
				mCurrentSize++;
			} else {
				mCurrentSize = 0;
			}
			changeSurfaceSize();
			switch (mCurrentSize) {
			case SURFACE_BEST_FIT:
				showInfo(R.string.surface_best_fit, 1000);
				break;
			case SURFACE_FIT_HORIZONTAL:
				showInfo(R.string.surface_fit_horizontal, 1000);
				break;
			case SURFACE_FIT_VERTICAL:
				showInfo(R.string.surface_fit_vertical, 1000);
				break;
			case SURFACE_FILL:
				showInfo(R.string.surface_fill, 1000);
				break;
			case SURFACE_16_9:
				showInfo("16:9", 1000);
				break;
			case SURFACE_4_3:
				showInfo("4:3", 1000);
				break;
			case SURFACE_ORIGINAL:
				showInfo(R.string.surface_original, 1000);
				break;
			}
			showOverlay();
		}
	};

	private final OnClickListener mRemainingTimeListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			mDisplayRemainingTime = !mDisplayRemainingTime;
			showOverlay();
		}
	};

	/**
	 * attach and disattach surface to the lib
	 */
	private final SurfaceHolder.Callback mSurfaceCallback = new Callback() {
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			if (format == PixelFormat.RGBX_8888)
				Log.d(TAG, "Pixel format is RGBX_8888");
			else if (format == PixelFormat.RGB_565)
				Log.d(TAG, "Pixel format is RGB_565");
			else if (format == ImageFormat.YV12)
				Log.d(TAG, "Pixel format is YV12");
			else
				Log.d(TAG, "Pixel format is other/unknown");
			mLibVLC.attachSurface(holder.getSurface(),
					VideoPlayerActivity.this, width, height);
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			mLibVLC.detachSurface();
		}
	};

	/**
	 * show overlay the the default timeout
	 */
	private void showOverlay() {
		showOverlay(OVERLAY_TIMEOUT);
	}

	/**
	 * show overlay
	 */
	private void showOverlay(int timeout) {
		mHandler.sendEmptyMessage(SHOW_PROGRESS);
		if (!mShowing) {
			mShowing = true;
			mOverlayLock.setVisibility(View.VISIBLE);
			if (!mIsLocked) {
				mOverlayHeader.setVisibility(View.VISIBLE);
				mOverlayOption.setVisibility(View.VISIBLE);
				mOverlayInterface.setVisibility(View.VISIBLE);
				dimStatusBar(false);
			}
			mOverlayProgress.setVisibility(View.VISIBLE);
		}
		// TRIBLER
		// Message msg = mHandler.obtainMessage(FADE_OUT);
		// if (timeout != 0) {
		// mHandler.removeMessages(FADE_OUT);
		// mHandler.sendMessageDelayed(msg, timeout);
		// }
		// updateOverlayPausePlay();
	}

	/**
	 * hider overlay
	 */
	private void hideOverlay(boolean fromUser) {
		if (mShowing) {
			mHandler.removeMessages(SHOW_PROGRESS);
			Log.i(TAG, "remove View!");
			if (!fromUser && !mIsLocked) {
				mOverlayLock.startAnimation(AnimationUtils.loadAnimation(this,
						android.R.anim.fade_out));
				mOverlayHeader.startAnimation(AnimationUtils.loadAnimation(
						this, android.R.anim.fade_out));
				mOverlayOption.startAnimation(AnimationUtils.loadAnimation(
						this, android.R.anim.fade_out));
				// TRIBLER
				// mOverlayProgress.startAnimation(AnimationUtils.loadAnimation(
				// this, android.R.anim.fade_out));
				mOverlayInterface.startAnimation(AnimationUtils.loadAnimation(
						this, android.R.anim.fade_out));
			}
			mOverlayLock.setVisibility(View.INVISIBLE);
			mOverlayHeader.setVisibility(View.INVISIBLE);
			mOverlayOption.setVisibility(View.INVISIBLE);
			// TRIBLER
			// mOverlayProgress.setVisibility(View.INVISIBLE);
			mOverlayInterface.setVisibility(View.INVISIBLE);
			mShowing = false;
			dimStatusBar(true);
		}
	}

	/**
	 * Dim the status bar and/or navigation icons when needed on Android 3.x.
	 * Hide it on Android 4.0 and later
	 */
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void dimStatusBar(boolean dim) {
		if (!Util.isHoneycombOrLater() || !Util.hasNavBar())
			return;
		int layout = 0;
		if (!Util.hasCombBar() && Util.isJellyBeanOrLater())
			layout = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
		mSurface.setSystemUiVisibility((dim ? (Util.hasCombBar() ? View.SYSTEM_UI_FLAG_LOW_PROFILE
				: View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
				: View.SYSTEM_UI_FLAG_VISIBLE)
				| layout);
	}

	private void updateOverlayPausePlay() {
		if (mLibVLC == null) {
			return;
		}
		Log.d(TAG, "updateOverlayPausePlay");
		// TRIBLER
		// && preBufferDone)
		if (initCompleted) {
			mControls.setState(mLibVLC.isPlaying());
			// mLibVLC.play();
		} else {
			mControls.setState(false);
		}
	}

	/**
	 * update the overlay
	 */
	private int setOverlayProgress() {
		if (mLibVLC == null) {
			return 0;
		}
		int time = (int) mLibVLC.getTime();
		int length = (int) mLibVLC.getLength();
		if (length == 0) {
			Media media = MediaDatabase.getInstance(this).getMedia(this,
					mLocation);
			if (media != null)
				length = (int) media.getLength();
		}

		// Update all view elements
		mControls.setSeekable(length > 0);
		// TRIBLER
		mSeekbar.setMax(length == 0 ? (int) fileLength : length);
		mSeekbar.setProgress(time);
		mSysTime.setText(DateFormat.getTimeFormat(this).format(
				new Date(System.currentTimeMillis())));
		if (time >= 0)
			mTime.setText(Util.millisToString(time));
		if (length >= 0)
			mLength.setText(mDisplayRemainingTime && length > 0 ? "- "
					+ Util.millisToString(length - time) : Util
					.millisToString(length));

		// TRIBLER
		if (torrentFile) {
			int max = mSeekbar.getMax();
			int sizeMB = (int) (fileLength / (1024 * 1024));
			// TRIBLER HACK pretend you are not as fast as you are.
			long progressSize = libTorrent()
					.GetTorrentProgressSize(contentName);
			int hackMb = sizeMB < max && progressSize > 5 ? 5 : 0;
			int progress = (int) (sizeMB == 0 ? 0
					: (max * progressSize - hackMb) / sizeMB);
			// Log.v(TAG, "MAX: " + max + " | SIZE: " + sizeMB + " | progress: "
			// + progress);
			mSeekbar.setSecondaryProgress(progress);
			((TextView) findViewById(R.id.libtorrent_debug))
					.setText(libTorrent().GetTorrentStatusText(contentName)
							+ "\n"
							+ libTorrent().GetTorrentProgressSize(contentName)
							+ " / "
							+ (fileLength / (1024 * 1024))
							+ "\n"
							+ TorrentState.values()[libTorrent()
									.GetTorrentState(contentName)].getName());
		}

		return time;
	}

	private void setESTracks() {
		if (mLastAudioTrack >= 0) {
			mLibVLC.setAudioTrack(mLastAudioTrack);
			mLastAudioTrack = -1;
		}
		if (mLastSpuTrack >= -1) {
			mLibVLC.setSpuTrack(mLastSpuTrack);
			mLastSpuTrack = -2;
		}
	}

	private void setESTrackLists() {
		setESTrackLists(false);
	}

	private void setESTrackLists(boolean force) {
		if (mAudioTracksList == null || force) {
			if (mLibVLC.getAudioTracksCount() > 2) {
				mAudioTracksList = mLibVLC.getAudioTrackDescription();
				mAudioTrack.setOnClickListener(mAudioTrackListener);
				mAudioTrack.setVisibility(View.VISIBLE);
			} else {
				mAudioTrack.setVisibility(View.GONE);
				mAudioTrack.setOnClickListener(null);
			}
		}
		if (mSubtitleTracksList == null || force) {
			if (mLibVLC.getSpuTracksCount() > 0) {
				mSubtitleTracksList = mLibVLC.getSpuTrackDescription();
				mSubtitle.setOnClickListener(mSubtitlesListener);
				mSubtitle.setVisibility(View.VISIBLE);
			} else {
				mSubtitle.setVisibility(View.GONE);
				mSubtitle.setOnClickListener(null);
			}
		}
	}

	/**
     *
     */
	private void play() {
		mLibVLC.play();
		mSurface.setKeepScreenOn(true);
	}

	/**
     *
     */
	private void pause() {
		mLibVLC.pause();
		mSurface.setKeepScreenOn(false);
	}

	// TRIBLER
	private File getTorrentDownloadDir() {
		return new File(new File(Environment.getExternalStorageDirectory(),
				"Download"), "TriblerStreamingCache");
	}

	// TRIBLER
	public String getPiecePrioritiesString(int[] priorities) {
		String result = "";
		for (int i : priorities) {
			result += "" + i;
		}
		return result;
	}

	// TRIBLER
	public static boolean deleteRecursive(File path)
			throws FileNotFoundException {
		if (!path.exists())
			throw new FileNotFoundException(path.getAbsolutePath());
		boolean ret = true;
		if (path.isDirectory()) {
			for (File f : path.listFiles()) {
				ret = ret && deleteRecursive(f);
			}
		}
		return ret && path.delete();
	}

	// TRIBLER
	private void setDownloadPrioritySeekTo(int progress) {
		final int setDownloadProgress = progress;

		long totalTime = mLibVLC.getLength();
		if (totalTime != 0) {
			double ratio = (double) progress / totalTime;

			int numberOfPieces = lastPieceIndex - firstPieceIndex;
			final int startNewDownloadHere = firstPieceIndex
					+ ((int) Math.floor(numberOfPieces * ratio)) - 10;

			Log.d(TAG, "startNewDownloadHere: " + startNewDownloadHere);

			for (int i = firstPieceIndex; i <= lastPieceIndex; i++) {
				piecePriorities[i] = i < startNewDownloadHere ? FilePriority.DONTDOWNLOAD
						.ordinal() : FilePriority.NORMAL.ordinal();
			}
			libTorrent().SetPiecePriorities(contentName, piecePriorities);
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected void onPreExecute() {
					Log.d(TAG, "PreExecute of seek..."
							+ getPiecePrioritiesString(piecePriorities));

					if (mLibVLC.isPlaying())
						mLibVLC.pause();

					findViewById(R.id.libtorrent_loading).setVisibility(
							View.VISIBLE);
				}

				@Override
				protected Void doInBackground(Void... params) {
					int pieceSize = (int) libTorrent().GetPieceSize(
							contentName, 0);
					int rightIndex = Math.min(
							(startNewDownloadHere + (REQUESTSIZE / pieceSize)),
							lastPieceIndex);
					int piecesToCheck = rightIndex - startNewDownloadHere;

					int newCheckSize = CHECKSIZE;
					if (piecesToCheck * pieceSize < CHECKSIZE)
						newCheckSize = piecesToCheck * pieceSize;

					while (!isCancelled()) {
						Log.d(TAG, "!isCancelled in Async, newCheckSize: "
								+ newCheckSize + " piecesToCheck: "
								+ piecesToCheck + " startPiece: "
								+ startNewDownloadHere);
						int sum = 0;

						for (int i = startNewDownloadHere; i <= rightIndex; i++) {
							if (libTorrent().HavePiece(contentName, i)) {
								sum += pieceSize;
							}
							if (sum >= newCheckSize) {
								return null;
							}
						}
						Log.d(TAG, "sum: " + sum);
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					return null;
				}

				@Override
				protected void onPostExecute(Void result) {
					Log.d(TAG, "postExecute seek..."
							+ getPiecePrioritiesString(piecePriorities));

					findViewById(R.id.libtorrent_loading).setVisibility(
							View.GONE);

					mLibVLC.setTime(setDownloadProgress);
					setOverlayProgress();
					mTime.setText(Util.millisToString(setDownloadProgress));
					showInfo(Util.millisToString(setDownloadProgress));
					showOverlay();
					hideInfo();
					initCompleted = true;
					mLibVLC.play();
				}

			}.execute();
		} else {
			Log.d(TAG, "No end time specified");
		}

	}

	/**
	 * delete the previous torrent cache, so the application holds only one
	 * file/movie at a time
	 * 
	 * @param savePath
	 *            , where the torrent cache is
	 */
	private void deletePreviousTorrentCache(File savePath) {
		Log.d(TAG, "deleting previous torrent cache");
		contentName = libTorrent().GetTorrentName(mLocation);
		String contentNameDir = new File(savePath, contentName)
				.getAbsolutePath();
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		String latestContent = prefs.getString(KEY_LATEST_CONTENT, "");
		String latestContentDir = prefs.getString(KEY_LATEST_CONTENT_DIR, "");
		Editor e = prefs.edit();

		if (!latestContent.equals(contentName)) {
			e.putString(KEY_LATEST_CONTENT, contentName);
			e.putString(KEY_LATEST_CONTENT_DIR, contentNameDir);
			try {
				deleteRecursive(new File(latestContentDir));
			} catch (FileNotFoundException fnfe) {
				fnfe.printStackTrace();
			}
		}
		e.commit();
	}

	/**
	 * Find biggest file, and set priorities, so you can play it.
	 * 
	 * @param savePath
	 *            , where is the location of the to be downloaded file
	 * @return location of biggest file
	 */
	private File setFilesPriorities(File savePath) {

		String[] files = libTorrent().GetTorrentFiles(contentName).split(
				"\\r?\\n");
		long[] sizes = new long[files.length];
		byte[] priorities = libTorrent().GetTorrentFilesPriority(contentName);

		fileLength = 0;
		int biggestFileIndex = 0;

		for (int i = 0; i < files.length; i++) {
			String file = files[i];
			int spaceIndex = file.lastIndexOf(" ");
			files[i] = file.substring(0, spaceIndex);
			String sizeString = file.substring(spaceIndex + 1);
			sizes[i] = getSize(sizeString);

			Log.v(TAG, "File: " + files[i] + " | " + sizes[i] + " | "
					+ FilePriority.values()[priorities[i]]);

			if (sizes[i] > fileLength) {
				priorities[biggestFileIndex] = FilePriority.DONTDOWNLOAD
						.getByte();
				fileLength = sizes[i];
				biggestFileIndex = i;
				priorities[biggestFileIndex] = FilePriority.NORMAL.getByte();
			} else {
				priorities[i] = FilePriority.DONTDOWNLOAD.getByte();
			}
		}

		// Set priorities to only biggest file gets downloaded
		Log.v(TAG, "=== RESULT ===");
		for (int i = 0; i < files.length; i++) {
			Log.v(TAG, "File: " + files[i] + " | " + sizes[i] + " | "
					+ FilePriority.values()[priorities[i]]);
		}
		libTorrent().SetTorrentFilesPriority(priorities, contentName);
		return new File(savePath, files[biggestFileIndex]);
	}

	/**
	 * check if (part of) movie is already downloaded in a previous app lifetime
	 */
	private boolean isAlreadyDownloaded() {
		if (libTorrent().GetTorrentState(contentName) == TorrentState.CHECKING_FILES
				.ordinal()
				|| libTorrent().GetTorrentProgressSize(contentName) >= CHECKSIZEMB) {

			mLibVLC.pause();
			findViewById(R.id.libtorrent_loading).setVisibility(View.VISIBLE);

			int counter = 0; 	// cheating, because you don't wait until
								// program action, but only a certain amount
								// of time!
			int sleeptime = 300;
			while (libTorrent().GetTorrentProgressSize(contentName) < CHECKSIZEMB
					&& counter <= 3000) {
				Log.d(TAG,
						"while "
								+ libTorrent().GetTorrentProgressSize(
										contentName) + " < CZ");
				try {
					Thread.sleep(sleeptime);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				if (libTorrent().GetTorrentState(contentName) != TorrentState.CHECKING_FILES
						.ordinal()) {
					Log.d(TAG, "breaking...it's not checking files anymore");
					return true;
				}
				counter += sleeptime;
			}
			return true;
		}
		return false;
	}

	private File addTorrent() {
		torrentFile = true;
		Log.d(TAG, "GOT TORRENT FILE: " + mLocation);
		mLocation = mLocation.substring(7);
		Log.d(TAG, "GOT TORRENT FILE: " + mLocation);
		File savePath = getTorrentDownloadDir();
		Log.d(TAG, "SAVEPATH: " + savePath.getAbsolutePath());
		libTorrent().AddTorrent(savePath.getAbsolutePath(), mLocation,
				StorageModes.ALLOCATE.ordinal(), false);
		return savePath;
	}
	
	private File addMagnet() {
		torrentFile = true;
		Log.d(TAG, "GOT MAGNET: " + mLocation);
		File savePath = getTorrentDownloadDir();
		Log.d(TAG, "SAVEPATH: " + savePath.getAbsolutePath());
		libTorrent().AddMagnet(savePath.getAbsolutePath(), 
				StorageModes.ALLOCATE.ordinal(), mLocation);
		return savePath;
	}
	/**
	 * External extras: - position (long) - position of the video to start with
	 * (in ms)
	 */
	@SuppressWarnings({ "deprecation", "unchecked" })
	private void load() {
		mLocation = null;
		String title = getResources().getString(R.string.title);
		boolean dontParse = false;
		boolean fromStart = false;
		String itemTitle = null;
		int itemPosition = -1; // Index in the media list as passed by
								// AudioServer (used only for vout transition
								// internally)
		long intentPosition = -1; // position passed in by intent (ms)

		if (getIntent().getAction() != null
				&& getIntent().getAction().equals(Intent.ACTION_VIEW)) {
			/* Started from external application */
			if (getIntent().getData() != null
					&& getIntent().getData().getScheme() != null
					&& getIntent().getData().getScheme().equals("content")) {
				if (getIntent().getData().getHost().equals("media")) {
					// Media URI
					Cursor cursor = managedQuery(getIntent().getData(),
							new String[] { MediaStore.Video.Media.DATA }, null,
							null, null);
					int column_index = cursor
							.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
					if (cursor.moveToFirst())
						mLocation = LibVLC.PathToURI(cursor
								.getString(column_index));
				} else {
					// other content-based URI (probably file pickers)
					mLocation = getIntent().getData().getPath();
				}
			} else {
				// Plain URI
				mLocation = getIntent().getDataString();
				Log.d(TAG, "datastring: " + mLocation);
			}
			if (getIntent().getExtras() != null)
				intentPosition = getIntent().getExtras()
						.getLong("position", -1);
		} else if (getIntent().getAction() != null
				&& getIntent().getAction().equals(PLAY_FROM_VIDEOGRID)
				&& getIntent().getExtras() != null) {
			/* Started from VideoListActivity */
			mLocation = getIntent().getExtras().getString("itemLocation");
			itemTitle = getIntent().getExtras().getString("itemTitle");
			dontParse = getIntent().getExtras().getBoolean("dontParse");
			fromStart = getIntent().getExtras().getBoolean("fromStart");
			itemPosition = getIntent().getExtras().getInt("itemPosition", -1);
		}

		// TRIBLER
		if (mLocation != null && (mLocation.endsWith(".torrent")|| mLocation.startsWith("magnet"))) {
			File savePath;
			
			if(mLocation.endsWith(".torrent")){
				savePath = addTorrent();
			} else { //mLocation.startsWith("magnet")
				savePath = addMagnet();
				while(!libTorrent().HasMetaData(savePath.getAbsolutePath(), mLocation)) {
					Log.d(TAG, "waiting for meta data");
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			
			deletePreviousTorrentCache(savePath);

			File location = setFilesPriorities(savePath);

			mLocation = "file://" + location.getAbsolutePath();
			while (!location.exists()) {
				try {
					Thread.sleep(300);
				} catch (InterruptedException ie) {
					Log.d(TAG, "================ Doesnt Exist Yet");
				}
			}
			Log.d(TAG, "!!!!!!!!!! Exists !!!!!!!!!!!!");

			if (isAlreadyDownloaded()
					&& libTorrent().GetTorrentProgressSize(contentName) >= CHECKSIZEMB) {
				Log.d(TAG, "contentsize >= checksize");
				findViewById(R.id.libtorrent_loading).setVisibility(View.GONE);
				mLibVLC.play();
				initCompleted = true;
			}

			if (!initCompleted) {
				piecePriorities = libTorrent().GetPiecePriorities(contentName);
				firstPieceIndex = -1;
				lastPieceIndex = -1;
				long firstPieceSize = libTorrent().GetPieceSize(contentName, 0);
				// minimal 18 pieces or 20MB
				int firstNumberOfPieces = Math.max(18,
						(int) (REQUESTSIZE / firstPieceSize));
				Log.d(TAG, "firstPieceSize " + firstPieceSize);
				Log.d(TAG, "firstNumberOfPieces " + firstNumberOfPieces);

				Log.v(TAG, "PRIOSBEFORE: "
						+ getPiecePrioritiesString(piecePriorities));

				for (int i = 0; i < piecePriorities.length; i++) {
					if (piecePriorities[i] != 0) {
						// We'ŕe in the file to download
						if (firstPieceIndex == -1)
							// Found the first piece
							firstPieceIndex = i;
						if (i < firstPieceIndex + firstNumberOfPieces) {
							piecePriorities[i] = FilePriority.NORMAL.ordinal();
						} else {
							piecePriorities[i] = FilePriority.DONTDOWNLOAD
									.ordinal();
						}
					} else {
						// We'ŕe either before or after the file to download
						if (firstPieceIndex != -1)
							// After firstPiece
							if (lastPieceIndex == -1)
								lastPieceIndex = i - 1;
						piecePriorities[i] = FilePriority.DONTDOWNLOAD
								.ordinal();
					}
				}
				if (lastPieceIndex == -1)
					lastPieceIndex = piecePriorities.length - 1;
				if (lastPieceIndex > -1)
					piecePriorities[lastPieceIndex] = FilePriority.MAXPRIORITY
							.ordinal();
				if (lastPieceIndex - 1 > -1)
					piecePriorities[lastPieceIndex - 1] = FilePriority.MAXPRIORITY
							.ordinal();

				Log.d(TAG, "LatestPiece Index: " + lastPieceIndex + " / "
						+ (piecePriorities.length - 1));

				String prios = "firstPiece: " + firstPieceIndex + "\n";
				prios += getPiecePrioritiesString(piecePriorities);
				Log.v(TAG, "PRIOSAFTER: " + prios + "\nLastpieceIndex "
						+ lastPieceIndex);

				libTorrent().SetPiecePriorities(contentName, piecePriorities);
			}
		}

		mSurface.setKeepScreenOn(true);

		Log.v("TRIBLER_DEBUG", "Start / resume playback: ");
		/* Start / resume playback */
		if (dontParse && itemPosition >= 0) {
			// Provided externally from AudioService
			Log.d(TAG, "Continuing playback from AudioService at index "
					+ itemPosition);
			savedIndexPosition = itemPosition;
			if (!mLibVLC.isPlaying()) {
				// AudioService-transitioned playback for item after sleep and
				// resume
				mLibVLC.playIndex(savedIndexPosition);
				dontParse = false;
			}
		} else if (savedIndexPosition > -1) {
			Log.d(TAG, "savedIndexPosition > -1 ");
			mLibVLC.setMediaList();
			mLibVLC.playIndex(savedIndexPosition);
		} else if (mLocation != null && mLocation.length() > 0 && !dontParse) {
			Log.v("TRIBLER_DEBUG",
					"mLocation != null && mLocation.length() > 0 && !dontParse");
			mLibVLC.setMediaList();
			mLibVLC.getMediaList().add(mLocation, false);

			savedIndexPosition = mLibVLC.getMediaList().size() - 1;
			Log.v("TRIBLER_DEBUG", "savedIndexPosition = " + savedIndexPosition);
			mLibVLC.playIndex(savedIndexPosition);

			if (!initCompleted) {
				Log.v("TRIBLER_DEBUG", "progress size = "
						+ libTorrent().GetTorrentProgressSize(contentName));
				// TODO: TRIBLER check progress of piece priority: if last 2 are
				// downloaded, setup libswift priorities.
				new AsyncTask<Void, Void, Void>() {
					@Override
					protected Void doInBackground(Void... params) {

						boolean startedOnce = false;
						while (!isCancelled()) {
							Log.d(TAG,
									"InitCompletedcheckthread "
											+ mLibVLC.getLength()
											+ " have first: "
											+ libTorrent().HavePiece(
													contentName,
													firstPieceIndex)
											+ " have last: "
											+ libTorrent()
													.HavePiece(contentName,
															lastPieceIndex));
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}

							if (firstPieceIndex != -1 && lastPieceIndex != -1) {
								if (!startedOnce) {
									if (libTorrent().GetTorrentProgressSize(
											contentName) >= CHECKSIZEMB) {
										mLibVLC.play();
										mLibVLC.stop();
										Log.d(TAG, "lets play");
										Log.d(TAG,
												"InitCompletedcheckthread "
														+ mLibVLC.getLength()
														+ " have first: "
														+ libTorrent()
																.HavePiece(
																		contentName,
																		firstPieceIndex)
														+ " have last: "
														+ libTorrent()
																.HavePiece(
																		contentName,
																		lastPieceIndex));
										mEndReached = false;
										startedOnce = true;
										return null;
									}
								}
							}
						}
						return null;
					}

					@Override
					protected void onPostExecute(Void result) {
						Log.v(TAG, "onPostExecute");
						findViewById(R.id.libtorrent_loading).setVisibility(
								View.GONE);

						for (int i = 0; i < piecePriorities.length; i++) {
							piecePriorities[i] = FilePriority.NORMAL.ordinal();
						}
						String prios = "firstPiece: " + firstPieceIndex + "\n";
						for (int i : piecePriorities) {
							prios += "" + i;
						}
						Log.v(TAG, "PRIOSAFTER: " + prios + "\nLastpieceIndex "
								+ lastPieceIndex);
						libTorrent().SetPiecePriorities(contentName,
								piecePriorities);
						initCompleted = true;
						libTorrent().ResumeTorrent(contentName);
						onTriblerResume();
					}

				}.execute();
			}
		}

		Log.v("TRIBLER_DEBUG", "SETTING MEDIA");
		if (mLocation != null && mLocation.length() > 0 && !dontParse) {
			Log.v("TRIBLER_DEBUG",
					"mLocation != null && mLocation.length() > 0 && !dontParse");
			// restore last position
			SharedPreferences preferences = getSharedPreferences(
					PreferencesActivity.NAME, MODE_PRIVATE);
			Media media = MediaDatabase.getInstance(this).getMedia(this,
					mLocation);
			if (media != null) {
				// in media library
				if (media.getTime() > 0 && !fromStart) {
					mLibVLC.setTime(media.getTime());
				}

				mLastAudioTrack = media.getAudioTrack();
				mLastSpuTrack = media.getSpuTrack();
			} else {
				Log.v("TRIBLER_DEBUG", "media == null");
				// not in media library
				long rTime = preferences.getLong(
						PreferencesActivity.VIDEO_RESUME_TIME, -1);
				SharedPreferences.Editor editor = preferences.edit();
				editor.putLong(PreferencesActivity.VIDEO_RESUME_TIME, -1);
				editor.commit();
				if (rTime > 0) {
					Log.v("TRIBLER_DEBUG", "rTime > 0");
					mLibVLC.setTime(rTime);
				}

				if (intentPosition > 0) {
					Log.v("TRIBLER_DEBUG", "intentPosition > 0");
					mLibVLC.setTime(intentPosition);
				}
			}

			// Subtitles

			String subtitleList_serialized = preferences.getString(
					PreferencesActivity.VIDEO_SUBTITLE_FILES, null);
			ArrayList<String> prefsList = new ArrayList<String>();
			if (subtitleList_serialized != null) {
				ByteArrayInputStream bis = new ByteArrayInputStream(
						subtitleList_serialized.getBytes());
				try {
					ObjectInputStream ois = new ObjectInputStream(bis);
					prefsList = (ArrayList<String>) ois.readObject();
				} catch (ClassNotFoundException e) {
				} catch (StreamCorruptedException e) {
				} catch (IOException e) {
				}
			}
			for (String x : prefsList) {
				if (!mSubtitleSelectedFiles.contains(x))
					mSubtitleSelectedFiles.add(x);
			}

			try {
				title = URLDecoder.decode(mLocation, "UTF-8");
			} catch (UnsupportedEncodingException e) {
			} catch (IllegalArgumentException e) {
			}
			if (title.startsWith("file:")) {
				title = new File(title).getName();
				int dotIndex = title.lastIndexOf('.');
				if (dotIndex != -1)
					title = title.substring(0, dotIndex);
			}
		} else if (itemTitle != null) {
			title = itemTitle;
		}
		mTitle.setText(title);
	}

	private long getSize(String sizeString) {
		sizeString = sizeString.toLowerCase(Locale.ENGLISH);
		Log.v(TAG, "getSize(" + sizeString + ")");
		long res = 0;
		int factor = 1;
		if (sizeString.endsWith("kb")) {
			sizeString = sizeString.substring(0, sizeString.length() - 2);
			factor = 1000;
		} else if (sizeString.endsWith("mb")) {
			sizeString = sizeString.substring(0, sizeString.length() - 2);
			factor = 1000000;
		} else if (sizeString.endsWith("gb")) {
			sizeString = sizeString.substring(0, sizeString.length() - 2);
			factor = 1000000000;
		} else if (sizeString.endsWith("b")) {
			sizeString = sizeString.substring(0, sizeString.length() - 1);
		}

		Log.v(TAG, "getSize(" + sizeString + " * " + factor + ")");
		try {
			res = (long) (Double.parseDouble(sizeString) * factor);
		} catch (NumberFormatException e) {
			Log.e(TAG, e.getLocalizedMessage());
		}

		return res;
	}

	@SuppressWarnings("deprecation")
	private int getScreenRotation() {
		WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO /*
																 * Android 2.2
																 * has
																 * getRotation
																 */) {
			try {
				Method m = display.getClass().getDeclaredMethod("getRotation");
				return (Integer) m.invoke(display);
			} catch (Exception e) {
				return Surface.ROTATION_0;
			}
		} else {
			return display.getOrientation();
		}
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private int getScreenOrientation() {
		WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		int rot = getScreenRotation();
		/*
		 * Since getRotation() returns the screen's "natural" orientation, which
		 * is not guaranteed to be SCREEN_ORIENTATION_PORTRAIT, we have to
		 * invert the SCREEN_ORIENTATION value if it is "naturally" landscape.
		 */
		@SuppressWarnings("deprecation")
		boolean defaultWide = display.getWidth() > display.getHeight();
		if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270)
			defaultWide = !defaultWide;
		if (defaultWide) {
			switch (rot) {
			case Surface.ROTATION_0:
				return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
			case Surface.ROTATION_90:
				return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
			case Surface.ROTATION_180:
				// SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
				// Level 9+
				return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
						: ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			case Surface.ROTATION_270:
				// SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
				// Level 9+
				return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
						: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			default:
				return 0;
			}
		} else {
			switch (rot) {
			case Surface.ROTATION_0:
				return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
			case Surface.ROTATION_90:
				return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
			case Surface.ROTATION_180:
				// SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
				// Level 9+
				return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
						: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			case Surface.ROTATION_270:
				// SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
				// Level 9+
				return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
						: ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			default:
				return 0;
			}
		}
	}

	public void showAdvancedOptions(View v) {
		CommonDialogs.advancedOptions(this, v, MenuType.Video);
	}
}