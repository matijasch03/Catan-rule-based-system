package com.ftn.sbnz.model;

public class Hexagon {
    private int id;
    private int q;
    private int r;
    private Resource field;
    private int dots;

    public Hexagon() {
    }

    public Hexagon(int id, int q, int r, Resource field, int dots) {
        this.id = id;
        this.q = q;
        this.r = r;
        this.field = field;
        this.dots = dots;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getQ() {
        return q;
    }

    public void setQ(int q) {
        this.q = q;
    }

    public int getR() {
        return r;
    }

    public void setR(int r) {
        this.r = r;
    }

    public Resource getField() {
        return field;
    }

    public void setField(Resource field) {
        this.field = field;
    }

    public int getDots() {
        return dots;
    }

    public void setDots(int dots) {
        if (dots >= 0 && dots <= 12) {
            this.dots = dots;
        }
    }

    @Override
    public String toString() {
        return "Hexagon{" +
                "id=" + id +
                ", field=" + field +
                ", dots=" + dots +
                '}';
    }
}
