package com.ftn.sbnz.service.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ftn.sbnz.model.Edge;
import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.Player;
import com.ftn.sbnz.model.Resource;
import com.ftn.sbnz.model.Settlement;
import com.ftn.sbnz.service.dto.BoardStateDto;
import com.ftn.sbnz.service.dto.AdviceDto;
import com.ftn.sbnz.service.dto.DiceRollDto;
import com.ftn.sbnz.service.dto.EdgeDto;
import com.ftn.sbnz.service.dto.NodeDto;
import com.ftn.sbnz.service.dto.PlayerDto;
import com.ftn.sbnz.service.service.EdgeService;
import com.ftn.sbnz.service.service.NodeService;
import com.ftn.sbnz.service.service.PlayerService;
import com.ftn.sbnz.service.service.PlacementAdviceService;

// Initial-placement game flow: three players each get a village + linked road.
// Players 1 and 2 are placed automatically; player 3 is the human user.
@RestController
@RequestMapping("/api/game")
public class GameController {

    private static final String[] COLORS = {"#d9382c", "#2f6fdb", "#e8821a"};

    private final NodeService nodeService;
    private final EdgeService edgeService;
    private final PlayerService playerService;
    private final PlacementAdviceService placementAdviceService;
    private final Random random = new Random();
 
    // Placement order over two rounds: round 1 goes P1,P2,P3; round 2 reverses to
    // P3,P2,P1. Each step is {round (1/2), player index 0..2}.
    private static final int[][] STEPS = {
        {1, 0}, {1, 1}, {1, 2},
        {2, 2}, {2, 1}, {2, 0},
    };
 
    // Turn state kept in memory; board piece ownership is persisted in the DB.
    private List<Integer> playerIds = new ArrayList<>();
    private Integer currentPlayerId = null;
    private String phase = "IDLE";
    private int step = 0;
    private int currentPlayerTurnIndex = 0;  // Index in playerIds for main game loop
    // When true players 1 and 2 are auto-played by the computer; otherwise the user
    // places for every player.
    private boolean autoOpponents = true;
    // Vertices that handed out resources (second villages): nodeId -> resource names.
    private final Map<Integer, List<String>> resourceNodes = new HashMap<>();
    // Track resources gained from dice roll (node -> resources gained this turn).
    private final Map<Integer, List<String>> diceResourceNodes = new HashMap<>();
    // Track last dice roll result for UI display
    private int lastDiceSum = 0;
    // Keep one complete P1-P2-P3 cycle so the UI can show computer rolls too.
    private final List<DiceRollDto> diceRolls = new ArrayList<>();
 
    public GameController(NodeService nodeService, EdgeService edgeService, PlayerService playerService,
                          PlacementAdviceService placementAdviceService) {
        this.nodeService = nodeService;
        this.edgeService = edgeService;
        this.playerService = playerService;
        this.placementAdviceService = placementAdviceService;
    }
 
    @GetMapping("/state")
    public BoardStateDto state() {
        return buildState();
    }
 
    // Start a fresh game. Round 1 goes P1, P2, P3; round 2 runs in reverse order.
    // If autoOpponents is true, players 1 and 2 are placed automatically and the
    // user only plays player 3; otherwise the user places for all three players.
    @PostMapping("/new")
    public BoardStateDto newGame(@RequestBody(required = false) NewGameRequest req) {
        resetBoard();
        createPlayers();
        autoOpponents = req == null || req.autoOpponents;
        step = 0;
        advanceToHuman();
        return buildState();
    }
 
    // Auto-place every computer-controlled player until it is a human's turn (or the
    // game is over), updating phase/currentPlayerId to point at the pending step.
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
        currentPlayerId = null;
        // Transition to main game phase
        currentPlayerTurnIndex = 0;
        currentPlayerId = playerIds.get(0);
        phase = "TURN_P1";
        // Auto-play opponents' first turn with dice roll if autoOpponents is enabled
        playTurnWithDiceIfNeeded();
    }
 
    private boolean isHuman(int playerIndex) {
        return playerIndex == 2 || !autoOpponents;
    }
 
    // The user places a village on a free node and a road on a linked edge. They do
    // this twice: once in round 1, then again first in the reverse-order round 2
    // (where the second village also yields up to three resources).
    @PostMapping("/place")
    public ResponseEntity<?> place(@RequestBody PlaceRequest req) {
        if (step >= STEPS.length || currentPlayerId == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Not your turn. Start a new game first.");
        }
        int round = STEPS[step][0];
        Node node = nodeService.getById(req.nodeId).orElse(null);
        Edge edge = edgeService.getById(req.edgeId).orElse(null);
        if (node == null || edge == null) {
            return ResponseEntity.badRequest().body("Unknown node or edge.");
        }
        List<Edge> edges = edgeService.getAll();
        if (!isPlaceable(node, edges)) {
            return ResponseEntity.badRequest().body("That spot is taken or too close to another village.");
        }
        if (edge.getNode1().getId() != node.getId() && edge.getNode2().getId() != node.getId()) {
            return ResponseEntity.badRequest().body("The road must connect to your village.");
        }
        if (edge.getOwner() != null) {
            return ResponseEntity.badRequest().body("That road is already taken.");
        }
 
        Player p = playerService.getById(currentPlayerId).orElseThrow();
        placeVillage(node, p);
        placeRoad(edge, p);
 
        if (round == 2) {
            grantResources(node, p);
        }
        step++;
        advanceToHuman();
        return ResponseEntity.ok(buildState());
    }
 

    // ---- main game loop: dice rolls ----
    
    // Auto-play computer turns with dice rolls, then prepare for human player
    private void playTurnWithDiceIfNeeded() {
        while (step >= STEPS.length && currentPlayerId != null) {
            if (isHuman(currentPlayerTurnIndex)) {
                // Roll dice for the human player and wait for their action
                rollDiceForCurrentPlayer();
                phase = "TURN_P" + (currentPlayerTurnIndex + 1) + "_ROLLED";
                return;
            }
            // Auto-play opponent turn
            rollDiceForCurrentPlayer();
            // TODO: Add AI logic here for opponent actions (building, trading, etc.)
            advanceToNextPlayer();
        }
    }
    
    // Roll dice for the current player and distribute resources
    private void rollDiceForCurrentPlayer() {
        // Keep rolling until we get a number that's not 7
        int dice1;
        int dice2;
        do {
            dice1 = random.nextInt(6) + 1;
            dice2 = random.nextInt(6) + 1;
            lastDiceSum = dice1 + dice2;
        } while (lastDiceSum == 7);

        diceRolls.add(new DiceRollDto(
                currentPlayerId, currentPlayerTurnIndex + 1, dice1, dice2));
        if (diceRolls.size() > playerIds.size()) {
            diceRolls.remove(0);
        }
        
        // Distribute resources to all players with settlements on hexagons matching the dice sum
        diceResourceNodes.clear();
        distributeDiceResources(lastDiceSum);
    }
    
    // Endpoint for human to end their turn and advance to next player
    @PostMapping("/endTurn")
    public ResponseEntity<?> endTurn() {
        if (step < STEPS.length) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Still in placement phase.");
        }
        advanceToNextPlayer();
        playTurnWithDiceIfNeeded();
        return ResponseEntity.ok(buildState());
    }


    
    // Distribute resources based on dice roll
    private void distributeDiceResources(int diceSum) {
        Set<Integer> processedHexIds = new HashSet<>();
        
        for (Hexagon hex : getHexagonsWithDots(diceSum)) {
            if (processedHexIds.contains(hex.getId())) continue;
            processedHexIds.add(hex.getId());
            
            Resource field = hex.getField();
            if (field == null || field == Resource.DESERT) continue;
            
            // Find all settlements on this hexagon and give resources
            for (Node node : nodeService.getAll()) {
                if (node.getSettlement() != null && node.getOwner() != null) {
                    boolean isOnHex = node.getAdjacentHexagons().stream()
                            .anyMatch(h -> h.getId() == hex.getId());
                    
                    if (isOnHex) {
                        Player owner = node.getOwner();
                        owner.addResource(field, 1);
                        playerService.create(owner);
                        
                        // Track for UI display
                        List<String> gained = diceResourceNodes.getOrDefault(node.getId(), new ArrayList<>());
                        gained.add(field.getDisplayName());
                        diceResourceNodes.put(node.getId(), gained);
                    }
                }
            }
        }
    }
    
    // Get all hexagons with specific dot number
    private List<Hexagon> getHexagonsWithDots(int dots) {
        // We need to get all hexagons - for now, get them from all nodes' adjacent hexagons
        Set<Hexagon> hexSet = new HashSet<>();
        for (Node n : nodeService.getAll()) {
            for (Hexagon h : n.getAdjacentHexagons()) {
                if (h.getDots() == dots) {
                    hexSet.add(h);
                }
            }
        }
        return new ArrayList<>(hexSet);
    }
    
    // Advance to the next player's turn
    private void advanceToNextPlayer() {
        currentPlayerTurnIndex = (currentPlayerTurnIndex + 1) % playerIds.size();
        currentPlayerId = playerIds.get(currentPlayerTurnIndex);
        phase = "TURN_P" + (currentPlayerTurnIndex + 1);
    }

    // ---- placement helpers ----

    private void autoPlace(int playerId, boolean grantResources) {
        Player p = playerService.getById(playerId).orElseThrow();
        List<Node> nodes = nodeService.getAll();
        List<Edge> edges = edgeService.getAll();

        List<Node> options = new ArrayList<>();
        for (Node n : nodes) {
            if (isPlaceable(n, edges)) {
                options.add(n);
            }
        }
        if (options.isEmpty()) {
            return;
        }
        Node chosen = options.get(random.nextInt(options.size()));
        placeVillage(chosen, p);
        if (grantResources) {
            grantResources(chosen, p);
        }

        List<Edge> incident = new ArrayList<>();
        for (Edge e : edges) {
            if (e.getOwner() == null
                    && (e.getNode1().getId() == chosen.getId() || e.getNode2().getId() == chosen.getId())) {
                incident.add(e);
            }
        }
        if (!incident.isEmpty()) {
            placeRoad(incident.get(random.nextInt(incident.size())), p);
        }
    }

    // A second village pays out one resource per adjacent (non-desert) field, so at
    // most three. Records what was gained on the node for the UI.
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
    }

    // A node may hold a village if it is empty and none of its edge-neighbours
    // already carry a settlement (Catan distance rule).
    private boolean isPlaceable(Node node, List<Edge> edges) {
        if (node.getSettlement() != null) {
            return false;
        }
        Set<Integer> neighbours = new HashSet<>();
        for (Edge e : edges) {
            if (e.getNode1().getId() == node.getId()) {
                neighbours.add(e.getNode2().getId());
            } else if (e.getNode2().getId() == node.getId()) {
                neighbours.add(e.getNode1().getId());
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
        for (Node n : nodeService.getAll()) {
            n.setSettlement(null);
            n.setOwner(null);
            nodeService.create(n);
        }
        for (Edge e : edgeService.getAll()) {
            e.setOwner(null);
            edgeService.create(e);
        }
        for (Player p : playerService.getAll()) {
            playerService.deleteById(p.getId());
        }
        playerIds.clear();
        resourceNodes.clear();
        diceResourceNodes.clear();
        lastDiceSum = 0;
        diceRolls.clear();
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
        for (Node n : nodeService.getAll()) {
            NodeDto dto = new NodeDto(n);
            List<String> gained = new ArrayList<>();
            gained.addAll(resourceNodes.getOrDefault(n.getId(), List.of()));
            gained.addAll(diceResourceNodes.getOrDefault(n.getId(), List.of()));
            if (!gained.isEmpty()) {
                dto.setResourcesGained(gained);
            }
            nodeDtos.add(dto);
        }
        nodeDtos.sort(Comparator.comparingInt(NodeDto::getId));

        List<EdgeDto> edgeDtos = new ArrayList<>();
        for (Edge e : edgeService.getAll()) {
            edgeDtos.add(new EdgeDto(e));
        }
        edgeDtos.sort(Comparator.comparingInt(EdgeDto::getId));

        List<PlayerDto> playerDtos = new ArrayList<>();
        for (int i = 0; i < playerIds.size(); i++) {
            int pid = playerIds.get(i);
            Map<String, Integer> resources = new LinkedHashMap<>();
            playerService.getById(pid).ifPresent(pl -> {
                if (pl.getResources() != null) {
                    pl.getResources().forEach((res, count) -> resources.put(res.getDisplayName(), count));
                }
            });
            playerDtos.add(new PlayerDto(pid, COLORS[i % COLORS.length], resources));
        }

        List<AdviceDto> advices = List.of();
        if (step < STEPS.length && playerIds.size() == 3
                && playerIds.get(2).equals(currentPlayerId)) {
            advices = placementAdviceService.openingAdvice(playerIds.get(2));
        }

        return new BoardStateDto(nodeDtos, edgeDtos, playerDtos, currentPlayerId, phase,
                lastDiceSum, new ArrayList<>(diceRolls), advices);
    }

    public static class PlaceRequest {
        public int nodeId;
        public int edgeId;
    }

    public static class NewGameRequest {
        public boolean autoOpponents = true;
    }
}
