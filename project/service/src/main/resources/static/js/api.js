export async function getJson(url, opts) {
  const res = await fetch(url, opts);
  if (!res.ok) {
    const message = await res.text();
    throw new Error(message || `${(opts && opts.method) || "GET"} ${url} -> ${res.status}`);
  }
  return res.json();
}

export function loadHexes() {
  return getJson("/api/board");
}

export function newGame(autoOpponents) {
  return getJson("/api/game/new", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ autoOpponents }),
  });
}

export function placeOpeningRoad(nodeId, edgeId) {
  return getJson("/api/game/place", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ nodeId, edgeId }),
  });
}

export function endTurn() {
  return getJson("/api/game/endTurn", { method: "POST" });
}

export function build(action, selection = {}) {
  return getJson("/api/game/build", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action, ...selection }),
  });
}

export function offerTrade(trade) {
  return getJson("/api/game/trade", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(trade),
  });
}

export function reshuffleBoard() {
  return getJson("/api/board/reshuffle", { method: "POST" });
}

export function loadGameState() {
  return getJson("/api/game/state");
}
