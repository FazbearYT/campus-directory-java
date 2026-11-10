package ru.fazbear.campusdirectory.io;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.ContactType;
import ru.fazbear.campusdirectory.model.CorporateContact;
import ru.fazbear.campusdirectory.model.EmergencyContact;
import ru.fazbear.campusdirectory.model.PersonalContact;

public record ContactData(
        ContactType type, String name, String phone, String email,
        String organization, String position, int extension
) {
    public static ContactData from(Contact contact) {
        return new ContactData(contact.getType(), contact.getName(), contact.getPhone(),
                contact.getEmail(), contact.getOrganization(),
                contact instanceof CorporateContact corporate ? corporate.getPosition() : "",
                contact instanceof CorporateContact corporate ? corporate.getExtension() : 0);
    }

    public Contact toContact() {
        return switch (type) {
            case PERSONAL -> new PersonalContact(name, phone, email, organization);
            case CORPORATE -> new CorporateContact(name, phone, email, organization, position, extension);
            case EMERGENCY -> new EmergencyContact(name, phone, email, organization);
        };
    }
}
