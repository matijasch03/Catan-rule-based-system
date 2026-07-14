package com.ftn.sbnz.service.service;

import java.util.ArrayList;
import java.util.ArrayDeque;
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

import com.ftn.sbnz.model.BuildActionFact;
import com.ftn.sbnz.kjar.ResourceProductionSignal;
import com.ftn.sbnz.kjar.RoadBuildEvent;
import com.ftn.sbnz.kjar.TradeSignal;
import com.ftn.sbnz.kjar.GoalAdvice;
import com.ftn.sbnz.model.Edge;
import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.Player;
import com.ftn.sbnz.model.PlayerScoreFact;
import com.ftn.sbnz.model.Resource;
import com.ftn.sbnz.model.Settlement;
import com.ftn.sbnz.model.SynergyPair;
import com.ftn.sbnz.service.controller.GameController.TradeRequest;
import com.ftn.sbnz.service.dto.AdviceDto;
import com.ftn.sbnz.service.dto.BoardStateDto;
import com.ftn.sbnz.service.dto.DiceRollDto;
import com.ftn.sbnz.service.dto.EdgeDto;
import com.ftn.sbnz.service.dto.GoalAdviceDto;
import com.ftn.sbnz.service.dto.NodeDto;
import com.ftn.sbnz.service.dto.PlayerDto;
import com.ftn.sbnz.service.dto.TradeProposalDto;

@Service
public class GameService {

    private static final String[] COLORS = {"#d9382c", "#2f6fdb", "#e8821a"};

    private static final int[][] STEPS = {
        {1, 0}, {1, 1}, {1, 2},
        {2, 2}, {2, 1}, {2, 0},
    };
    private static final int CEP_WINDOW_TURNS = 3;
    private static final int UNREACHABLE_ROUTE_DISTANCE = 99;

    private final NodeService nodeService;
    private final EdgeService edgeService;
    private final PlayerService playerService;
    private final PlacementAdviceService placementAdviceService;
    private final ScoringService scoringService;
    private final BuildActionService buildActionService;
    private final GoalPlanningService goalPlanningService;
    private final AdviceService adviceService;
    private final SynergyPairService synergyPairService;
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
    private String turnMessage = "";
    private boolean tradeAttempted = false;
    private boolean tradeRefused = false;
    private int turnSequence = 0;
    private final List<RoadBuildEvent> roadBuildEvents = new ArrayList<>();
    private final List<TradeSignal> tradeSignals = new ArrayList<>();

    public GameService(NodeService nodeService, EdgeService edgeService, PlayerService playerService,
                       PlacementAdviceService placementAdviceService, ScoringService scoringService,
                       BuildActionService buildActionService, GoalPlanningService goalPlanningService,
                       AdviceService adviceService, SynergyPairService synergyPairService) {
        this.nodeService = nodeService;
        this.edgeService = edgeService;
        this.playerService = playerService;
        this.placementAdviceService = placementAdviceService;
        this.scoringService = scoringService;
        this.buildActionService = buildActionService;
        this.goalPlanningService = goalPlanningService;
        this.adviceService = adviceService;
        this.synergyPairService = synergyPairService;
    }

    public synchronized BoardStateDto state() {
        if (needsFreshGame()) {
            return newGame(autoOpponents);
        }
        return buildState();
    }

    private boolean needsFreshGame() {
        return playerIds.isEmpty()
                || ("IDLE".equals(phase) && currentPlayerId == null && step == 0);
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
        placementAdviceService.updatePersistedRoutes(player.getId());

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

        if (isReadyTurn()) {
            rollDiceForCurrentPlayer();
            phase = turnPhase("ROLLED");
            turnMessage = diceMessage(currentPlayerTurnIndex + 1);
            return buildState();
        }

        if (isRolledTurn()) {
            if (isAutoOpponentTurn()) {
                turnMessage = simulateOpponentBuild();
                phase = turnPhase("BUILT");
            } else {
                advanceToNextPlayer();
                startCurrentTurn();
            }
            return buildState();
        }

        if (isBuiltTurn()) {
            advanceToNextPlayer();
            startCurrentTurn();
            return buildState();
        }

        startCurrentTurn();
        return buildState();
    }

    public synchronized BoardStateDto build(String action, Integer nodeId, Integer edgeId) {
        if (!isMainTurn() || !isUserControlledCurrentPlayer()) {
            throw new GameActionException(HttpStatus.CONFLICT, "You can build only after dice are rolled.");
        }
        Player player = playerService.getById(currentPlayerId).orElseThrow();
        int beforeDistance = routeDistanceFor(player.getId());
        buildActionService.build(player, action, nodeId, edgeId);
        if (BuildActionService.ROAD.equalsIgnoreCase(action)) {
            placementAdviceService.updatePersistedRoutes(player.getId());
            recordRoadBuild(player.getId(), edgeId, beforeDistance);
        }
        turnMessage = playerLabel(currentPlayerTurnIndex + 1) + " built " + action.toLowerCase() + ".";
        return buildState();
    }

    public synchronized BoardStateDto offerTrade(TradeRequest request) {
        if (!isMainTurn() || !isUserControlledCurrentPlayer()) {
            throw new GameActionException(HttpStatus.CONFLICT, "You can offer trades only on your turn after dice are rolled.");
        }
        tradeAttempted = true;
        tradeRefused = false;
        Player player = playerService.getById(currentPlayerId).orElseThrow();
        if (request == null || request.wantedResource == null || request.offeredResource == null) {
            turnMessage = "Trade proposal noted, but no concrete resource exchange was selected.";
            return buildState();
        }

        Resource wanted = parseResource(request.wantedResource);
        Resource offered = parseResource(request.offeredResource);
        int offeredAmount = Math.max(1, request.offeredAmount);

        if (request.bankTrade) {
            executeBankTrade(player, offered, wanted, offeredAmount);
        } else {
            executeOpponentTrade(player, request.opponentId, offered, wanted, offeredAmount);
        }
        return buildState();
    }

    private void executeOpponentTrade(Player player, Integer opponentId, Resource offered,
                                      Resource wanted, int offeredAmount) {
        if (opponentId == null) {
            throw new GameActionException(HttpStatus.BAD_REQUEST, "Choose an opponent for this trade.");
        }
        Player opponent = playerService.getById(opponentId)
                .orElseThrow(() -> new GameActionException(HttpStatus.BAD_REQUEST, "Unknown opponent."));
        if (resourceCount(player, offered) < offeredAmount) {
            throw new GameActionException(HttpStatus.BAD_REQUEST, "You no longer have enough " + offered.getDisplayName() + " to trade.");
        }
        if (resourceCount(opponent, wanted) < 1) {
            throw new GameActionException(HttpStatus.BAD_REQUEST, playerLabel(playerNumber(opponent.getId()))
                    + " no longer has " + wanted.getDisplayName() + ".");
        }

        int acceptanceChance = offeredAmount >= 2 ? 85 : 65;
        if (random.nextInt(100) >= acceptanceChance) {
            tradeRefused = true;
            recordTradeSignal(opponent.getId(), wanted, false);
            turnMessage = playerLabel(playerNumber(opponent.getId()))
                    + " refused your offer: " + amountLabel(offeredAmount, offered)
                    + " for 1 " + wanted.getDisplayName() + ".";
            return;
        }

        player.removeResource(offered, offeredAmount);
        player.addResource(wanted, 1);
        opponent.removeResource(wanted, 1);
        opponent.addResource(offered, offeredAmount);
        playerService.create(player);
        playerService.create(opponent);
        recordTradeSignal(opponent.getId(), offered, true);
        tradeRefused = false;
        turnMessage = playerLabel(playerNumber(opponent.getId()))
                + " accepted your offer. Trade completed: you gave " + amountLabel(offeredAmount, offered)
                + " to " + playerLabel(playerNumber(opponent.getId()))
                + " and received 1 " + wanted.getDisplayName() + ".";
    }

    private void executeBankTrade(Player player, Resource offered, Resource wanted, int offeredAmount) {
        int amount = Math.max(4, offeredAmount);
        if (resourceCount(player, offered) < amount) {
            throw new GameActionException(HttpStatus.BAD_REQUEST, "You need " + amount + " "
                    + offered.getDisplayName() + " for this bank trade.");
        }
        player.removeResource(offered, amount);
        player.addResource(wanted, 1);
        playerService.create(player);
        turnMessage = "Bank trade completed: you gave " + amountLabel(amount, offered)
                + " and received 1 " + wanted.getDisplayName() + ".";
    }

    private void advanceToHuman() {
        while (step < STEPS.length) {
            int round = STEPS[step][0];
            int idx = STEPS[step][1];
            if (isHuman(idx)) {
                currentPlayerTurnIndex = idx;
                currentPlayerId = playerIds.get(idx);
                phase = "R" + round + "_P" + (idx + 1);
                return;
            }
            autoPlace(playerIds.get(idx), round == 2);
            step++;
        }

        currentPlayerTurnIndex = 0;
        currentPlayerId = playerIds.get(0);
        startCurrentTurn();
    }

    private boolean isHuman(int playerIndex) {
        return playerIndex == 2 || !autoOpponents;
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

    private boolean isReadyTurn() {
        return step >= STEPS.length && phase != null && phase.matches("TURN_P\\d+_READY");
    }

    private boolean isRolledTurn() {
        return isMainTurn();
    }

    private boolean isBuiltTurn() {
        return step >= STEPS.length && phase != null && phase.matches("TURN_P\\d+_BUILT");
    }

    private boolean isAutoOpponentTurn() {
        return autoOpponents && currentPlayerTurnIndex != 2;
    }

    private boolean isUserControlledCurrentPlayer() {
        return !autoOpponents || currentPlayerTurnIndex == 2;
    }

    private String turnPhase(String suffix) {
        return "TURN_P" + (currentPlayerTurnIndex + 1) + "_" + suffix;
    }

    private void startCurrentTurn() {
        turnSequence++;
        pruneCepWindow();
        diceResourceNodes.clear();
        tradeAttempted = false;
        tradeRefused = false;
        phase = turnPhase("READY");
        turnMessage = playerLabel(currentPlayerTurnIndex + 1) + " is ready to roll.";
    }

    private String diceMessage(int playerNumber) {
        String who = playerLabel(playerNumber);
        if (lastDiceSum == 7) {
            return who + " rolled 7. Players with more than 7 cards discarded half to the bank.";
        }
        return who + " rolled " + lastDiceSum + ". Resources were distributed to matching villages and towns.";
    }

    private String playerLabel(int playerNumber) {
        return playerNumber == 3 ? "You" : "Player " + playerNumber;
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

    private String simulateOpponentBuild() {
        Player player = playerService.getById(currentPlayerId).orElseThrow();
        int playerNumber = currentPlayerTurnIndex + 1;

        List<String> actions = buildActionService.availableActions(player);
        if (actions.contains(BuildActionService.TOWN)) {
            List<Integer> nodes = buildActionService.legalTownNodeIds(player);
            if (!nodes.isEmpty()) {
                int nodeId = randomChoice(nodes);
                buildActionService.build(player, BuildActionService.TOWN, nodeId, null);
                return playerLabel(playerNumber) + " upgraded village " + nodeId + " into a town.";
            }
        }

        if (actions.contains(BuildActionService.VILLAGE)) {
            List<Integer> nodes = buildActionService.legalVillageNodeIds(player);
            if (!nodes.isEmpty()) {
                int nodeId = randomChoice(nodes);
                buildActionService.build(player, BuildActionService.VILLAGE, nodeId, null);
                return playerLabel(playerNumber) + " built a village on node " + nodeId + ".";
            }
        }

        if (actions.contains(BuildActionService.ROAD)) {
            List<Integer> edges = buildActionService.legalRoadEdgeIds(player);
            if (!edges.isEmpty()) {
                int edgeId = randomChoice(edges);
                int beforeDistance = routeDistanceFor(player.getId());
                buildActionService.build(player, BuildActionService.ROAD, null, edgeId);
                recordRoadBuild(player.getId(), edgeId, beforeDistance);
                return playerLabel(playerNumber) + " built a road on edge " + edgeId + ".";
            }
        }

        return playerLabel(playerNumber) + " could not afford a legal build and passed.";
    }

    private int randomChoice(List<Integer> ids) {
        return ids.get(random.nextInt(ids.size()));
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
        adviceService.deleteAll();
        synergyPairService.deleteAll();

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
        turnMessage = "";
        tradeAttempted = false;
        tradeRefused = false;
        turnSequence = 0;
        roadBuildEvents.clear();
        tradeSignals.clear();
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
        List<AdviceDto> advices = List.of();
        if (currentPlayerId != null && isUserControlledCurrentPlayer()) {
            advices = placementAdviceService.openingAdvice(currentPlayerId);
        }

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

        List<GoalAdviceDto> goalAdvices = goalAdvices(players, scoreByPlayer);

        BuildOptions buildOptions = buildOptions(players);
        return new BoardStateDto(nodeDtos, edgeDtos, playerDtos, currentPlayerId, phase,
                lastDiceSum, new ArrayList<>(diceRolls), advices, goalAdvices,
                buildOptions.actions(), buildOptions.roadEdgeIds(),
                buildOptions.villageNodeIds(), buildOptions.townNodeIds(), turnMessage);
    }

    private List<GoalAdviceDto> goalAdvices(List<Player> players, Map<Integer, PlayerScoreFact> scoreByPlayer) {
        if (!isMainTurn() || !isUserControlledCurrentPlayer() || currentPlayerId == null) {
            return List.of();
        }
        Player player = players.stream()
                .filter(candidate -> candidate.getId() == currentPlayerId)
                .findFirst()
                .orElse(null);
        PlayerScoreFact score = scoreByPlayer.get(currentPlayerId);
        if (player == null || score == null) {
            return List.of();
        }
        List<Object> cepFacts = cepFacts(player, players);
        List<GoalAdviceDto> advice = new ArrayList<>(goalPlanningService.advice(player, score, tradeAttempted, tradeRefused,
                        cepFacts, roadsMissingForPlannedRoute(player.getId()),
                        maxOpponentRoadCards(player.getId(), players)).stream()
                .map(goalAdvice -> new GoalAdviceDto(goalAdvice, tradeProposal(goalAdvice.getTitle(), player, players)))
                .toList());
        addConcreteTradeSuggestions(advice, player, players);
        return advice.stream()
                .limit(10)
                .toList();
    }

    private void addConcreteTradeSuggestions(List<GoalAdviceDto> advice, Player player, List<Player> players) {
        BuildActionFact build = buildActionService.evaluate(player);
        if (build.isHasVillageToUpgrade() && !build.isCanBuildTown()) {
            addMissingResourceTrades(advice, player, players, "town",
                    Map.of(Resource.ORE, 3, Resource.GRAIN, 2));
        }
        if (build.isHasLegalVillageNode() && !build.isCanBuildVillage()) {
            addMissingResourceTrades(advice, player, players, "village",
                    Map.of(Resource.WOOD, 1, Resource.BRICK, 1, Resource.GRAIN, 1, Resource.WOOL, 1));
        }
        if (build.isHasOpenRoadEdge() && !build.isCanBuildRoad()) {
            addMissingResourceTrades(advice, player, players, "road",
                    Map.of(Resource.WOOD, 1, Resource.BRICK, 1));
        }
    }

    private List<Object> cepFacts(Player me, List<Player> players) {
        pruneCepWindow();
        List<Object> facts = new ArrayList<>();
        for (RoadBuildEvent event : roadBuildEvents) {
            if (event.getPlayerId() == me.getId() || event.getDistanceToRoute() >= UNREACHABLE_ROUTE_DISTANCE) {
                continue;
            }
            facts.add(new RoadBuildEvent(event.getPlayerId(), me.getId(), event.getEdgeId(),
                    event.getTurn(), event.getDistanceToRoute(),
                    event.getPreviousDistanceToRoute(), event.getDirection()));
        }
        for (Player player : players) {
            if (player.getId() == me.getId()) {
                continue;
            }
            for (Resource resource : List.of(Resource.WOOD, Resource.BRICK, Resource.ORE, Resource.GRAIN, Resource.WOOL)) {
                double score = productionScore(player.getId(), resource);
                if (score > 0.0) {
                    facts.add(new ResourceProductionSignal(player.getId(), resource, score));
                }
            }
        }
        facts.addAll(tradeSignals);
        return facts;
    }

    private void recordRoadBuild(int playerId, Integer edgeId, int previousDistance) {
        if (edgeId == null) {
            return;
        }
        int mePlayerId = controlledPlayerId();
        if (mePlayerId == 0 || playerId == mePlayerId) {
            return;
        }
        int currentDistance = routeDistanceFor(playerId);
        if (currentDistance >= UNREACHABLE_ROUTE_DISTANCE && previousDistance >= UNREACHABLE_ROUTE_DISTANCE) {
            return;
        }
        roadBuildEvents.add(new RoadBuildEvent(playerId, mePlayerId, edgeId, turnSequence,
                currentDistance, previousDistance, routeDirectionFor(playerId)));
        pruneCepWindow();
    }

    private void recordTradeSignal(int playerId, Resource resource, boolean successful) {
        double weight = resourceWeight(resource);
        if (weight <= 0.0) {
            return;
        }
        double tradeSuccess = successful ? 1.0 : 0.4;
        tradeSignals.add(new TradeSignal(playerId, resource, turnSequence, successful, weight * tradeSuccess));
        pruneCepWindow();
    }

    private void pruneCepWindow() {
        int oldestTurn = turnSequence - CEP_WINDOW_TURNS;
        roadBuildEvents.removeIf(event -> event.getTurn() < oldestTurn);
        tradeSignals.removeIf(event -> event.getTurn() < oldestTurn);
    }

    private int roadsMissingForPlannedRoute(int playerId) {
        return plannedRouteNodes(playerId).stream()
                .mapToInt(node -> hasOwnedIncidentRoad(node.getId(), playerId) ? 0 : 1)
                .sum();
    }

    private int maxOpponentRoadCards(int mePlayerId, List<Player> players) {
        return players.stream()
                .filter(player -> player.getId() != mePlayerId)
                .mapToInt(player -> Math.min(resourceCount(player, Resource.WOOD), resourceCount(player, Resource.BRICK)))
                .max()
                .orElse(0);
    }

    private int routeDistanceFor(int playerId) {
        int mePlayerId = controlledPlayerId();
        if (mePlayerId == 0) {
            return UNREACHABLE_ROUTE_DISTANCE;
        }
        Set<Integer> targetRoute = plannedRouteNodeIds(mePlayerId);
        if (targetRoute.isEmpty()) {
            return UNREACHABLE_ROUTE_DISTANCE;
        }

        ArrayDeque<Integer> frontier = new ArrayDeque<>();
        Map<Integer, Integer> distance = new HashMap<>();
        for (Edge edge : edgeService.getAll()) {
            if (edge.getOwner() == null || edge.getOwner().getId() != playerId) {
                continue;
            }
            int first = edge.getNode1().getId();
            int second = edge.getNode2().getId();
            if (distance.putIfAbsent(first, 0) == null) {
                frontier.add(first);
            }
            if (distance.putIfAbsent(second, 0) == null) {
                frontier.add(second);
            }
        }
        if (frontier.isEmpty()) {
            for (Node node : nodeService.getAll()) {
                if (node.getOwner() != null && node.getOwner().getId() == playerId && node.getSettlement() != null) {
                    distance.put(node.getId(), 0);
                    frontier.add(node.getId());
                }
            }
        }

        Map<Integer, List<Edge>> edgesByNode = edgesByNode();
        while (!frontier.isEmpty()) {
            int current = frontier.remove();
            int currentDistance = distance.get(current);
            if (targetRoute.contains(current)) {
                return currentDistance;
            }
            if (currentDistance >= 6) {
                continue;
            }
            for (Edge edge : edgesByNode.getOrDefault(current, List.of())) {
                if (edge.getOwner() != null && edge.getOwner().getId() != playerId) {
                    continue;
                }
                int next = otherNodeId(edge, current);
                if (distance.containsKey(next)) {
                    continue;
                }
                distance.put(next, currentDistance + 1);
                frontier.add(next);
            }
        }
        return UNREACHABLE_ROUTE_DISTANCE;
    }

    private String routeDirectionFor(int playerId) {
        Node opponent = firstNetworkNode(playerId);
        Node target = nearestPlannedRouteNode(playerId);
        if (opponent == null || target == null
                || opponent.getPossessiveHexagon() == null || target.getPossessiveHexagon() == null) {
            return "unknown";
        }
        int dq = opponent.getPossessiveHexagon().getQ() - target.getPossessiveHexagon().getQ();
        int dr = opponent.getPossessiveHexagon().getR() - target.getPossessiveHexagon().getR();
        if (Math.abs(dq) >= Math.abs(dr)) {
            return dq >= 0 ? "east" : "west";
        }
        return dr >= 0 ? "south" : "north";
    }

    private Node nearestPlannedRouteNode(int playerId) {
        Set<Integer> routeIds = plannedRouteNodeIds(controlledPlayerId());
        if (routeIds.isEmpty()) {
            return null;
        }
        Set<Integer> network = networkNodeIds(playerId);
        if (network.isEmpty()) {
            return null;
        }
        Map<Integer, List<Edge>> edgesByNode = edgesByNode();
        ArrayDeque<Integer> frontier = new ArrayDeque<>(network);
        Map<Integer, Integer> distance = new HashMap<>();
        for (int nodeId : network) {
            distance.put(nodeId, 0);
        }
        Map<Integer, Node> nodes = nodesById();
        while (!frontier.isEmpty()) {
            int current = frontier.remove();
            if (routeIds.contains(current)) {
                return nodes.get(current);
            }
            for (Edge edge : edgesByNode.getOrDefault(current, List.of())) {
                if (edge.getOwner() != null && edge.getOwner().getId() != playerId) {
                    continue;
                }
                int next = otherNodeId(edge, current);
                if (distance.putIfAbsent(next, distance.get(current) + 1) == null) {
                    frontier.add(next);
                }
            }
        }
        return null;
    }

    private Node firstNetworkNode(int playerId) {
        Set<Integer> network = networkNodeIds(playerId);
        if (network.isEmpty()) {
            return null;
        }
        Map<Integer, Node> nodes = nodesById();
        return nodes.get(network.iterator().next());
    }

    private Set<Integer> networkNodeIds(int playerId) {
        Set<Integer> ids = new HashSet<>();
        for (Edge edge : edgeService.getAll()) {
            if (edge.getOwner() != null && edge.getOwner().getId() == playerId) {
                ids.add(edge.getNode1().getId());
                ids.add(edge.getNode2().getId());
            }
        }
        for (Node node : nodeService.getAll()) {
            if (node.getOwner() != null && node.getOwner().getId() == playerId && node.getSettlement() != null) {
                ids.add(node.getId());
            }
        }
        return ids;
    }

    private double productionScore(int playerId, Resource resource) {
        double weight = resourceWeight(resource);
        if (weight <= 0.0) {
            return 0.0;
        }
        double score = 0.0;
        for (Node node : nodeService.getAll()) {
            if (node.getOwner() == null || node.getOwner().getId() != playerId || node.getSettlement() == null) {
                continue;
            }
            for (Hexagon hex : node.getAdjacentHexagons()) {
                if (hex.getField() == resource) {
                    score += weight * diceWeight(hex.getDots());
                }
            }
        }
        return score;
    }

    private double resourceWeight(Resource resource) {
        return switch (resource) {
            case WOOD, BRICK -> 3.0;
            case ORE -> 0.5;
            case GRAIN, WOOL -> 1.5;
            default -> 0.0;
        };
    }

    private int diceWeight(int dots) {
        return switch (dots) {
            case 2, 12 -> 1;
            case 3, 11 -> 2;
            case 4, 10 -> 3;
            case 5, 9 -> 4;
            case 6, 8 -> 5;
            default -> 0;
        };
    }

    private Set<Integer> plannedRouteNodeIds(int playerId) {
        Set<Integer> ids = new HashSet<>();
        for (Node node : plannedRouteNodes(playerId)) {
            ids.add(node.getId());
        }
        return ids;
    }

    private List<Node> plannedRouteNodes(int playerId) {
        return synergyPairService.getAll().stream()
                .filter(pair -> pair.getRouteNodes() != null && !pair.getRouteNodes().isEmpty())
                .filter(pair -> pairBelongsToPlayer(pair, playerId))
                .max(Comparator.comparingInt(SynergyPair::getScore))
                .map(SynergyPair::getRouteNodes)
                .orElse(List.of());
    }

    private boolean pairBelongsToPlayer(SynergyPair pair, int playerId) {
        return pair.getRouteNodes().stream()
                .anyMatch(node -> node.getOwner() != null && node.getOwner().getId() == playerId);
    }

    private boolean hasOwnedIncidentRoad(int nodeId, int playerId) {
        return edgeService.getAll().stream()
                .anyMatch(edge -> edge.getOwner() != null
                        && edge.getOwner().getId() == playerId
                        && (edge.getNode1().getId() == nodeId || edge.getNode2().getId() == nodeId));
    }

    private Map<Integer, List<Edge>> edgesByNode() {
        Map<Integer, List<Edge>> edgesByNode = new HashMap<>();
        for (Edge edge : edgeService.getAll()) {
            edgesByNode.computeIfAbsent(edge.getNode1().getId(), ignored -> new ArrayList<>()).add(edge);
            edgesByNode.computeIfAbsent(edge.getNode2().getId(), ignored -> new ArrayList<>()).add(edge);
        }
        return edgesByNode;
    }

    private Map<Integer, Node> nodesById() {
        Map<Integer, Node> nodes = new HashMap<>();
        for (Node node : nodeService.getAll()) {
            nodes.put(node.getId(), node);
        }
        return nodes;
    }

    private int otherNodeId(Edge edge, int nodeId) {
        return edge.getNode1().getId() == nodeId ? edge.getNode2().getId() : edge.getNode1().getId();
    }

    private int controlledPlayerId() {
        if (autoOpponents && playerIds.size() >= 3) {
            return playerIds.get(2);
        }
        return currentPlayerId == null ? 0 : currentPlayerId;
    }

    private void addMissingResourceTrades(List<GoalAdviceDto> advice, Player player, List<Player> players,
                                          String object, Map<Resource, Integer> target) {
        for (Map.Entry<Resource, Integer> entry : target.entrySet()) {
            Resource wanted = entry.getKey();
            if (resourceCount(player, wanted) >= entry.getValue()
                    || hasConcreteTradeFor(advice, wanted)) {
                continue;
            }
            String title = "Trade for " + object + " " + wanted.getDisplayName().toLowerCase();
            TradeProposalDto proposal = tradeProposal(title, player, players);
            if (proposal == null) {
                continue;
            }
            advice.add(new GoalAdviceDto(new GoalAdvice(3, title,
                    "You are collecting for a " + object
                            + " and can make this concrete trade now."), proposal));
        }
    }

    private boolean hasConcreteTradeFor(List<GoalAdviceDto> advice, Resource wanted) {
        return advice.stream()
                .map(GoalAdviceDto::getTradeProposal)
                .anyMatch(proposal -> proposal != null
                        && wanted.getDisplayName().equals(proposal.getWantedResource()));
    }

    private TradeProposalDto tradeProposal(String title, Player player, List<Player> players) {
        String normalizedTitle = title == null ? "" : title.toLowerCase();
        Map<Resource, Integer> target = targetCost(title, player);
        Resource wanted = resourceMentionedInTitle(normalizedTitle);
        if (wanted == null) {
            wanted = firstMissingResource(player, target);
        }
        if (wanted == null && normalizedTitle.contains("bank")) {
            wanted = firstUsefulBankWantedResource(player);
        }
        if (wanted == null) {
            return null;
        }

        if (normalizedTitle.contains("bank")) {
            Resource offered = resourceWithAtLeast(player, 4, wanted);
            if (offered == null) {
                return null;
            }
            return new TradeProposalDto(true, null, "bank", wanted.getDisplayName(),
                    offered.getDisplayName(), 4,
                    "Concrete trade: give 4 " + offered.getDisplayName()
                            + " to the bank for 1 " + wanted.getDisplayName() + ".");
        }

        Player opponent = opponentWithResource(players, player.getId(), wanted);
        int offeredAmount = normalizedTitle.contains("refusal") ? 2 : 1;
        Resource offered = offeredResource(player, wanted, target, offeredAmount);
        if (offered == null) {
            return null;
        }
        if (opponent == null) {
            return bankTradeProposal(player, wanted);
        }

        int opponentNumber = playerNumber(opponent.getId());
        return new TradeProposalDto(false, opponent.getId(), playerLabel(opponentNumber),
                wanted.getDisplayName(), offered.getDisplayName(), offeredAmount,
                "Concrete trade: you are missing 1 " + wanted.getDisplayName()
                        + "; trade " + amountLabel(offeredAmount, offered) + " with "
                        + playerLabel(opponentNumber) + ", who has " + wanted.getDisplayName()
                        + ". Acceptance chance: " + (offeredAmount >= 2 ? "85" : "65") + "%.");
    }

    private TradeProposalDto bankTradeProposal(Player player, Resource wanted) {
        Resource offered = resourceWithAtLeast(player, 4, wanted);
        if (offered == null) {
            return null;
        }
        return new TradeProposalDto(true, null, "bank", wanted.getDisplayName(),
                offered.getDisplayName(), 4,
                "Concrete trade: give 4 " + offered.getDisplayName()
                        + " to the bank for 1 " + wanted.getDisplayName() + ".");
    }

    private Resource resourceMentionedInTitle(String normalizedTitle) {
        if (normalizedTitle.contains("wood")) {
            return Resource.WOOD;
        }
        if (normalizedTitle.contains("brick")) {
            return Resource.BRICK;
        }
        if (normalizedTitle.contains("grain")) {
            return Resource.GRAIN;
        }
        if (normalizedTitle.contains("wool") || normalizedTitle.contains("sheep")) {
            return Resource.WOOL;
        }
        if (normalizedTitle.contains("ore")) {
            return Resource.ORE;
        }
        return null;
    }

    private Map<Resource, Integer> targetCost(String title, Player player) {
        Map<Resource, Integer> cost = new LinkedHashMap<>();
        if (title == null) {
            return cost;
        }
        if (title.contains("refusal") || title.contains("Refusal")) {
            BuildActionFact build = buildActionService.evaluate(player);
            if (build.isHasLegalVillageNode() && !build.isCanBuildVillage()) {
                cost.put(Resource.WOOD, 1);
                cost.put(Resource.BRICK, 1);
                cost.put(Resource.GRAIN, 1);
                cost.put(Resource.WOOL, 1);
            } else if (build.isHasVillageToUpgrade() && !build.isCanBuildTown()) {
                cost.put(Resource.ORE, 3);
                cost.put(Resource.GRAIN, 2);
            } else {
                cost.put(Resource.WOOD, 1);
                cost.put(Resource.BRICK, 1);
            }
            return cost;
        }
        if (title.contains("road") || title.contains("Road")) {
            cost.put(Resource.WOOD, 1);
            cost.put(Resource.BRICK, 1);
            return cost;
        }
        if (title.contains("village") || title.contains("Village")) {
            cost.put(Resource.WOOD, 1);
            cost.put(Resource.BRICK, 1);
            cost.put(Resource.GRAIN, 1);
            cost.put(Resource.WOOL, 1);
            return cost;
        }
        if (title.contains("town") || title.contains("Town") || title.contains("bank")) {
            cost.put(Resource.ORE, 3);
            cost.put(Resource.GRAIN, 2);
            return cost;
        }

        if (buildActionService.evaluate(player).isCanBuildVillage()) {
            cost.put(Resource.ORE, 3);
            cost.put(Resource.GRAIN, 2);
        } else {
            cost.put(Resource.WOOD, 1);
            cost.put(Resource.BRICK, 1);
        }
        return cost;
    }

    private Resource firstMissingResource(Player player, Map<Resource, Integer> target) {
        for (Map.Entry<Resource, Integer> entry : target.entrySet()) {
            if (resourceCount(player, entry.getKey()) < entry.getValue()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private Resource firstUsefulBankWantedResource(Player player) {
        Map<Resource, Integer> town = new LinkedHashMap<>();
        town.put(Resource.ORE, 3);
        town.put(Resource.GRAIN, 2);
        Resource missingTown = firstMissingResource(player, town);
        if (missingTown != null) {
            return missingTown;
        }

        Map<Resource, Integer> road = new LinkedHashMap<>();
        road.put(Resource.WOOD, 1);
        road.put(Resource.BRICK, 1);
        return firstMissingResource(player, road);
    }

    private Player opponentWithResource(List<Player> players, int playerId, Resource resource) {
        return players.stream()
                .filter(player -> player.getId() != playerId)
                .filter(player -> resourceCount(player, resource) > 0)
                .max(Comparator.comparingInt(player -> resourceCount(player, resource)))
                .orElse(null);
    }

    private Resource offeredResource(Player player, Resource wanted, Map<Resource, Integer> target, int amount) {
        Resource best = null;
        int bestSurplus = Integer.MIN_VALUE;
        List<Resource> preference = wanted == Resource.BRICK
                ? List.of(Resource.WOOD, Resource.WOOL, Resource.GRAIN, Resource.ORE)
                : List.of(Resource.WOOL, Resource.WOOD, Resource.BRICK, Resource.GRAIN, Resource.ORE);
        for (Resource resource : preference) {
            if (resource == wanted || resourceCount(player, resource) < amount) {
                continue;
            }
            int surplus = resourceCount(player, resource) - target.getOrDefault(resource, 0);
            if (surplus > bestSurplus) {
                best = resource;
                bestSurplus = surplus;
            }
        }
        return best;
    }

    private Resource resourceWithAtLeast(Player player, int amount, Resource except) {
        return List.of(Resource.WOOL, Resource.WOOD, Resource.BRICK, Resource.GRAIN, Resource.ORE).stream()
                .filter(resource -> resource != except)
                .filter(resource -> resourceCount(player, resource) >= amount)
                .findFirst()
                .orElse(null);
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
        if (!isMainTurn() || !isUserControlledCurrentPlayer() || currentPlayerId == null) {
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

    private int resourceCount(Player player, Resource resource) {
        if (player == null || player.getResources() == null) {
            return 0;
        }
        return player.getResources().getOrDefault(resource, 0);
    }

    private Resource parseResource(String label) {
        for (Resource resource : Resource.values()) {
            if (resource.getDisplayName().equalsIgnoreCase(label) || resource.name().equalsIgnoreCase(label)) {
                return resource;
            }
        }
        throw new GameActionException(HttpStatus.BAD_REQUEST, "Unknown resource: " + label + ".");
    }

    private int playerNumber(int playerId) {
        int index = playerIds.indexOf(playerId);
        return index < 0 ? playerId : index + 1;
    }

    private String amountLabel(int amount, Resource resource) {
        return amount + " " + resource.getDisplayName();
    }
}
