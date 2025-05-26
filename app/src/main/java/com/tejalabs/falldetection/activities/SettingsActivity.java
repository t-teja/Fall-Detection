package com.tejalabs.falldetection.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Toast;

import com.tejalabs.falldetection.R;

/**
 * Settings activity for configuring fall detection parameters
 * TODO: Implement full settings UI with preferences
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // For now, show a placeholder message
        Toast.makeText(this, "Settings screen - Coming soon!", Toast.LENGTH_LONG).show();
        
        // Close activity for now
        finish();
    }
}
