package com.ftn.sbnz.kjar;

import java.util.ArrayList;
import java.util.List;

import com.ftn.sbnz.model.Node;

public class NodeDistance {

    private final Node node1;
    private final Node node2;
    private final int distance;
    private final boolean freePath;
    private final List<Node> routeNodes;
    private final List<Node> checkPoints;

    public NodeDistance(Node node1, Node node2, int distance, boolean freePath,
                        List<Node> routeNodes, List<Node> checkPoints) {
        this.node1 = node1;
        this.node2 = node2;
        this.distance = distance;
        this.freePath = freePath;
        this.routeNodes = new ArrayList<>(routeNodes);
        this.checkPoints = new ArrayList<>(checkPoints);
    }

    public Node getNode1() { return node1; }
    public Node getNode2() { return node2; }
    public int getDistance() { return distance; }
    public boolean isFreePath() { return freePath; }
    public List<Node> getRouteNodes() { return routeNodes; }
    public List<Node> getCheckPoints() { return checkPoints; }
}
