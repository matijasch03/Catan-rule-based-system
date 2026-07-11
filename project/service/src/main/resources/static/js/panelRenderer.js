import { CARD_COLOR_BY_LABEL, ICON_BY_LABEL } from "./constants.js";
import {
  adviceListEl,
  advicePanelEl,
  buildButtons,
  buildPanelEl,
  dicePanelEl,
  diceRollsEl,
  endTurnBtn,
  adviceColumnEl,
  goalListEl,
  goalPanelEl,
  handCardsEl,
  playersEl,
} from "./dom.js";
import { canAdvanceTurn, isUserControlledMainTurn, turnButtonLabel } from "./phase.js?v=20260707-turn-steps";
import { state } from "./state.js";

let lastHandSignature = "";
let shuffledHandCards = [];

export function renderPanel(selectNode, buildAction, tradeAction) {
  renderPlayers();
  renderDiceRolls();
  renderAdvice(selectNode);
  renderGoalAdvice(tradeAction);
  adviceColumnEl.hidden = advicePanelEl.hidden && goalPanelEl.hidden;
  renderBuildActions(buildAction);
  endTurnBtn.hidden = !canAdvanceTurn();
  endTurnBtn.textContent = turnButtonLabel();
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
    const route = adviceRoute(advice);
    const first = route[0] || advice.nodeId;
    const last = route[route.length - 1] || advice.nodeId;
    const openingPair = (advice.tags || []).includes("OpeningPair");
    button.innerHTML = openingPair
      ? `<strong>Start ${first} + ${last}</strong><span>Score ${advice.score}</span>`
      : `<strong>Nodes ${first}-${last}</strong><span>Score ${advice.score}</span>`;
    button.title = advice.description;
    button.addEventListener("click", () => selectNode(advice.nodeId));
    item.appendChild(button);

    const details = document.createElement("div");
    details.className = "advice-details";

    const nodes = document.createElement("p");
    nodes.textContent = openingPair
      ? `Opening settlements: red node ${first} first, blue node ${last} second.`
      : `Recommended settlements: node ${first} and node ${last}.`;
    details.appendChild(nodes);

    const path = document.createElement("p");
    path.textContent = `Route: ${route.join(" -> ")}.`;
    details.appendChild(path);

    const checkpoints = document.createElement("p");
    const checkpointIds = advice.checkpointNodeIds || [];
    checkpoints.textContent = checkpointIds.length
      ? `Checkpoints for later villages: ${checkpointIds.join(", ")}.`
      : "Checkpoints for later villages: none on this route.";
    details.appendChild(checkpoints);

    const reason = document.createElement("p");
    reason.textContent = advice.description;
    details.appendChild(reason);

    item.appendChild(details);
    adviceListEl.appendChild(item);
  }
}

function adviceRoute(advice) {
  const route = Array.isArray(advice.routeNodeIds)
    ? advice.routeNodeIds.filter(Boolean)
    : [];
  if (!route.length && advice.nodeId) {
    return [advice.nodeId];
  }
  if (advice.nodeId && !route.includes(advice.nodeId)) {
    return [advice.nodeId, ...route];
  }
  return route;
}

function renderGoalAdvice(tradeAction) {
  const advices = (state.game && state.game.goalAdvices) || [];
  goalPanelEl.hidden = advices.length === 0;
  goalListEl.innerHTML = "";

  for (const advice of advices) {
    const item = document.createElement("li");
    const title = document.createElement("strong");
    title.textContent = advice.title;
    item.appendChild(title);

    const description = document.createElement("p");
    description.textContent = advice.description;
    item.appendChild(description);

    if (advice.tradeAction) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "trade-advice-btn";
      const proposal = advice.tradeProposal;
      button.textContent = proposal
        ? `Trade ${proposal.offeredAmount || 1} ${proposal.offeredResource} for ${proposal.wantedResource}`
        : "Offer trade";
      button.disabled = !proposal;
      button.addEventListener("click", () => tradeAction({ title: advice.title, ...proposal }));
      item.appendChild(button);
    }
    goalListEl.appendChild(item);
  }
}

function renderBuildActions(buildAction) {
  const available = new Set((state.game && state.game.availableActions) || []);
  buildPanelEl.hidden = !isUserControlledMainTurn();

  for (const button of buildButtons) {
    const action = button.dataset.action;
    button.disabled = !available.has(action);
    button.classList.toggle("active", state.buildMode === action);
    button.onclick = () => buildAction(action);
  }
}

function resourceCards(tally) {
  const signature = JSON.stringify(Object.entries(tally).sort(([a], [b]) => a.localeCompare(b)));
  if (signature === lastHandSignature) {
    return shuffledHandCards;
  }

  lastHandSignature = signature;
  shuffledHandCards = mixedResourceCards(tally);
  return shuffledHandCards;
}

function mixedResourceCards(tally) {
  const remaining = Object.fromEntries(
    Object.entries(tally).filter(([, count]) => count > 0)
  );
  const cards = [];

  while (Object.keys(remaining).length) {
    const labels = Object.keys(remaining);
    const choices = labels.filter((label) => label !== cards[cards.length - 1]);
    const pool = choices.length ? choices : labels;
    const next = weightedPick(pool, remaining);

    cards.push(next);
    remaining[next] -= 1;
    if (remaining[next] <= 0) {
      delete remaining[next];
    }
  }

  return cards;
}

function weightedPick(labels, weights) {
  const total = labels.reduce((sum, label) => sum + weights[label], 0);
  let roll = Math.random() * total;
  for (const label of labels) {
    roll -= weights[label];
    if (roll <= 0) {
      return label;
    }
  }
  return labels[labels.length - 1];
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
