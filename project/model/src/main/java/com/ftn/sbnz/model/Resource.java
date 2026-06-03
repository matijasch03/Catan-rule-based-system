package com.ftn.sbnz.model;

public enum Resource {
    WOOD("Wood"),
    WOOL("Wool"),
    GRAIN("Grain"),
    BRICK("Brick"),
    ORE("Ore"),
    DESERT("Desert");

    private final String displayName;

    Resource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
