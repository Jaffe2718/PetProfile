package io.github.jaffe2718.petprofile.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import io.github.jaffe2718.petprofile.ui.MapPickerActivity;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public final class LocationHelper {
    public static final int REQUEST_LOCATION = 4001;
    private static final long TIMEOUT_MS = 15000L;

    private LocationHelper() {
    }

    public interface Callback {
        void onResult(LocationResult result);

        void onError(String message);
    }

    public static class LocationResult {
        public String name;
        public double latitude;
        public double longitude;
    }

    public static void openMapPicker(Activity activity, int requestCode, double initialLatitude, double initialLongitude) {
        Intent intent = new Intent(activity, MapPickerActivity.class);
        intent.putExtra(MapPickerActivity.EXTRA_INITIAL_LATITUDE, initialLatitude);
        intent.putExtra(MapPickerActivity.EXTRA_INITIAL_LONGITUDE, initialLongitude);
        activity.startActivityForResult(intent, requestCode);
    }

    public static double[] lastKnownCoordinates(Activity activity) {
        LocationManager manager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return null;
        }
        Location location = lastKnownLocation(manager);
        if (location == null) {
            return null;
        }
        return new double[]{location.getLatitude(), location.getLongitude()};
    }

    public static void resolveAddress(Activity activity, double latitude, double longitude, Callback callback) {
        Location location = new Location(LocationManager.PASSIVE_PROVIDER);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        reverseGeocode(activity, location, callback);
    }

    public static String formatDms(double value, boolean latitude) {
        String hemisphere;
        if (latitude) {
            hemisphere = value >= 0 ? "N" : "S";
        } else {
            hemisphere = value >= 0 ? "E" : "W";
        }
        double absolute = Math.abs(value);
        int degrees = (int) absolute;
        double minutesDouble = (absolute - degrees) * 60;
        int minutes = (int) minutesDouble;
        double secondsDouble = (minutesDouble - minutes) * 60;
        int seconds = (int) Math.round(secondsDouble);
        return degrees + "°" + minutes + "'" + seconds + "\"" + hemisphere;
    }

    public static void request(Activity activity, Callback callback) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION
            );
            callback.onError(activity.getString(io.github.jaffe2718.petprofile.R.string.error_location_permission));
            return;
        }
        locate(activity, callback);
    }

    private static void locate(Activity activity, Callback callback) {
        LocationManager manager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            callback.onError(activity.getString(io.github.jaffe2718.petprofile.R.string.error_location_unavailable));
            return;
        }

        Location cached = lastKnownLocation(manager);
        if (cached != null) {
            Async.run(() -> reverseGeocode(activity, cached, callback));
            return;
        }
        requestCurrentLocation(activity, manager, callback);
    }

    private static Location lastKnownLocation(LocationManager manager) {
        try {
            Location gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (gps != null) {
                return gps;
            }
            return manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private static void requestCurrentLocation(Activity activity, LocationManager manager, Callback callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location == null) {
                    return;
                }
                cleanup(manager, this, handler, null);
                Async.run(() -> reverseGeocode(activity, location, callback));
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };
        Runnable timeout = () -> {
            cleanup(manager, listener, handler, null);
            callback.onError(activity.getString(io.github.jaffe2718.petprofile.R.string.error_location_timeout));
        };

        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper());
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper());
        } catch (SecurityException ignored) {
            cleanup(manager, listener, handler, null);
            callback.onError(activity.getString(io.github.jaffe2718.petprofile.R.string.error_location_permission));
            return;
        }
        handler.postDelayed(timeout, TIMEOUT_MS);
    }

    private static void cleanup(LocationManager manager, LocationListener listener, Handler handler, Runnable timeout) {
        if (handler != null && timeout != null) {
            handler.removeCallbacks(timeout);
        }
        try {
            manager.removeUpdates(listener);
        } catch (Exception ignored) {
        }
    }

    private static void reverseGeocode(Activity activity, Location location, Callback callback) {
        LocationResult result = new LocationResult();
        result.latitude = location.getLatitude();
        result.longitude = location.getLongitude();
        try {
            Geocoder geocoder = new Geocoder(activity, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(result.latitude, result.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder name = new StringBuilder();
                append(name, address.getLocality());
                append(name, address.getSubLocality());
                append(name, address.getThoroughfare());
                if (name.length() == 0) {
                    append(name, address.getCountryName());
                }
                result.name = name.toString();
            }
        } catch (IOException ignored) {
            result.name = formatDms(result.latitude, true) + ", " + formatDms(result.longitude, false);
        }
        if (result.name == null || result.name.trim().isEmpty()) {
            result.name = formatDms(result.latitude, true) + ", " + formatDms(result.longitude, false);
        }
        Async.ui(() -> callback.onResult(result));
    }

    private static void append(StringBuilder builder, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(value.trim());
    }
}
