package com.ftn.sbnz.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

@Entity
public class SynergyPair {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "node1_id")
    private Node node1;

    @ManyToOne
    @JoinColumn(name = "node2_id")
    private Node node2;

    private int distance;
    private int score;

    @ManyToMany
    @JoinTable(
        name = "synergy_pair_checkpoints",
        joinColumns = @JoinColumn(name = "synergy_pair_id"),
        inverseJoinColumns = @JoinColumn(name = "node_id")
    )
    private List<Node> checkPoints;

    @Transient
    private Set<String> tags = new HashSet<>();

    @Transient
    private List<Node> routeNodes = new ArrayList<>();
    
    public SynergyPair() {
        this.checkPoints = new ArrayList<>();
    }

    public SynergyPair(int id, Node node1, Node node2, int distance, int score) {
        this();
        this.id = id;
        this.node1 = node1;
        this.node2 = node2;
        this.distance = distance;
        this.score = score;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }


    public Node getNode1() {
        return node1;
    }

    public void setNode1(Node node1) {
        this.node1 = node1;
    }

    public Node getNode2() {
        return node2;
    }

    public void setNode2(Node node2) {
        this.node2 = node2;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public List<Node> getCheckPoints() {
        return checkPoints;
    }

    public void setCheckPoints(List<Node> checkPoints) {
        this.checkPoints = checkPoints;
    }

    public void addCheckPoint(Node node) {
        this.checkPoints.add(node);
    }

    public List<Node> getRouteNodes() {
        if (routeNodes == null) {
            routeNodes = new ArrayList<>();
        }
        return routeNodes;
    }

    public void setRouteNodes(List<Node> routeNodes) {
        this.routeNodes = routeNodes;
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

    @Override
    public String toString() {
        return "SynergyPair{" +
                "id=" + id +
                ", node1=" + node1.getId() +
                ", node2=" + node2.getId() +
                ", distance=" + distance +
                ", score=" + score +
                '}';
    }
}
