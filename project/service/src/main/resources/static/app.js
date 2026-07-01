const HEX_SIZE = 62;
const PADDING = 28;
const SVG_NS = "http://www.w3.org/2000/svg";

const RESOURCE = {
  WOOD: { icon: "\u{1F332}", label: "Wood" },
  WOOL: { icon: "\u{1F411}", label: "Wool" },
  GRAIN: { icon: "\u{1F33E}", label: "Grain" },
  BRICK: { icon: "\u{1F9F1}", label: "Brick" },
  ORE: { icon: "\u26F0\uFE0F", label: "Ore" },
  DESERT: { icon: "\u{1F3DC}\uFE0F", label: "Desert" },
};

// Resource icon keyed by its display name (as the backend sends it).
const ICON_BY_LABEL = Object.fromEntries(
  Object.values(RESOURCE).map((r) => [r.label, r.icon])
);

// Card accent colour keyed by resource display name.
const CARD_COLOR_BY_LABEL = {
  Wood: "#2f6b32",
  Wool: "#7cb342",
  Grain: "#e3b505",
  Brick: "#c1532a",
  Ore: "#7d8a96",
};

const boardEl = document.getElementById("board");
const statusEl = document.getElementById("status");
const playersEl = document.getElementById("players");
const handCardsEl = document.getElementById("hand-cards");
const reloadBtn = document.getElementById("reload-btn");
const newGameBtn = document.getElementById("newgame-btn");

let layout = null; // { minX, minY, offX, offY }
let game = null; // last BoardState from the server
let colorByPlayer = {}; // playerId -> color
let selectedNodeId = null; // node chosen by the user, awaiting a road
let lastHexes = []; // most recent hexes, so the overlay can redraw without refetching
let autoOpponents = true; // whether players 1 & 2 are computer-controlled
 
// A human turn is any non-terminal phase: the server auto-resolves computer
// players within a request and only pauses when it needs the user to place.
function isHumanTurn() {
  return game && game.phase !== "IDLE" && game.phase !== "DONE";
}
 
// Parse a phase like "R2_P1" into { round, player } (1-based); null otherwise.
function parsePhase() {
  const m = game && /^R(\d)_P(\d)$/.exec(game.phase || "");
  return m ? { round: Number(m[1]), player: Number(m[2]) } : null;
}
 
// Probability pips for a number token (higher near 6/8).
function pipsFor(n) {
  if (!n || n < 2 || n > 12) return "";
  const count = 6 - Math.abs(7 - n);
  return "\u2022".repeat(count);
}

function hexCenterPx(q, r) {
  return {
    x: HEX_SIZE * Math.sqrt(3) * (q + r / 2),
    y: HEX_SIZE * 1.5 * r,
  };
}

// Board (circumradius-1) coordinates -> screen pixels, matching the hex layout.
function toScreen(mx, my) {
  return {
    x: HEX_SIZE * mx - layout.minX + layout.offX,
    y: HEX_SIZE * my - layout.minY + layout.offY,
  };
}

function renderHexes(hexes) {
  boardEl.innerHTML = "";

  const points = hexes.map((h) => hexCenterPx(h.q, h.r));
  const minX = Math.min(...points.map((p) => p.x));
  const maxX = Math.max(...points.map((p) => p.x));
  const minY = Math.min(...points.map((p) => p.y));
  const maxY = Math.max(...points.map((p) => p.y));

  const hexW = HEX_SIZE * Math.sqrt(3);
  const hexH = HEX_SIZE * 2;
  layout = { minX, minY, offX: hexW / 2 + PADDING, offY: hexH / 2 + PADDING };

  const width = maxX - minX + hexW + PADDING * 2;
  const height = maxY - minY + hexH + PADDING * 2;
  boardEl.style.width = `${width}px`;
  boardEl.style.height = `${height}px`;

  for (const h of hexes) {
    const { x, y } = hexCenterPx(h.q, h.r);
    const res = RESOURCE[h.field] || { icon: "?", label: h.field };

    const hex = document.createElement("div");
    hex.className = `hex field-${h.field}`;
    hex.style.left = `${x - minX + layout.offX}px`;
    hex.style.top = `${y - minY + layout.offY}px`;
    hex.title = `${res.label} (q=${h.q}, r=${h.r})`;

    const icon = document.createElement("div");
    icon.className = "icon";
    icon.textContent = res.icon;
    hex.appendChild(icon);

    if (h.field !== "DESERT" && h.dots) {
      const token = document.createElement("div");
      token.className = "token" + (h.dots === 6 || h.dots === 8 ? " hot" : "");
      const number = document.createElement("span");
      number.className = "number";
      number.textContent = h.dots;
      token.appendChild(number);
      const pips = document.createElement("span");
      pips.className = "pips";
      pips.textContent = pipsFor(h.dots);
      token.appendChild(pips);
      hex.appendChild(token);
    }

    boardEl.appendChild(hex);
  }

  return { width, height };
}

function svgEl(name, attrs) {
  const el = document.createElementNS(SVG_NS, name);
  for (const [k, v] of Object.entries(attrs)) el.setAttribute(k, v);
  return el;
}

// Attach an SVG <title> (hover tooltip) to an element and return the element.
function withTitle(el, text) {
  const t = svgEl("title", {});
  t.textContent = text;
  el.appendChild(t);
  return el;
}

// House-shaped path (village/town) centred at (cx, cy).
function housePath(cx, cy, s) {
  const w = s, h = s * 0.7, roof = s * 0.55;
  const left = cx - w / 2, right = cx + w / 2, bottom = cy + h / 2, mid = cy - h / 2;
  return `M ${left} ${bottom} L ${left} ${mid} L ${cx} ${mid - roof} L ${right} ${mid} L ${right} ${bottom} Z`;
}

function renderOverlay(size) {
  const old = boardEl.querySelector("svg.overlay");
  if (old) old.remove();
  if (!game) return;

  const svg = svgEl("svg", { class: "overlay", width: size.width, height: size.height });
  const nodeById = {};
  for (const n of game.nodes) nodeById[n.id] = n;

  const isUserTurn = isHumanTurn();
  // ---- edges (base lines + roads) ----
  for (const e of game.edges) {
    const a = nodeById[e.node1Id];
    const b = nodeById[e.node2Id];
    if (!a || !b) continue;
    const p1 = toScreen(a.x, a.y);
    const p2 = toScreen(b.x, b.y);

    if (e.ownerId != null) {
      svg.appendChild(svgEl("line", {
        x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y,
        class: "road", stroke: colorByPlayer[e.ownerId] || "#fff",
      }));
      continue;
    }

    const incidentToSelected =
      selectedNodeId != null && (e.node1Id === selectedNodeId || e.node2Id === selectedNodeId);
    const line = withTitle(svgEl("line", {
      x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y,
      class: "edge" + (incidentToSelected ? " edge-selectable" : ""),
    }), `Edge ${e.id}`);
    if (incidentToSelected) {
      line.addEventListener("click", () => placeRoad(e.id));
    }
    svg.appendChild(line);
  }

  // ---- nodes (free markers + villages/towns) ----
  for (const n of game.nodes) {
    const p = toScreen(n.x, n.y);

    if (n.settlement) {
      const piece = withTitle(svgEl("path", {
        d: housePath(p.x, p.y, n.settlement === "TOWN" ? 30 : 15),
        class: "piece",
        fill: colorByPlayer[n.ownerId] || "#fff",
      }), `Node ${n.id} \u2013 ${n.settlement}`);
      svg.appendChild(piece);
      if (n.resourcesGained && n.resourcesGained.length) {
        renderResourceBadges(svg, p.x, p.y, n.resourcesGained);
      }
      continue;
    }

    const selectable = isUserTurn && selectedNodeId == null;
    const selected = n.id === selectedNodeId;
    const c = withTitle(svgEl("circle", {
      cx: p.x, cy: p.y, r: 6,
      class: "node" + (selectable ? " node-pick" : "") + (selected ? " node-selected" : ""),
    }), `Node ${n.id}`);
    if (selectable) {
      c.addEventListener("click", () => selectNode(n.id));
    }
    svg.appendChild(c);
  }

  boardEl.appendChild(svg);
}

// Up to three resource icons shown just above a second village.
function renderResourceBadges(svg, cx, cy, resources) {
  const gap = 17;
  const startX = cx - ((resources.length - 1) * gap) / 2;
  const by = cy - 24;
  resources.forEach((label, i) => {
    const x = startX + i * gap;
    svg.appendChild(svgEl("circle", { cx: x, cy: by, r: 8, class: "res-badge" }));
    const t = svgEl("text", { x, y: by, class: "res-icon" });
    t.textContent = ICON_BY_LABEL[label] || "?";
    withTitle(t, label);
    svg.appendChild(t);
  });
}

function renderPanel() {
  playersEl.innerHTML = "";
  const oppLabel = autoOpponents ? "computer" : "you";
  const labels = [`Player 1 (${oppLabel})`, `Player 2 (${oppLabel})`, "You (Player 3)"];  (game ? game.players : []).forEach((pl, i) => {
    const li = document.createElement("li");
    const sw = document.createElement("span");
    sw.className = "swatch";
    sw.style.background = pl.color;
    li.appendChild(sw);
    li.appendChild(document.createTextNode(labels[i] || `Player ${pl.id}`));
    if (game.currentPlayerId === pl.id) li.classList.add("active");

    const res = document.createElement("span");
    res.className = "res-tally";
    const entries = Object.entries(pl.resources || {});
    res.textContent = entries.length
      ? entries.map(([label, count]) => `${ICON_BY_LABEL[label] || label}${count}`).join(" ")
      : "\u2013";
    li.appendChild(res);
    playersEl.appendChild(li);
  });
}

// Big resource cards for the human player's hand (one card per resource unit).
function renderHand() {
  handCardsEl.innerHTML = "";
  const you = game && game.players ? game.players[game.players.length - 1] : null;
  const tally = (you && you.resources) || {};
  const cards = [];
  for (const [label, count] of Object.entries(tally)) {
    for (let i = 0; i < count; i++) cards.push(label);
  }
  if (!cards.length) {
    const empty = document.createElement("p");
    empty.className = "hand-empty";
    empty.textContent = "No cards yet \u2013 you draw resources from your second village.";
    handCardsEl.appendChild(empty);
    return;
  }
  for (const label of cards) {
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

    handCardsEl.appendChild(card);
  }
}

function setStatus(text) {
  statusEl.textContent = text;
}

function draw(hexes) {
  lastHexes = hexes;
  const size = renderHexes(hexes);
  colorByPlayer = {};
  if (game) for (const pl of game.players) colorByPlayer[pl.id] = pl.color;
  renderOverlay(size);
  renderPanel();
  renderHand();
}

function describePhase() {
  if (!game || game.phase === "IDLE") {
    setStatus(autoOpponents
      ? "Press \u201CNew Game\u201D: players 1 and 2 are placed automatically, then it\u2019s your turn."
      : "Press \u201CNew Game\u201D: you place villages and roads for all three players.");
    return;
  }
  if (game.phase === "DONE") {
    setStatus("Both rounds done \u2013 each second village collected the resources next to it. Press \u201CNew Game\u201D to play again.");
    return;
  }
  const info = parsePhase();
  if (!info) return;
  // Whom the user is currently placing for.
  const who = !autoOpponents && info.player !== 3 ? `Player ${info.player}` : "you";
  const whoCap = who === "you" ? "You" : who;
  const second = info.round === 2;
  if (selectedNodeId == null) {
    const roundTxt = second
      ? "(round 2, reverse order) \u2013 place a SECOND village; it earns one resource per neighbouring tile."
      : "(round 1) \u2013 place a village.";
    setStatus(`${whoCap} ${roundTxt} Click a free spot (circle).`);
  } else {
    setStatus(`${second ? "Second village" : "Village"} on node ${selectedNodeId} for ${who}. Now click one of the highlighted roads next to it.`);
  }
}

// ---- server calls ----

async function getJson(url, opts) {
  const res = await fetch(url, opts);
  if (!res.ok) throw new Error(`${(opts && opts.method) || "GET"} ${url} -> ${res.status}`);
  return res.json();
}

async function loadHexes() {
  return getJson("/api/board");
}

async function refreshAll(hexesPromise) {
  const [hexes, state] = await Promise.all([
    hexesPromise || loadHexes(),
    getJson("/api/game/state"),
  ]);
  game = state;
  selectedNodeId = null;
  draw(hexes);
  describePhase();
}

function busy(on) {
  reloadBtn.disabled = on;
  newGameBtn.disabled = on;
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
  selectedNodeId = nodeId;
  draw(lastHexes);
  describePhase();
}

async function placeRoad(edgeId) {
  await run(async () => {
    game = await getJson("/api/game/place", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ nodeId: selectedNodeId, edgeId }),
    });
    selectedNodeId = null;
    draw(lastHexes);
    describePhase();
  }, "Placing\u2026");
}

function selectedOpponentsMode() {
  const picked = document.querySelector('input[name="opponents"]:checked');
  return !picked || picked.value === "computer";
}

newGameBtn.addEventListener("click", () =>
  run(async () => {
    autoOpponents = selectedOpponentsMode();
    const hexes = await loadHexes();
    game = await getJson("/api/game/new", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ autoOpponents }),
    });
    selectedNodeId = null;
    draw(hexes);
    describePhase();
  }, "Dealing\u2026")
);

reloadBtn.addEventListener("click", () =>
  run(async () => {
    const hexes = await getJson("/api/board/reshuffle", { method: "POST" });
    await refreshAll(Promise.resolve(hexes));
  }, "Reshuffling\u2026")
);

run(() => refreshAll(), "Loading board\u2026");
