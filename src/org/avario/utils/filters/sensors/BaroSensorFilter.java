package org.avario.utils.filters.sensors;

import org.avario.AVarioActivity;
import org.avario.R;
import org.avario.engine.SensorProducer;
import org.avario.engine.consumerdef.LocationConsumer;
import org.avario.engine.datastore.DataAccessObject;
import org.avario.engine.prefs.Preferences;
import org.avario.engine.tracks.Tracker;
import org.avario.utils.Logger;
import org.avario.utils.StringFormatter;
import org.avario.utils.UnitsConverter;
import org.avario.utils.filters.Filter;
import org.avario.utils.filters.impl.Kalman2Filter;

import android.hardware.SensorManager;
import android.location.Location;
import android.widget.Toast;

public class BaroSensorFilter implements LocationConsumer {
	private Filter baroFilter = new Kalman2Filter(Preferences.baro_sensitivity); // new
																					// MedianFixFilter(Preferences.baro_sensitivity);
	private volatile float referrencePresure = Preferences.ref_qnh;

	public static volatile float lastPresureNotified = -1f;
	private volatile boolean gpsAltitude = false;

	public BaroSensorFilter() {
		Logger.get().log("Filter baro with % " + Preferences.baro_sensitivity);
		SensorProducer.get().registerConsumer(this);
	}

	// filter the pressure and transform it to altitude
	public synchronized float toAltitude(float currentPresure) {
		lastPresureNotified = baroFilter.doFilter(currentPresure)[0];
		if (referrencePresure != Preferences.ref_qnh) {
			baroFilter.reset();
			// altitudeFilter.reset();
			DataAccessObject.get().resetVSpeed();
			referrencePresure = Preferences.ref_qnh;
		}
		float sensorAlt = SensorManager.getAltitude(referrencePresure, lastPresureNotified);
		return sensorAlt;
	}

	@Override
	public void notifyWithLocation(final Location location) {
		if (!gpsAltitude && location.hasAltitude() && lastPresureNotified > 0f && location.getAccuracy() < 10) {
			Logger.get().log("GPS Altitude " + location.getAltitude());
			gpsAltitude = true;
			adjustAltitude(location.getAltitude());

			// Start the track if selected
			if (Preferences.auto_track && !Tracker.get().isTracking()) {
				AVarioActivity.startAutoTrack();
			}
			AVarioActivity.CONTEXT.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					String altitudeChangeNotif = AVarioActivity.CONTEXT.getApplicationContext()
							.getString(
									R.string.altitude_from_gps,
									StringFormatter.noDecimals(UnitsConverter.toPreferredShort((float) location
											.getAltitude())));
					Toast.makeText(AVarioActivity.CONTEXT, altitudeChangeNotif, Toast.LENGTH_LONG).show();
				}
			});
		}
	}

	protected synchronized void adjustAltitude(double h) {
		float ref = SensorManager.PRESSURE_STANDARD_ATMOSPHERE + 100;
		// double h = location.getAltitude();
		// adjust the reference pressure until the pressure sensor
		// altitude match the gps altitude +-5m
		if (h > 0) {
			double delta = Math.abs(SensorManager.getAltitude(ref, lastPresureNotified) - h);
			while (delta > 2 && ref > 0) {
				ref -= 0.1 * delta;
				delta = Math.abs(SensorManager.getAltitude(ref, lastPresureNotified) - h);
			}
		} else {
			ref = SensorManager.PRESSURE_STANDARD_ATMOSPHERE;
		}
		baroFilter.reset();
		// altitudeFilter.reset();
		DataAccessObject.get().resetVSpeed();
		referrencePresure = ref;
	}
}
