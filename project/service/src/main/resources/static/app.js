const HEX_SIZE = 62;
const PADDING = 28;

const RESOURCE = {
  WOOD: { icon: "\u{1F332}", label: "Wood" },
  WOOL: { icon: "\u{1F411}", label: "Wool" },
  GRAIN: { icon: "\u{1F33E}", label: "Grain" },
  BRICK: { icon: "\u{1F9F1}", label: "Brick" },
  ORE: { icon: "\u26F0\uFE0F", label: "Ore" },
  DESERT: { icon: "\u{1F3DC}\uFE0F", label: "Desert" },
};

const boardEl = document.getElementById("board");
const statusEl = document.getElementById("status");
const reloadBtn = document.getElementById("reload-btn");

// Probability pips for a number token (higher near 6/8).
function pipsFor(n) {
  if (!n || n < 2 || n > 12) return "";
  const count = 6 - Math.abs(7 - n);
  return "\u2022".repeat(count);
}

function axialToPixel(q, r) {
  return {
    x: HEX_SIZE * Math.sqrt(3) * (q + r / 2),
    y: HEX_SIZE * 1.5 * r,
  };
}

function render(hexes) {
  boardEl.innerHTML = "";
  if (!hexes.length) {
    statusEl.textContent = "No hexes returned by the server.";
    return;
  }

  const points = hexes.map((h) => axialToPixel(h.q, h.r));
  const minX = Math.min(...points.map((p) => p.x));
  const maxX = Math.max(...points.map((p) => p.x));
  const minY = Math.min(...points.map((p) => p.y));
  const maxY = Math.max(...points.map((p) => p.y));

  const hexW = HEX_SIZE * Math.sqrt(3);
  const hexH = HEX_SIZE * 2;
  boardEl.style.width = `${maxX - minX + hexW + PADDING * 2}px`;
  boardEl.style.height = `${maxY - minY + hexH + PADDING * 2}px`;

  for (const h of hexes) {
    const { x, y } = axialToPixel(h.q, h.r);
    const res = RESOURCE[h.field] || { icon: "?", label: h.field };

    const hex = document.createElement("div");
    hex.className = `hex field-${h.field}`;
    hex.style.left = `${x - minX + hexW / 2 + PADDING}px`;
    hex.style.top = `${y - minY + hexH / 2 + PADDING}px`;
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
}

async function loadBoard() {
  const res = await fetch("/api/board");
  if (!res.ok) throw new Error(`GET /api/board -> ${res.status}`);
  return res.json();
}

async function reshuffle() {
  const res = await fetch("/api/board/reshuffle", { method: "POST" });
  if (!res.ok) throw new Error(`POST /api/board/reshuffle -> ${res.status}`);
  return res.json();
}

async function refresh(action) {
  reloadBtn.disabled = true;
  statusEl.textContent = action === "reshuffle" ? "Reshuffling\u2026" : "Loading board\u2026";
  try {
    const hexes = action === "reshuffle" ? await reshuffle() : await loadBoard();
    render(hexes);
    statusEl.textContent = `${hexes.length} hexes`;
  } catch (err) {
    statusEl.textContent = `Error: ${err.message}`;
  } finally {
    reloadBtn.disabled = false;
  }
}

reloadBtn.addEventListener("click", () => refresh("reshuffle"));
refresh("load");
