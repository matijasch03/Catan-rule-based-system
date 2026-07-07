import {
  endTurn,
  loadGameState,
  loadHexes,
  newGame,
  placeOpeningRoad,
  reshuffleBoard,
} from "./js/api.js";
import { renderBoard } from "./js/boardRenderer.js";
import { endTurnBtn, newGameBtn, reloadBtn, selectedOpponentsMode } from "./js/dom.js";
import { renderHand, renderPanel } from "./js/panelRenderer.js";
import { state } from "./js/state.js";
import { describePhase, setStatus } from "./js/status.js";

const actions = {
  selectNode,
  placeRoad,
};

async function refreshAll(hexesPromise) {
  const [hexes, gameState] = await Promise.all([
    hexesPromise || loadHexes(),
    loadGameState(),
  ]);
  state.game = gameState;
  state.selectedNodeId = null;
  draw(hexes);
  describePhase();
}

function draw(hexes) {
  renderBoard(hexes, actions);
  renderPanel(selectNode);
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
  state.selectedNodeId = nodeId;
  draw(state.lastHexes);
  describePhase();
}

async function placeRoad(edgeId) {
  await run(async () => {
    state.game = await placeOpeningRoad(state.selectedNodeId, edgeId);
    state.selectedNodeId = null;
    draw(state.lastHexes);
    describePhase();
  }, "Placing\u2026");
}

newGameBtn.addEventListener("click", () =>
  run(async () => {
    state.autoOpponents = selectedOpponentsMode();
    const hexes = await loadHexes();
    state.game = await newGame(state.autoOpponents);
    state.selectedNodeId = null;
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
    draw(state.lastHexes);
    describePhase();
  }, "Playing the next turns\u2026")
);

run(() => refreshAll(), "Loading board\u2026");
