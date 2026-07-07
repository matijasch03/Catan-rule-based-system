import { statusEl } from "./dom.js";
import { isMainTurn, parsePhase } from "./phase.js";
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

  if (isMainTurn()) {
    const playerNumber = Number(/^TURN_P(\d+)_ROLLED$/.exec(state.game.phase)[1]);
    const who = playerNumber === 3 ? "You rolled" : `Player ${playerNumber} rolled`;
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
    if (state.game.lastDiceSum === 7) {
      setStatus(`${who} 7. Players with more than 7 cards discarded half to the bank.`);
      return;
    }
    setStatus(`${who} ${state.game.lastDiceSum}. Resources were given to every village beside a ${state.game.lastDiceSum} tile.`);
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
