import { FAMILIES, LETTERS, TYPES, familyOf, range, withMatches } from "./model";

const fmt = (v) => Number(v).toLocaleString("en-US");

/** All orderings of a few symbols (for the preference order of a Ranges match). */
function permutations(items) {
  if (items.length <= 1) return [items];
  return items.flatMap((x, i) => permutations([...items.slice(0, i), ...items.slice(i + 1)]).map((rest) => [x, ...rest]));
}

/** "1-3, 7, 9" */
function describe(ms) {
  const parts = [];
  for (let i = 0; i < ms.length;) {
    let j = i;
    while (j + 1 < ms.length && ms[j + 1] === ms[j] + 1) j++;
    parts.push(j - i >= 2 ? `${ms[i]}-${ms[j]}` : ms.slice(i, j + 1).join(", "));
    i = j + 1;
  }
  return parts.join(", ");
}

function MatchPicks({ match, picks, type, onChange }) {
  const family = familyOf(picks[0]);
  const toggle = (s) => {
    if (picks.includes(s)) {
      if (picks.length > 1) onChange(picks.filter((x) => x !== s));
    } else {
      onChange(type === "full" ? family.symbols.filter((x) => x === s || picks.includes(x)) : [...picks, s]);
    }
  };
  const ordered = type === "errors" || type === "covering";
  return (
    <div className="match">
      <div className="match-head">
        <strong>Match {match}</strong>
        <select value={family.id} aria-label={`Match ${match}: market`}
          onChange={(e) => {
            const f = FAMILIES.find((x) => x.id === e.target.value);
            onChange(type === "ranges" ? [...f.symbols] : [f.symbols[0]]);
          }}>
          {FAMILIES.map((f) => <option key={f.id} value={f.id}>{f.label}</option>)}
        </select>
      </div>
      <div className="row">
        {family.symbols.map((s) => (
          <button key={s} type="button" className={picks.includes(s) ? "num on" : "num"} aria-label={`Match ${match}: ${s}`}
            aria-pressed={picks.includes(s)} onClick={() => toggle(s)}>
            {s}
          </button>
        ))}
      </div>
      {ordered && picks.length > 1 && (
        <label className="order muted">
          prediction{" "}
          <select value={picks[0]} aria-label={`Match ${match}: prediction`}
            onChange={(e) => onChange([e.target.value, ...picks.filter((x) => x !== e.target.value)])}>
            {picks.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </label>
      )}
      {type === "ranges" && picks.length > 1 && (
        <label className="order muted">
          preference{" "}
          <select value={picks.join(" ")} aria-label={`Match ${match}: preference`} onChange={(e) => onChange(e.target.value.split(" "))}>
            {permutations(picks).map((p) => <option key={p.join(" ")} value={p.join(" ")}>{p.join(" › ")}</option>)}
          </select>
        </label>
      )}
    </div>
  );
}

export default function GroupCard({ group, index, n, takenBy, counted, onChange, onRemove, canRemove }) {
  const set = (patch) => onChange({ ...group, ...patch });
  const size = group.matches.length;

  function toggleMatch(m) {
    const has = group.matches.includes(m);
    if ((has && size === 1) || (!has && takenBy.has(m))) return;
    onChange(withMatches(group, has ? group.matches.filter((x) => x !== m) : [...group.matches, m]));
  }

  function setType(type) {
    let picks = group.picks;
    if (type === "ranges") picks = picks.map((p) => (p.length > 1 ? p : [...familyOf(p[0]).symbols]));
    if (type === "full") picks = picks.map((p) => familyOf(p[0]).symbols.filter((s) => p.includes(s)));
    // Covering: guarantee every match by default, within the size of the group.
    const extra = type === "covering" ? { radius: Math.min(Number(group.radius) || 0, size), guarantee: size } : {};
    set({ type, picks, ...extra });
  }

  const help = TYPES.find((t) => t.id === group.type).help;
  return (
    <section className="card group">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h3>
          Group {LETTERS[index]}{" "}
          <span className="muted small" style={{ fontWeight: 400 }}>
            matches {describe(group.matches)}{counted ? ` · ${fmt(counted.count)} lines` : ""}
          </span>
        </h3>
        {canRemove && <button className="icon" title="Remove group" aria-label={`Remove group ${LETTERS[index]}`} onClick={onRemove}>✕</button>}
      </div>

      <p className="muted small" style={{ margin: "4px 0" }}>Matches in this group (a match can belong to one group only):</p>
      <div className="row">
        {range(1, n).map((m) => {
          const mine = group.matches.includes(m);
          const other = !mine && takenBy.get(m);
          return (
            <button key={m} type="button" className={mine ? "num on" : other ? "num blocked" : "num"} disabled={!!other}
              title={other ? `In group ${other}` : undefined} aria-label={`Match ${m}`} aria-pressed={mine} onClick={() => toggleMatch(m)}>
              {m}
            </button>
          );
        })}
      </div>

      <div className="row" style={{ marginTop: 10 }}>
        {TYPES.map((t) => (
          <button key={t.id} type="button" className={group.type === t.id ? "pill selected" : "pill"} onClick={() => setType(t.id)}>
            {t.label}
          </button>
        ))}
      </div>
      <p className="muted small" style={{ margin: "6px 0" }}>{help}</p>

      {group.type === "errors" && (
        <label className="row small">
          Errors to play
          <input type="text" value={group.errors} style={{ width: 120 }} placeholder="e.g. 0,1,2" aria-label="Errors to play"
            onChange={(e) => set({ errors: e.target.value })} />
          <span className="muted">(0 = your prediction exactly)</span>
        </label>
      )}
      {group.type === "ranges" && (
        <div className="row small">
          {["1st", "2nd", "3rd"].map((label, r) => (
            <label key={r}>
              {label} choice{" "}
              <input type="number" min={0} max={size} value={group.ranges[r][0]} style={{ width: 56 }} aria-label={`${label} choice from`}
                onChange={(e) => set({ ranges: group.ranges.map((x, j) => (j === r ? [e.target.value, x[1]] : x)) })} />
              {" to "}
              <input type="number" min={0} max={size} value={group.ranges[r][1]} style={{ width: 56 }} aria-label={`${label} choice to`}
                onChange={(e) => set({ ranges: group.ranges.map((x, j) => (j === r ? [x[0], e.target.value] : x)) })} />
            </label>
          ))}
        </div>
      )}
      {group.type === "covering" && (
        <div className="row small">
          <label>
            If at most{" "}
            <input type="number" min={0} max={size} value={group.radius} style={{ width: 56 }} aria-label="Covered changes"
              onChange={(e) => set({ radius: e.target.value })} />{" "}
            matches differ from my prediction,
          </label>
          <label>
            some line has at least{" "}
            <input type="number" min={1} max={size} value={group.guarantee} style={{ width: 56 }} aria-label="Guaranteed hits"
              onChange={(e) => set({ guarantee: e.target.value })} />{" "}
            hits.
          </label>
        </div>
      )}

      <div className="match-grid">
        {group.matches.map((m, i) => (
          <MatchPicks key={m} match={m} picks={group.picks[i]} type={group.type}
            onChange={(p) => set({ picks: group.picks.map((x, j) => (j === i ? p : x)) })} />
        ))}
      </div>
    </section>
  );
}
