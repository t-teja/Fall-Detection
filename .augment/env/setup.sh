#!/bin/bash

# Update system packages
sudo apt-get update

# Install OpenJDK 17 (required for Android development)
sudo apt-get install -y openjdk-17-jdk

# Set JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> $HOME/.profile

# Install Android SDK command line tools
cd $HOME
wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
unzip -q commandlinetools-linux-9477386_latest.zip
mkdir -p android-sdk/cmdline-tools
mv cmdline-tools android-sdk/cmdline-tools/latest

# Set Android SDK environment variables
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
echo 'export ANDROID_HOME=$HOME/android-sdk' >> $HOME/.profile
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> $HOME/.profile

# Accept Android SDK licenses
yes | sdkmanager --licenses

# Install required Android SDK components
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Go back to workspace
cd /mnt/persist/workspace

# Make gradlew executable
chmod +x ./gradlew

# Update EmergencyContact model class with ID field
mkdir -p app/src/main/java/com/tejalabs/falldetection/models
cat > app/src/main/java/com/tejalabs/falldetection/models/EmergencyContact.java << 'EOF'
package com.tejalabs.falldetection.models;

public class EmergencyContact {
    private long id;
    private String name;
    private String phoneNumber;
    private boolean isPrimary;
    private static long nextId = 1;

    public EmergencyContact(String name, String phoneNumber) {
        this.id = nextId++;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.isPrimary = false;
    }

    public EmergencyContact(String name, String phoneNumber, boolean isPrimary) {
        this.id = nextId++;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.isPrimary = isPrimary;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
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

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean primary) {
        isPrimary = primary;
    }

    @Override
    public String toString() {
        return "EmergencyContact{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", isPrimary=" + isPrimary +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        EmergencyContact that = (EmergencyContact) obj;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
EOF

# Update ContactManager class with all required methods
cat > app/src/main/java/com/tejalabs/falldetection/utils/ContactManager.java << 'EOF'
package com.tejalabs.falldetection.utils;

import android.content.Context;
import com.tejalabs.falldetection.models.EmergencyContact;
import java.util.ArrayList;
import java.util.List;

public class ContactManager {
    private Context context;
    private List<EmergencyContact> contacts;

    public ContactManager(Context context) {
        this.context = context;
        this.contacts = new ArrayList<>();
    }

    public List<EmergencyContact> getAllContacts() {
        return new ArrayList<>(contacts);
    }

    public List<EmergencyContact> getEmergencyContacts() {
        return getAllContacts();
    }

    public boolean addContact(EmergencyContact contact) {
        if (contact != null && !contacts.contains(contact)) {
            contacts.add(contact);
            return true;
        }
        return false;
    }

    public void updateContact(EmergencyContact oldContact, EmergencyContact newContact) {
        int index = contacts.indexOf(oldContact);
        if (index != -1) {
            contacts.set(index, newContact);
        }
    }

    public boolean updateContact(EmergencyContact contact) {
        for (int i = 0; i < contacts.size(); i++) {
            if (contacts.get(i).getId() == contact.getId()) {
                contacts.set(i, contact);
                return true;
            }
        }
        return false;
    }

    public void deleteContact(EmergencyContact contact) {
        contacts.remove(contact);
    }

    public boolean removeContact(long contactId) {
        return contacts.removeIf(contact -> contact.getId() == contactId);
    }

    public boolean hasContacts() {
        return !contacts.isEmpty();
    }

    public int getContactCount() {
        return contacts.size();
    }

    public EmergencyContact getPrimaryContact() {
        for (EmergencyContact contact : contacts) {
            if (contact.isPrimary()) {
                return contact;
            }
        }
        return contacts.isEmpty() ? null : contacts.get(0);
    }

    public EmergencyContact getPrimaryEmergencyContact() {
        return getPrimaryContact();
    }

    // Inner class for backward compatibility
    public static class EmergencyContact extends com.tejalabs.falldetection.models.EmergencyContact {
        public EmergencyContact(String name, String phoneNumber) {
            super(name, phoneNumber);
        }

        public EmergencyContact(String name, String phoneNumber, boolean isPrimary) {
            super(name, phoneNumber, isPrimary);
        }
    }
}
EOF

# Create a basic test directory structure
mkdir -p app/src/test/java/com/tejalabs/falldetection

# Create a simple unit test for EmergencyContact
cat > app/src/test/java/com/tejalabs/falldetection/EmergencyContactTest.java << 'EOF'
package com.tejalabs.falldetection;

import com.tejalabs.falldetection.models.EmergencyContact;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmergencyContactTest {

    @Test
    public void testEmergencyContactCreation() {
        EmergencyContact contact = new EmergencyContact("John Doe", "123-456-7890");
        assertEquals("John Doe", contact.getName());
        assertEquals("123-456-7890", contact.getPhoneNumber());
        assertFalse(contact.isPrimary());
        assertTrue(contact.getId() > 0);
    }

    @Test
    public void testEmergencyContactWithPrimary() {
        EmergencyContact contact = new EmergencyContact("Jane Doe", "098-765-4321", true);
        assertEquals("Jane Doe", contact.getName());
        assertEquals("098-765-4321", contact.getPhoneNumber());
        assertTrue(contact.isPrimary());
        assertTrue(contact.getId() > 0);
    }

    @Test
    public void testSetters() {
        EmergencyContact contact = new EmergencyContact("Test", "000-000-0000");
        long originalId = contact.getId();
        
        contact.setName("Updated Name");
        contact.setPhoneNumber("111-111-1111");
        contact.setPrimary(true);
        contact.setId(999);
        
        assertEquals("Updated Name", contact.getName());
        assertEquals("111-111-1111", contact.getPhoneNumber());
        assertTrue(contact.isPrimary());
        assertEquals(999, contact.getId());
    }

    @Test
    public void testEqualsAndHashCode() {
        EmergencyContact contact1 = new EmergencyContact("Test", "123-456-7890");
        EmergencyContact contact2 = new EmergencyContact("Test2", "098-765-4321");
        
        // Set same ID
        contact2.setId(contact1.getId());
        
        assertEquals(contact1, contact2);
        assertEquals(contact1.hashCode(), contact2.hashCode());
    }
}
EOF

# Create a simple unit test for ContactManager
cat > app/src/test/java/com/tejalabs/falldetection/ContactManagerTest.java << 'EOF'
package com.tejalabs.falldetection;

import android.content.Context;
import com.tejalabs.falldetection.models.EmergencyContact;
import com.tejalabs.falldetection.utils.ContactManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class ContactManagerTest {

    @Mock
    private Context mockContext;

    private ContactManager contactManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        contactManager = new ContactManager(mockContext);
    }

    @Test
    public void testAddContact() {
        EmergencyContact contact = new EmergencyContact("Test User", "123-456-7890");
        boolean added = contactManager.addContact(contact);
        
        assertTrue(added);
        assertTrue(contactManager.hasContacts());
        assertEquals(1, contactManager.getContactCount());
        assertEquals(1, contactManager.getAllContacts().size());
    }

    @Test
    public void testGetPrimaryContact() {
        EmergencyContact contact1 = new EmergencyContact("User 1", "111-111-1111");
        EmergencyContact contact2 = new EmergencyContact("User 2", "222-222-2222", true);
        
        contactManager.addContact(contact1);
        contactManager.addContact(contact2);
        
        EmergencyContact primary = contactManager.getPrimaryContact();
        assertEquals("User 2", primary.getName());
        assertTrue(primary.isPrimary());
        
        EmergencyContact primaryEmergency = contactManager.getPrimaryEmergencyContact();
        assertEquals(primary, primaryEmergency);
    }

    @Test
    public void testDeleteContact() {
        EmergencyContact contact = new EmergencyContact("Test User", "123-456-7890");
        contactManager.addContact(contact);
        assertTrue(contactManager.hasContacts());
        
        contactManager.deleteContact(contact);
        assertFalse(contactManager.hasContacts());
        assertEquals(0, contactManager.getContactCount());
    }

    @Test
    public void testRemoveContactById() {
        EmergencyContact contact = new EmergencyContact("Test User", "123-456-7890");
        contactManager.addContact(contact);
        assertTrue(contactManager.hasContacts());
        
        boolean removed = contactManager.removeContact(contact.getId());
        assertTrue(removed);
        assertFalse(contactManager.hasContacts());
    }

    @Test
    public void testUpdateContact() {
        EmergencyContact contact = new EmergencyContact("Test User", "123-456-7890");
        contactManager.addContact(contact);
        
        contact.setName("Updated Name");
        boolean updated = contactManager.updateContact(contact);
        
        assertTrue(updated);
        assertEquals("Updated Name", contactManager.getAllContacts().get(0).getName());
    }

    @Test
    public void testGetEmergencyContacts() {
        EmergencyContact contact1 = new EmergencyContact("User 1", "111-111-1111");
        EmergencyContact contact2 = new EmergencyContact("User 2", "222-222-2222");
        
        contactManager.addContact(contact1);
        contactManager.addContact(contact2);
        
        assertEquals(2, contactManager.getEmergencyContacts().size());
        assertEquals(contactManager.getAllContacts(), contactManager.getEmergencyContacts());
    }
}
EOF

echo "Updated EmergencyContact and ContactManager classes with all required methods"
echo "Created comprehensive unit tests"

# Try to build the project
./gradlew clean build --stacktrace