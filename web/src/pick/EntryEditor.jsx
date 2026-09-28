import { useState } from "react";
import NumberPad from "../components/NumberPad";

export const ENTRY_TYPES = [
  { id: "single", label: "Single", help: "One line." },
  { id: "system", label: "System", help: "Every combination of the numbers you pick." },
  { id: "groups", label: "Groups", help: "Take a set count from each group (A × B × …); a number belongs to one group." },
  { id: "wheel", label: "Wheel", help: "Reduced system: if at least G of the drawn numbers are among yours, some line has at least G hits." },
];

const LETTERS = "ABCDEFGHIJ";

/** "System of 8: 1 2 3 … | 4 9" */
export function describeEntry(e) {
  const bonus = e.bonus?.length ? ` | ${e.bonus.join(" ")}` : "";
  switch (e.type) {
    case "single": return `Single: ${e.numbers.join(" ")}${bonus}`;
    case "system": return `System of ${e.numbers.length}: ${e.numbers.join(" ")}${bonus}`;
    case "wheel": return `Wheel of ${e.numbers.length}, guarantee ${e.guarantee}: ${e.numbers.join(" ")}${bonus}`;
    default: return `Groups ${e.groups.map((g, i) => `${LETTERS[i]}(${e.take[i]} of ${g.length}: ${g.join(" ")})`).join(" × ")}${bonus}`;
  }
}

/** Draft of a new entry -> the entry for the server, or an error message. */
function draftEntry(type, numbers, groups, guarantee, k, game, bonus) {
  const e = mainDraft(type, numbers, groups, guarantee, k);
  if (typeof e === "string" || !game.bonus) return e;
  const bp = game.bonus.pick;
  if (type === "system" ? bonus.length < bp : bonus.length !== bp)
    return `${game.bonus.label}: choose ${type === "system" ? "at least" : "exactly"} ${bp}.`;
  return { ...e, bonus };
}

function mainDraft(type, numbers, groups, guarantee, k) {
  if (type === "single") return numbers.length === k ? { type, numbers } : `Pick exactly ${k} numbers.`;
  if (type === "system") return numbers.length >= k ? { type, numbers } : `Pick at least ${k} numbers.`;
  if (type === "wheel") return numbers.length > k ? { type, numbers, guarantee: Number(guarantee) } : `Pick more than ${k} numbers.`;
  const sum = groups.reduce((s, g) => s + Number(g.take || 0), 0);
  if (sum !== k) return `The numbers taken must add up to ${k} (now ${sum}).`;
  if (groups.some((g) => g.numbers.length < Number(g.take) || Number(g.take) < 1)) return "Each group needs at least as many numbers as it takes.";
  return { type, groups: groups.map((g) => g.numbers), take: groups.map((g) => Number(g.take)) };
}

export default function EntryEditor({ game, onAdd }) {
  const k = game.pick;
  const [type, setType] = useState("system");
  const [numbers, setNumbers] = useState([]);
  const [groups, setGroups] = useState([{ numbers: [], take: 1 }]);
  const [guarantee, setGuarantee] = useState(Math.min(3, k));
  const [bonus, setBonus] = useState([]);
  const draft = draftEntry(type, numbers, groups, guarantee, k, game, bonus);
  const min = game.min ?? 1, max = game.max ?? game.numbers;
  const help = ENTRY_TYPES.find((t) => t.id === type).help;

  function add() {
    if (typeof draft === "string") return;
    onAdd(draft);
    setNumbers([]);
    setBonus([]);
    setGroups([{ numbers: [], take: 1 }]);
  }

  return (
    <section className="card">
      <h2>New entry</h2>
      <div className="row">
        {ENTRY_TYPES.map((t) => (
          <button key={t.id} type="button" className={type === t.id ? "pill selected" : "pill"} onClick={() => setType(t.id)}>{t.label}</button>
        ))}
      </div>
      <p className="muted small">{help}</p>

      {type !== "groups" ? (
        <>
          <NumberPad min={min} max={max} selected={numbers} onChange={setNumbers}
            limit={type === "single" ? k : undefined} randomCount={type === "single" ? k : k + 2} />
          {type === "wheel" && (
            <label className="row small" style={{ marginTop: 8 }}>
              Guarantee{" "}
              <input type="number" min={1} max={k} value={guarantee} style={{ width: 60 }} aria-label="Wheel guarantee"
                onChange={(e) => setGuarantee(Math.max(1, Math.min(k, Number(e.target.value) || 1)))} />
              <span className="muted">hits if at least that many drawn numbers are among yours</span>
            </label>
          )}
        </>
      ) : (
        <>
          {groups.map((g, i) => (
            <div key={i} className="card" style={{ marginTop: 8 }}>
              <div className="row" style={{ justifyContent: "space-between" }}>
                <h3>Group {LETTERS[i]}</h3>
                <div className="row small">
                  <label>take{" "}
                    <input type="number" min={1} max={k} value={g.take} style={{ width: 56 }} aria-label={`Group ${LETTERS[i]} takes`}
                      onChange={(e) => setGroups(groups.map((x, j) => (j === i ? { ...x, take: e.target.value } : x)))} />
                  </label>
                  {groups.length > 1 && <button className="icon" aria-label={`Remove group ${LETTERS[i]}`} onClick={() => setGroups(groups.filter((_, j) => j !== i))}>✕</button>}
                </div>
              </div>
              <NumberPad min={min} max={max} selected={g.numbers} label={`Group ${LETTERS[i]} number`}
                blocked={groups.flatMap((o, j) => (j === i ? [] : o.numbers))}
                onChange={(nums) => setGroups(groups.map((x, j) => (j === i ? { ...x, numbers: nums } : x)))} randomCount={Number(g.take) + 1} />
            </div>
          ))}
          {groups.length < Math.min(k, LETTERS.length) && (
            <button className="pill" style={{ marginTop: 8 }} onClick={() => setGroups([...groups, { numbers: [], take: 1 }])}>+ Group</button>
          )}
        </>
      )}
      {game.bonus && (
        <div style={{ marginTop: 12 }}>
          <h3>{game.bonus.label} <span className="muted small" style={{ fontWeight: 400 }}>
            {type === "system" ? `${game.bonus.pick} or more -- every combination of ${game.bonus.pick}` : `exactly ${game.bonus.pick}`}</span></h3>
          <NumberPad min={game.bonus.min} max={game.bonus.max} selected={bonus} onChange={setBonus} label={game.bonus.label}
            limit={type === "system" ? undefined : game.bonus.pick} randomCount={game.bonus.pick} />
        </div>
      )}
      <div className="row" style={{ marginTop: 10 }}>
        <button onClick={add} disabled={typeof draft === "string"}>+ Add entry</button>
        {typeof draft === "string" && <span className="muted small">{draft}</span>}
      </div>
    </section>
  );
}
