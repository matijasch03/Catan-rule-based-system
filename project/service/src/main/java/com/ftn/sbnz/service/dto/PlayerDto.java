package com.ftn.sbnz.service.dto;

import java.util.LinkedHashMap;
import java.util.Map;

// A player as the UI needs it: its id, its board colour and the resources it has
// collected so far (resource display name -> count).
public class PlayerDto {

    private int id;
    private String color;
    private Map<String, Integer> resources = new LinkedHashMap<>();

    public PlayerDto() {
    }

    public PlayerDto(int id, String color) {
        this.id = id;
        this.color = color;
    }

    public PlayerDto(int id, String color, Map<String, Integer> resources) {
        this.id = id;
        this.color = color;
        this.resources = resources;
    }

    public int getId() { return id; }
    public String getColor() { return color; }
    public Map<String, Integer> getResources() { return resources; }
}
