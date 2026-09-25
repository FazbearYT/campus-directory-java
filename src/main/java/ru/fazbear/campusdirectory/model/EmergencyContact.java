package ru.fazbear.campusdirectory.model;

public final class EmergencyContact extends Contact {
    public EmergencyContact(String name, String phone, String email, String organization) {
        super(name, phone, email, organization);
    }

    @Override
    public ContactType getType() {
        return ContactType.EMERGENCY;
    }
}
