package com.ftn.sbnz.service.dto;

import com.ftn.sbnz.model.Edge;

// Flat view of a board edge for the UI: the two vertex ids it connects and any
// road owner.
public class EdgeDto {

    private int id;
    private int node1Id;
    private int node2Id;
    private Integer ownerId;

    public EdgeDto() {
    }

    public EdgeDto(Edge edge) {
        this.id = edge.getId();
        this.node1Id = edge.getNode1().getId();
        this.node2Id = edge.getNode2().getId();
        this.ownerId = edge.getOwner() != null ? edge.getOwner().getId() : null;
    }

    public int getId() { return id; }
    public int getNode1Id() { return node1Id; }
    public int getNode2Id() { return node2Id; }
    public Integer getOwnerId() { return ownerId; }
}
