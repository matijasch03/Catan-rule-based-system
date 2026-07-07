export const boardEl = document.getElementById("board");
export const statusEl = document.getElementById("status");
export const playersEl = document.getElementById("players");
export const handCardsEl = document.getElementById("hand-cards");
export const reloadBtn = document.getElementById("reload-btn");
export const newGameBtn = document.getElementById("newgame-btn");
export const endTurnBtn = document.getElementById("end-turn-btn");
export const dicePanelEl = document.getElementById("dice-panel");
export const diceRollsEl = document.getElementById("dice-rolls");
export const advicePanelEl = document.getElementById("advice-panel");
export const adviceListEl = document.getElementById("advice-list");

export function selectedOpponentsMode() {
  const picked = document.querySelector('input[name="opponents"]:checked');
  return !picked || picked.value === "computer";
}
