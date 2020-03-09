package com.vasilev.potholedetective;

import com.google.android.gms.maps.model.LatLng;

import java.util.Locale;
import java.util.Map;

public final class Pothole {

    private double latitude;
    private double longitude;

    public Pothole() {
    }

    Pothole(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    LatLng toLatLng() {
        return new LatLng(latitude, longitude);
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(),
                "Pothole(latitude = %f, longitude = %f)", latitude, longitude);
    }

    static Pothole fromMap(Map<String, Object> map) {
        double latitude = (double) map.get("latitude");
        double longitude = (double) map.get("longitude");
        return new Pothole(latitude, longitude);
    }
}
