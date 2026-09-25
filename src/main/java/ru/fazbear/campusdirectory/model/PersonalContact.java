package ru.fazbear.campusdirectory.model;

import java.util.List;

public final class PersonalContact extends Contact implements Editable {
    public PersonalContact(String name, String phone, String email, String organization) {
        super(name, phone, email, organization);
    }

    @Override
    public ContactType getType() {
        return ContactType.PERSONAL;
    }

    @Override
    public List<String> validate() {
        return ContactValidator.validate(this);
    }
}
