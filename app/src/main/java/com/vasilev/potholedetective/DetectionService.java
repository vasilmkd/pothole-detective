package com.vasilev.potholedetective;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public final class DetectionService extends Service implements SensorEventListener {

    public static final String ACTION_START = "action_start";
    public static final String ACTION_STOP = "action_stop";

    private static final float Z_ACCELERATION_THRESHOLD = 25f;

    private static final String NOTIFICATION_CHANNEL_DETECTION_ID =
            "notification_channel_detection";
    private static final int DETECTION_NOTIFICATION_ID = 1;

    private SharedPreferences sharedPreferences;

    private SensorManager sensorManager;

    private Sensor rotationVectorSensor;
    private float[] rotationMatrix;

    private Sensor linearAccelerationSensor;
    private float[] linearAccelerationVector;

    private Handler handler;

    private FusedLocationProviderClient fusedLocationProviderClient;

    private Location location;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            location = locationResult.getLastLocation();
        }
    };

    private Runnable broadcast = () -> {
        double latitude = location != null ? location.getLatitude() : 41.994293;
        double longitude = location != null ? location.getLongitude() : 21.418497;
        db.collection("potholes").document("global")
                .update("potholes", FieldValue.arrayUnion(new Pothole(latitude, longitude)));
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences =
                getSharedPreferences(MainViewModel.SHARED_PREFERENCES_NAME, MODE_PRIVATE);
        sensorManager = getSystemService(SensorManager.class);
        assert sensorManager != null;
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        handler = new Handler(Looper.getMainLooper());
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        assert intent != null;
        String action = intent.getAction();
        assert action != null;
        switch (action) {
            case ACTION_START:
                startForeground(DETECTION_NOTIFICATION_ID, createDetectionNotification());
                sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
                sensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_GAME);
                fusedLocationProviderClient.requestLocationUpdates(createLocationRequest(),
                        locationCallback, Looper.getMainLooper());
                setDetectionRunning(true);
                break;
            case ACTION_STOP:
                stopForeground(true);
                sensorManager.unregisterListener(this, rotationVectorSensor);
                sensorManager.unregisterListener(this, linearAccelerationSensor);
                fusedLocationProviderClient.removeLocationUpdates(locationCallback);
                setDetectionRunning(false);
                stopSelf();
                break;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:
                if (rotationMatrix == null) {
                    rotationMatrix = new float[9];
                }
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                invertMatrix(rotationMatrix);
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                if (rotationMatrix != null) {
                    float[] result = multiply(rotationMatrix, event.values);
                    if (Double.compare(result[2], Z_ACCELERATION_THRESHOLD) > 0) {
                        if (linearAccelerationVector == null) {
                            linearAccelerationVector = result;
                        }
                        linearAccelerationVector = maxVector(linearAccelerationVector, result);
                        handler.removeCallbacks(broadcast);
                        handler.postDelayed(broadcast, 5000L);
                    }
                }
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void setDetectionRunning(boolean running) {
        sharedPreferences.edit().putBoolean(MainViewModel.DETECTION_RUNNING_KEY, running).apply();
    }

    private Notification createDetectionNotification() {
        createDetectionNotificationChannel();
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, intent, 0);

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_DETECTION_ID)
                .setContentTitle(getString(R.string.notification_detection_title))
                .setContentText(getString(R.string.notification_detection_text))
                .setSmallIcon(R.drawable.ic_car_white_24dp)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void createDetectionNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String name = getString(R.string.notification_channel_detection_name);
            String description = getString(R.string.notification_channel_detection_description);
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel =
                    new NotificationChannel(NOTIFICATION_CHANNEL_DETECTION_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        }
    }

    private LocationRequest createLocationRequest() {
        LocationRequest request = LocationRequest.create();
        request.setInterval(10000L);
        request.setFastestInterval(5000L);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return request;
    }

    private static float[] maxVector(float[] vec1, float[] vec2) {
        if (Float.compare(vec1[2], vec2[2]) < 0) return vec2;
        else return vec1;
    }

    private static float[] multiply(float[] matrix, float[] vector) {
        int idx;
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                idx = 3 * i + j;
                result[i] += matrix[idx] * vector[j];
            }
        }
        return result;
    }

    private static void invertMatrix(float[] matrix) {
        float tmp;
        int idx;
        int transIdx;
        for (int i = 0; i < 3; i++) {
            for (int j = i; j < 3; j++) {
                idx = 3 * i + j;
                transIdx = transposeIndex(idx);
                tmp = matrix[idx];
                matrix[idx] = matrix[transIdx];
                matrix[transIdx] = tmp;
            }
        }
    }

    private static int transposeIndex(int index) {
        int row = index / 3;
        int col = index - (3 * row);
        return 3 * col + row;
    }
}
