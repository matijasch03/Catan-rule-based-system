package com.ftn.sbnz.model;

public class Edge {
    private int id;
    private Node node1;
    private Node node2;
    private Player owner;

    public Edge() {
    }

    public Edge(int id, Node node1, Node node2) {
        this.id = id;
        this.node1 = node1;
        this.node2 = node2;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public Player getOwner() {
        return owner;
    }

    public void setOwner(Player owner) {
        this.owner = owner;
    }

    @Override
    public String toString() {
        return "Edge{" +
                "id=" + id +
                ", node1=" + node1.getId() +
                ", node2=" + node2.getId() +
                ", owner=" + (owner != null ? owner.getId() : "null") +
                '}';
    }
}
