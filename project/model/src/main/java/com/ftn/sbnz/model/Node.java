package com.ftn.sbnz.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.*;

@Entity
public class Node {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToMany(mappedBy = "nodes")
    private List<Hexagon> adjacentHexagons;

    @ManyToOne
    @JoinColumn(name = "hexagon_id")
    private Hexagon possessiveHexagon;

    @Enumerated(EnumType.STRING)
    private NodeOrientation orientation;

    @ManyToOne
    @JoinColumn(name = "player_id", referencedColumnName = "id")
    private Player owner;

    @Enumerated(EnumType.STRING)
    private Settlement settlement;

    private int score;

    // Temporary forward-chaining state. These values describe one advice run and
    // are deliberately not persisted with the board.
    @Transient
    private boolean available = true;

    @Transient
    private Set<String> tags = new HashSet<>();

    public Node() {
        this.adjacentHexagons = new ArrayList<>();
    }

    // Getters and Setters
    public int getId() {
        return id;
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

    public Hexagon getPossessiveHexagon() {
        return possessiveHexagon;
    }

    public void setPossessiveHexagon(Hexagon possessiveHexagon) {
        this.possessiveHexagon = possessiveHexagon;
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

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public Set<String> getTags() {
        if (tags == null) {
            tags = new HashSet<>();
        }
        return tags;
    }

    public void addTag(String tag) {
        getTags().add(tag);
    }

    public void resetAnalysis() {
        score = 0;
        available = true;
        getTags().clear();
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
