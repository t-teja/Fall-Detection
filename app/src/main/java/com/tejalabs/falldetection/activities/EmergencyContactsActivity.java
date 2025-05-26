package com.tejalabs.falldetection.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Toast;

import com.tejalabs.falldetection.R;

/**
 * Emergency contacts management activity
 * TODO: Implement full contact management UI
 */
public class EmergencyContactsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // For now, show a placeholder message
        Toast.makeText(this, "Emergency Contacts screen - Coming soon!", Toast.LENGTH_LONG).show();
        
        // Close activity for now
        finish();
    }
}
