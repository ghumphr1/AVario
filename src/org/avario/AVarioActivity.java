package org.avario;

import org.avario.engine.DataAccessObject;
import org.avario.engine.LocationsHistory;
import org.avario.engine.SensorProducer;
import org.avario.engine.poi.PoiManager;
import org.avario.engine.prefs.Preferences;
import org.avario.engine.sounds.BeepBeeper;
import org.avario.engine.tracks.Tracker;
import org.avario.ui.NavigatorUpdater;
import org.avario.ui.NumericViewUpdater;
import org.avario.ui.VarioMeterScaleUpdater;
import org.avario.ui.poi.PoiList;
import org.avario.ui.prefs.PreferencesMenu;
import org.avario.ui.tracks.TracksList;
import org.avario.utils.Logger;
import org.avario.utils.Speaker;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * 
 */
public class AVarioActivity extends Activity {
	public static Context CONTEXT;
	private PowerManager.WakeLock wakeLock;
	private int startVolume = Integer.MIN_VALUE;
	private boolean viewCreated = false;

	public AVarioActivity() {
		super();
		AVarioActivity.CONTEXT = this;
	}

	/** Called with the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (viewCreated) {
			return;
		}
		viewCreated = true;

		AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		startVolume = audio.getStreamVolume(AudioManager.STREAM_DTMF);

		Preferences.update(this);
		super.onCreate(savedInstanceState);
		Logger.init();
		SensorProducer.init(this);
		// Draw the UI from the vario.xml layout
		setContentView(R.layout.vario);
		DataAccessObject.init();
		// Initialize sensors listeners
		NumericViewUpdater.init(this);
		VarioMeterScaleUpdater.init(this);
		LocationsHistory.init(this);
		BeepBeeper.init(this);
		NavigatorUpdater.init(this);
		Tracker.init(this);
		PoiManager.init();
		Speaker.init(this);

		// Keep the screen awake
		try {
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "AVario lock");
			wakeLock.acquire();
		} catch (Exception ex) {
			Logger.get().log("Fail keeping awake " + ex.getMessage());
		}
		addNotification();
	}

	private void addNotification() {
		try {
			Notification notification = new Notification(R.drawable.icon, "AVario", System.currentTimeMillis());
			notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
			NotificationManager notifier = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

			Intent intent = new Intent(this, AVarioActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);

			// Set the info for the views that show in the notification panel.
			notification.setLatestEventInfo(this, "AVario", "AVario", contentIntent);

			notifier.notify(22313, notification);
		} catch (Exception ex) {
			Logger.get().log("Fail placing notification icon " + ex.getMessage());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.preferences:
			startActivityForResult(new Intent(this, PreferencesMenu.class), 1);
			return true;
		case R.id.tracks:
			startActivityForResult(new Intent(this, TracksList.class), 2);
			return true;
		case R.id.poi:
			startActivityForResult(new Intent(this, PoiList.class), 3);
			return true;
		case R.id.ontrack:
			if (Tracker.get().isTracking()) {
				Tracker.get().stopTracking();
				item.setTitle(R.string.starttracking);
			} else {
				Tracker.get().startTracking();
				item.setTitle(R.string.stoptracking);
			}
			return true;
		case R.id.exit:
			// A bit brutal ....
			onDestroy();
			System.runFinalizersOnExit(true);
			System.exit(0);
			return true;
		}
		return false;
	}

	@Override
	public void onBackPressed() {
		// To disable back button
		Toast.makeText(this, R.string.exit_from_menu, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onAttachedToWindow() {
		// To disable home button
		// this.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD);
		super.onAttachedToWindow();
	}

	@Override
	protected void onDestroy() {
		try {
			if (wakeLock != null) {
				wakeLock.release();
			}
			Tracker.get().stopTracking();
			removeNotification();
			SensorProducer.clear();
			BeepBeeper.clear();
			VarioMeterScaleUpdater.clear();
			NumericViewUpdater.clear();
			DataAccessObject.clear();
			Logger.get().log("App terminated...");
			Logger.get().close();
		} catch (Exception ex) {
			Logger.get().log("Fail terminating awake " + ex.getMessage());
		} finally {
			super.onDestroy();
		}
	}

	private void removeNotification() {
		try {
			NotificationManager notifier = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			notifier.cancel(22313);
			if (startVolume != Integer.MIN_VALUE) {
				AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				audio.setStreamVolume(AudioManager.STREAM_DTMF, startVolume, 0);
			}
		} catch (Exception ex) {
			Logger.get().log("Fail to restore volume", ex);
		}
	}

}
