package com.ftn.sbnz.service.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ftn.sbnz.model.Edge;
import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.NodeOrientation;
import com.ftn.sbnz.model.Resource;
import com.ftn.sbnz.service.service.EdgeService;
import com.ftn.sbnz.service.service.HexagonService;
import com.ftn.sbnz.service.service.NodeService;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BoardGenerator implements CommandLineRunner {

    private static final int RADIUS = 2;

    private static final double SIDE_LENGTH = 1.0;
    private static final double EPSILON = 1e-6;
    private static final double HALF_WIDTH = Math.sqrt(3.0) / 2.0;
 
    private final HexagonService hexagonService;
    private final NodeService nodeService;
    private final EdgeService edgeService;
 
    public BoardGenerator(HexagonService hexagonService, NodeService nodeService, EdgeService edgeService) {
        this.hexagonService = hexagonService;
        this.nodeService = nodeService;
        this.edgeService = edgeService;
    }

    @Override
    public void run(String... args) {
        List<Hexagon> existing = hexagonService.getAll();
        if (existing.isEmpty()) {
            // First run: build the full board (hexagons + nodes + edges).
            List<Hexagon> hexagons = generateHexagons();
            List<Node> nodes = generateNodes(hexagons);
            fillAdjacentHexagons(hexagons, nodes);
            generateEdges(nodes);
        } else {
            // Later runs: only reshuffle fields and dots.
            reshuffle(existing);
        }
    }

    // Reshuffle the persisted board on demand (e.g. from the Reload button),
    // returning the hexagons sorted by id.
    public List<Hexagon> reshuffle() {
        List<Hexagon> hexagons = hexagonService.getAll();
        reshuffle(hexagons);
        return hexagons;
    }

    // Reassign random resources and number tokens to the existing hexagons,
    // leaving their ids/coordinates and all nodes unchanged.
    private void reshuffle(List<Hexagon> hexagons) {
        hexagons.sort(Comparator.comparingInt(Hexagon::getId));
        assignFieldsAndDots(hexagons);
        for (Hexagon h : hexagons) {
            hexagonService.create(h);
        }
    }

    // Build the 19 hexes of a radius-2 board in axial coordinates with random
    // resources and number tokens, persisting each through the service layer.
    private List<Hexagon> generateHexagons() {
        List<Hexagon> hexagons = new ArrayList<>();
        for (int r = -RADIUS; r <= RADIUS; r++) {
            int qMin = Math.max(-RADIUS, -r - RADIUS);
            int qMax = Math.min(RADIUS, -r + RADIUS);
            for (int q = qMin; q <= qMax; q++) {
                Hexagon h = new Hexagon();
                h.setQ(q);
                h.setR(r);
                hexagons.add(h);
            }
        }

        assignFieldsAndDots(hexagons);

        List<Hexagon> saved = new ArrayList<>();
        for (Hexagon h : hexagons) {
            saved.add(hexagonService.create(h));
        }
        return saved;
    }

    // Randomly distribute the standard resource tiles and number tokens across
    // the given hexagons (desert gets no token).
    private void assignFieldsAndDots(List<Hexagon> hexagons) {
        List<Resource> tiles = buildTiles();
        Collections.shuffle(tiles);

        List<Integer> tokens = buildTokens();
        Collections.shuffle(tokens);

        int tileIndex = 0;
        int tokenIndex = 0;
        for (Hexagon h : hexagons) {
            Resource res = tiles.get(tileIndex++);
            h.setField(res);
            if (res != Resource.DESERT) {
                h.setDots(tokens.get(tokenIndex++));
            } else {
                h.setDots(0);
            }
        }
    }

    // Create the board vertices. Each hexagon owns its N and NE corners; the
    // remaining perimeter corners are owned by the boundary hexes per the rules.
    private List<Node> generateNodes(List<Hexagon> hexagons) {
        List<Node> nodes = new ArrayList<>();
        for (Hexagon h : hexagons) {
            nodes.add(createNode(h, NodeOrientation.N));
            nodes.add(createNode(h, NodeOrientation.NE));

            if (h.getId() == 2 || h.getId() == 3) {
                nodes.add(createNode(h, NodeOrientation.S));
                nodes.add(createNode(h, NodeOrientation.SW));
            }
            else if (h.getQ() == -RADIUS) {
                nodes.add(createNode(h, NodeOrientation.NW));
            }
            if (h.getQ() + h.getR() == -RADIUS) {
                nodes.add(createNode(h, NodeOrientation.SW));
                nodes.add(createNode(h, NodeOrientation.S));
            }
            else if (h.getQ() == RADIUS) {
                nodes.add(createNode(h, NodeOrientation.SE));
            }
        }
        return nodes;
    }

    private Node createNode(Hexagon hexagon, NodeOrientation orientation) {
        Node node = new Node();
        node.setOrientation(orientation);
        node.setPossessiveHexagon(hexagon);
        Node saved = nodeService.create(node);

        return saved;
    }

    private void fillAdjacentHexagons(List<Hexagon> hexagons, List<Node> nodes) {
        Map<String, Hexagon> byCoord = new HashMap<>();
        for (Hexagon h : hexagons) {
            byCoord.put(coordKey(h.getQ(), h.getR()), h);
        }
 
        for (Node node : nodes) {
            Hexagon owner = node.getPossessiveHexagon();
            node.setAdjacentHexagons(new ArrayList<Hexagon>());
            node.addAdjacentHexagon(owner);

            for (int[] offset : neighbourOffsets(node.getOrientation())) {
                Hexagon neighbour = byCoord.get(coordKey(owner.getQ() + offset[0], owner.getR() + offset[1]));
                if (neighbour != null && !node.getAdjacentHexagons().contains(neighbour)) {
                    node.addAdjacentHexagon(neighbour);
                    neighbour.addNode(node);
                }
            }
        }
 
        // Persist the completed join table (each hexagon now lists every node on
        // its six corners).
        for (Hexagon h : hexagons) {
            hexagonService.create(h);
        }
    }
 
    // The two hexagons (other than the owner) that share the corner of the given
    // orientation, as axial offsets relative to the owning hexagon.
    private int[][] neighbourOffsets(NodeOrientation orientation) {
        return switch (orientation) {
            case N -> new int[][]{{-1, 0}, {0, -1}};
            case NE -> new int[][]{{0, -1}, {1, -1}};
            case SE -> new int[][]{{1, -1}, {1, 0}};
            case S -> new int[][]{{1, 0}, {0, 1}};
            case SW -> new int[][]{{0, 1}, {-1, 1}};
            case NW -> new int[][]{{-1, 1}, {-1, 0}};
        };
    }
 
    private static String coordKey(int q, int r) {
        return q + "," + r;
    }

    // An edge connects two neighbouring vertices. Compute every node's 2D position from
    // its owning hexagon and connect each pair that is exactly one side-length apart.
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
 
    // Position of a node = its owning hexagon's centre plus the corner offset for
    // the node's orientation (pointy-top hexagon, circumradius 1).
    private double[] position(Node node) {
        Hexagon hex = node.getAdjacentHexagons().get(0);
        double cx = Math.sqrt(3.0) * (hex.getQ() + hex.getR() / 2.0);
        double cy = 1.5 * hex.getR();
        double[] offset = cornerOffset(node.getOrientation());
        return new double[]{cx + offset[0], cy + offset[1]};
    }
 
    // Corner offsets derived from the model's corner definition (each corner is
    // the centroid of the three hexes that touch it), so the orientation labels
    // match the rest of the codebase rather than a literal compass.
    private double[] cornerOffset(NodeOrientation orientation) {
        return switch (orientation) {
            case N -> new double[]{-HALF_WIDTH, -0.5};
            case NE -> new double[]{0.0, -1.0};
            case SE -> new double[]{HALF_WIDTH, -0.5};
            case S -> new double[]{HALF_WIDTH, 0.5};
            case SW -> new double[]{0.0, 1.0};
            case NW -> new double[]{-HALF_WIDTH, 0.5};
        };
    }

    private List<Resource> buildTiles() {
        List<Resource> tiles = new ArrayList<>();
        addN(tiles, Resource.WOOD, 4);
        addN(tiles, Resource.WOOL, 4);
        addN(tiles, Resource.GRAIN, 4);
        addN(tiles, Resource.BRICK, 3);
        addN(tiles, Resource.ORE, 3);
        addN(tiles, Resource.DESERT, 1);
        return tiles;
    }

    private List<Integer> buildTokens() {
        List<Integer> tokens = new ArrayList<>();
        tokens.add(2);
        addN(tokens, 3, 2);
        addN(tokens, 4, 2);
        addN(tokens, 5, 2);
        addN(tokens, 6, 2);
        addN(tokens, 8, 2);
        addN(tokens, 9, 2);
        addN(tokens, 10, 2);
        addN(tokens, 11, 2);
        tokens.add(12);
        return tokens;
    }

    private static <T> void addN(List<T> list, T value, int n) {
        for (int i = 0; i < n; i++) {
            list.add(value);
        }
    }
}