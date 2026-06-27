package com.ftn.sbnz.model;

import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;


@Entity
public class Edge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "node1_id", referencedColumnName = "id")
    private Node node1;

    @ManyToOne
    @JoinColumn(name = "node2_id", referencedColumnName = "id")
    private Node node2;

    @ManyToOne
    @JoinColumn(name = "player_id", referencedColumnName = "id")
    private Player owner;

    public Edge() {
    }

    public Edge(Node nodeA, Node nodeB) {
        // to avoid duplicates (A-B and B-A), sort them by ID
        if (nodeA.getId() < nodeB.getId()) {
            this.node1 = nodeA;
            this.node2 = nodeB;
        } else {
            this.node1 = nodeB;
            this.node2 = nodeA;
        }
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

    public Player getOwner() {
        return owner;
    }

    public void setOwner(Player owner) {
        this.owner = owner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edge edge = (Edge) o;
        return Objects.equals(node1, edge.node1) && Objects.equals(node2, edge.node2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node1, node2);
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
