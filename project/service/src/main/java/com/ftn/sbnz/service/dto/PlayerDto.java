package com.ftn.sbnz.service.dto;

// A player as the UI needs it: its id and its board colour.
public class PlayerDto {

    private int id;
    private String color;

    public PlayerDto() {
    }

    public PlayerDto(int id, String color) {
        this.id = id;
        this.color = color;
    }

    public int getId() { return id; }
    public String getColor() { return color; }
}
