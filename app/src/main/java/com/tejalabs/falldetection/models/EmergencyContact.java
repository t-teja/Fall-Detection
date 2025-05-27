package com.tejalabs.falldetection.models;

/**
 * Emergency Contact model class
 * This class represents an emergency contact with all necessary information
 */
public class EmergencyContact {
    private String id;
    private String name;
    private String phoneNumber;
    private String relationship;
    private boolean isPrimary;
    private boolean whatsappEnabled;
    private boolean smsEnabled;
    private long dateAdded;

    /**
     * Default constructor
     */
    public EmergencyContact() {
        this.id = generateId();
        this.dateAdded = System.currentTimeMillis();
        this.isPrimary = false;
        this.whatsappEnabled = true; // Enable WhatsApp by default
        this.smsEnabled = true; // Enable SMS by default
    }

    /**
     * Constructor with name and phone number
     */
    public EmergencyContact(String name, String phoneNumber) {
        this();
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.relationship = "Emergency Contact";
    }

    /**
     * Constructor with name, phone number and relationship
     */
    public EmergencyContact(String name, String phoneNumber, String relationship) {
        this();
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.relationship = relationship;
    }

    /**
     * Generate unique ID for the contact
     */
    private String generateId() {
        return "contact_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    /**
     * Validate if the contact has required information
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() &&
               phoneNumber != null && !phoneNumber.trim().isEmpty() &&
               isValidPhoneNumber(phoneNumber);
    }

    /**
     * Validate phone number format
     */
    private boolean isValidPhoneNumber(String phone) {
        // Remove all non-digit characters
        String cleanPhone = phone.replaceAll("[^0-9+]", "");

        // Check if it's a valid length (10-15 digits, optionally starting with +)
        return cleanPhone.matches("^\\+?[0-9]{10,15}$");
    }

    /**
     * Get formatted phone number for display
     */
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

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean primary) {
        isPrimary = primary;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(long dateAdded) {
        this.dateAdded = dateAdded;
    }

    public boolean isWhatsappEnabled() {
        return whatsappEnabled;
    }

    public void setWhatsappEnabled(boolean whatsappEnabled) {
        this.whatsappEnabled = whatsappEnabled;
    }

    public boolean isSmsEnabled() {
        return smsEnabled;
    }

    public void setSmsEnabled(boolean smsEnabled) {
        this.smsEnabled = smsEnabled;
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - %s", name, relationship, getFormattedPhoneNumber());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        EmergencyContact that = (EmergencyContact) obj;
        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
