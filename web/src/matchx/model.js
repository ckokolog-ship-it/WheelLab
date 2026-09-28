// MatchX ticket model (browser side) -- the same shape the server expects, plus ids for React.

export const FAMILIES = [
  { id: "1X2", label: "1 X 2", symbols: ["1", "X", "2"] },
  { id: "UO", label: "Under / Over", symbols: ["U", "O"] },
  { id: "GN", label: "GG / NG", symbols: ["GG", "NG"] },
];

export const TYPES = [
  { id: "full", label: "Full", help: "Every combination of the outcomes you pick." },
  { id: "errors", label: "Errors", help: "Your prediction (first pick) with a chosen number of wrong matches." },
  { id: "ranges", label: "Ranges", help: "Picks in order of preference; limit how many matches take their 1st, 2nd and 3rd choice." },
  { id: "covering", label: "Covering", help: "Reduced system: if at most R matches differ from your prediction, some line has at least G hits." },
];

export const MIN_MATCHES = 1;
export const MAX_MATCHES = 20;
export const MAX_TICKETS = 10;
export const LETTERS = "ABCDEFGHIJKLMNOPQRST";

export const familyOf = (symbol) => FAMILIES.find((f) => f.symbols.includes(symbol)) ?? FAMILIES[0];
export const range = (a, b) => Array.from({ length: Math.max(0, b - a + 1) }, (_, i) => a + i);

let nextId = 1;
export const newId = () => nextId++;

export function newGroup(matches, type = "full") {
  return {
    id: newId(),
    type,
    matches: [...matches],
    picks: matches.map(() => (type === "ranges" ? ["1", "X", "2"] : ["1"])),
    errors: "0,1",
    ranges: [[0, matches.length], [0, matches.length], [0, matches.length]],
    radius: Math.min(1, matches.length),
    guarantee: matches.length,
  };
}

export const newTicket = (n) => ({ id: newId(), groups: [newGroup(range(1, n))] });

/** New matches for a group: keep the picks of the matches that stay, default picks for new ones. */
export function withMatches(g, matches) {
  const sorted = [...new Set(matches)].sort((a, b) => a - b);
  const old = new Map(g.matches.map((m, i) => [m, g.picks[i]]));
  const k = sorted.length;
  return {
    ...g,
    matches: sorted,
    picks: sorted.map((m) => old.get(m) ?? (g.type === "ranges" ? ["1", "X", "2"] : ["1"])),
    // keep the covering numbers valid for the new size (a larger group keeps "all matches" if it had it)
    radius: Math.min(Number(g.radius) || 0, k),
    guarantee: Number(g.guarantee) >= g.matches.length ? k : Math.min(Math.max(1, Number(g.guarantee) || 1), k),
  };
}

/** The request body for the server. */
export function toBody(state) {
  return {
    matches: state.matches,
    tickets: state.tickets.map((t) => ({
      groups: t.groups.map((g) => {
        const o = { type: g.type, matches: g.matches, picks: g.picks };
        if (g.type === "errors") o.errors = String(g.errors).split(/[,\s]+/).filter(Boolean).map(Number);
        if (g.type === "ranges") o.ranges = g.ranges.map((r) => r.map(Number));
        if (g.type === "covering") Object.assign(o, { radius: Number(g.radius), guarantee: Number(g.guarantee) });
        return o;
      }),
    })),
  };
}

/** Validates a saved state (file or browser storage) -> state with fresh ids, or throws. */
export function restore(data) {
  const n = Number(data?.matches);
  if (!Number.isInteger(n) || n < MIN_MATCHES || n > MAX_MATCHES) throw new Error("Wrong number of matches in the file.");
  if (!Array.isArray(data.tickets) || data.tickets.length < 1 || data.tickets.length > MAX_TICKETS) throw new Error("Wrong tickets in the file.");
  const tickets = data.tickets.map((t) => {
    if (!Array.isArray(t.groups) || t.groups.length === 0) throw new Error("A ticket without groups in the file.");
    return {
      id: newId(),
      groups: t.groups.map((g) => {
        const ms = Array.isArray(g.matches) ? g.matches.map(Number) : [];
        const ok = TYPES.some((x) => x.id === g.type) && ms.length > 0 && Array.isArray(g.picks) && g.picks.length === ms.length
          && ms.every((m, i) => Number.isInteger(m) && m >= 1 && m <= n && (i === 0 || m > ms[i - 1]))
          && g.picks.every((p) => Array.isArray(p) && p.length > 0 && p.every((s) => familyOf(p[0]).symbols.includes(s)));
        if (!ok) throw new Error("A wrong group in the file.");
        return { ...newGroup(ms, g.type), ...g, id: newId(), matches: ms, picks: g.picks.map((p) => p.map(String)) };
      }),
    };
  });
  return { matches: n, tickets };
}
