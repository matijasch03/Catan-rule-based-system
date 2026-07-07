import { state } from "./state.js";

export function isHumanTurn() {
  return parsePhase() !== null;
}

export function isMainTurn() {
  return state.game && /^TURN_P\d+_ROLLED$/.test(state.game.phase || "");
}

export function isUserControlledMainTurn() {
  const turn = parseTurnPhase();
  return isMainTurn() && turn && (!state.autoOpponents || turn.player === 3);
}

export function canAdvanceTurn() {
  const turn = parseTurnPhase();
  if (!state.game || state.game.phase === "DONE" || state.game.phase === "IDLE") {
    return false;
  }
  return Boolean(turn);
}

export function turnButtonLabel() {
  const turn = parseTurnPhase();
  if (!turn) return "Continue";
  if (turn.stage === "READY") return "Roll dice";
  if (turn.stage === "ROLLED") {
    return state.autoOpponents && turn.player !== 3 ? "Simulate build" : "End turn";
  }
  if (turn.stage === "BUILT") return "Next player";
  return "Continue";
}

export function parsePhase() {
  const match = state.game && /^R(\d)_P(\d)$/.exec(state.game.phase || "");
  return match ? { round: Number(match[1]), player: Number(match[2]) } : null;
}

export function parseTurnPhase() {
  const match = state.game && /^TURN_P(\d+)_(READY|ROLLED|BUILT)$/.exec(state.game.phase || "");
  return match ? { player: Number(match[1]), stage: match[2] } : null;
}
