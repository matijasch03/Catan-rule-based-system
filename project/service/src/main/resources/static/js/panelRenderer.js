import { CARD_COLOR_BY_LABEL, ICON_BY_LABEL } from "./constants.js";
import {
  adviceListEl,
  advicePanelEl,
  dicePanelEl,
  diceRollsEl,
  endTurnBtn,
  handCardsEl,
  playersEl,
} from "./dom.js";
import { isMainTurn } from "./phase.js";
import { state } from "./state.js";

export function renderPanel(selectNode) {
  renderPlayers();
  renderDiceRolls();
  renderAdvice(selectNode);
  endTurnBtn.hidden = !isMainTurn();
}

export function renderHand() {
  handCardsEl.innerHTML = "";
  const you = state.game && state.game.players ? state.game.players[state.game.players.length - 1] : null;
  const tally = (you && you.resources) || {};
  const cards = resourceCards(tally);

  if (!cards.length) {
    const empty = document.createElement("p");
    empty.className = "hand-empty";
    empty.textContent = "No cards yet \u2013 matching dice rolls will add them here.";
    handCardsEl.appendChild(empty);
    return;
  }

  for (const label of cards) {
    handCardsEl.appendChild(createCard(label));
  }
}

function renderPlayers() {
  playersEl.innerHTML = "";
  const oppLabel = state.autoOpponents ? "computer" : "you";
  const labels = [`Player 1 (${oppLabel})`, `Player 2 (${oppLabel})`, "You (Player 3)"];

  (state.game ? state.game.players : []).forEach((player, index) => {
    const item = document.createElement("li");

    const swatch = document.createElement("span");
    swatch.className = "swatch";
    swatch.style.background = player.color;
    item.appendChild(swatch);

    const name = document.createElement("span");
    name.className = "player-name";
    name.textContent = labels[index] || `Player ${player.id}`;
    item.appendChild(name);

    const score = document.createElement("strong");
    score.className = "player-score";
    score.textContent = `${player.score} VP`;
    score.title = player.longestRoad
      ? `Longest road: ${player.longestRoadLength} (+2 VP)`
      : `Longest road: ${player.longestRoadLength}`;
    item.appendChild(score);

    if (player.winner) item.classList.add("winner");
    if (state.game.currentPlayerId === player.id) item.classList.add("active");

    item.appendChild(resourceTally(player.resources || {}));
    playersEl.appendChild(item);
  });
}

function resourceTally(resources) {
  const tally = document.createElement("span");
  tally.className = "res-tally";
  const entries = Object.entries(resources);
  tally.textContent = entries.length
    ? entries.map(([label, count]) => `${ICON_BY_LABEL[label] || label}${count}`).join(" ")
    : "\u2013";
  return tally;
}

function renderDiceRolls() {
  const rolls = (state.game && state.game.diceRolls) || [];
  dicePanelEl.hidden = rolls.length === 0;
  diceRollsEl.innerHTML = "";

  for (const roll of rolls) {
    const item = document.createElement("div");
    item.className = "dice-roll";

    const player = document.createElement("span");
    player.className = "dice-player";
    player.textContent = roll.playerNumber === 3 ? "You" : `Player ${roll.playerNumber}`;
    item.appendChild(player);

    for (const value of [roll.dieOne, roll.dieTwo]) {
      const die = document.createElement("span");
      die.className = "die";
      die.textContent = value;
      item.appendChild(die);
    }

    const sum = document.createElement("strong");
    sum.className = "dice-sum";
    sum.textContent = `= ${roll.sum}`;
    item.appendChild(sum);
    diceRollsEl.appendChild(item);
  }
}

function renderAdvice(selectNode) {
  const advices = (state.game && state.game.advices) || [];
  advicePanelEl.hidden = advices.length === 0;
  adviceListEl.innerHTML = "";

  for (const advice of advices) {
    const item = document.createElement("li");
    const button = document.createElement("button");
    button.type = "button";
    button.className = `advice advice-${advice.rank}`;
    button.innerHTML = `<strong>Node ${advice.nodeId}</strong><span>Score ${advice.score}</span>`;
    button.title = advice.description;
    button.addEventListener("click", () => selectNode(advice.nodeId));
    item.appendChild(button);
    adviceListEl.appendChild(item);
  }
}

function resourceCards(tally) {
  const cards = [];
  for (const [label, count] of Object.entries(tally)) {
    for (let i = 0; i < count; i++) {
      cards.push(label);
    }
  }
  return cards;
}

function createCard(label) {
  const card = document.createElement("div");
  card.className = "card";
  card.style.setProperty("--card-accent", CARD_COLOR_BY_LABEL[label] || "#7f99ab");
  card.title = label;

  const icon = document.createElement("span");
  icon.className = "card-icon";
  icon.textContent = ICON_BY_LABEL[label] || "?";
  card.appendChild(icon);

  const name = document.createElement("span");
  name.className = "card-name";
  name.textContent = label;
  card.appendChild(name);

  return card;
}
