export const boardEl = document.getElementById("board");
export const statusEl = document.getElementById("status");
export const playersEl = document.getElementById("players");
export const handCardsEl = document.getElementById("hand-cards");
export const reloadBtn = document.getElementById("reload-btn");
export const newGameBtn = document.getElementById("newgame-btn");
export const cepScenarioBtn = document.getElementById("cep-scenario-btn");
export const cepToggleBtn = document.getElementById("cep-toggle-btn");
export const endTurnBtn = document.getElementById("end-turn-btn");
export const dicePanelEl = document.getElementById("dice-panel");
export const diceRollsEl = document.getElementById("dice-rolls");
export const adviceColumnEl = document.getElementById("advice-column");
export const advicePanelEl = document.getElementById("advice-panel");
export const adviceListEl = document.getElementById("advice-list");
export const goalPanelEl = document.getElementById("goal-panel");
export const goalListEl = document.getElementById("goal-list");
export const buildPanelEl = document.getElementById("build-panel");
export const buildButtons = Array.from(document.querySelectorAll("[data-action]"));

export function selectedOpponentsMode() {
  const picked = document.querySelector('input[name="opponents"]:checked');
  return !picked || picked.value === "computer";
}
