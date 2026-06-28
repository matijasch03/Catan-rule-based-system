package com.ftn.sbnz.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;

@Entity
public class Hexagon {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private int id;
    private int q;
    private int r;

    @Enumerated(EnumType.STRING)
    private Resource field;

    private int dots;

    @ManyToMany
    @JoinTable(joinColumns = @JoinColumn(name = "hex_id"),
    inverseJoinColumns = @JoinColumn(name = "node_id"))
    private List<Node> nodes;

    public Hexagon() {
    }

    public Hexagon(int q, int r, Resource field, int dots, List<Node> nodes) {
        this.q = q;
        this.r = r;
        this.field = field;
        this.dots = dots;
        if (nodes != null) {
            this.nodes = nodes;
        } else {
            this.nodes = new ArrayList<>();
        }
    }

    // Getters and Setters
    public int getId() {
        return id;
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

    public List<Node> getNodes() {
        if (nodes == null) {
            nodes = new ArrayList<>();
        }
        return nodes;
    }
 
    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }
 
    public void addNode(Node node) {
        getNodes().add(node);
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
