import type { ReactNode } from "react";
import type { MultiwaySeat } from "./multiwayApi";

export type TableMove = "waiting" | "blind" | "fold" | "check" | "call" | "raise";
export type TablePlayer = {
  seat: MultiwaySeat;
  status: string;
  stack: string;
  contribution: string;
  move: TableMove;
  moveLabel: string;
  folded: boolean;
  allIn: boolean;
};
const seats: MultiwaySeat[] = ["UTG", "HJ", "CO", "BTN", "SB", "BB"];

export default function PokerTable({ players, heroSeat, heroCombo, potBb, toCallBb, selectedMove, children }: {
  players: TablePlayer[]; heroSeat: MultiwaySeat; heroCombo: string;
  potBb: number; toCallBb: number; selectedMove?: { kind: TableMove; label: string };
  children?: ReactNode;
}) {
  const heroIndex = seats.indexOf(heroSeat);
  return <div className="poker-table-stage">
    <div className="poker-table" aria-label="Six-seat action table">
      <div className="poker-felt" aria-hidden="true" />
      <div className="poker-table-center">
        <strong><small>Pot</small> {potBb} bb</strong>
        <span className="poker-call">{toCallBb > 0 ? `${toCallBb} bb to call` : "No bet to call"}</span>
      </div>
      {players.map((player) => {
        const hero = player.seat === heroSeat;
        const move = hero && selectedMove ? selectedMove.kind : player.move;
        const position = (seats.indexOf(player.seat) - heroIndex + seats.length) % seats.length;
        return <div key={player.seat} aria-label={`${player.seat} player`}
          className={`poker-player position-${position} ${hero ? "hero" : "villain"} ${player.folded ? "folded" : ""} ${hero && !selectedMove ? "acting" : ""} move-${move}`}>
          <div className="poker-player-panel">
            <div className="poker-player-heading"><strong>{player.seat}</strong>
              {player.seat === "BTN" && <span className="poker-dealer" title="Dealer button" aria-label="Dealer button">D</span>}
              <span className="poker-role">{hero ? "HERO · YOU" : "VILLAIN"}</span>
            </div>
            {hero ? <div className="poker-cards" aria-label="Your cards">
              {heroCombo.split(" ").map((card) => <span key={card} aria-label={card}
                className={card.endsWith("h") || card.endsWith("d") ? "red" : ""}>
                {card.slice(0, -1)}{({ s: "♠", h: "♥", d: "♦", c: "♣" } as Record<string, string>)[card.slice(-1)]}
              </span>)}
            </div> : <div className="poker-card-backs" aria-hidden="true"><span /><span /></div>}
            <span className="poker-stack">{player.stack}</span>
            <span className="poker-contribution">{player.contribution}</span>
          </div>
          <div className="poker-move" aria-live={hero ? "polite" : "off"}>
            <span>{hero && selectedMove ? `You chose · ${selectedMove.label}` : hero ? "▶ Your turn" : player.allIn ? `All-in · ${player.moveLabel}` : player.status}</span>
            {!(hero && selectedMove) && <strong>{player.moveLabel}</strong>}
          </div>
        </div>;
      })}
    </div>
    {children && <div className="poker-hero-controls" aria-label="Your decision controls">{children}</div>}
    <div className="poker-legend" aria-label="Table colour key">
      <span className="hero">Blue: you</span><span className="villain">Red: opponents</span>
      <span className="check">Yellow: check</span><span className="call">Green: call</span>
      <span className="raise">Red: raise / all-in</span><span className="fold">Grey: fold</span>
    </div>
  </div>;
}
