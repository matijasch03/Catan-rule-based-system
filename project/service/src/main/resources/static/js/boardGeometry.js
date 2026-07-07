import { HEX_SIZE } from "./constants.js";
import { state } from "./state.js";

export function pipsFor(n) {
  if (!n || n < 2 || n > 12) return "";
  const count = 6 - Math.abs(7 - n);
  return "\u2022".repeat(count);
}

export function hexCenterPx(q, r) {
  return {
    x: HEX_SIZE * Math.sqrt(3) * (q + r / 2),
    y: HEX_SIZE * 1.5 * r,
  };
}

export function toScreen(mx, my) {
  return {
    x: HEX_SIZE * mx - state.layout.minX + state.layout.offX,
    y: HEX_SIZE * my - state.layout.minY + state.layout.offY,
  };
}
