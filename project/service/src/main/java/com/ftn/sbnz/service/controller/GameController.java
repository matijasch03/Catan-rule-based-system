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
import com.ftn.sbnz.service.dto.EdgeDto;
import com.ftn.sbnz.service.dto.NodeDto;
import com.ftn.sbnz.service.dto.PlayerDto;
import com.ftn.sbnz.service.service.EdgeService;
import com.ftn.sbnz.service.service.NodeService;
import com.ftn.sbnz.service.service.PlayerService;

// Initial-placement game flow: three players each get a village + linked road.
// Players 1 and 2 are placed automatically; player 3 is the human user.
@RestController
@RequestMapping("/api/game")
public class GameController {

    private static final String[] COLORS = {"#d9382c", "#2f6fdb", "#e8821a"};

    private final NodeService nodeService;
    private final EdgeService edgeService;
    private final PlayerService playerService;
    private final Random random = new Random();

    // Turn state kept in memory; board piece ownership is persisted in the DB.
    private List<Integer> playerIds = new ArrayList<>();
    private Integer currentPlayerId = null;
    private String phase = "IDLE";
    // Vertices that handed out resources (second villages): nodeId -> resource names.
    private final Map<Integer, List<String>> resourceNodes = new HashMap<>();

    public GameController(NodeService nodeService, EdgeService edgeService, PlayerService playerService) {
        this.nodeService = nodeService;
        this.edgeService = edgeService;
        this.playerService = playerService;
    }

    @GetMapping("/state")
    public BoardStateDto state() {
        return buildState();
    }

    // Start a fresh game. Round 1 goes P1, P2, P3: players 1 and 2 are auto-placed,
    // then the user (player 3) places. Round 2 then runs in reverse order.
    @PostMapping("/new")
    public BoardStateDto newGame() {
        resetBoard();
        createPlayers();

        autoPlace(playerIds.get(0), false);
        autoPlace(playerIds.get(1), false);

        currentPlayerId = playerIds.get(2);
        phase = "R1_P3";
        return buildState();
    }

    // The user places a village on a free node and a road on a linked edge. They do
    // this twice: once in round 1, then again first in the reverse-order round 2
    // (where the second village also yields up to three resources).
    @PostMapping("/place")
    public ResponseEntity<?> place(@RequestBody PlaceRequest req) {
        boolean round1 = "R1_P3".equals(phase);
        boolean round2 = "R2_P3".equals(phase);
        if (!round1 && !round2) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Not your turn. Start a new game first.");
        }
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

        Player p = playerService.getById(playerIds.get(2)).orElseThrow();
        placeVillage(node, p);
        placeRoad(edge, p);

        if (round1) {
            // Round 2 starts with the same player (reverse order), so it's still your turn.
            phase = "R2_P3";
            currentPlayerId = playerIds.get(2);
        } else {
            grantResources(node, p);
            // Reverse order: after you, players 2 then 1 take their second turn.
            autoPlace(playerIds.get(1), true);
            autoPlace(playerIds.get(0), true);
            phase = "DONE";
            currentPlayerId = null;
        }
        return ResponseEntity.ok(buildState());
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
        nodeService.create(node);
    }

    private void placeRoad(Edge edge, Player player) {
        edge.setOwner(player);
        edgeService.create(edge);
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
            List<String> gained = resourceNodes.get(n.getId());
            if (gained != null) {
                dto.setResourcesGained(new ArrayList<>(gained));
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

        return new BoardStateDto(nodeDtos, edgeDtos, playerDtos, currentPlayerId, phase);
    }

    public static class PlaceRequest {
        public int nodeId;
        public int edgeId;
    }
}
