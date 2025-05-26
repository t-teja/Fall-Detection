package com.tejalabs.falldetection.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages emergency contacts for fall detection alerts
 * Handles contact storage, retrieval, and validation
 */
public class ContactManager {
    
    private static final String TAG = "ContactManager";
    private static final String PREFS_NAME = "emergency_contacts";
    private static final String KEY_CONTACTS = "contacts_list";
    private static final String KEY_PRIMARY_CONTACT = "primary_contact_id";
    
    private Context context;
    private SharedPreferences preferences;
    private Gson gson;
    private List<EmergencyContact> contacts;
    
    public static class EmergencyContact {
        public String id;
        public String name;
        public String phoneNumber;
        public String relationship;
        public boolean isPrimary;
        public long dateAdded;
        
        public EmergencyContact() {
            this.id = generateId();
            this.dateAdded = System.currentTimeMillis();
            this.isPrimary = false;
        }
        
        public EmergencyContact(String name, String phoneNumber, String relationship) {
            this();
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.relationship = relationship;
        }
        
        private String generateId() {
            return "contact_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        }
        
        public boolean isValid() {
            return name != null && !name.trim().isEmpty() &&
                   phoneNumber != null && !phoneNumber.trim().isEmpty() &&
                   isValidPhoneNumber(phoneNumber);
        }
        
        private boolean isValidPhoneNumber(String phone) {
            // Remove all non-digit characters
            String cleanPhone = phone.replaceAll("[^0-9+]", "");
            
            // Check if it's a valid length (10-15 digits, optionally starting with +)
            return cleanPhone.matches("^\\+?[0-9]{10,15}$");
        }
        
        public String getFormattedPhoneNumber() {
            if (phoneNumber == null) return "";
            
            // Remove all non-digit characters except +
            String cleaned = phoneNumber.replaceAll("[^0-9+]", "");
            
            // Format for display (if it's a 10-digit US number)
            if (cleaned.length() == 10) {
                return String.format("(%s) %s-%s",
                    cleaned.substring(0, 3),
                    cleaned.substring(3, 6),
                    cleaned.substring(6));
            }
            
            return cleaned;
        }
        
        @Override
        public String toString() {
            return String.format("%s (%s) - %s", name, relationship, getFormattedPhoneNumber());
        }
    }
    
    public ContactManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.contacts = new ArrayList<>();
        
        loadContacts();
        Log.d(TAG, "ContactManager initialized with " + contacts.size() + " contacts");
    }
    
    /**
     * Load contacts from SharedPreferences
     */
    private void loadContacts() {
        String contactsJson = preferences.getString(KEY_CONTACTS, "[]");
        Type listType = new TypeToken<List<EmergencyContact>>(){}.getType();
        
        try {
            List<EmergencyContact> loadedContacts = gson.fromJson(contactsJson, listType);
            if (loadedContacts != null) {
                contacts = loadedContacts;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading contacts", e);
            contacts = new ArrayList<>();
        }
    }
    
    /**
     * Save contacts to SharedPreferences
     */
    private void saveContacts() {
        String contactsJson = gson.toJson(contacts);
        preferences.edit()
            .putString(KEY_CONTACTS, contactsJson)
            .apply();
        
        Log.d(TAG, "Contacts saved: " + contacts.size());
    }
    
    /**
     * Add a new emergency contact
     */
    public boolean addContact(EmergencyContact contact) {
        if (contact == null || !contact.isValid()) {
            Log.w(TAG, "Invalid contact provided");
            return false;
        }
        
        // Check for duplicate phone numbers
        for (EmergencyContact existing : contacts) {
            if (existing.phoneNumber.equals(contact.phoneNumber)) {
                Log.w(TAG, "Contact with this phone number already exists");
                return false;
            }
        }
        
        // If this is the first contact, make it primary
        if (contacts.isEmpty()) {
            contact.isPrimary = true;
        }
        
        contacts.add(contact);
        saveContacts();
        
        Log.i(TAG, "Contact added: " + contact.name);
        return true;
    }
    
    /**
     * Update an existing contact
     */
    public boolean updateContact(String contactId, EmergencyContact updatedContact) {
        if (contactId == null || updatedContact == null || !updatedContact.isValid()) {
            return false;
        }
        
        for (int i = 0; i < contacts.size(); i++) {
            if (contacts.get(i).id.equals(contactId)) {
                // Preserve original ID and date
                updatedContact.id = contactId;
                updatedContact.dateAdded = contacts.get(i).dateAdded;
                
                contacts.set(i, updatedContact);
                saveContacts();
                
                Log.i(TAG, "Contact updated: " + updatedContact.name);
                return true;
            }
        }
        
        Log.w(TAG, "Contact not found for update: " + contactId);
        return false;
    }
    
    /**
     * Remove a contact
     */
    public boolean removeContact(String contactId) {
        if (contactId == null) {
            return false;
        }
        
        EmergencyContact removedContact = null;
        for (int i = 0; i < contacts.size(); i++) {
            if (contacts.get(i).id.equals(contactId)) {
                removedContact = contacts.remove(i);
                break;
            }
        }
        
        if (removedContact != null) {
            // If we removed the primary contact, make the first remaining contact primary
            if (removedContact.isPrimary && !contacts.isEmpty()) {
                contacts.get(0).isPrimary = true;
            }
            
            saveContacts();
            Log.i(TAG, "Contact removed: " + removedContact.name);
            return true;
        }
        
        Log.w(TAG, "Contact not found for removal: " + contactId);
        return false;
    }
    
    /**
     * Set a contact as primary
     */
    public boolean setPrimaryContact(String contactId) {
        if (contactId == null) {
            return false;
        }
        
        // Remove primary flag from all contacts
        for (EmergencyContact contact : contacts) {
            contact.isPrimary = false;
        }
        
        // Set the specified contact as primary
        for (EmergencyContact contact : contacts) {
            if (contact.id.equals(contactId)) {
                contact.isPrimary = true;
                saveContacts();
                
                Log.i(TAG, "Primary contact set: " + contact.name);
                return true;
            }
        }
        
        Log.w(TAG, "Contact not found for primary setting: " + contactId);
        return false;
    }
    
    /**
     * Get all emergency contacts
     */
    public List<EmergencyContact> getEmergencyContacts() {
        return new ArrayList<>(contacts);
    }
    
    /**
     * Get primary emergency contact
     */
    public EmergencyContact getPrimaryEmergencyContact() {
        for (EmergencyContact contact : contacts) {
            if (contact.isPrimary) {
                return contact;
            }
        }
        
        // If no primary contact is set, return the first one
        if (!contacts.isEmpty()) {
            contacts.get(0).isPrimary = true;
            saveContacts();
            return contacts.get(0);
        }
        
        return null;
    }
    
    /**
     * Get contact by ID
     */
    public EmergencyContact getContactById(String contactId) {
        if (contactId == null) {
            return null;
        }
        
        for (EmergencyContact contact : contacts) {
            if (contact.id.equals(contactId)) {
                return contact;
            }
        }
        
        return null;
    }
    
    /**
     * Check if any contacts are configured
     */
    public boolean hasEmergencyContacts() {
        return !contacts.isEmpty();
    }
    
    /**
     * Get contact count
     */
    public int getContactCount() {
        return contacts.size();
    }
    
    /**
     * Validate all contacts and remove invalid ones
     */
    public int validateAndCleanContacts() {
        List<EmergencyContact> validContacts = new ArrayList<>();
        int removedCount = 0;
        
        for (EmergencyContact contact : contacts) {
            if (contact.isValid()) {
                validContacts.add(contact);
            } else {
                removedCount++;
                Log.w(TAG, "Removing invalid contact: " + contact.name);
            }
        }
        
        if (removedCount > 0) {
            contacts = validContacts;
            saveContacts();
            Log.i(TAG, "Cleaned " + removedCount + " invalid contacts");
        }
        
        return removedCount;
    }
    
    /**
     * Export contacts as JSON string
     */
    public String exportContacts() {
        return gson.toJson(contacts);
    }
    
    /**
     * Import contacts from JSON string
     */
    public boolean importContacts(String contactsJson, boolean replaceExisting) {
        try {
            Type listType = new TypeToken<List<EmergencyContact>>(){}.getType();
            List<EmergencyContact> importedContacts = gson.fromJson(contactsJson, listType);
            
            if (importedContacts == null) {
                return false;
            }
            
            if (replaceExisting) {
                contacts.clear();
            }
            
            int addedCount = 0;
            for (EmergencyContact contact : importedContacts) {
                if (contact.isValid()) {
                    // Generate new ID to avoid conflicts
                    contact.id = contact.generateId();
                    contact.dateAdded = System.currentTimeMillis();
                    
                    // Check for duplicates
                    boolean isDuplicate = false;
                    for (EmergencyContact existing : contacts) {
                        if (existing.phoneNumber.equals(contact.phoneNumber)) {
                            isDuplicate = true;
                            break;
                        }
                    }
                    
                    if (!isDuplicate) {
                        contacts.add(contact);
                        addedCount++;
                    }
                }
            }
            
            // Ensure we have a primary contact
            if (!contacts.isEmpty()) {
                boolean hasPrimary = false;
                for (EmergencyContact contact : contacts) {
                    if (contact.isPrimary) {
                        hasPrimary = true;
                        break;
                    }
                }
                
                if (!hasPrimary) {
                    contacts.get(0).isPrimary = true;
                }
            }
            
            saveContacts();
            Log.i(TAG, "Imported " + addedCount + " contacts");
            return addedCount > 0;
            
        } catch (Exception e) {
            Log.e(TAG, "Error importing contacts", e);
            return false;
        }
    }
    
    /**
     * Clear all contacts
     */
    public void clearAllContacts() {
        contacts.clear();
        saveContacts();
        Log.i(TAG, "All contacts cleared");
    }
}
