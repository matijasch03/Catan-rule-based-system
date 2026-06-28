package com.ftn.sbnz.service.board;
 
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
 
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
 
import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.NodeOrientation;
import com.ftn.sbnz.model.Resource;
import com.ftn.sbnz.service.service.HexagonService;
import com.ftn.sbnz.service.service.NodeService;
 
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BoardGenerator implements CommandLineRunner {
 
    private static final int RADIUS = 2;
 
    private final HexagonService hexagonService;
    private final NodeService nodeService;
 
    public BoardGenerator(HexagonService hexagonService, NodeService nodeService) {
        this.hexagonService = hexagonService;
        this.nodeService = nodeService;
    }
 
    @Override
    public void run(String... args) {
        // Generate the board only once (skip if hexagons already exist).
        if (!hexagonService.getAll().isEmpty()) {
            return;
        }
        List<Hexagon> hexagons = generateHexagons();
        generateNodes(hexagons);
    }
 
    // Build the 19 hexes of a radius-2 board in axial coordinates with random
    // resources and number tokens, persisting each through the service layer.
    private List<Hexagon> generateHexagons() {
        List<Resource> tiles = buildTiles();
        Collections.shuffle(tiles);
 
        List<Integer> tokens = buildTokens();
        Collections.shuffle(tokens);
 
        List<Hexagon> hexagons = new ArrayList<>();
        int tileIndex = 0;
        int tokenIndex = 0;
 
        for (int r = -RADIUS; r <= RADIUS; r++) {
            int qMin = Math.max(-RADIUS, -r - RADIUS);
            int qMax = Math.min(RADIUS, -r + RADIUS);
            for (int q = qMin; q <= qMax; q++) {
                Hexagon h = new Hexagon();
                h.setQ(q);
                h.setR(r);
 
                Resource res = tiles.get(tileIndex++);
                h.setField(res);
                if (res != Resource.DESERT) {
                    h.setDots(tokens.get(tokenIndex++));
                } else {
                    h.setDots(0);
                }
 
                hexagons.add(hexagonService.create(h));
            }
        }
 
        return hexagons;
    }
 
    // Create the board vertices. Each hexagon owns its N and NE corners; the
    // remaining perimeter corners are owned by the boundary hexes per the rules.
    private void generateNodes(List<Hexagon> hexagons) {
        for (Hexagon h : hexagons) {
            createNode(h, NodeOrientation.N);
            createNode(h, NodeOrientation.NE);
 
            if (h.getQ() == -RADIUS) {
                createNode(h, NodeOrientation.NW);
            }
            if (h.getQ() + h.getR() == -RADIUS) {
                createNode(h, NodeOrientation.SW);
                createNode(h, NodeOrientation.S);
            }
            if (h.getQ() == RADIUS) {
                createNode(h, NodeOrientation.SE);
            }
            if (h.getId() == 18 || h.getId() == 19) {
                createNode(h, NodeOrientation.S);
                createNode(h, NodeOrientation.SW);
            }
        }
    }
 
    private void createNode(Hexagon hexagon, NodeOrientation orientation) {
        Node node = new Node();
        node.setOrientation(orientation);
        node.addAdjacentHexagon(hexagon);
        Node saved = nodeService.create(node);
 
        hexagon.addNode(saved);
        hexagonService.create(hexagon);
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