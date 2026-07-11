import {
  build,
  endTurn,
  loadGameState,
  loadHexes,
  newGame,
  offerTrade,
  placeOpeningRoad,
  reshuffleBoard,
} from "./js/api.js";
import { renderBoard } from "./js/boardRenderer.js?v=20260711-node-score-tooltip";
import { endTurnBtn, newGameBtn, reloadBtn, selectedOpponentsMode } from "./js/dom.js";
import { renderHand, renderPanel } from "./js/panelRenderer.js?v=20260709-trade-flow";
import { state } from "./js/state.js";
import { describePhase, setStatus } from "./js/status.js?v=20260707-turn-steps";

const actions = {
  selectNode,
  placeRoad,
  buildOnEdge,
  buildOnNode,
};

async function refreshAll(hexesPromise) {
  const [hexes, gameState] = await Promise.all([
    hexesPromise || loadHexes(),
    loadGameState(),
  ]);
  state.game = gameState;
  state.selectedNodeId = null;
  state.buildMode = null;
  draw(hexes);
  describePhase();
}

function draw(hexes) {
  renderBoard(hexes, actions);
  renderPanel(selectNode, startBuildAction, tradeAction);
  renderHand();
}

function busy(on) {
  reloadBtn.disabled = on;
  newGameBtn.disabled = on;
  endTurnBtn.disabled = on;
}

async function run(fn, msg) {
  busy(true);
  if (msg) setStatus(msg);
  try {
    await fn();
  } catch (err) {
    setStatus(`Error: ${err.message}`);
  } finally {
    busy(false);
  }
}

function selectNode(nodeId) {
  if (!isOpeningPlaceable(nodeId)) {
    setStatus("That spot is taken or too close to another village.");
    return;
  }
  state.buildMode = null;
  state.selectedNodeId = nodeId;
  draw(state.lastHexes);
  describePhase();
}

function isOpeningPlaceable(nodeId) {
  const game = state.game;
  if (!game || state.buildMode === "VILLAGE") return true;
  const node = (game.nodes || []).find((candidate) => candidate.id === nodeId);
  if (!node || node.settlement) return false;
  return !(game.edges || []).some((edge) => {
    if (edge.node1Id !== nodeId && edge.node2Id !== nodeId) return false;
    const otherId = edge.node1Id === nodeId ? edge.node2Id : edge.node1Id;
    const other = (game.nodes || []).find((candidate) => candidate.id === otherId);
    return other && other.settlement;
  });
}

async function placeRoad(edgeId) {
  await run(async () => {
    state.game = await placeOpeningRoad(state.selectedNodeId, edgeId);
    state.selectedNodeId = null;
    state.buildMode = null;
    draw(state.lastHexes);
    describePhase();
  }, "Placing\u2026");
}

function startBuildAction(action) {
  state.selectedNodeId = null;
  state.buildMode = state.buildMode === action ? null : action;
  draw(state.lastHexes);
  describePhase();
}

async function buildOnEdge(edgeId) {
  await buildAction(state.buildMode, { edgeId });
}

async function buildOnNode(nodeId) {
  await buildAction(state.buildMode, { nodeId });
}

async function buildAction(action, selection) {
  await run(async () => {
    state.game = await build(action, selection);
    state.buildMode = null;
    draw(state.lastHexes);
    describePhase();
  }, "Building\u2026");
}

async function tradeAction(trade) {
  await run(async () => {
    state.game = await offerTrade(trade);
    draw(state.lastHexes);
    describePhase();
  }, "Trading...");
}

newGameBtn.addEventListener("click", () =>
  run(async () => {
    state.autoOpponents = selectedOpponentsMode();
    const hexes = await loadHexes();
    state.game = await newGame(state.autoOpponents);
    state.selectedNodeId = null;
    state.buildMode = null;
    draw(hexes);
    describePhase();
  }, "Dealing\u2026")
);

reloadBtn.addEventListener("click", () =>
  run(async () => {
    const hexes = await reshuffleBoard();
    await refreshAll(Promise.resolve(hexes));
  }, "Reshuffling\u2026")
);

endTurnBtn.addEventListener("click", () =>
  run(async () => {
    state.game = await endTurn();
    state.selectedNodeId = null;
    state.buildMode = null;
    draw(state.lastHexes);
    describePhase();
  }, "Advancing\u2026")
);

run(() => refreshAll(), "Loading board\u2026");
