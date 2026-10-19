package ru.fazbear.campusdirectory.model;

import java.util.List;
import java.util.Objects;

public final class CorporateContact extends Contact implements Editable {
    private final String position;
    private final int extension;

    public CorporateContact(
            String name,
            String phone,
            String email,
            String organization,
            String position,
            int extension
    ) {
        super(name, phone, email, organization);
        this.position = Objects.requireNonNull(position, "position must not be null").strip();
        this.extension = extension;
    }

    @Override
    public ContactType getType() {
        return ContactType.CORPORATE;
    }

    public String getPosition() {
        return position;
    }

    public int getExtension() {
        return extension;
    }

    @Override
    public List<String> validate() {
        return ContactValidator.validate(this);
    }

    @Override
    public boolean equals(Object other) {
        if (!super.equals(other)) {
            return false;
        }
        CorporateContact contact = (CorporateContact) other;
        return extension == contact.extension && position.equals(contact.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), position, extension);
    }
}
