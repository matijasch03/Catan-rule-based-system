package com.ftn.sbnz.model;

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.*;
@Entity
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private int score;

    @ElementCollection
    @CollectionTable(
        name = "player_resources",
        joinColumns = @JoinColumn(name = "player_id")
    )
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "resource_count")
    private Map<Resource, Integer> resources;

    public Player() {
        this.resources = new HashMap<>();
    }

    public Player(int id, int score) {
        this();
        this.id = id;
        this.score = score;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public Map<Resource, Integer> getResources() {
        return resources;
    }

    public void setResources(Map<Resource, Integer> resources) {
        this.resources = resources;
    }

    public void addResource(Resource resource, int amount) {
        resources.put(resource, resources.getOrDefault(resource, 0) + amount);
    }

    public void removeResource(Resource resource, int amount) {
        int current = resources.getOrDefault(resource, 0);
        if (current == amount) {
            resources.remove(resource);
        } else if (current > amount) {
            resources.put(resource, current - amount);
        }
    }

    @Override
    public String toString() {
        return "Player{" +
                "id=" + id +
                ", score=" + score +
                ", resources=" + resources +
                '}';
    }
}
