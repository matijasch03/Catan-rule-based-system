import { HEX_SIZE, ICON_BY_LABEL, PADDING, RESOURCE } from "./constants.js";
import { boardEl } from "./dom.js";
import { hexCenterPx, pipsFor, toScreen } from "./boardGeometry.js";
import { housePath, svgEl, withTitle } from "./svg.js";
import { isHumanTurn } from "./phase.js?v=20260707-turn-steps";
import { state } from "./state.js";

export function renderBoard(hexes, actions) {
  state.lastHexes = hexes;
  const size = renderHexes(hexes);
  state.colorByPlayer = {};
  if (state.game) {
    for (const player of state.game.players) {
      state.colorByPlayer[player.id] = player.color;
    }
  }
  renderOverlay(size, actions);
}

function renderHexes(hexes) {
  boardEl.innerHTML = "";

  const points = hexes.map((hex) => hexCenterPx(hex.q, hex.r));
  const minX = Math.min(...points.map((point) => point.x));
  const maxX = Math.max(...points.map((point) => point.x));
  const minY = Math.min(...points.map((point) => point.y));
  const maxY = Math.max(...points.map((point) => point.y));

  const hexW = HEX_SIZE * Math.sqrt(3);
  const hexH = HEX_SIZE * 2;
  state.layout = { minX, minY, offX: hexW / 2 + PADDING, offY: hexH / 2 + PADDING };

  const width = maxX - minX + hexW + PADDING * 2;
  const height = maxY - minY + hexH + PADDING * 2;
  boardEl.style.width = `${width}px`;
  boardEl.style.height = `${height}px`;

  for (const hexData of hexes) {
    const { x, y } = hexCenterPx(hexData.q, hexData.r);
    const resource = RESOURCE[hexData.field] || { icon: "?", label: hexData.field };

    const hex = document.createElement("div");
    hex.className = `hex field-${hexData.field}`;
    hex.style.left = `${x - minX + state.layout.offX}px`;
    hex.style.top = `${y - minY + state.layout.offY}px`;
    hex.title = `${resource.label} (q=${hexData.q}, r=${hexData.r})`;

    const icon = document.createElement("div");
    icon.className = "icon";
    icon.textContent = resource.icon;
    hex.appendChild(icon);

    if (hexData.field !== "DESERT" && hexData.dots) {
      hex.appendChild(numberToken(hexData.dots));
    }

    boardEl.appendChild(hex);
  }

  return { width, height };
}

function numberToken(dots) {
  const token = document.createElement("div");
  token.className = "token" + (dots === 6 || dots === 8 ? " hot" : "");

  const number = document.createElement("span");
  number.className = "number";
  number.textContent = dots;
  token.appendChild(number);

  const pips = document.createElement("span");
  pips.className = "pips";
  pips.textContent = pipsFor(dots);
  token.appendChild(pips);

  return token;
}

function renderOverlay(size, actions) {
  const old = boardEl.querySelector("svg.overlay");
  if (old) old.remove();
  if (!state.game) return;

  const svg = svgEl("svg", { class: "overlay", width: size.width, height: size.height });
  const nodeById = Object.fromEntries(state.game.nodes.map((node) => [node.id, node]));
  const adviceByNode = adviceNodeMap(state.game.advices || []);
  const goalAdviceByNode = goalAdviceNodeMap(state.game.goalAdvices || []);

  renderEdges(svg, nodeById, actions);
  renderAdviceRoutes(svg, nodeById, state.game.advices || []);
  renderNodes(svg, adviceByNode, goalAdviceByNode, actions);
  boardEl.appendChild(svg);
}

function adviceNodeMap(advices) {
  const byNode = {};
  for (const advice of advices) {
    const checkpointIds = new Set(advice.checkpointNodeIds || []);
    const route = adviceRoute(advice);
    const firstEndpoint = route[0];
    const secondEndpoint = route[route.length - 1];
    const endpointIds = new Set([firstEndpoint, secondEndpoint, advice.nodeId].filter(Boolean));
    const openingPair = (advice.tags || []).includes("OpeningPair");
    for (const nodeId of route) {
      const markerRank = openingPair && nodeId === secondEndpoint && secondEndpoint !== firstEndpoint
        ? 2
        : advice.rank;
      if (byNode[nodeId] && byNode[nodeId].rank < markerRank) continue;
      byNode[nodeId] = {
        ...advice,
        rank: markerRank,
        checkpoint: checkpointIds.has(nodeId),
        endpoint: endpointIds.has(nodeId),
      };
    }
  }
  return byNode;
}

function goalAdviceNodeMap(advices) {
  const byNode = {};
  for (const advice of advices) {
    if (!advice.nodeId) continue;
    if (advice.title !== "Upgrade a village to town") continue;
    byNode[advice.nodeId] = advice;
  }
  return byNode;
}

function renderAdviceRoutes(svg, nodeById, advices) {
  for (const advice of advices) {
    const route = adviceRoute(advice);
    for (let i = 0; i < route.length - 1; i++) {
      const a = nodeById[route[i]];
      const b = nodeById[route[i + 1]];
      if (!a || !b) continue;
      const p1 = toScreen(a.x, a.y);
      const p2 = toScreen(b.x, b.y);
      svg.appendChild(withTitle(svgEl("line", {
        x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y,
        class: `advice-route advice-route-${advice.rank}`,
      }), advice.description));
    }
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

function renderEdges(svg, nodeById, actions) {
  const legalRoadEdges = new Set(state.game.legalRoadEdgeIds || []);
  for (const edge of state.game.edges) {
    const a = nodeById[edge.node1Id];
    const b = nodeById[edge.node2Id];
    if (!a || !b) continue;

    const p1 = toScreen(a.x, a.y);
    const p2 = toScreen(b.x, b.y);
    if (edge.ownerId != null) {
      svg.appendChild(svgEl("line", {
        x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y,
        class: "road", stroke: state.colorByPlayer[edge.ownerId] || "#fff",
      }));
      continue;
    }

    const incidentToSelected =
      state.selectedNodeId != null
      && (edge.node1Id === state.selectedNodeId || edge.node2Id === state.selectedNodeId);
    const legalBuildRoad = state.buildMode === "ROAD" && legalRoadEdges.has(edge.id);
    const line = withTitle(svgEl("line", {
      x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y,
      class: "edge" + (incidentToSelected || legalBuildRoad ? " edge-selectable" : ""),
    }), `Edge ${edge.id}`);
    let hitbox = null;
    if (legalBuildRoad) {
      hitbox = withTitle(svgEl("line", {
        x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y,
        class: "edge-hitbox",
      }), `Build road on edge ${edge.id}`);
      hitbox.addEventListener("click", () => actions.buildOnEdge(edge.id));
      line.addEventListener("click", () => actions.buildOnEdge(edge.id));
    } else if (incidentToSelected) {
      line.addEventListener("click", () => actions.placeRoad(edge.id));
    }
    svg.appendChild(line);
    if (hitbox) {
      svg.appendChild(hitbox);
    }
  }
}

function renderNodes(svg, adviceByNode, goalAdviceByNode, actions) {
  const isUserTurn = isHumanTurn();
  const legalVillageNodes = new Set(state.game.legalVillageNodeIds || []);
  for (const node of state.game.nodes) {
    const point = toScreen(node.x, node.y);

    if (node.settlement) {
      renderSettlement(svg, node, point, goalAdviceByNode[node.id], actions);
      continue;
    }

    const openingSelectable =
      isUserTurn
      && state.selectedNodeId == null
      && state.buildMode == null
      && isOpeningPlaceable(node);
    const legalBuildVillage = state.buildMode === "VILLAGE" && legalVillageNodes.has(node.id);
    const selectable = openingSelectable || legalBuildVillage;
    const selected = node.id === state.selectedNodeId;
    const advice = adviceByNode[node.id];
    const marker = withTitle(svgEl("circle", {
      cx: point.x, cy: point.y, r: 6,
      class: "node" + (selectable ? " node-pick" : "")
        + (advice ? ` node-advice-${advice.rank}` : "")
        + (advice && advice.endpoint ? " node-advice-endpoint" : "")
        + (advice && advice.checkpoint ? " node-advice-checkpoint" : "")
        + (selected ? " node-selected" : ""),
    }), nodeTitle(node, advice));
    if (legalBuildVillage) {
      marker.addEventListener("click", () => actions.buildOnNode(node.id));
    } else if (openingSelectable) {
      marker.addEventListener("click", () => actions.selectNode(node.id));
    }
    svg.appendChild(marker);
  }
}

function isOpeningPlaceable(node) {
  if (node.settlement) return false;
  return !(state.game.edges || []).some((edge) => {
    if (edge.node1Id !== node.id && edge.node2Id !== node.id) return false;
    const otherId = edge.node1Id === node.id ? edge.node2Id : edge.node1Id;
    const other = (state.game.nodes || []).find((candidate) => candidate.id === otherId);
    return other && other.settlement;
  });
}

function nodeTitle(node, advice = null) {
  const base = `Node ${node.id} | Score: ${node.score ?? 0}`;
  if (!advice) return base;
  return `${base} | ${advice.description}`;
}

function renderSettlement(svg, node, point, goalAdvice, actions) {
  const legalTownNodes = new Set(state.game.legalTownNodeIds || []);
  const legalUpgrade = state.buildMode === "TOWN" && legalTownNodes.has(node.id);
  const recommendedTown = goalAdvice && goalAdvice.title === "Upgrade a village to town";
  const piece = withTitle(svgEl("path", {
    d: housePath(point.x, point.y, node.settlement === "TOWN" ? 30 : 15),
    class: "piece"
      + (legalUpgrade ? " piece-upgrade" : "")
      + (recommendedTown ? " piece-town-recommendation" : ""),
    fill: state.colorByPlayer[node.ownerId] || "#fff",
  }), `${nodeTitle(node)} | ${node.settlement}${recommendedTown ? ` | ${goalAdvice.description}` : ""}`);
  if (legalUpgrade) {
    piece.addEventListener("click", () => actions.buildOnNode(node.id));
  }
  svg.appendChild(piece);

  if (node.resourcesGained && node.resourcesGained.length) {
    renderResourceBadges(svg, point.x, point.y, node.resourcesGained);
  }
}

function renderResourceBadges(svg, cx, cy, resources) {
  const gap = 17;
  const startX = cx - ((resources.length - 1) * gap) / 2;
  const by = cy - 24;
  resources.forEach((label, index) => {
    const x = startX + index * gap;
    svg.appendChild(svgEl("circle", { cx: x, cy: by, r: 8, class: "res-badge" }));

    const text = svgEl("text", { x, y: by, class: "res-icon" });
    text.textContent = ICON_BY_LABEL[label] || "?";
    withTitle(text, label);
    svg.appendChild(text);
  });
}
