package com.vasilev.potholedetective;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public final class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int START_DETECTION_SERVICE_LOCATION_PERMISSION_REQUEST = 1;

    private GoogleMap map;

    private CoordinatorLayout coordinatorLayout;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    private MutableLiveData<List<Pothole>> potholes = new MutableLiveData<>();

    @SuppressWarnings("unchecked")
    private EventListener<DocumentSnapshot> documentSnapshotEventListener = (snapshot, e) -> {
        if (e != null) {
            Log.e("MainActivity", "Document snapshot listening failed", e);
        } else {
            if (snapshot != null && snapshot.exists()) {
                Map<String, Object> data = snapshot.getData();
                assert data != null;
                List<Map<String, Object>> potholeMaps = (List<Map<String, Object>>)
                        data.get("potholes");
                assert potholeMaps != null;
                List<Pothole> potholeData = potholeMaps.stream()
                        .map(Pothole::fromMap).collect(Collectors.toList());
                potholes.setValue(potholeData);
            } else {
                Log.d("MainActivity", "Current data: null");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        coordinatorLayout = findViewById(R.id.coordinator_layout);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        MainViewModel viewModel = new ViewModelProvider(this,
                new ViewModelProvider.AndroidViewModelFactory(getApplication()))
                .get(MainViewModel.class);
        viewModel.detectionButtonImage().observe(this, fab::setImageResource);
        viewModel.detectionButtonContentDescription().observe(this, description -> {
            fab.setContentDescription(description);
            ViewCompat.setTooltipText(fab, description);
        });
        viewModel.detectionRunning().observe(this, running -> {
            FloatingActionButton.OnClickListener listener = running
                    ? v -> stopDetectionService()
                    : v -> startDetectionService();
            fab.setOnClickListener(listener);
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        DocumentReference docRef = db.collection("potholes").document("global");
        docRef.addSnapshotListener(documentSnapshotEventListener);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == START_DETECTION_SERVICE_LOCATION_PERMISSION_REQUEST) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDetectionService();
            } else {
                Toast.makeText(this, R.string.no_detection_location_permission_unavailable,
                        Toast.LENGTH_LONG).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.map = googleMap;

        LatLng skopje = new LatLng(41.994293, 21.418497);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(skopje, 14.0f));

        potholes.observe(this, ps ->
                ps.forEach(p -> map.addMarker(new MarkerOptions().position(p.toLatLng()))));
    }

    private void startDetectionService() {
        if (locationPermissionApproved()) {
            Intent intent = new Intent(this, DetectionService.class);
            intent.setAction(DetectionService.ACTION_START);
            ContextCompat.startForegroundService(this, intent);
            Snackbar.make(coordinatorLayout, R.string.detection_started, Snackbar.LENGTH_LONG)
                    .show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION},
                    START_DETECTION_SERVICE_LOCATION_PERMISSION_REQUEST);
        }
    }

    private void stopDetectionService() {
        Intent intent = new Intent(this, DetectionService.class);
        intent.setAction(DetectionService.ACTION_STOP);
        startService(intent);
        Snackbar.make(coordinatorLayout, R.string.detection_stopped, Snackbar.LENGTH_LONG).show();
    }

    private boolean locationPermissionApproved() {
        return ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED;
    }
}
