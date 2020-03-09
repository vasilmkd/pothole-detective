package com.vasilev.potholedetective;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

public final class MainViewModel extends AndroidViewModel
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    static final String SHARED_PREFERENCES_NAME = "view_mode_shared_preferences_main";
    static final String DETECTION_RUNNING_KEY = "detection_running";

    private SharedPreferences preferences;
    private MutableLiveData<Boolean> detectionRunning = new MutableLiveData<>();

    public MainViewModel(@NonNull Application application) {
        super(application);
        preferences = getApplication()
                .getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(this);
        detectionRunning.setValue(preferences.getBoolean(DETECTION_RUNNING_KEY, false));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        preferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DETECTION_RUNNING_KEY.equals(key)) {
            detectionRunning.setValue(sharedPreferences.getBoolean(key, false));
        }
    }

    LiveData<Boolean> detectionRunning() {
        return detectionRunning;
    }

    LiveData<Integer> detectionButtonImage() {
        return Transformations.map(detectionRunning, running ->
                running ? R.drawable.ic_stop_black_24dp : R.drawable.ic_play_arrow_black_24dp);
    }

    LiveData<String> detectionButtonContentDescription() {
        return Transformations.map(detectionRunning, running -> {
            int resId = running ? R.string.button_stop_detection : R.string.button_start_detection;
            return getApplication().getString(resId);
        });
    }
}
