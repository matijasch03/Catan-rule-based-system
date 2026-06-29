package com.ftn.sbnz.service.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
        List<Hexagon> existing = hexagonService.getAll();
        if (existing.isEmpty()) {
            // First run: build the full board (hexagons + nodes + edges).
            List<Hexagon> hexagons = generateHexagons();
            generateNodes(hexagons);
        } else {
            // Later runs: only reshuffle fields and dots.
            reshuffle(existing);
        }
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
    private void generateNodes(List<Hexagon> hexagons) {
        for (Hexagon h : hexagons) {
            createNode(h, NodeOrientation.N);
            createNode(h, NodeOrientation.NE);

            if (h.getId() == 2 || h.getId() == 3) {
                createNode(h, NodeOrientation.S);
                createNode(h, NodeOrientation.SW);
            }
            else if (h.getQ() == -RADIUS) {
                createNode(h, NodeOrientation.NW);
            }
            if (h.getQ() + h.getR() == -RADIUS) {
                createNode(h, NodeOrientation.SW);
                createNode(h, NodeOrientation.S);
            }
            else if (h.getQ() == RADIUS) {
                createNode(h, NodeOrientation.SE);
            }
            
        }
    }

    private void createNode(Hexagon hexagon, NodeOrientation orientation) {
        Node node = new Node();
        node.setOrientation(orientation);
        node.setPossessiveHexagon(hexagon);
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