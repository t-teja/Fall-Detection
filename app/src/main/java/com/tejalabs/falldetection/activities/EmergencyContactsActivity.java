package com.tejalabs.falldetection.activities;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.tejalabs.falldetection.R;
import com.tejalabs.falldetection.adapters.EmergencyContactAdapter;
import com.tejalabs.falldetection.models.EmergencyContact;
import com.tejalabs.falldetection.utils.ContactManager;

import java.util.List;

public class EmergencyContactsActivity extends AppCompatActivity implements EmergencyContactAdapter.OnContactActionListener {

    private static final String TAG = "EmergencyContactsActivity";
    private static final int PICK_CONTACT_REQUEST = 1001;

    // UI Components
    private RecyclerView recyclerViewContacts;
    private FloatingActionButton fabAddContact;
    private View emptyStateView;

    // Utilities
    private ContactManager contactManager;
    private EmergencyContactAdapter contactAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contacts);

        Log.d(TAG, "EmergencyContactsActivity created");

        // Initialize utilities
        contactManager = new ContactManager(this);

        // Setup toolbar
        setupToolbar();

        // Initialize UI
        initializeUI();

        // Load contacts
        loadContacts();
    }

    /**
     * Setup toolbar with back button
     */
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Emergency Contacts");
        }
    }

    /**
     * Initialize UI components
     */
    private void initializeUI() {
        recyclerViewContacts = findViewById(R.id.recycler_view_contacts);
        fabAddContact = findViewById(R.id.fab_add_contact);
        emptyStateView = findViewById(R.id.empty_state_view);

        // Setup RecyclerView
        recyclerViewContacts.setLayoutManager(new LinearLayoutManager(this));
        contactAdapter = new EmergencyContactAdapter(this);
        recyclerViewContacts.setAdapter(contactAdapter);

        // Setup FAB
        fabAddContact.setOnClickListener(v -> showAddContactDialog());
    }

    /**
     * Load emergency contacts
     */
    private void loadContacts() {
        try {
            List<EmergencyContact> contacts = contactManager.getAllContacts();
            contactAdapter.updateContacts(contacts);

            // Show/hide empty state
            if (contacts.isEmpty()) {
                recyclerViewContacts.setVisibility(View.GONE);
                emptyStateView.setVisibility(View.VISIBLE);
            } else {
                recyclerViewContacts.setVisibility(View.VISIBLE);
                emptyStateView.setVisibility(View.GONE);
            }

            Log.d(TAG, "Loaded " + contacts.size() + " emergency contacts");
        } catch (Exception e) {
            Log.e(TAG, "Error loading contacts", e);
            Toast.makeText(this, "Error loading contacts", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show dialog to add new contact
     */
    private void showAddContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Emergency Contact");

        String[] options = {"Enter manually", "Pick from contacts"};
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                showManualEntryDialog();
            } else {
                pickFromContacts();
            }
        });

        builder.show();
    }

    /**
     * Show manual entry dialog
     */
    private void showManualEntryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Contact Manually");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_contact, null);
        EditText editName = dialogView.findViewById(R.id.edit_contact_name);
        EditText editPhone = dialogView.findViewById(R.id.edit_contact_phone);

        builder.setView(dialogView);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = editName.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            addContact(name, phone);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Pick contact from phone contacts
     */
    private void pickFromContacts() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        startActivityForResult(intent, PICK_CONTACT_REQUEST);
    }

    /**
     * Add new emergency contact
     */
    private void addContact(String name, String phoneNumber) {
        try {
            EmergencyContact contact = new EmergencyContact(name, phoneNumber);
            boolean added = contactManager.addContact(contact);

            if (added) {
                Toast.makeText(this, "Contact added successfully", Toast.LENGTH_SHORT).show();
                loadContacts(); // Refresh list
            } else {
                Toast.makeText(this, "Failed to add contact", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding contact", e);
            Toast.makeText(this, "Error adding contact", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK && data != null) {
            try {
                Uri contactUri = data.getData();
                String[] projection = {
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                };

                android.database.Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    int phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                    String name = cursor.getString(nameIndex);
                    String phone = cursor.getString(phoneIndex);

                    cursor.close();

                    addContact(name, phone);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error picking contact", e);
                Toast.makeText(this, "Error picking contact", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // EmergencyContactAdapter.OnContactActionListener implementation
    @Override
    public void onEditContact(EmergencyContact contact) {
        showEditContactDialog(contact);
    }

    @Override
    public void onDeleteContact(EmergencyContact contact) {
        showDeleteConfirmationDialog(contact);
    }

    @Override
    public void onCallContact(EmergencyContact contact) {
        try {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + contact.getPhoneNumber()));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error making call", e);
            Toast.makeText(this, "Error making call", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show edit contact dialog
     */
    private void showEditContactDialog(EmergencyContact contact) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Contact");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_contact, null);
        EditText editName = dialogView.findViewById(R.id.edit_contact_name);
        EditText editPhone = dialogView.findViewById(R.id.edit_contact_phone);

        // Pre-fill with current values
        editName.setText(contact.getName());
        editPhone.setText(contact.getPhoneNumber());

        builder.setView(dialogView);
        builder.setPositiveButton("Update", (dialog, which) -> {
            String name = editName.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            updateContact(contact, name, phone);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Show delete confirmation dialog
     */
    private void showDeleteConfirmationDialog(EmergencyContact contact) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Contact");
        builder.setMessage("Are you sure you want to delete " + contact.getName() + "?");

        builder.setPositiveButton("Delete", (dialog, which) -> {
            deleteContact(contact);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Update existing contact
     */
    private void updateContact(EmergencyContact oldContact, String newName, String newPhone) {
        try {
            EmergencyContact newContact = new EmergencyContact(newName, newPhone);
            newContact.setId(oldContact.getId());

            boolean updated = contactManager.updateContact(newContact);

            if (updated) {
                Toast.makeText(this, "Contact updated successfully", Toast.LENGTH_SHORT).show();
                loadContacts(); // Refresh list
            } else {
                Toast.makeText(this, "Failed to update contact", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating contact", e);
            Toast.makeText(this, "Error updating contact", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Delete contact
     */
    private void deleteContact(EmergencyContact contact) {
        try {
            boolean deleted = contactManager.removeContact(contact.getId());

            if (deleted) {
                Toast.makeText(this, "Contact deleted successfully", Toast.LENGTH_SHORT).show();
                loadContacts(); // Refresh list
            } else {
                Toast.makeText(this, "Failed to delete contact", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting contact", e);
            Toast.makeText(this, "Error deleting contact", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
