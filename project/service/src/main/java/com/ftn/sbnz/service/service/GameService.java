package com.ftn.sbnz.service.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.ftn.sbnz.model.Edge;
import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.Player;
import com.ftn.sbnz.model.PlayerScoreFact;
import com.ftn.sbnz.model.Resource;
import com.ftn.sbnz.model.Settlement;
import com.ftn.sbnz.service.dto.AdviceDto;
import com.ftn.sbnz.service.dto.BoardStateDto;
import com.ftn.sbnz.service.dto.DiceRollDto;
import com.ftn.sbnz.service.dto.EdgeDto;
import com.ftn.sbnz.service.dto.NodeDto;
import com.ftn.sbnz.service.dto.PlayerDto;

@Service
public class GameService {

    private static final String[] COLORS = {"#d9382c", "#2f6fdb", "#e8821a"};

    private static final int[][] STEPS = {
        {1, 0}, {1, 1}, {1, 2},
        {2, 2}, {2, 1}, {2, 0},
    };

    private final NodeService nodeService;
    private final EdgeService edgeService;
    private final PlayerService playerService;
    private final PlacementAdviceService placementAdviceService;
    private final ScoringService scoringService;
    private final BuildActionService buildActionService;
    private final Random random = new Random();

    private List<Integer> playerIds = new ArrayList<>();
    private Integer currentPlayerId = null;
    private String phase = "IDLE";
    private int step = 0;
    private int currentPlayerTurnIndex = 0;
    private boolean autoOpponents = true;
    private final Map<Integer, List<String>> resourceNodes = new HashMap<>();
    private final Map<Integer, List<String>> diceResourceNodes = new HashMap<>();
    private int lastDiceSum = 0;
    private final List<DiceRollDto> diceRolls = new ArrayList<>();

    public GameService(NodeService nodeService, EdgeService edgeService, PlayerService playerService,
                       PlacementAdviceService placementAdviceService, ScoringService scoringService,
                       BuildActionService buildActionService) {
        this.nodeService = nodeService;
        this.edgeService = edgeService;
        this.playerService = playerService;
        this.placementAdviceService = placementAdviceService;
        this.scoringService = scoringService;
        this.buildActionService = buildActionService;
    }

    public synchronized BoardStateDto state() {
        return buildState();
    }

    public synchronized BoardStateDto newGame(boolean autoOpponents) {
        resetBoard();
        createPlayers();
        this.autoOpponents = autoOpponents;
        step = 0;
        advanceToHuman();
        return buildState();
    }

    public synchronized BoardStateDto place(int nodeId, int edgeId) {
        if (step >= STEPS.length || currentPlayerId == null) {
            throw new GameActionException(HttpStatus.CONFLICT, "Not your turn. Start a new game first.");
        }

        int round = STEPS[step][0];
        Node node = nodeService.getById(nodeId).orElse(null);
        Edge edge = edgeService.getById(edgeId).orElse(null);
        if (node == null || edge == null) {
            throw new GameActionException(HttpStatus.BAD_REQUEST, "Unknown node or edge.");
        }

        List<Edge> edges = edgeService.getAll();
        if (!isPlaceable(node, edges)) {
            throw new GameActionException(
                    HttpStatus.BAD_REQUEST,
                    "That spot is taken or too close to another village.");
        }
        if (edge.getNode1().getId() != node.getId() && edge.getNode2().getId() != node.getId()) {
            throw new GameActionException(HttpStatus.BAD_REQUEST, "The road must connect to your village.");
        }
        if (edge.getOwner() != null) {
            throw new GameActionException(HttpStatus.BAD_REQUEST, "That road is already taken.");
        }

        Player player = playerService.getById(currentPlayerId).orElseThrow();
        placeVillage(node, player);
        placeRoad(edge, player);

        if (round == 2) {
            grantResources(node, player);
        }
        step++;
        advanceToHuman();
        return buildState();
    }

    public synchronized BoardStateDto endTurn() {
        if ("DONE".equals(phase)) {
            throw new GameActionException(HttpStatus.CONFLICT, "The game is already over.");
        }
        if (step < STEPS.length) {
            throw new GameActionException(HttpStatus.CONFLICT, "Still in placement phase.");
        }
        advanceToNextPlayer();
        playTurnWithDiceIfNeeded();
        return buildState();
    }

    public synchronized BoardStateDto build(String action, Integer nodeId, Integer edgeId) {
        if (!isMainTurn()) {
            throw new GameActionException(HttpStatus.CONFLICT, "You can build only after dice are rolled.");
        }
        Player player = playerService.getById(currentPlayerId).orElseThrow();
        buildActionService.build(player, action, nodeId, edgeId);
        return buildState();
    }

    private void advanceToHuman() {
        while (step < STEPS.length) {
            int round = STEPS[step][0];
            int idx = STEPS[step][1];
            if (isHuman(idx)) {
                currentPlayerId = playerIds.get(idx);
                phase = "R" + round + "_P" + (idx + 1);
                return;
            }
            autoPlace(playerIds.get(idx), round == 2);
            step++;
        }

        currentPlayerTurnIndex = 0;
        currentPlayerId = playerIds.get(0);
        phase = "TURN_P1";
        playTurnWithDiceIfNeeded();
    }

    private boolean isHuman(int playerIndex) {
        return playerIndex == 2 || !autoOpponents;
    }

    private void playTurnWithDiceIfNeeded() {
        while (step >= STEPS.length && currentPlayerId != null) {
            if (isHuman(currentPlayerTurnIndex)) {
                rollDiceForCurrentPlayer();
                phase = "TURN_P" + (currentPlayerTurnIndex + 1) + "_ROLLED";
                return;
            }
            rollDiceForCurrentPlayer();
            advanceToNextPlayer();
        }
    }

    private void rollDiceForCurrentPlayer() {
        int dice1 = random.nextInt(6) + 1;
        int dice2 = random.nextInt(6) + 1;
        lastDiceSum = dice1 + dice2;

        diceRolls.add(new DiceRollDto(currentPlayerId, currentPlayerTurnIndex + 1, dice1, dice2));
        if (diceRolls.size() > playerIds.size()) {
            diceRolls.remove(0);
        }

        diceResourceNodes.clear();
        if (lastDiceSum == 7) {
            discardForSeven();
            return;
        }
        distributeDiceResources(lastDiceSum);
    }

    private boolean isMainTurn() {
        return step >= STEPS.length && phase != null && phase.matches("TURN_P\\d+_ROLLED");
    }

    private void discardForSeven() {
        for (Integer playerId : playerIds) {
            playerService.getById(playerId).ifPresent(this::discardHalfIfOverSeven);
        }
    }

    private void discardHalfIfOverSeven(Player player) {
        List<Resource> cards = resourceCards(player);
        if (cards.size() <= 7) {
            return;
        }

        Collections.shuffle(cards, random);
        int toDiscard = cards.size() / 2;
        for (int i = 0; i < toDiscard; i++) {
            player.removeResource(cards.get(i), 1);
        }
        playerService.create(player);
    }

    private List<Resource> resourceCards(Player player) {
        List<Resource> cards = new ArrayList<>();
        if (player.getResources() == null) {
            return cards;
        }
        player.getResources().forEach((resource, count) -> {
            for (int i = 0; i < count; i++) {
                cards.add(resource);
            }
        });
        return cards;
    }

    private void distributeDiceResources(int diceSum) {
        Set<Integer> processedHexIds = new HashSet<>();

        for (Hexagon hex : getHexagonsWithDots(diceSum)) {
            if (processedHexIds.contains(hex.getId())) {
                continue;
            }
            processedHexIds.add(hex.getId());

            Resource field = hex.getField();
            if (field == null || field == Resource.DESERT) {
                continue;
            }

            for (Node node : nodeService.getAll()) {
                if (node.getSettlement() == null || node.getOwner() == null) {
                    continue;
                }
                boolean isOnHex = node.getAdjacentHexagons().stream()
                        .anyMatch(h -> h.getId() == hex.getId());
                if (!isOnHex) {
                    continue;
                }

                Player owner = node.getOwner();
                int amount = node.getSettlement() == Settlement.TOWN ? 2 : 1;
                owner.addResource(field, amount);
                playerService.create(owner);

                List<String> gained = diceResourceNodes.getOrDefault(node.getId(), new ArrayList<>());
                for (int i = 0; i < amount; i++) {
                    gained.add(field.getDisplayName());
                }
                diceResourceNodes.put(node.getId(), gained);
            }
        }
    }

    private List<Hexagon> getHexagonsWithDots(int dots) {
        Set<Hexagon> hexSet = new HashSet<>();
        for (Node node : nodeService.getAll()) {
            for (Hexagon hex : node.getAdjacentHexagons()) {
                if (hex.getDots() == dots) {
                    hexSet.add(hex);
                }
            }
        }
        return new ArrayList<>(hexSet);
    }

    private void advanceToNextPlayer() {
        currentPlayerTurnIndex = (currentPlayerTurnIndex + 1) % playerIds.size();
        currentPlayerId = playerIds.get(currentPlayerTurnIndex);
        phase = "TURN_P" + (currentPlayerTurnIndex + 1);
    }

    private void autoPlace(int playerId, boolean grantResources) {
        Player player = playerService.getById(playerId).orElseThrow();
        List<Node> nodes = nodeService.getAll();
        List<Edge> edges = edgeService.getAll();

        List<Node> options = new ArrayList<>();
        for (Node node : nodes) {
            if (isPlaceable(node, edges)) {
                options.add(node);
            }
        }
        if (options.isEmpty()) {
            return;
        }

        Node chosen = options.get(random.nextInt(options.size()));
        placeVillage(chosen, player);
        if (grantResources) {
            grantResources(chosen, player);
        }

        List<Edge> incident = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.getOwner() == null
                    && (edge.getNode1().getId() == chosen.getId()
                    || edge.getNode2().getId() == chosen.getId())) {
                incident.add(edge);
            }
        }
        if (!incident.isEmpty()) {
            placeRoad(incident.get(random.nextInt(incident.size())), player);
        }
    }

    private void grantResources(Node node, Player player) {
        List<String> gained = new ArrayList<>();
        for (Hexagon hex : node.getAdjacentHexagons()) {
            Resource field = hex.getField();
            if (field == null || field == Resource.DESERT) {
                continue;
            }
            player.addResource(field, 1);
            gained.add(field.getDisplayName());
        }
        playerService.create(player);
        resourceNodes.put(node.getId(), gained);
    }

    private void placeVillage(Node node, Player player) {
        node.setSettlement(Settlement.VILLAGE);
        node.setOwner(player);
        nodeService.updateById(node.getId(), node);
    }

    private void placeRoad(Edge edge, Player player) {
        edge.setOwner(player);
        edgeService.updateById(edge.getId(), edge);
        scoringService.recordRoadBuilt(player.getId());
    }

    private boolean isPlaceable(Node node, List<Edge> edges) {
        if (node.getSettlement() != null) {
            return false;
        }

        Set<Integer> neighbours = new HashSet<>();
        for (Edge edge : edges) {
            if (edge.getNode1().getId() == node.getId()) {
                neighbours.add(edge.getNode2().getId());
            } else if (edge.getNode2().getId() == node.getId()) {
                neighbours.add(edge.getNode1().getId());
            }
        }
        for (Node other : nodeService.getAll()) {
            if (neighbours.contains(other.getId()) && other.getSettlement() != null) {
                return false;
            }
        }
        return true;
    }

    private void resetBoard() {
        for (Node node : nodeService.getAll()) {
            node.setSettlement(null);
            node.setOwner(null);
            nodeService.create(node);
        }
        for (Edge edge : edgeService.getAll()) {
            edge.setOwner(null);
            edgeService.create(edge);
        }
        for (Player player : playerService.getAll()) {
            playerService.deleteById(player.getId());
        }

        playerIds.clear();
        resourceNodes.clear();
        diceResourceNodes.clear();
        lastDiceSum = 0;
        diceRolls.clear();
        scoringService.reset();
    }

    private void createPlayers() {
        playerIds.clear();
        for (int i = 0; i < 3; i++) {
            Player created = playerService.create(new Player());
            playerIds.add(created.getId());
        }
    }

    private BoardStateDto buildState() {
        List<NodeDto> nodeDtos = new ArrayList<>();
        for (Node node : nodeService.getAll()) {
            NodeDto dto = new NodeDto(node);
            List<String> gained = new ArrayList<>();
            gained.addAll(resourceNodes.getOrDefault(node.getId(), List.of()));
            gained.addAll(diceResourceNodes.getOrDefault(node.getId(), List.of()));
            if (!gained.isEmpty()) {
                dto.setResourcesGained(gained);
            }
            nodeDtos.add(dto);
        }
        nodeDtos.sort(Comparator.comparingInt(NodeDto::getId));

        List<EdgeDto> edgeDtos = new ArrayList<>();
        for (Edge edge : edgeService.getAll()) {
            edgeDtos.add(new EdgeDto(edge));
        }
        edgeDtos.sort(Comparator.comparingInt(EdgeDto::getId));

        List<Player> players = new ArrayList<>();
        for (int playerId : playerIds) {
            playerService.getById(playerId).ifPresent(players::add);
        }

        List<PlayerScoreFact> scoreFacts = scoringService.calculate(players);
        Map<Integer, PlayerScoreFact> scoreByPlayer = new HashMap<>();
        for (PlayerScoreFact fact : scoreFacts) {
            scoreByPlayer.put(fact.getPlayerId(), fact);
            if (fact.isWinner()) {
                phase = "DONE";
                currentPlayerId = null;
            }
        }

        List<PlayerDto> playerDtos = new ArrayList<>();
        for (int i = 0; i < playerIds.size(); i++) {
            int playerId = playerIds.get(i);
            Map<String, Integer> resources = playerResources(players, playerId);
            PlayerScoreFact score = scoreByPlayer.get(playerId);
            playerDtos.add(new PlayerDto(playerId, COLORS[i % COLORS.length],
                    score == null ? 0 : score.getScore(),
                    score == null ? 0 : score.getLongestRoadLength(),
                    score != null && score.isLongestRoadAwarded(),
                    score != null && score.isWinner(),
                    resources));
        }

        List<AdviceDto> advices = List.of();
        if (step < STEPS.length && playerIds.size() == 3
                && playerIds.get(2).equals(currentPlayerId)) {
            advices = placementAdviceService.openingAdvice(playerIds.get(2));
        }

        BuildOptions buildOptions = buildOptions(players);
        return new BoardStateDto(nodeDtos, edgeDtos, playerDtos, currentPlayerId, phase,
                lastDiceSum, new ArrayList<>(diceRolls), advices,
                buildOptions.actions(), buildOptions.roadEdgeIds(),
                buildOptions.villageNodeIds(), buildOptions.townNodeIds());
    }

    private Map<String, Integer> playerResources(List<Player> players, int playerId) {
        Map<String, Integer> resources = new LinkedHashMap<>();
        Player player = players.stream()
                .filter(candidate -> candidate.getId() == playerId)
                .findFirst()
                .orElse(null);
        if (player != null && player.getResources() != null) {
            player.getResources().forEach((resource, count) ->
                    resources.put(resource.getDisplayName(), count));
        }
        return resources;
    }

    private BuildOptions buildOptions(List<Player> players) {
        if (!isMainTurn() || currentPlayerId == null) {
            return BuildOptions.empty();
        }
        return players.stream()
                .filter(player -> player.getId() == currentPlayerId)
                .findFirst()
                .map(player -> new BuildOptions(
                        buildActionService.availableActions(player),
                        buildActionService.legalRoadEdgeIds(player),
                        buildActionService.legalVillageNodeIds(player),
                        buildActionService.legalTownNodeIds(player)))
                .orElseGet(BuildOptions::empty);
    }

    private record BuildOptions(List<String> actions, List<Integer> roadEdgeIds,
                                List<Integer> villageNodeIds, List<Integer> townNodeIds) {
        private static BuildOptions empty() {
            return new BuildOptions(List.of(), List.of(), List.of(), List.of());
        }
    }
}
