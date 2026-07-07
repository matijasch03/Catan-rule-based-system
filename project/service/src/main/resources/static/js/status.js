import { statusEl } from "./dom.js";
import { isMainTurn, isUserControlledMainTurn, parsePhase, parseTurnPhase } from "./phase.js?v=20260707-turn-steps";
import { state } from "./state.js";

export function setStatus(text) {
  statusEl.textContent = text;
}

export function describePhase() {
  if (!state.game || state.game.phase === "IDLE") {
    setStatus(state.autoOpponents
      ? "Press \u201CNew Game\u201D: players 1 and 2 are placed automatically, then it\u2019s your turn."
      : "Press \u201CNew Game\u201D: you place villages and roads for all three players.");
    return;
  }

  if (state.game.phase === "DONE") {
    const winner = (state.game.players || []).find((player) => player.winner);
    const name = winner
      ? (winner === state.game.players[state.game.players.length - 1]
        ? "You have"
        : `Player ${state.game.players.indexOf(winner) + 1} has`)
      : "A player has";
    setStatus(`${name} won with 10 victory points! Press \u201CNew Game\u201D to play again.`);
    return;
  }

  const turn = parseTurnPhase();
  if (turn) {
    const message = state.game.turnMessage || "";
    if (turn.stage === "READY") {
      setStatus(`${message} Press “Roll dice” to resolve dice and resources.`);
      return;
    }
    if (state.buildMode === "ROAD") {
      setStatus("Choose one highlighted road connected to your road, village, or town.");
      return;
    }
    if (state.buildMode === "VILLAGE") {
      setStatus("Choose one highlighted free spot connected by at least two of your roads.");
      return;
    }
    if (state.buildMode === "TOWN") {
      setStatus("Choose one highlighted village to upgrade into a town.");
      return;
    }
    if (turn.stage === "BUILT") {
      setStatus(`${message} Press “Next player” to continue.`);
      return;
    }
    if (state.game.lastDiceSum === 7) {
      setStatus(`${message} ${turn.player === 3 || !state.autoOpponents ? "You can build or end the turn." : "Press “Simulate build” for this opponent."}`);
      return;
    }
    if (isMainTurn() && !isUserControlledMainTurn()) {
      setStatus(`${message} Press “Simulate build” for this opponent.`);
      return;
    }
    setStatus(`${message} You can build, or end the turn.`);
    return;
  }

  const info = parsePhase();
  if (!info) return;

  const who = !state.autoOpponents && info.player !== 3 ? `Player ${info.player}` : "you";
  const whoCap = who === "you" ? "You" : who;
  const second = info.round === 2;
  if (state.selectedNodeId == null) {
    const roundTxt = second
      ? "(round 2, reverse order) \u2013 place a SECOND village; it earns one resource per neighbouring tile."
      : "(round 1) \u2013 place a village.";
    setStatus(`${whoCap} ${roundTxt} Click a free spot (circle).`);
  } else {
    setStatus(`${second ? "Second village" : "Village"} on node ${state.selectedNodeId} for ${who}. Now click one of the highlighted roads next to it.`);
  }
}
