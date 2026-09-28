import { useEffect, useState } from "react";
import { health } from "./api";
import MatchXScreen from "./matchx/MatchXScreen";
import PickScreen from "./pick/PickScreen";

const LOTTO = {
  id: "lotto",
  title: "LOTTO",
  intro: "Pick 6 numbers from 1-49 (or set your own); as many numbers are drawn as a line has.",
  game: { numbers: 49, pick: 6, draw: 6 },
  drawEqualsPick: true,
  numbersRange: [5, 100],
  pickRange: [1, 20],
};

const KENO = {
  id: "keno",
  title: "KENO",
  intro: "Pick 1 to 12 numbers (spots) from 1-80; 20 numbers are drawn.",
  game: { numbers: 80, pick: 5, draw: 20 },
  drawEqualsPick: false,
  numbersRange: null,
  pickRange: [1, 12],
  pickLabel: "Spots",
};

function LottoScreen() {
  return <PickScreen preset={LOTTO} />;
}

function KenoScreen() {
  return <PickScreen preset={KENO} />;
}

const GAMES = [
  { id: "matchx", label: "MatchX", Screen: MatchXScreen },
  { id: "lotto", label: "LOTTO", Screen: LottoScreen },
  { id: "keno", label: "KENO", Screen: KenoScreen },
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
