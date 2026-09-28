import { useEffect, useState } from "react";
import { health } from "./api";
import MatchXScreen from "./matchx/MatchXScreen";

const GAMES = [
  { id: "matchx", label: "MatchX", Screen: MatchXScreen },
];

export default function App() {
  const [active, setActive] = useState(GAMES[0].id);
  const [server, setServer] = useState(null);
  const game = GAMES.find((g) => g.id === active) ?? GAMES[0];

  useEffect(() => {
    health().then(() => setServer("ok")).catch((e) => setServer(e.message));
  }, []);

  return (
    <>
      <header className="app-header">
        <div className="brand">Wheel<span>Lab</span></div>
        <nav className="nav" aria-label="Games">
          {GAMES.map((g) => (
            <button key={g.id} className={g.id === active ? "pill selected" : "pill"} aria-current={g.id === active ? "page" : undefined}
              onClick={() => setActive(g.id)}>
              {g.label}
            </button>
          ))}
        </nav>
        <span className={server === "ok" ? "server-status muted" : "server-status error"}>
          {server === null ? "Connecting…" : server === "ok" ? "● Server connected" : server}
        </span>
      </header>
      <main>
        <game.Screen />
      </main>
    </>
  );
}
