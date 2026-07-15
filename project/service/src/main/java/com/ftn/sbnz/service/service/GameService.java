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
    private static final int CEP_ROUTE_NODE_COUNT = 8;
    private static final int[][] CEP_SCRIPT_DICE = {
            {4, 4}, {3, 1}, {5, 4},
            {3, 3}, {5, 5}, {2, 3},
            {4, 4}, {6, 4}, {5, 3},
    };

    private final NodeService nodeService;
    private final EdgeService edgeService;
    private final HexagonService hexagonService;
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
    private boolean cepScriptMode = false;
    private int cepScriptStep = 0;
    private List<Integer> cepPlayerOneNode34To23Path = List.of();
    private Integer cepPlayerOneNode23RoadTurn = null;

    public GameService(NodeService nodeService, EdgeService edgeService, HexagonService hexagonService,
                       PlayerService playerService,
                       PlacementAdviceService placementAdviceService, ScoringService scoringService,
                       BuildActionService buildActionService, GoalPlanningService goalPlanningService,
                       AdviceService adviceService, SynergyPairService synergyPairService) {
        this.nodeService = nodeService;
        this.edgeService = edgeService;
        this.hexagonService = hexagonService;
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

    public synchronized BoardStateDto cepScenario() {
        resetBoard();
        createPlayers();
        applyCepScenarioTiles();
        autoOpponents = true;

        Player playerOne = playerService.getById(playerIds.get(0)).orElseThrow();
        Player playerTwo = playerService.getById(playerIds.get(1)).orElseThrow();
        Player me = playerService.getById(playerIds.get(2)).orElseThrow();

        List<Node> route = plannedCepRoute();
        if (route.size() < CEP_ROUTE_NODE_COUNT) {
            throw new GameActionException(HttpStatus.CONFLICT, "CEP scenario cannot be created on the current board topology.");
        }

        placeSettlement(route.get(0), me, Settlement.VILLAGE);
        placeSettlement(route.get(route.size() - 1), me, Settlement.VILLAGE);
        for (int i = 0; i < 3 && i + 1 < route.size(); i++) {
            placeRoad(edgeBetween(route.get(i), route.get(i + 1)), me);
        }

        createScenarioGoalPair(route);
        placeProductionSettlements(playerOne, playerTwo, me);
        forcePlayerOneNode12Village(playerOne);
        setupPlayerOneNode34To23Path(playerOne);

        List<Node> fastBlocker = approachPath(route, 5, 1);
        List<Node> slowBlocker = approachPath(route, 6, 2);
        if (fastBlocker.size() >= 1) {
            placeSettlement(fastBlocker.get(0), playerOne, Settlement.TOWN);
        }
        if (slowBlocker.size() >= 1) {
            placeSettlement(slowBlocker.get(0), playerTwo, Settlement.VILLAGE);
        }

        addResources(playerOne, Map.of(Resource.GRAIN, 1, Resource.WOOL, 1));
        addResources(playerTwo, Map.of(Resource.WOOD, 1, Resource.BRICK, 1, Resource.WOOL, 1));
        addResources(me, Map.of(Resource.WOOD, 1, Resource.GRAIN, 2, Resource.ORE, 2, Resource.WOOL, 1));

        step = STEPS.length;
        currentPlayerTurnIndex = 2;
        currentPlayerId = me.getId();
        turnSequence = 1;
        lastDiceSum = 0;
        cepScriptMode = true;
        cepScriptStep = 0;

        tradeAttempted = false;
        tradeRefused = false;
        phase = turnPhase("ROLLED");
        turnMessage = "CEP script ready: three deterministic rounds will play automatically, with scripted dice and no random opponent turns.";
        return buildState();
    }

    public synchronized BoardStateDto cepScenarioStep() {
        if (!cepScriptMode || playerIds.size() < 3) {
            return buildState();
        }
        Player playerOne = playerService.getById(playerIds.get(0)).orElseThrow();
        Player playerTwo = playerService.getById(playerIds.get(1)).orElseThrow();
        Player me = playerService.getById(playerIds.get(2)).orElseThrow();
        currentPlayerTurnIndex = 2;
        currentPlayerId = me.getId();
        phase = turnPhase("ROLLED");

        List<Node> route = plannedRouteNodes(me.getId());
        if (route.size() < CEP_ROUTE_NODE_COUNT) {
            cepScriptMode = false;
            phase = "CEP_DONE";
            turnMessage = "CEP script stopped: planned route is no longer available.";
            return buildState();
        }
        if (cepScriptStep >= CEP_SCRIPT_DICE.length) {
            cepScriptMode = false;
            phase = "CEP_DONE";
            turnMessage = "CEP script finished: each player took three scripted turns, the warnings arrived before the cut, and your route stayed open.";
            return buildState();
        }

        List<Node> fastBlocker = approachPath(route, 5, 1);
        List<Node> slowBlocker = approachPath(route, 6, 2);
        Set<Integer> protectedRoute = route.stream().map(Node::getId).collect(java.util.stream.Collectors.toSet());
        fastBlocker.forEach(node -> protectedRoute.add(node.getId()));
        slowBlocker.forEach(node -> protectedRoute.add(node.getId()));
        scriptedCepDice(cepScriptStep);
        switch (cepScriptStep) {
            case 0 -> {
                scriptedEconomyRoad(playerOne, protectedRoute,
                        "Round 1/3 - Player 1 builds from the town-side economy first. The town on wood/brick means this player can threaten roads quickly later.");
            }
            case 1 -> {
                scriptedEconomyRoad(playerTwo, protectedRoute,
                        "Round 1/3 - Player 2 expands more slowly and asks for a trade setup, but has only villages on wood/brick.");
            }
            case 2 -> {
                scriptedMyRoad(me, route, 3,
                        "Round 1/3 - You extend the planned longest-road corridor. Regular goal advice stays visible while the CEP script controls the move.");
            }
            case 3 -> {
                scriptedPlayerOneNode23Road(playerOne);
                tradeSignals.add(new TradeSignal(playerOne.getId(), Resource.BRICK, Resource.WOOL, turnSequence, false,
                        resourceWeight(Resource.BRICK) * 0.4));
                turnMessage = "Round 2/3 - Player 1 builds the missing road out of node 23, linking the prepared 34-23 pressure route. He offers Wool for your Brick, but you refuse.";
            }
            case 4 -> {
                scriptedOpponentRoad(playerTwo, slowBlocker, 0, "Player 2 starts the slower cut and asks for a trade, but the trade fails.");
                tradeSignals.add(new TradeSignal(playerTwo.getId(), Resource.WOOD, turnSequence, false,
                        resourceWeight(Resource.WOOD) * 0.4));
                turnMessage = "Round 2/3 - Player 2 starts a slower cut and asks for a wood trade. The trade fails, so this threat is weaker but still counted.";
            }
            case 5 -> {
                scriptedMyRoad(me, route, 4,
                        "Round 2/3 - You follow the blockade warning and claim the contested road before Player 1 can cut it.");
            }
            case 6 -> {
                scriptedOpponentRoad(playerOne, fastBlocker, 0, "Player 1 tries the next approach segment, but your earlier road already protects the cut point.");
                turnMessage = "Round 3/3 - Player 1 keeps pushing from the stronger town economy after the node 23 road and failed Wool-for-Brick trade.";
            }
            case 7 -> {
                scriptedOpponentRoad(playerTwo, slowBlocker, 1, "Player 2 tries to continue the slower cut.");
                tradeSignals.add(new TradeSignal(playerTwo.getId(), Resource.BRICK, turnSequence, false,
                        resourceWeight(Resource.BRICK) * 0.4));
                turnMessage = "Round 3/3 - Player 2 continues the distant pressure, but another failed trade keeps the blockade risk below Player 1's.";
            }
            case 8 -> {
                scriptedMyRoad(me, route, 5,
                        "Round 3/3 - You place the next route road and the planned longest-road corridor stays open.");
            }
            default -> {
                cepScriptMode = false;
                phase = "CEP_DONE";
                turnMessage = "CEP script finished: each player took three scripted turns, the warnings arrived before the cut, and your route stayed open.";
                return buildState();
            }
        }
        cepScriptStep++;
        turnSequence++;
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

        diceResourceNodes.clear();
        if (lastDiceSum == 7) {
            discardForSeven();
            appendDiceRoll(currentPlayerId, currentPlayerTurnIndex + 1, dice1, dice2,
                    List.of("No resources: robber rolled."));
            return;
        }
        Map<Integer, List<String>> gained = distributeDiceResources(lastDiceSum);
        appendDiceRoll(currentPlayerId, currentPlayerTurnIndex + 1, dice1, dice2,
                resourceGainSummary(gained));
    }

    private void scriptedCepDice(int scriptStep) {
        int[] dice = CEP_SCRIPT_DICE[scriptStep];
        int playerIndex = scriptStep % playerIds.size();
        int rollingPlayerId = playerIds.get(playerIndex);
        lastDiceSum = dice[0] + dice[1];
        diceResourceNodes.clear();
        if (lastDiceSum == 7) {
            discardForSeven();
            appendDiceRoll(rollingPlayerId, playerIndex + 1, dice[0], dice[1],
                    List.of("No resources: robber rolled."));
            return;
        }
        Map<Integer, List<String>> gained = distributeDiceResources(lastDiceSum);
        appendDiceRoll(rollingPlayerId, playerIndex + 1, dice[0], dice[1],
                resourceGainSummary(gained));
    }

    private void appendDiceRoll(int playerId, int playerNumber, int dice1, int dice2, List<String> resourceSummary) {
        diceRolls.add(new DiceRollDto(playerId, playerNumber, dice1, dice2, resourceSummary));
        int maxRolls = cepScriptMode ? CEP_SCRIPT_DICE.length : playerIds.size();
        while (diceRolls.size() > maxRolls) {
            diceRolls.remove(0);
        }
    }

    private List<String> resourceGainSummary(Map<Integer, List<String>> gainsByPlayer) {
        if (gainsByPlayer.isEmpty()) {
            return List.of("No resources gained.");
        }
        List<String> summary = new ArrayList<>();
        for (int i = 0; i < playerIds.size(); i++) {
            int playerId = playerIds.get(i);
            List<String> gains = gainsByPlayer.getOrDefault(playerId, List.of());
            if (gains.isEmpty()) {
                continue;
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String gain : gains) {
                counts.put(gain, counts.getOrDefault(gain, 0) + 1);
            }
            String resources = counts.entrySet().stream()
                    .map(entry -> entry.getKey() + " x" + entry.getValue())
                    .collect(java.util.stream.Collectors.joining(", "));
            summary.add(playerLabel(i + 1) + ": " + resources);
        }
        return summary.isEmpty() ? List.of("No resources gained.") : summary;
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

    private Map<Integer, List<String>> distributeDiceResources(int diceSum) {
        Map<Integer, List<String>> gainsByPlayer = new HashMap<>();
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
                    gainsByPlayer.computeIfAbsent(owner.getId(), ignored -> new ArrayList<>())
                            .add(field.getDisplayName());
                }
                diceResourceNodes.put(node.getId(), gained);
            }
        }
        return gainsByPlayer;
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
        placeSettlement(node, player, Settlement.VILLAGE);
    }

    private void placeSettlement(Node node, Player player, Settlement settlement) {
        if (node == null || player == null) {
            return;
        }
        node.setSettlement(settlement);
        node.setOwner(player);
        nodeService.updateById(node.getId(), node);
    }

    private void placeRoad(Edge edge, Player player) {
        if (edge == null || player == null) {
            return;
        }
        edge.setOwner(player);
        edgeService.updateById(edge.getId(), edge);
        scoringService.recordRoadBuilt(player.getId());
    }

    private void applyCepScenarioTiles() {
        Resource[] fields = {
                Resource.ORE, Resource.WOOD, Resource.BRICK,
                Resource.GRAIN, Resource.WOOD, Resource.WOOL, Resource.BRICK,
                Resource.ORE, Resource.GRAIN, Resource.DESERT, Resource.WOOD, Resource.WOOL,
                Resource.BRICK, Resource.GRAIN, Resource.WOOD, Resource.ORE,
                Resource.WOOL, Resource.BRICK, Resource.GRAIN
        };
        int[] dots = {5, 8, 6, 9, 4, 10, 3, 11, 5, 0, 8, 6, 10, 4, 9, 3, 11, 2, 12};
        List<Hexagon> hexes = hexagonService.getAll().stream()
                .sorted(Comparator.comparingInt(Hexagon::getId))
                .toList();
        for (int i = 0; i < hexes.size() && i < fields.length; i++) {
            Hexagon hex = hexes.get(i);
            hex.setField(fields[i]);
            hex.setDots(dots[i]);
            hexagonService.create(hex);
        }
    }

    private List<Node> plannedCepRoute() {
        Map<Integer, List<Edge>> graph = edgesByNode();
        List<Node> nodes = nodeService.getAll().stream()
                .sorted(Comparator.comparingInt(Node::getId))
                .toList();
        for (Node start : nodes) {
            List<Integer> path = new ArrayList<>();
            if (findSimplePath(start.getId(), CEP_ROUTE_NODE_COUNT, graph, new HashSet<>(), path)) {
                Map<Integer, Node> byId = nodesById();
                return path.stream().map(byId::get).toList();
            }
        }
        return List.of();
    }

    private boolean findSimplePath(int nodeId, int targetSize, Map<Integer, List<Edge>> graph,
                                   Set<Integer> used, List<Integer> path) {
        used.add(nodeId);
        path.add(nodeId);
        if (path.size() == targetSize) {
            return true;
        }
        List<Edge> edges = new ArrayList<>(graph.getOrDefault(nodeId, List.of()));
        edges.sort(Comparator.comparingInt(Edge::getId));
        for (Edge edge : edges) {
            int next = otherNodeId(edge, nodeId);
            if (!used.contains(next) && findSimplePath(next, targetSize, graph, used, path)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        used.remove(nodeId);
        return false;
    }

    private void createScenarioGoalPair(List<Node> route) {
        SynergyPair pair = new SynergyPair();
        pair.setNode1(route.get(0));
        pair.setNode2(route.get(route.size() - 1));
        pair.setDistance(route.size() - 1);
        pair.setScore(200);
        pair.setRouteNodes(new ArrayList<>(route));
        pair.setCheckPoints(route.stream().skip(3).limit(3).toList());
        synergyPairService.create(pair);
    }

    private void placeProductionSettlements(Player playerOne, Player playerTwo, Player me) {
        Set<Integer> reserved = new HashSet<>(plannedRouteNodeIds(me.getId()));
        Node playerOneTown = bestRoadProductionNode(reserved);
        placeSettlement(playerOneTown, playerOne, Settlement.TOWN);
        reserved.add(playerOneTown.getId());

        Node playerOneVillage = bestRoadProductionNode(reserved);
        placeSettlement(playerOneVillage, playerOne, Settlement.VILLAGE);
        reserved.add(playerOneVillage.getId());

        Node playerTwoVillage = bestRoadProductionNode(reserved);
        placeSettlement(playerTwoVillage, playerTwo, Settlement.VILLAGE);
    }

    private void forcePlayerOneNode12Village(Player playerOne) {
        Node node12 = nodesById().get(12);
        if (node12 != null && node12.getOwner() != null
                && node12.getOwner().getId() == playerOne.getId()) {
            placeSettlement(node12, playerOne, Settlement.VILLAGE);
        }
    }

    private Node bestRoadProductionNode(Set<Integer> reserved) {
        return nodeService.getAll().stream()
                .filter(node -> !reserved.contains(node.getId()))
                .filter(node -> node.getSettlement() == null)
                .max(Comparator
                        .comparingDouble((Node node) -> roadResourceProduction(node) + settlementSpreadBonus(node, reserved))
                        .thenComparingInt(Node::getId))
                .orElseThrow(() -> new GameActionException(HttpStatus.CONFLICT, "No free node for CEP scenario."));
    }

    private double roadResourceProduction(Node node) {
        double score = 0.0;
        for (Hexagon hex : node.getAdjacentHexagons()) {
            if (hex.getField() == Resource.WOOD || hex.getField() == Resource.BRICK) {
                score += diceWeight(hex.getDots());
            }
        }
        return score;
    }

    private double settlementSpreadBonus(Node node, Set<Integer> reserved) {
        return reserved.contains(node.getId()) ? -100.0 : 0.0;
    }

    private List<Node> approachPath(List<Node> route, int targetIndex, int distanceToRoute) {
        Set<Integer> routeIds = route.stream().map(Node::getId).collect(java.util.stream.Collectors.toSet());
        Node target = route.get(Math.min(targetIndex, route.size() - 1));
        List<Integer> path = new ArrayList<>();
        if (findApproachPath(target.getId(), distanceToRoute + 1, routeIds, new HashSet<>(), path)) {
            Collections.reverse(path);
            Map<Integer, Node> nodes = nodesById();
            return path.stream().map(nodes::get).toList();
        }
        return List.of();
    }

    private boolean findApproachPath(int nodeId, int remainingEdges, Set<Integer> routeIds,
                                     Set<Integer> used, List<Integer> reversePath) {
        used.add(nodeId);
        reversePath.add(nodeId);
        if (remainingEdges == 0) {
            return true;
        }
        List<Edge> edges = new ArrayList<>(edgesByNode().getOrDefault(nodeId, List.of()));
        edges.sort(Comparator.comparingInt(Edge::getId).reversed());
        for (Edge edge : edges) {
            int next = otherNodeId(edge, nodeId);
            if (used.contains(next) || routeIds.contains(next)) {
                continue;
            }
            if (findApproachPath(next, remainingEdges - 1, routeIds, used, reversePath)) {
                return true;
            }
        }
        reversePath.remove(reversePath.size() - 1);
        used.remove(nodeId);
        return false;
    }

    private Edge edgeBetween(Node first, Node second) {
        if (first == null || second == null) {
            return null;
        }
        return edgeService.getAll().stream()
                .filter(edge -> (edge.getNode1().getId() == first.getId() && edge.getNode2().getId() == second.getId())
                        || (edge.getNode1().getId() == second.getId() && edge.getNode2().getId() == first.getId()))
                .findFirst()
                .orElse(null);
    }

    private void addResources(Player player, Map<Resource, Integer> resources) {
        for (Map.Entry<Resource, Integer> entry : resources.entrySet()) {
            player.addResource(entry.getKey(), entry.getValue());
        }
        playerService.create(player);
    }

    private void setupPlayerOneNode34To23Path(Player playerOne) {
        cepPlayerOneNode34To23Path = shortestNodePath(34, 23);
        cepPlayerOneNode23RoadTurn = null;
        if (cepPlayerOneNode34To23Path.size() < 2) {
            return;
        }

        Map<Integer, Node> nodes = nodesById();
        Node anchor = nodes.get(34);
        if (anchor != null && anchor.getSettlement() == null) {
            placeSettlement(anchor, playerOne, Settlement.TOWN);
        }

        for (int i = 0; i + 1 < cepPlayerOneNode34To23Path.size(); i++) {
            int first = cepPlayerOneNode34To23Path.get(i);
            int second = cepPlayerOneNode34To23Path.get(i + 1);
            if (first == 23 || second == 23) {
                continue;
            }
            Edge edge = edgeBetween(nodes.get(first), nodes.get(second));
            if (edge != null && edge.getOwner() == null) {
                placeRoad(edge, playerOne);
            }
        }
    }

    private void scriptedPlayerOneNode23Road(Player playerOne) {
        if (cepPlayerOneNode34To23Path.size() < 2) {
            turnMessage = "Player 1 cannot find the prepared node 34 to node 23 route.";
            return;
        }
        Map<Integer, Node> nodes = nodesById();
        for (int i = 0; i + 1 < cepPlayerOneNode34To23Path.size(); i++) {
            int first = cepPlayerOneNode34To23Path.get(i);
            int second = cepPlayerOneNode34To23Path.get(i + 1);
            if (first != 23 && second != 23) {
                continue;
            }
            Edge edge = edgeBetween(nodes.get(first), nodes.get(second));
            if (edge == null || edge.getOwner() != null) {
                return;
            }
            int beforeDistance = routeDistanceFor(playerOne.getId());
            placeRoad(edge, playerOne);
            recordRoadBuild(playerOne.getId(), edge.getId(), beforeDistance);
            cepPlayerOneNode23RoadTurn = turnSequence;
            return;
        }
    }

    private List<Integer> shortestNodePath(int startNodeId, int endNodeId) {
        Map<Integer, List<Edge>> graph = edgesByNode();
        ArrayDeque<Integer> frontier = new ArrayDeque<>();
        Map<Integer, Integer> previous = new HashMap<>();
        Set<Integer> visited = new HashSet<>();
        frontier.add(startNodeId);
        visited.add(startNodeId);

        while (!frontier.isEmpty()) {
            int current = frontier.remove();
            if (current == endNodeId) {
                break;
            }
            List<Edge> edges = new ArrayList<>(graph.getOrDefault(current, List.of()));
            edges.sort(Comparator.comparingInt(Edge::getId));
            for (Edge edge : edges) {
                int next = otherNodeId(edge, current);
                if (!visited.add(next)) {
                    continue;
                }
                previous.put(next, current);
                frontier.add(next);
            }
        }

        if (!visited.contains(endNodeId)) {
            return List.of();
        }
        List<Integer> path = new ArrayList<>();
        int current = endNodeId;
        path.add(current);
        while (current != startNodeId) {
            current = previous.get(current);
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }

    private void scriptedOpponentRoad(Player player, List<Node> path, int edgeIndex, String fallbackMessage) {
        if (path.size() <= edgeIndex + 1) {
            turnMessage = fallbackMessage;
            return;
        }
        Edge edge = edgeBetween(path.get(edgeIndex), path.get(edgeIndex + 1));
        if (edge == null || edge.getOwner() != null) {
            turnMessage = fallbackMessage;
            return;
        }
        int beforeDistance = routeDistanceFor(player.getId());
        placeRoad(edge, player);
        recordRoadBuild(player.getId(), edge.getId(), beforeDistance);
    }

    private void scriptedEconomyRoad(Player player, Set<Integer> avoidNodeIds, String message) {
        Set<Integer> network = networkNodeIds(player.getId());
        for (Edge edge : edgeService.getAll().stream().sorted(Comparator.comparingInt(Edge::getId)).toList()) {
            if (edge.getOwner() != null) {
                continue;
            }
            int first = edge.getNode1().getId();
            int second = edge.getNode2().getId();
            if (!network.contains(first) && !network.contains(second)) {
                continue;
            }
            if (avoidNodeIds.contains(first) || avoidNodeIds.contains(second)) {
                continue;
            }
            placeRoad(edge, player);
            break;
        }
        turnMessage = message;
    }

    private void scriptedMyRoad(Player me, List<Node> route, int edgeIndex, String message) {
        if (route.size() <= edgeIndex + 1) {
            turnMessage = message;
            return;
        }
        Edge edge = edgeBetween(route.get(edgeIndex), route.get(edgeIndex + 1));
        if (edge != null && edge.getOwner() == null) {
            placeRoad(edge, me);
            placementAdviceService.updatePersistedRoutes(me.getId());
        }
        turnMessage = message;
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
        cepScriptMode = false;
        cepScriptStep = 0;
        cepPlayerOneNode34To23Path = List.of();
        cepPlayerOneNode23RoadTurn = null;
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
        if (!cepScriptMode && currentPlayerId != null && isUserControlledCurrentPlayer()) {
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
        enrichCepAdvice(advice, player, players);
        if (!cepScriptMode) {
            addConcreteTradeSuggestions(advice, player, players);
        }
        return advice.stream()
                .limit(10)
                .toList();
    }

    private void enrichCepAdvice(List<GoalAdviceDto> advice, Player me, List<Player> players) {
        for (int i = 0; i < advice.size(); i++) {
            GoalAdviceDto item = advice.get(i);
            if (!isCepAdvice(item) || item.getNodeId() == null) {
                continue;
            }
            Player opponent = players.stream()
                    .filter(candidate -> candidate.getId() == item.getNodeId())
                    .findFirst()
                    .orElse(null);
            if (opponent == null) {
                continue;
            }
            String description = item.getDescription() + "\n\nSliding window - previous 3 steps:\n"
                    + cepApproachLine(opponent.getId()) + "\n"
                    + cepNode23Line(opponent.getId()) + "\n"
                    + cepResourcesLine("Opponent", opponent) + "\n"
                    + cepResourcesLine("You", me) + "\n"
                    + cepTradeLine(opponent.getId()) + "\n"
                    + cepRaceConclusion(me, opponent);
            advice.set(i, new GoalAdviceDto(item.getRank(), item.getTitle(), description, item.getNodeId(),
                    item.isTradeAction(), item.getTradeProposal()));
        }
    }

    private boolean isCepAdvice(GoalAdviceDto advice) {
        return "Blockade threat".equals(advice.getTitle())
                || "Watch blockade route".equals(advice.getTitle());
    }

    private String cepApproachLine(int opponentId) {
        List<RoadBuildEvent> recent = recentRoadEvents(opponentId);
        if (recent.isEmpty()) {
            return "- Approach: no road movement in the current 3-step window.";
        }
        int movedCloser = recent.stream()
                .mapToInt(event -> Math.max(0, event.getPreviousDistanceToRoute() - event.getDistanceToRoute()))
                .sum();
        RoadBuildEvent latest = recent.get(recent.size() - 1);
        return "- Approach: moved " + movedCloser + " road(s) closer from " + latest.getDirection()
                + "; now " + latest.getDistanceToRoute() + " road(s) from your planned route.";
    }

    private String cepNode23Line(int opponentId) {
        if (playerIds.isEmpty() || opponentId != playerIds.get(0) || cepPlayerOneNode34To23Path.isEmpty()) {
            return "- Node 23 route: no prepared 34-23 pressure route for this opponent.";
        }
        String path = cepPlayerOneNode34To23Path.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(" -> "));
        if (cepPlayerOneNode23RoadTurn == null) {
            return "- Node 23 route: Player 1 has prepared roads on path " + path
                    + ", but the road out of node 23 is still not built.";
        }
        int age = Math.max(0, turnSequence - cepPlayerOneNode23RoadTurn);
        return "- Node 23 route: Player 1 prepared path " + path
                + "; the missing road out of node 23 was built " + age
                + " step(s) ago inside the sliding window.";
    }

    private List<RoadBuildEvent> recentRoadEvents(int opponentId) {
        int oldestTurn = cepWindowOldestTurn();
        return roadBuildEvents.stream()
                .filter(event -> event.getPlayerId() == opponentId)
                .filter(event -> event.getTurn() >= oldestTurn)
                .sorted(Comparator.comparingInt(RoadBuildEvent::getTurn))
                .toList();
    }

    private String cepResourcesLine(String label, Player player) {
        int wood = resourceCount(player, Resource.WOOD);
        int brick = resourceCount(player, Resource.BRICK);
        int grain = resourceCount(player, Resource.GRAIN);
        int wool = resourceCount(player, Resource.WOOL);
        int ore = resourceCount(player, Resource.ORE);
        int roadCards = Math.min(wood, brick);
        return "- " + label + " resources: Wood " + wood + ", Brick " + brick + ", Grain " + grain
                + ", Wool " + wool + ", Ore " + ore + " (road-ready pairs: " + roadCards + ").";
    }

    private String cepTradeLine(int opponentId) {
        int oldestTurn = cepTradeWindowOldestTurn();
        List<TradeSignal> recent = tradeSignals.stream()
                .filter(signal -> signal.getPlayerId() == opponentId)
                .filter(signal -> signal.getTurn() >= oldestTurn)
                .sorted(Comparator.comparingInt(TradeSignal::getTurn))
                .toList();
        if (recent.isEmpty()) {
            return "- Trade: no trade request detected in this CEP window.";
        }
        return "- Trade: " + recent.stream()
                .map(signal -> tradeSignalText(signal))
                .collect(java.util.stream.Collectors.joining("; ")) + ".";
    }

    private String tradeSignalText(TradeSignal signal) {
        String outcome = signal.isSuccessful() ? "succeeded" : "failed";
        if (signal.getOfferedResource() != null) {
            return "offered " + signal.getOfferedResource().getDisplayName()
                    + " for your " + signal.getResource().getDisplayName()
                    + " and it " + outcome;
        }
        return "asked for " + signal.getResource().getDisplayName() + " and it " + outcome;
    }

    private String cepRaceConclusion(Player me, Player opponent) {
        int myWood = resourceCount(me, Resource.WOOD);
        int myBrick = resourceCount(me, Resource.BRICK);
        int opponentRoadCards = Math.min(resourceCount(opponent, Resource.WOOD), resourceCount(opponent, Resource.BRICK));
        int myRoadCards = Math.min(myWood, myBrick);
        int missingWood = Math.max(0, 1 - myWood);
        int missingBrick = Math.max(0, 1 - myBrick);
        int opponentDistance = recentRoadEvents(opponent.getId()).stream()
                .reduce((first, second) -> second)
                .map(RoadBuildEvent::getDistanceToRoute)
                .orElse(routeDistanceFor(opponent.getId()));
        String missing = missingWood == 0 && missingBrick == 0
                ? "you are not missing road resources"
                : "you are missing " + missingWood + " Wood and " + missingBrick + " Brick";

        String winner;
        if (myRoadCards > 0 && (opponentDistance > 0 || opponentRoadCards <= myRoadCards)) {
            winner = "you have the better immediate position because you can build a road now";
        } else if (myRoadCards == 0 && opponentRoadCards > 0) {
            winner = "the opponent has the better position; start trading for Wood/Brick";
        } else if (opponentRoadCards > myRoadCards) {
            winner = "the opponent has better road-resource momentum";
        } else {
            winner = "the race is close, so prioritize the critical road before spending elsewhere";
        }
        return "- Conclusion: " + winner + "; " + missing + ". Opponent is "
                + opponentDistance + " road(s) away with " + opponentRoadCards + " road-ready pair(s).";
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
        Edge builtEdge = edgeService.getAll().stream()
                .filter(edge -> edge.getId() == edgeId)
                .findFirst()
                .orElse(null);
        int currentDistance = branchDistanceAfterRoadBuild(playerId, mePlayerId, builtEdge);
        int branchPreviousDistance = branchDistanceBeforeRoadBuild(playerId, mePlayerId, builtEdge, edgeId, currentDistance);
        if (currentDistance >= UNREACHABLE_ROUTE_DISTANCE && branchPreviousDistance >= UNREACHABLE_ROUTE_DISTANCE) {
            return;
        }
        roadBuildEvents.add(new RoadBuildEvent(playerId, mePlayerId, edgeId, turnSequence,
                currentDistance, branchPreviousDistance, routeDirectionFor(playerId)));
        pruneCepWindow();
    }

    private int branchDistanceAfterRoadBuild(int playerId, int mePlayerId, Edge builtEdge) {
        if (builtEdge == null) {
            return routeDistanceFor(playerId);
        }
        int firstDistance = distanceFromNodeToPlannedRoute(builtEdge.getNode1().getId(), playerId, mePlayerId);
        int secondDistance = distanceFromNodeToPlannedRoute(builtEdge.getNode2().getId(), playerId, mePlayerId);
        return Math.min(firstDistance, secondDistance);
    }

    private int branchDistanceBeforeRoadBuild(int playerId, int mePlayerId, Edge builtEdge,
                                              int builtEdgeId, int currentDistance) {
        if (builtEdge == null) {
            return routeDistanceFor(playerId);
        }
        List<Integer> oldEndpointDistances = new ArrayList<>();
        boolean firstWasNetwork = wasNetworkNodeBeforeRoad(playerId, builtEdge.getNode1(), builtEdgeId);
        boolean secondWasNetwork = wasNetworkNodeBeforeRoad(playerId, builtEdge.getNode2(), builtEdgeId);
        if (firstWasNetwork) {
            oldEndpointDistances.add(distanceFromNodeToPlannedRoute(builtEdge.getNode1().getId(), playerId, mePlayerId, builtEdgeId));
        }
        if (secondWasNetwork) {
            oldEndpointDistances.add(distanceFromNodeToPlannedRoute(builtEdge.getNode2().getId(), playerId, mePlayerId, builtEdgeId));
        }
        if (oldEndpointDistances.isEmpty()) {
            return routeDistanceFor(playerId);
        }
        int previousBranchDistance = oldEndpointDistances.stream()
                .mapToInt(Integer::intValue)
                .min()
                .orElse(UNREACHABLE_ROUTE_DISTANCE);
        if (currentDistance < 3 && previousBranchDistance <= currentDistance) {
            return currentDistance + 1;
        }
        return Math.max(previousBranchDistance, currentDistance);
    }

    private boolean wasNetworkNodeBeforeRoad(int playerId, Node node, int builtEdgeId) {
        if (node.getOwner() != null && node.getOwner().getId() == playerId && node.getSettlement() != null) {
            return true;
        }
        for (Edge edge : edgesByNode().getOrDefault(node.getId(), List.of())) {
            if (edge.getId() != builtEdgeId && edge.getOwner() != null && edge.getOwner().getId() == playerId) {
                return true;
            }
        }
        return false;
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
        int oldestTurn = cepWindowOldestTurn();
        roadBuildEvents.removeIf(event -> event.getTurn() < oldestTurn);
        int oldestTradeTurn = cepTradeWindowOldestTurn();
        tradeSignals.removeIf(event -> event.getTurn() < oldestTradeTurn);
    }

    private int cepWindowOldestTurn() {
        return turnSequence - CEP_WINDOW_TURNS - 1;
    }

    private int cepTradeWindowOldestTurn() {
        if (cepScriptMode) {
            return turnSequence - (CEP_WINDOW_TURNS * 2) - 1;
        }
        return cepWindowOldestTurn();
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

    private int distanceFromNodeToPlannedRoute(int startNodeId, int playerId, int mePlayerId) {
        return distanceFromNodeToPlannedRoute(startNodeId, playerId, mePlayerId, null);
    }

    private int distanceFromNodeToPlannedRoute(int startNodeId, int playerId, int mePlayerId, Integer excludedEdgeId) {
        Set<Integer> targetRoute = plannedRouteNodeIds(mePlayerId);
        if (targetRoute.isEmpty()) {
            return UNREACHABLE_ROUTE_DISTANCE;
        }
        if (targetRoute.contains(startNodeId)) {
            return 0;
        }

        Map<Integer, List<Edge>> edgesByNode = edgesByNode();
        ArrayDeque<Integer> frontier = new ArrayDeque<>();
        Map<Integer, Integer> distance = new HashMap<>();
        frontier.add(startNodeId);
        distance.put(startNodeId, 0);
        while (!frontier.isEmpty()) {
            int current = frontier.remove();
            int currentDistance = distance.get(current);
            if (currentDistance >= 6) {
                continue;
            }
            for (Edge edge : edgesByNode.getOrDefault(current, List.of())) {
                if (excludedEdgeId != null && edge.getId() == excludedEdgeId) {
                    continue;
                }
                if (edge.getOwner() != null && edge.getOwner().getId() != playerId) {
                    continue;
                }
                int next = otherNodeId(edge, current);
                if (targetRoute.contains(next)) {
                    return currentDistance + 1;
                }
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
        if (cepScriptMode || !isMainTurn() || !isUserControlledCurrentPlayer() || currentPlayerId == null) {
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
