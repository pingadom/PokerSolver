type HandAction = "k" | "b" | "c" | "f";

type PublicAction = {
  street: string;
  player: 0 | 1;
  action: HandAction;
};

type BoardCard = {
  text: string;
  red?: boolean;
  label?: string;
};

const actionName: Record<HandAction, string> = {
  k: "Checked", b: "Bet", c: "Called", f: "Folded",
};

type HandTableProps = {
  street: string;
  heroPlayer: 0 | 1;
  heroCombo: string;
  opponentCombo: string | null;
  heroRemainingStackBb: number;
  potBb: number;
  board: BoardCard[];
  publicActions: PublicAction[];
  complete: boolean;
  showdown: boolean;
};

export default function HandTable({
  street, heroPlayer, heroCombo, opponentCombo, heroRemainingStackBb,
  potBb, board, publicActions, complete, showdown,
}: HandTableProps) {
  const latestAction = (player: 0 | 1) =>
    [...publicActions].reverse().find((event) => event.player === player);
  const playerState = (player: 0 | 1) => {
    const last = latestAction(player);
    if (complete && showdown) return "At showdown";
    if (complete && last?.action === "f") return "Folded";
    if (complete && publicActions.at(-1)?.action === "f") return "Won by fold";
    if (!complete && player === heroPlayer) return "Your turn";
    if (last) return `Last: ${actionName[last.action].toLowerCase()} · ${last.street.toLowerCase()}`;
    return "No action yet";
  };
  const opponentPlayer = heroPlayer === 0 ? 1 : 0;
  const lastEvent = publicActions.at(-1);
  const shownOpponentCombo = complete && showdown ? opponentCombo : null;

  return (
    <section className="hand-table" aria-label="Players and table">
      <div className="hand-table-main">
        <div className="hand-player hand-player-opponent" aria-label={`Opponent, ${opponentPlayer === 0 ? "first" : "second"} player`}>
          <div className="hand-player-heading"><strong>Opponent</strong><span>{opponentPlayer === 0 ? "First player" : "Second player"}</span></div>
          <div className="hand-player-cards" aria-label={shownOpponentCombo ? "Opponent cards revealed" : "Opponent cards hidden"}>
            {shownOpponentCombo ? shownOpponentCombo.split(" ").map((card) => <span key={card}>{card}</span>) : <><span className="hidden-card">?</span><span className="hidden-card">?</span></>}
          </div>
          <p className="hand-player-state">{playerState(opponentPlayer)}</p>
        </div>

        <div className="hand-table-center">
          <div className="hand-table-street"><span>{street}</span><strong>{potBb.toFixed(1)} bb pot</strong></div>
          <div className="hand-table-board" aria-label={`${street.toLowerCase()} board`}>
            {board.map((card, index) => <span key={`${card.text}-${index}`} className={`${card.red ? "red" : ""} ${card.label ? "unrevealed" : ""}`} aria-label={card.label}>{card.text}</span>)}
          </div>
          <p className="hand-table-latest">{lastEvent ? `Latest: ${lastEvent.player === heroPlayer ? "You" : "Opponent"} ${actionName[lastEvent.action].toLowerCase()} · ${lastEvent.street.toLowerCase()}` : "No actions yet"}</p>
        </div>

        <div className="hand-player hand-player-hero" aria-label={`You, ${heroPlayer === 0 ? "first" : "second"} player`}>
          <div className="hand-player-heading"><strong>You</strong><span>{heroPlayer === 0 ? "First player" : "Second player"}</span></div>
          <div className="hand-player-cards" aria-label="Your cards">{heroCombo.split(" ").map((card) => <span key={card}>{card}</span>)}</div>
          <p className="hand-player-state">{playerState(heroPlayer)}</p>
          <small>{heroRemainingStackBb.toFixed(1)} bb behind</small>
        </div>
      </div>
    </section>
  );
}
