export const HEX_SIZE = 62;
export const PADDING = 28;
export const SVG_NS = "http://www.w3.org/2000/svg";

export const RESOURCE = {
  WOOD: { icon: "\u{1F332}", label: "Wood" },
  WOOL: { icon: "\u{1F411}", label: "Wool" },
  GRAIN: { icon: "\u{1F33E}", label: "Grain" },
  BRICK: { icon: "\u{1F9F1}", label: "Brick" },
  ORE: { icon: "\u26F0\uFE0F", label: "Ore" },
  DESERT: { icon: "\u{1F3DC}\uFE0F", label: "Desert" },
};

export const ICON_BY_LABEL = Object.fromEntries(
  Object.values(RESOURCE).map((resource) => [resource.label, resource.icon])
);

export const CARD_COLOR_BY_LABEL = {
  Wood: "#2f6b32",
  Wool: "#7cb342",
  Grain: "#e3b505",
  Brick: "#c1532a",
  Ore: "#7d8a96",
};
