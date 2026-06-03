package com.ftn.sbnz.model;

import java.util.HashMap;
import java.util.Map;

public class Player {
    private int id;
    private int score;
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

    public void setId(int id) {
        this.id = id;
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
        if (current >= amount) {
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
