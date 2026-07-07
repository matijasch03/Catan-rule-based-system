import { state } from "./state.js";

export function isHumanTurn() {
  return parsePhase() !== null;
}

export function isMainTurn() {
  return state.game && /^TURN_P\d+_ROLLED$/.test(state.game.phase || "");
}

export function parsePhase() {
  const match = state.game && /^R(\d)_P(\d)$/.exec(state.game.phase || "");
  return match ? { round: Number(match[1]), player: Number(match[2]) } : null;
}
