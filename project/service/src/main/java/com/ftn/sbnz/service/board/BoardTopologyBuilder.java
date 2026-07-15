package com.ftn.sbnz.service.board;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ftn.sbnz.model.Edge;
import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.NodeOrientation;
import com.ftn.sbnz.service.service.EdgeService;
import com.ftn.sbnz.service.service.HexagonService;
import com.ftn.sbnz.service.service.NodeService;

@Component
public class BoardTopologyBuilder {

    private static final int RADIUS = 2;
    private static final double SIDE_LENGTH = 1.0;
    private static final double EPSILON = 1e-6;
    private static final double HALF_WIDTH = Math.sqrt(3.0) / 2.0;

    private final HexagonService hexagonService;
    private final NodeService nodeService;
    private final EdgeService edgeService;
    private final BoardTileShuffler tileShuffler;

    public BoardTopologyBuilder(HexagonService hexagonService, NodeService nodeService,
                                EdgeService edgeService, BoardTileShuffler tileShuffler) {
        this.hexagonService = hexagonService;
        this.nodeService = nodeService;
        this.edgeService = edgeService;
        this.tileShuffler = tileShuffler;
    }

    public void createBoard() {
        List<Hexagon> hexagons = generateHexagons();
        List<Node> nodes = generateNodes(hexagons);
        fillAdjacentHexagons(hexagons, nodes);
        generateEdges(nodes);
    }

    private List<Hexagon> generateHexagons() {
        List<Hexagon> hexagons = new ArrayList<>();
        for (int r = -RADIUS; r <= RADIUS; r++) {
            int qMin = Math.max(-RADIUS, -r - RADIUS);
            int qMax = Math.min(RADIUS, -r + RADIUS);
            for (int q = qMin; q <= qMax; q++) {
                Hexagon hexagon = new Hexagon();
                hexagon.setQ(q);
                hexagon.setR(r);
                hexagons.add(hexagon);
            }
        }

        tileShuffler.assignFieldsAndDots(hexagons);

        List<Hexagon> saved = new ArrayList<>();
        for (Hexagon hexagon : hexagons) {
            saved.add(hexagonService.create(hexagon));
        }
        return saved;
    }

    private List<Node> generateNodes(List<Hexagon> hexagons) {
        List<Node> nodes = new ArrayList<>();
        for (Hexagon hexagon : hexagons) {
            nodes.add(createNode(hexagon, NodeOrientation.N));
            nodes.add(createNode(hexagon, NodeOrientation.NE));

            if (hexagon.getId() == 2 || hexagon.getId() == 3) {
                nodes.add(createNode(hexagon, NodeOrientation.S));
                nodes.add(createNode(hexagon, NodeOrientation.SW));
            } else if (hexagon.getQ() == -RADIUS) {
                nodes.add(createNode(hexagon, NodeOrientation.NW));
            }
            if (hexagon.getQ() + hexagon.getR() == -RADIUS) {
                nodes.add(createNode(hexagon, NodeOrientation.SW));
                nodes.add(createNode(hexagon, NodeOrientation.S));
            } else if (hexagon.getQ() == RADIUS) {
                nodes.add(createNode(hexagon, NodeOrientation.SE));
            }
        }
        return nodes;
    }

    private Node createNode(Hexagon hexagon, NodeOrientation orientation) {
        Node node = new Node();
        node.setOrientation(orientation);
        node.setPossessiveHexagon(hexagon);
        return nodeService.create(node);
    }

    private void fillAdjacentHexagons(List<Hexagon> hexagons, List<Node> nodes) {
        Map<String, Hexagon> byCoord = new HashMap<>();
        for (Hexagon hexagon : hexagons) {
            byCoord.put(coordKey(hexagon.getQ(), hexagon.getR()), hexagon);
        }

        for (Node node : nodes) {
            Hexagon owner = node.getPossessiveHexagon();
            node.setAdjacentHexagons(new ArrayList<>());
            node.addAdjacentHexagon(owner);
            owner.addNode(node);

            for (int[] offset : neighbourOffsets(node.getOrientation())) {
                Hexagon neighbour = byCoord.get(coordKey(owner.getQ() + offset[0], owner.getR() + offset[1]));
                if (neighbour != null && !node.getAdjacentHexagons().contains(neighbour)) {
                    node.addAdjacentHexagon(neighbour);
                    neighbour.addNode(node);
                }
            }
        }

        for (Hexagon hexagon : hexagons) {
            hexagonService.create(hexagon);
        }
    }

    private int[][] neighbourOffsets(NodeOrientation orientation) {
        return switch (orientation) {
            case N -> new int[][]{{-1, 1}, {0, 1}};
            case NE -> new int[][]{{0, 1}, {1, 0}};
            case S -> new int[][]{{1, -1}};
            case SW -> new int[][]{{-1, 0}};
            default -> new int[][]{};
        };
    }

    private void generateEdges(List<Node> nodes) {
        double[][] positions = new double[nodes.size()][];
        for (int i = 0; i < nodes.size(); i++) {
            positions[i] = position(nodes.get(i));
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                double dx = positions[i][0] - positions[j][0];
                double dy = positions[i][1] - positions[j][1];
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (Math.abs(distance - SIDE_LENGTH) < EPSILON) {
                    edgeService.create(new Edge(nodes.get(i), nodes.get(j)));
                }
            }
        }
    }

    private double[] position(Node node) {
        Hexagon hexagon = node.getAdjacentHexagons().get(0);
        double cx = Math.sqrt(3.0) * (hexagon.getQ() + hexagon.getR() / 2.0);
        double cy = 1.5 * hexagon.getR();
        double[] offset = cornerOffset(node.getOrientation());
        return new double[]{cx + offset[0], cy + offset[1]};
    }

    private double[] cornerOffset(NodeOrientation orientation) {
        return switch (orientation) {
            case N -> new double[]{0.0, 1.0};
            case NE -> new double[]{HALF_WIDTH, 0.5};
            case SE -> new double[]{HALF_WIDTH, -0.5};
            case S -> new double[]{0.0, -1.0};
            case SW -> new double[]{-HALF_WIDTH, -0.5};
            case NW -> new double[]{-HALF_WIDTH, 0.5};
        };
    }

    private static String coordKey(int q, int r) {
        return q + "," + r;
    }
}
