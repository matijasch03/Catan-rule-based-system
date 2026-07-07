package com.ftn.sbnz.service.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.model.Resource;

@Component
public class BoardTileShuffler {

    private static final int SHUFFLE_ATTEMPTS = 300;
    private static final int SAME_RESOURCE_PENALTY = 8;
    private static final int HOT_TOKEN_NEIGHBOUR_PENALTY = 25;
    private static final int HOT_RESOURCE_REPEAT_PENALTY = 10;
    private static final int DESERT_HOT_NEIGHBOUR_PENALTY = 4;
    private static final int PROBABILITY_CLUSTER_PENALTY = 2;

    private final Random random = new Random();

    public void assignFieldsAndDots(List<Hexagon> hexagons) {
        BoardCandidate best = null;
        for (int i = 0; i < SHUFFLE_ATTEMPTS; i++) {
            BoardCandidate candidate = randomCandidate(hexagons);
            if (best == null || candidate.score() < best.score()) {
                best = candidate;
            }
        }

        for (int i = 0; i < hexagons.size(); i++) {
            Hexagon hexagon = hexagons.get(i);
            hexagon.setField(best.fields().get(i));
            hexagon.setDots(best.dots().get(i));
        }
    }

    private BoardCandidate randomCandidate(List<Hexagon> hexagons) {
        List<Resource> fields = buildTiles();
        Collections.shuffle(fields, random);

        List<Integer> tokens = buildTokens();
        Collections.shuffle(tokens, random);

        List<Integer> dots = new ArrayList<>();
        int tokenIndex = 0;
        for (Resource field : fields) {
            dots.add(field == Resource.DESERT ? 0 : tokens.get(tokenIndex++));
        }

        return new BoardCandidate(fields, dots, diversityScore(hexagons, fields, dots));
    }

    private int diversityScore(List<Hexagon> hexagons, List<Resource> fields, List<Integer> dots) {
        int score = 0;
        Map<String, Integer> indexByCoord = new HashMap<>();
        for (int i = 0; i < hexagons.size(); i++) {
            Hexagon hexagon = hexagons.get(i);
            indexByCoord.put(coordKey(hexagon.getQ(), hexagon.getR()), i);
        }

        score += hotResourceRepeatScore(fields, dots);
        for (int i = 0; i < hexagons.size(); i++) {
            Hexagon hexagon = hexagons.get(i);
            for (int neighbour : forwardNeighbours(hexagon, indexByCoord)) {
                score += neighbourScore(fields, dots, i, neighbour);
            }
        }
        return score;
    }

    private int hotResourceRepeatScore(List<Resource> fields, List<Integer> dots) {
        int score = 0;
        Map<Resource, Integer> hotByResource = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            if (isHot(dots.get(i))) {
                hotByResource.merge(fields.get(i), 1, Integer::sum);
            }
        }
        for (Map.Entry<Resource, Integer> entry : hotByResource.entrySet()) {
            if (entry.getKey() != Resource.DESERT && entry.getValue() > 1) {
                score += (entry.getValue() - 1) * HOT_RESOURCE_REPEAT_PENALTY;
            }
        }
        return score;
    }

    private int neighbourScore(List<Resource> fields, List<Integer> dots, int first, int second) {
        int score = 0;
        if (fields.get(first) == fields.get(second) && fields.get(first) != Resource.DESERT) {
            score += SAME_RESOURCE_PENALTY;
        }
        if (isHot(dots.get(first)) && isHot(dots.get(second))) {
            score += HOT_TOKEN_NEIGHBOUR_PENALTY;
        }
        if ((fields.get(first) == Resource.DESERT && isHot(dots.get(second)))
                || (fields.get(second) == Resource.DESERT && isHot(dots.get(first)))) {
            score += DESERT_HOT_NEIGHBOUR_PENALTY;
        }
        return score + Math.min(diceWeight(dots.get(first)), diceWeight(dots.get(second)))
                * PROBABILITY_CLUSTER_PENALTY;
    }

    private List<Integer> forwardNeighbours(Hexagon hexagon, Map<String, Integer> indexByCoord) {
        List<Integer> neighbours = new ArrayList<>();
        addNeighbour(neighbours, indexByCoord, hexagon.getQ() + 1, hexagon.getR());
        addNeighbour(neighbours, indexByCoord, hexagon.getQ(), hexagon.getR() + 1);
        addNeighbour(neighbours, indexByCoord, hexagon.getQ() + 1, hexagon.getR() - 1);
        return neighbours;
    }

    private void addNeighbour(List<Integer> neighbours, Map<String, Integer> indexByCoord, int q, int r) {
        Integer index = indexByCoord.get(coordKey(q, r));
        if (index != null) {
            neighbours.add(index);
        }
    }

    private boolean isHot(int dots) {
        return dots == 6 || dots == 8;
    }

    private int diceWeight(int dots) {
        return switch (dots) {
            case 6, 8 -> 5;
            case 5, 9 -> 4;
            case 4, 10 -> 3;
            case 3, 11 -> 2;
            case 2, 12 -> 1;
            default -> 0;
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

    private static String coordKey(int q, int r) {
        return q + "," + r;
    }

    private static <T> void addN(List<T> list, T value, int n) {
        for (int i = 0; i < n; i++) {
            list.add(value);
        }
    }

    private record BoardCandidate(List<Resource> fields, List<Integer> dots, int score) {
    }
}
