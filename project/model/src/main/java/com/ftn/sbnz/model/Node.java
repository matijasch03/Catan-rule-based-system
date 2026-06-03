package com.ftn.sbnz.model;

import java.util.ArrayList;
import java.util.List;

public class Node {
    private int id;
    private List<Hexagon> adjacentHexagons;
    private NodeOrientation orientation;
    private Player owner;
    private Settlement settlement;
    private int score;

    public Node() {
        this.adjacentHexagons = new ArrayList<>();
    }

    public Node(int id) {
        this();
        this.id = id;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public List<Hexagon> getAdjacentHexagons() {
        return adjacentHexagons;
    }

    public void setAdjacentHexagons(List<Hexagon> adjacentHexagons) {
        this.adjacentHexagons = adjacentHexagons;
    }

    public void addAdjacentHexagon(Hexagon hexagon) {
        this.adjacentHexagons.add(hexagon);
    }

    public NodeOrientation getOrientation() {
        return orientation;
    }

    public void setOrientation(NodeOrientation orientation) {
        this.orientation = orientation;
    }

    public Player getOwner() {
        return owner;
    }

    public void setOwner(Player owner) {
        this.owner = owner;
    }

    public Settlement getSettlement() {
        return settlement;
    }

    public void setSettlement(Settlement settlement) {
        this.settlement = settlement;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    @Override
    public String toString() {
        return "Node{" +
                "id=" + id +
                ", orientation=" + orientation +
                ", owner=" + (owner != null ? owner.getId() : "null") +
                ", settlement=" + settlement +
                ", score=" + score +
                '}';
    }
}
