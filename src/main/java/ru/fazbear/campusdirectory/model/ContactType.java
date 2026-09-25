package ru.fazbear.campusdirectory.model;

public enum ContactType {
    PERSONAL("Личный"),
    CORPORATE("Корпоративный"),
    EMERGENCY("Аварийный");

    private final String displayName;

    ContactType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
