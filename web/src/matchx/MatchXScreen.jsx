import { useEffect, useRef, useState } from "react";
import { matchxBuild, matchxCount } from "../api";
import LinesGrid from "../components/LinesGrid";
import { downloadText, loadLocal, pickTextFile, saveLocal, stamp } from "../storage";
import CheckPanel from "./CheckPanel";
import GroupCard from "./GroupCard";
import { LETTERS, MAX_MATCHES, MAX_TICKETS, MIN_MATCHES, newGroup, newId, newTicket, range, restore, toBody, withMatches } from "./model";

const STORAGE_KEY = "wheellab.matchx";
const DEFAULT_MATCHES = 13;
const fmt = (v) => Number(v).toLocaleString("en-US");

function initialState() {
  try {
    const saved = loadLocal(STORAGE_KEY, null);
    if (saved) return restore(saved);
  } catch {
    /* old or broken data -- start fresh */
  }
  return { matches: DEFAULT_MATCHES, tickets: [newTicket(DEFAULT_MATCHES)] };
}

/**
 * MatchX: predict the outcome of N matches (1 X 2, Under/Over or GG/NG per match). A ticket splits the
 * matches into groups, each with its own system; its lines are every combination of the groups' lines.
 * Several tickets are appended.
 */
export default function MatchXScreen() {
  const [state, setState] = useState(initialState);
  const [tab, setTab] = useState("build");
  const [active, setActive] = useState(0);
  const [counted, setCounted] = useState(null);
  const [build, setBuild] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [note, setNote] = useState(null);
  const seq = useRef(0);
  const body = toBody(state);
  const bodyKey = JSON.stringify(body);
  const ticket = state.tickets[Math.min(active, state.tickets.length - 1)];

  useEffect(() => saveLocal(STORAGE_KEY, state), [state]);

  // Live line count (nothing is built) shortly after every change.
  useEffect(() => {
    const my = ++seq.current;
    const timer = setTimeout(() => {
      matchxCount(JSON.parse(bodyKey))
        .then((r) => my === seq.current && setCounted({ key: bodyKey, data: r, error: null }))
        .catch((e) => my === seq.current && setCounted({ key: bodyKey, data: null, error: e.message }));
    }, 300);
    return () => clearTimeout(timer);
  }, [bodyKey]);

  const count = counted?.key === bodyKey ? counted : null;
  const ticketCount = count?.data?.tickets?.[Math.min(active, state.tickets.length - 1)];

  function change(next) {
    setState(next);
    setBuild(null);
  }
  const updateTicket = (t) => change({ ...state, tickets: state.tickets.map((x) => (x.id === ticket.id ? t : x)) });

  function resize(value) {
    const n = Math.max(MIN_MATCHES, Math.min(MAX_MATCHES, Number(value) || MIN_MATCHES));
    change({
      matches: n,
      tickets: state.tickets.map((t) => {
        let groups = t.groups.map((g) => withMatches(g, g.matches.filter((m) => m <= n))).filter((g) => g.matches.length > 0);
        if (groups.length === 0) groups = [newGroup(range(1, n))];
        if (n > state.matches) {
          const gi = groups.findIndex((g) => g.matches.includes(state.matches));
          if (gi >= 0) groups[gi] = withMatches(groups[gi], [...groups[gi].matches, ...range(state.matches + 1, n)]);
        }
        return { ...t, groups };
      }),
    });
  }

  function addGroup() {
    const used = new Set(ticket.groups.flatMap((g) => g.matches));
    const free = range(1, state.matches).filter((m) => !used.has(m));
    if (free.length) return updateTicket({ ...ticket, groups: [...ticket.groups, newGroup(free)] });
    const last = ticket.groups[ticket.groups.length - 1];
    if (last.matches.length < 2) return;
    const half = Math.ceil(last.matches.length / 2);
    updateTicket({ ...ticket, groups: [...ticket.groups.slice(0, -1), withMatches(last, last.matches.slice(0, half)), newGroup(last.matches.slice(half))] });
  }

  function addTicket(copy) {
    if (state.tickets.length >= MAX_TICKETS) return;
    const t = copy ? { id: newId(), groups: ticket.groups.map((g) => ({ ...structuredClone(g), id: newId() })) } : newTicket(state.matches);
    change({ ...state, tickets: [...state.tickets, t] });
    setActive(state.tickets.length);
  }

  async function doBuild() {
    setBusy(true);
    setError(null);
    try {
      setBuild(await matchxBuild(body));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  async function openFile() {
    setNote(null);
    try {
      const text = await pickTextFile();
      if (text == null) return;
      const data = JSON.parse(text);
      change(restore(data.matchx ?? data));
      setActive(0);
      setNote("Opened.");
    } catch (e) {
      setError(e.message);
    }
  }

  const invalid = !count || !!count.error;
  return (
    <>
      <div className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === "build"} className={tab === "build" ? "pill selected" : "pill"} onClick={() => setTab("build")}>🎯 Build</button>
        <button role="tab" aria-selected={tab === "check"} className={tab === "check" ? "pill selected" : "pill"} onClick={() => setTab("check")}>✅ Check</button>
      </div>

      {tab === "check" ? (
        <CheckPanel state={state} body={body} disabled={invalid} />
      ) : (
        <>
          <section className="card">
            <h2>MatchX</h2>
            <p className="muted">
              Predict {state.matches} matches -- 1 X 2, Under/Over or GG/NG on each. Split the matches into groups, each
              with its own system; the lines of a ticket are every combination of its groups&apos; lines.
            </p>
            <div className="row">
              <label>
                Matches{" "}
                <input type="number" min={MIN_MATCHES} max={MAX_MATCHES} value={state.matches} style={{ width: 64 }}
                  aria-label="Number of matches" onChange={(e) => resize(e.target.value)} />
              </label>
              <button className="pill" onClick={() => downloadText(`matchx_${stamp()}.json`, JSON.stringify({ app: "WheelLab", matchx: state }, null, 2), "application/json")}>💾 Save</button>
              <button className="pill" onClick={openFile}>📂 Open</button>
              <button className="pill" onClick={() => window.confirm("Start a new ticket? Unsaved changes are lost.") && (change({ matches: DEFAULT_MATCHES, tickets: [newTicket(DEFAULT_MATCHES)] }), setActive(0))}>
                New
              </button>
              {note && <span className="muted small">{note}</span>}
            </div>
            <div className="row" style={{ marginTop: 10 }}>
              {state.tickets.map((t, i) => {
                const c = count?.data?.tickets?.[i];
                return (
                  <button key={t.id} className={i === active ? "pill selected" : "pill"} onClick={() => setActive(i)}>
                    Ticket {i + 1}{c ? ` · ${fmt(c.total)}` : ""}
                  </button>
                );
              })}
              {state.tickets.length < MAX_TICKETS && (
                <>
                  <button className="pill" onClick={() => addTicket(false)}>+ Ticket</button>
                  <button className="pill" onClick={() => addTicket(true)}>⧉ Copy ticket</button>
                </>
              )}
              {state.tickets.length > 1 && (
                <button className="icon" title="Remove this ticket" aria-label="Remove ticket"
                  onClick={() => { change({ ...state, tickets: state.tickets.filter((t) => t.id !== ticket.id) }); setActive(Math.max(0, active - 1)); }}>✕</button>
              )}
            </div>
          </section>

          {ticket.groups.map((g, i) => (
            <GroupCard key={g.id} group={g} index={i} n={state.matches}
              takenBy={new Map(ticket.groups.flatMap((o, j) => (j === i ? [] : o.matches.map((m) => [m, LETTERS[j]]))))}
              counted={ticketCount?.groups?.[i]}
              onChange={(x) => updateTicket({ ...ticket, groups: ticket.groups.map((y) => (y.id === g.id ? x : y)) })}
              onRemove={() => updateTicket({ ...ticket, groups: ticket.groups.filter((y) => y.id !== g.id) })}
              canRemove={ticket.groups.length > 1} />
          ))}

          <section className="card">
            <div className="row">
              <button className="pill" onClick={addGroup}>+ Group</button>
              <button onClick={doBuild} disabled={busy || invalid}>{busy ? "Building…" : "Build lines"}</button>
              {count?.data && <span>Lines: <strong>{fmt(count.data.total)}</strong>{state.tickets.length > 1 && <span className="muted"> ({count.data.tickets.map((t) => fmt(t.total)).join(" + ")})</span>}</span>}
            </div>
            {ticketCount?.uncovered?.length > 0 && (
              <p className="muted small">Matches {ticketCount.uncovered.join(", ")} are in no group -- they show “-”.</p>
            )}
            {count?.error && <p className="error">{count.error}</p>}
            {error && <p className="error">{error}</p>}
          </section>

          {build && (
            <section className="card">
              <h2>Lines -- {fmt(build.total)}</h2>
              {build.perTicket.length > 1 && <p className="muted small">{build.perTicket.map((v, i) => `Ticket ${i + 1}: ${fmt(v)}`).join(" · ")}</p>}
              <LinesGrid key={build.id} id={build.id} total={build.total} name="matchx" />
            </section>
          )}
        </>
      )}
    </>
  );
}
