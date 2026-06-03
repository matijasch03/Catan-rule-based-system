package com.ftn.sbnz.model;

public enum Settlement {
    VILLAGE("Village"),
    TOWN("Town");

    private final String displayName;

    Settlement(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
