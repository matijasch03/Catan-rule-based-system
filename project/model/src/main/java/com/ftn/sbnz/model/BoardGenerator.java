package com.ftn.sbnz.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BoardGenerator {

    // Generate a full hex board of radius 2. Rows correspond to axial r from -2..2
    public static List<List<Hexagon>> generateBoard() {
        List<List<Hexagon>> board = new ArrayList<>();

        final int radius = 2;

        // Prepare playable tile resources (19 tiles)
        List<Resource> tiles = new ArrayList<>();
        addNTiles(tiles, Resource.WOOD, 4);
        addNTiles(tiles, Resource.WOOL, 4);
        addNTiles(tiles, Resource.GRAIN, 4);
        addNTiles(tiles, Resource.BRICK, 3);
        addNTiles(tiles, Resource.ORE, 3);
        // Desert represented as DESERT in this model (one desert among playable)
        addNTiles(tiles, Resource.DESERT, 1);

        Collections.shuffle(tiles);

        int tileIndex = 0;
        // Prepare standard Catan number tokens (dots) for 18 non-desert tiles
        List<Integer> tokens = new ArrayList<>();
        // distribution: 2(1),3(2),4(2),5(2),6(2),8(2),9(2),10(2),11(2),12(1)
        tokens.add(2);
        addNTokens(tokens, 3, 2);
        addNTokens(tokens, 4, 2);
        addNTokens(tokens, 5, 2);
        addNTokens(tokens, 6, 2);
        addNTokens(tokens, 8, 2);
        addNTokens(tokens, 9, 2);
        addNTokens(tokens, 10, 2);
        addNTokens(tokens, 11, 2);
        tokens.add(12);
        Collections.shuffle(tokens);
        int tokenIndex = 0;

        // axial r from -radius to +radius (top to bottom)
        for (int ra = -radius; ra <= radius; ra++) {
            int qMin = Math.max(-radius, -ra - radius);
            int qMax = Math.min(radius, -ra + radius);
            int len = qMax - qMin + 1;
            List<Hexagon> row = new ArrayList<>(len);
            for (int c = 0; c < len; c++) {
                int q = qMin + c;
                int r = ra;

                Hexagon h = new Hexagon();
                h.setQ(q);
                h.setR(r);

                // playable tiles only; assign resource and number token (dots)
                if (tileIndex < tiles.size()) {
                    Resource res = tiles.get(tileIndex++);
                    h.setField(res);
                    if (res != Resource.DESERT) {
                        if (tokenIndex < tokens.size()) {
                            h.setDots(tokens.get(tokenIndex++));
                        } else {
                            h.setDots(0);
                        }
                    } else {
                        h.setDots(0); // desert has no token
                    }
                } else {
                    h.setField(Resource.DESERT);
                    h.setDots(0);
                }
                row.add(h);
            }
            board.add(row);
        }

        return board;
    }

    // Generate nodes (vertices) for the given board. Nodes are deduplicated by the
    // set of adjacent hex coordinates they touch.
    public static List<Node> generateNodes(List<List<Hexagon>> board) {
        Map<String, Hexagon> hexByCoord = new HashMap<>();
        for (List<Hexagon> row : board) {
            for (Hexagon h : row) {
                hexByCoord.put(coordKey(h.getQ(), h.getR()), h);
            }
        }

        Map<String, Node> nodes = new HashMap<>();
        int nodeId = 1;

        // Offsets for the three hexes touching each corner, per orientation
        Map<NodeOrientation, int[][]> cornerOffsets = new HashMap<>();
        cornerOffsets.put(NodeOrientation.N, new int[][]{{0,0},{-1,0},{0,-1}});
        cornerOffsets.put(NodeOrientation.NE, new int[][]{{0,0},{0,-1},{1,-1}});
        cornerOffsets.put(NodeOrientation.SE, new int[][]{{0,0},{1,-1},{1,0}});
        cornerOffsets.put(NodeOrientation.S, new int[][]{{0,0},{1,0},{0,1}});
        cornerOffsets.put(NodeOrientation.SW, new int[][]{{0,0},{0,1},{-1,1}});
        cornerOffsets.put(NodeOrientation.NW, new int[][]{{0,0},{-1,1},{-1,0}});

        for (List<Hexagon> row : board) {
            for (Hexagon h : row) {
                for (NodeOrientation ori : NodeOrientation.values()) {
                    int[][] offs = cornerOffsets.get(ori);
                    // collect actual existing adjacent hexes for this corner
                    List<Hexagon> adj = new ArrayList<>();
                    for (int[] o : offs) {
                        int q = h.getQ() + o[0];
                        int r = h.getR() + o[1];
                        Hexagon hh = hexByCoord.get(coordKey(q,r));
                        if (hh != null) adj.add(hh);
                    }

                    // if no adjacent hex was found (shouldn't happen) skip
                    if (adj.isEmpty()) continue;

                    // create a canonical key based on sorted coordinates of adjacent hexes
                    String key = adj.stream()
                            .map(x -> coordKey(x.getQ(), x.getR()))
                            .sorted()
                            .collect(Collectors.joining("|"));

                    Node node = nodes.get(key);
                    if (node == null) {
                        node = new Node();
                        node.setOrientation(ori);
                        // owner and settlement remain null initially
                        nodes.put(key, node);
                    }

                    // attach adjacent hexagon references
                    for (Hexagon ah : adj) {
                        if (!node.getAdjacentHexagons().contains(ah)) node.addAdjacentHexagon(ah);
                    }
                }
            }
        }

        // compute score for each node as sum of two-dice probability weights of adjacent hexes
        // and apply a 20% bonus if the node touches more than two different resources.
        for (Node n : nodes.values()) {
            int baseScore = 0;
            Set<Resource> distinctResources = new HashSet<>();
            for (Hexagon hh : n.getAdjacentHexagons()) {
                baseScore += getDiceProbabilityWeight(hh.getDots());
                if (hh.getField() != null && hh.getField() != Resource.DESERT) {
                    distinctResources.add(hh.getField());
                }
            }
            int finalScore = baseScore;
            if (distinctResources.size() > 2) {
                finalScore = (int) Math.round(baseScore * 1.2);
            }
            n.setScore(finalScore);
        }

        return new ArrayList<>(nodes.values());
    }

    private static String coordKey(int q, int r) {
        return q + "," + r;
    }

    private static void addNTiles(List<Resource> list, Resource r, int n) {
        for (int i = 0; i < n; i++) list.add(r);
    }

    private static void addNTokens(List<Integer> list, int value, int n) {
        for (int i = 0; i < n; i++) list.add(value);
    }

    // Standard Catan-style probability weights for two dice.
    // Higher weight means a tile is easier to roll.
    private static int getDiceProbabilityWeight(int dots) {
        return switch (dots) {
            case 2, 12 -> 1;
            case 3, 11 -> 2;
            case 4, 10 -> 3;
            case 5, 9 -> 4;
            case 6, 8 -> 5;
            default -> 0;
        };
    }
}