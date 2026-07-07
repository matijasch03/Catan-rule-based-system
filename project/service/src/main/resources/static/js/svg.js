import { SVG_NS } from "./constants.js";

export function svgEl(name, attrs) {
  const el = document.createElementNS(SVG_NS, name);
  for (const [key, value] of Object.entries(attrs)) {
    el.setAttribute(key, value);
  }
  return el;
}

export function withTitle(el, text) {
  const title = svgEl("title", {});
  title.textContent = text;
  el.appendChild(title);
  return el;
}

export function housePath(cx, cy, size) {
  const width = size;
  const height = size * 0.7;
  const roof = size * 0.55;
  const left = cx - width / 2;
  const right = cx + width / 2;
  const bottom = cy + height / 2;
  const mid = cy - height / 2;
  return `M ${left} ${bottom} L ${left} ${mid} L ${cx} ${mid - roof} L ${right} ${mid} L ${right} ${bottom} Z`;
}
