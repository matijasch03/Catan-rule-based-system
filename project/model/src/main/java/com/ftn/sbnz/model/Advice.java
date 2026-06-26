package com.ftn.sbnz.model;

import jakarta.persistence.*;

@Entity
public class Advice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String description;

    @ManyToOne
    @JoinColumn(name = "player_id", referencedColumnName = "id")
    private Player player;

    @ManyToOne
    @JoinColumn(name = "synergy_pair_id")
    private SynergyPair longestRoad;

    @ManyToOne
    @JoinColumn(name = "target_node_id", referencedColumnName = "id")
    private Node targetNode;
    private int success;

    public Advice() {
    }

    public Advice(int id, String description, Player player, Node targetNode, int success) {
        this.id = id;
        this.description = description;
        this.player = player;
        this.targetNode = targetNode;
        this.success = success;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public SynergyPair getLongestRoad() {
        return longestRoad;
    }

    public void setLongestRoad(SynergyPair longestRoad) {
        this.longestRoad = longestRoad;
    }

    public Node getTargetNode() {
        return targetNode;
    }

    public void setTargetNode(Node targetNode) {
        this.targetNode = targetNode;
    }

    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    @Override
    public String toString() {
        return "Advice{" +
                "id=" + id +
                ", description='" + description + '\'' +
                ", player=" + (player != null ? player.getId() : "null") +
                ", targetNode=" + (targetNode != null ? targetNode.getId() : "null") +
                ", success=" + success +
                '}';
    }
}
