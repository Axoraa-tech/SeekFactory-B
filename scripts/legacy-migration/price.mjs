// Parses the legacy free-text `priceRange` into { currency, amount, tiers[], unit }.
// Amounts stay in their source currency; build-sql converts to INR with the platform's own
// exchange_rates setting so the site shows the original figure back in that currency.
const UNITS = { set: "Set", piece: "Piece", unit: "Unit", pack: "Pack", acre: "Acre", kilogram: "Kilogram", box: "Box" };
const UNIT_RE = "(sets?|pieces?|units?|packs?|acres?|kilograms?|box(?:es)?)";
const num = (s) => Number(String(s).replace(/,/g, ""));
const unitOf = (word) => {
  const w = word.toLowerCase();
  return UNITS[w] || UNITS[w.replace(/es$/, "")] || UNITS[w.replace(/s$/, "")] || "Set";
};

export function parsePrice(raw) {
  const text = String(raw ?? "").replace(/["“”]/g, " ").replace(/\s+/g, " ").trim();
  const currency = /₹/.test(text) ? "INR" : /￥|¥|RMB/i.test(text) ? "CNY" : "USD";

  // quantity breaks: "1-2 sets", "1 - 4 sets", "≥5 sets", ">= 10 sets", "1 set"
  const qtys = [];
  let unit = null;
  const qtyRe = new RegExp(`(?:(\\d[\\d,]*)\\s*-\\s*\\d[\\d,]*|(?:≥|>=)\\s*(\\d[\\d,]*)|(\\d[\\d,]*))\\s*${UNIT_RE}\\b`, "gi");
  const withoutQty = text.replace(/minimum order quantity:?\s*\d+\s*\w+/gi, (m) => {
    const u = m.match(new RegExp(UNIT_RE, "i"));
    if (u) unit = unitOf(u[1]);
    return " ";
  }).replace(qtyRe, (_m, a, b, c, u) => {
    qtys.push(num(a ?? b ?? c));
    unit ??= unitOf(u);
    return " | ";
  });
  if (!unit && /per unit/i.test(text)) unit = "Unit";

  // prices: currency-marked or bare numbers; "a-b" ranges keep the lower bound.
  // A number glued to letters ("40HQ" container size) is not a price.
  let prices = [...withoutQty.matchAll(/(\d[\d,]*(?:\.\d+)?)(?:\s*-\s*\d[\d,]*(?:\.\d+)?)?(?![\d.,]*[A-Za-z])/g)]
    .map((m) => num(m[1]))
    .filter((n) => n > 0);

  // "sale list" price pairs per tier ("$1,000.35 $1,053 1-4 sets"): keep the sale (first) price
  if (qtys.length > 1 && prices.length === 2 * qtys.length) prices = prices.filter((_, i) => i % 2 === 0);

  let tiers = [];
  if (qtys.length > 1 && prices.length === qtys.length) {
    tiers = qtys.map((minQty, i) => ({ minQty: Math.max(1, minQty), amount: prices[i] }))
      .sort((a, b) => a.minQty - b.minQty);
  }
  const amount = tiers.length ? tiers[0].amount : prices.length ? prices[0] : 0;
  return { currency, amount, tiers, unit: unit || "Set", firstQty: qtys.length ? Math.max(1, Math.min(...qtys)) : null };
}

export function moqText(n, unit) {
  const plural = n === 1 ? unit : unit === "Box" ? "Boxes" : unit + "s";
  return `${n} ${plural}`;
}
