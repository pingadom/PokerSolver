import { useEffect, useState, type FormEvent } from "react";
import { parseScenario, request, type Results, type Simulation } from "./api";
import { presets, scenarioFromPreset, type Preset } from "./presets";

const number = new Intl.NumberFormat("en-GB");
const initialPlayers = [
  { name: "AA", cards: "AS AH" },
  { name: "KK", cards: "KS KH" },
  { name: "QQ", cards: "QS QH" },
];

export default function App() {
  const [players, setPlayers] = useState(initialPlayers);
  const [board, setBoard] = useState("");
  const [iterations, setIterations] = useState("1000000");
  const [seed, setSeed] = useState("42");
  const [formError, setFormError] = useState("");
  const [loadError, setLoadError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [simulation, setSimulation] = useState<Simulation | null>(null);
  const [results, setResults] = useState<Results | null>(null);
  const [history, setHistory] = useState<Simulation[]>([]);
  const [historyError, setHistoryError] = useState(false);
  const [retry, setRetry] = useState(0);
  const [loadedPreset, setLoadedPreset] = useState<Preset | null>(null);

  function loadPreset(preset: Preset) {
    const scenario = scenarioFromPreset(preset);
    setPlayers(
      scenario.players.map((player) => ({
        name: player.name,
        cards: player.cards.join(" "),
      })),
    );
    setBoard(scenario.board.join(" "));
    setIterations(String(scenario.iterations));
    setSeed(scenario.seed ?? "");
    setFormError("");
    setLoadError("");
    setSelected(null);
    setSimulation(null);
    setResults(null);
    setLoadedPreset(preset);
    document.getElementById("new-simulation")?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  }

  useEffect(() => {
    const abort = new AbortController();
    request<Simulation[]>("?limit=8", { signal: abort.signal })
      .then((value) => {
        setHistory(value);
        setHistoryError(false);
      })
      .catch(() => {
        if (!abort.signal.aborted) setHistoryError(true);
      });
    return () => abort.abort();
  }, [selected, simulation?.status]);

  useEffect(() => {
    if (!selected) return;
    const abort = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    setSimulation(null);
    setResults(null);
    setLoadError("");
    async function poll() {
      try {
        const current = await request<Simulation>(`/${selected}`, {
          signal: abort.signal,
        });
        if (abort.signal.aborted) return;
        setSimulation(current);
        if (current.status === "COMPLETED") {
          const final = await request<Results>(`/${selected}/results`, {
            signal: abort.signal,
          });
          if (!abort.signal.aborted) setResults(final);
        } else if (
          current.status === "QUEUED" ||
          current.status === "RUNNING"
        ) {
          timer = setTimeout(poll, 2000);
        }
      } catch (error) {
        if (!abort.signal.aborted)
          setLoadError(
            error instanceof Error
              ? error.message
              : "Unable to load simulation.",
          );
      }
    }
    void poll();
    return () => {
      abort.abort();
      clearTimeout(timer);
    };
  }, [selected, retry]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setFormError("");
    try {
      const scenario = parseScenario(players, board, iterations, seed);
      setSubmitting(true);
      const created = await request<{ simulationId: string }>("", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(scenario),
      });
      setSelected(created.simulationId);
    } catch (error) {
      setFormError(
        error instanceof Error ? error.message : "Unable to submit simulation.",
      );
    } finally {
      setSubmitting(false);
    }
  }
  const progress = simulation
    ? simulation.completedIterations / simulation.requestedIterations
    : 0;
  return (
    <div className="workspace">
      <aside className="sidebar">
        <a className="brand" href="/" aria-label="PokerLab home">
          <span className="brand-mark">♠</span>
          <span>
            PokerLab<span className="cloud">CLOUD</span>
          </span>
        </a>
        <div className="nav-label">WORKSPACE</div>
        <a className="nav-item active" href="#new-simulation">
          <span>◈</span> Equity simulator
        </a>
        <a className="nav-item" href="#matchups">
          <span>◇</span> Example matchups
        </a>
        <a className="nav-item" href="#trainer">
          <span>♧</span> GTO trainer
        </a>
        <a className="nav-item" href="#recent">
          <span>◷</span> Recent simulations
        </a>
        <div className="sidebar-note">
          <span className="small-label">ABOUT THIS TOOL</span>
          <p>Texas Hold’em equity calculator</p>
          <small>Exact hole cards · simulated remaining boards</small>
        </div>
        <div className="sidebar-bottom">EXACT HANDS · 2–9 PLAYERS</div>
      </aside>
      <main>
        <header className="topbar">
          <span>
            Workspace <span className="slash">/</span> Equity simulator
          </span>
          <span className="edition">POKERLAB / 01</span>
        </header>
        <div className="page-content">
          <div className="page-heading">
            <div>
              <p className="eyebrow">TEXAS HOLD’EM</p>
              <h1>Hand equity simulator</h1>
              <p className="subtitle">
                Enter exact hands and known board cards to estimate each
                player’s share of the pot.
              </p>
            </div>
            <span className="heading-suit" aria-hidden="true">
              ♠
            </span>
          </div>
          <div className="main-grid">
            <section
              className="panel scenario-panel"
              id="new-simulation"
              aria-labelledby="scenario-title"
            >
              <div className="panel-heading">
                <div>
                  <span className="section-number">01</span>
                  <h2 id="scenario-title">Set up a simulation</h2>
                </div>
                <span className="pill">Texas Hold’em</span>
              </div>
              {loadedPreset && (
                <div className="loaded-preset" role="status">
                  <strong>{loadedPreset.title} loaded.</strong> Review the
                  cards, then run the simulation.
                </div>
              )}
              <form onSubmit={submit}>
                <div className="section-title">
                  <h3>Players & hole cards</h3>
                  <span>{players.length} / 9 players</span>
                </div>
                <p className="field-hint" id="card-help">
                  Two cards per player. Use A K Q J T 9–2 and S H D C.
                </p>
                <div className="players">
                  {players.map((player, index) => (
                    <div className="player-row" key={index}>
                      <span className={`player-marker marker-${index % 3}`}>
                        {String(index + 1).padStart(2, "0")}
                      </span>
                      <label>
                        <span className="sr-only">Player {index + 1} name</span>
                        <input
                          maxLength={80}
                          aria-label={`Player ${index + 1} name`}
                          value={player.name}
                          onChange={(e) => {
                            setLoadedPreset(null);
                            setPlayers(
                              players.map((p, i) =>
                                i === index
                                  ? { ...p, name: e.target.value }
                                  : p,
                              ),
                            );
                          }}
                          required
                        />
                      </label>
                      <label>
                        <span className="sr-only">
                          Player {index + 1} hole cards
                        </span>
                        <input
                          className="card-input"
                          aria-label={`Player ${index + 1} hole cards`}
                          aria-describedby="card-help"
                          value={player.cards}
                          onChange={(e) => {
                            setLoadedPreset(null);
                            setPlayers(
                              players.map((p, i) =>
                                i === index
                                  ? { ...p, cards: e.target.value }
                                  : p,
                              ),
                            );
                          }}
                          required
                          placeholder="AS KH"
                        />
                      </label>
                      <button
                        className="remove"
                        type="button"
                        disabled={players.length <= 2}
                        onClick={() => {
                          setLoadedPreset(null);
                          setPlayers(players.filter((_, i) => i !== index));
                        }}
                        aria-label={`Remove player ${index + 1}`}
                      >
                        ×
                      </button>
                    </div>
                  ))}
                </div>
                <button
                  className="text-button"
                  type="button"
                  disabled={players.length >= 9}
                  onClick={() => {
                    setLoadedPreset(null);
                    setPlayers([
                      ...players,
                      { name: `Player ${players.length + 1}`, cards: "" },
                    ]);
                  }}
                >
                  + Add player
                </button>
                <div className="divider" />
                <label className="field-label" htmlFor="board">
                  Community cards <span>Optional</span>
                </label>
                <input
                  id="board"
                  value={board}
                  onChange={(e) => {
                    setLoadedPreset(null);
                    setBoard(e.target.value);
                  }}
                  placeholder="e.g.  7H  8H  9C"
                  aria-describedby="board-help"
                />
                <p className="field-hint" id="board-help">
                  Leave empty for preflop, or enter up to five known cards.
                </p>
                <div className="divider" />
                <div className="settings-grid">
                  <div>
                    <label className="field-label" htmlFor="iterations">
                      Number of trials
                    </label>
                    <input
                      id="iterations"
                      type="number"
                      min="1"
                      max="100000000"
                      step="1"
                      value={iterations}
                      onChange={(e) => {
                        setLoadedPreset(null);
                        setIterations(e.target.value);
                      }}
                      required
                    />
                  </div>
                  <div>
                    <label className="field-label" htmlFor="seed">
                      Random seed <span>Optional</span>
                    </label>
                    <input
                      id="seed"
                      type="text"
                      inputMode="numeric"
                      value={seed}
                      onChange={(e) => {
                        setLoadedPreset(null);
                        setSeed(e.target.value);
                      }}
                      placeholder="Auto"
                    />
                  </div>
                </div>
                <p className="field-hint">
                  More trials reduce sampling noise. A seed makes the run
                  repeatable.
                </p>
                {formError && (
                  <p className="error" role="alert">
                    {formError}
                  </p>
                )}
                <button className="primary" disabled={submitting} type="submit">
                  {submitting ? "Submitting…" : "Run simulation"}
                  <span aria-hidden="true">↗</span>
                </button>
                <p className="form-footnote">
                  Equity estimates are computed from simulated boards.
                </p>
              </form>
            </section>
            <section
              className="panel results-panel"
              aria-labelledby="results-title"
            >
              <div className="panel-heading">
                <div>
                  <span className="section-number">02</span>
                  <h2 id="results-title">Results</h2>
                </div>
                <span className={`pill ${results ? "success" : ""}`}>
                  {simulation?.status.toLowerCase() ?? "No run selected"}
                </span>
              </div>
              {!selected && (
                <div className="empty-state">
                  <div className="card-fan" aria-hidden="true">
                    <span>
                      A<small>♠</small>
                    </span>
                    <span>
                      K<small>♥</small>
                    </span>
                    <span>
                      Q<small>♣</small>
                    </span>
                  </div>
                  <h3>No results yet</h3>
                  <p>
                    Run the hands in the form, or load an example matchup below.
                  </p>
                  <div className="empty-stats">
                    <span>WIN PROBABILITY</span>
                    <span>SPLIT POTS</span>
                    <span>EQUITY</span>
                  </div>
                </div>
              )}
              {selected && !simulation && !loadError && (
                <p className="loading" role="status">
                  Loading your simulation…
                </p>
              )}
              {loadError && (
                <div className="error" role="alert">
                  {loadError}{" "}
                  <button type="button" onClick={() => setRetry(retry + 1)}>
                    Retry
                  </button>
                </div>
              )}
              {simulation && (
                <div className="simulation-detail">
                  <div className="run-meta">
                    <span>
                      {number.format(simulation.requestedIterations)} trials
                    </span>
                    <code title={simulation.simulationId}>
                      {simulation.simulationId.slice(0, 8)}
                    </code>
                  </div>
                  <div className="progress-caption">
                    <strong>{Math.round(progress * 100)}% complete</strong>
                    <span>
                      {simulation.completedBatches} / {simulation.totalBatches}{" "}
                      batches
                    </span>
                  </div>
                  <progress
                    value={simulation.completedIterations}
                    max={simulation.requestedIterations}
                    aria-label="Simulation progress"
                  />
                  <p className="field-hint" aria-live="polite">
                    {number.format(simulation.completedIterations)} of{" "}
                    {number.format(simulation.requestedIterations)} trials
                    processed
                  </p>
                  {simulation.status === "FAILED" && (
                    <p role="alert" className="error">
                      {simulation.errorMessage ?? "Simulation failed."}
                    </p>
                  )}
                  {simulation.status === "CANCELLED" && (
                    <p role="status">This simulation was cancelled.</p>
                  )}
                  {results && (
                    <>
                      <div className="equities">
                        {results.players.map((p, i) => (
                          <div className="equity-row" key={p.name}>
                            <div>
                              <span>
                                <i className={`dot marker-${i % 3}`} />
                                {p.name}
                              </span>
                              <strong>
                                {(p.equity * 100).toFixed(2)}
                                <small>%</small>
                              </strong>
                            </div>
                            <div className="equity-track">
                              <span
                                className={`marker-${i % 3}`}
                                style={{ width: `${p.equity * 100}%` }}
                              />
                            </div>
                          </div>
                        ))}
                      </div>
                      <div className="table-scroll">
                        <table>
                          <caption className="sr-only">
                            Results per player
                          </caption>
                          <thead>
                            <tr>
                              <th>Player</th>
                              <th>Wins</th>
                              <th>Ties</th>
                              <th>Losses</th>
                            </tr>
                          </thead>
                          <tbody>
                            {results.players.map((p) => (
                              <tr key={p.name}>
                                <th scope="row">{p.name}</th>
                                <td>{number.format(p.wins)}</td>
                                <td>{number.format(p.ties)}</td>
                                <td>{number.format(p.losses)}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                      <div className="result-footer">
                        <span>
                          Completed in {(results.elapsedMs / 1000).toFixed(2)}s
                        </span>
                        <span>Seed {simulation.configuration.seed}</span>
                      </div>
                      <p className="field-hint">
                        Equity includes your share of tied pots. Counts show
                        outright wins and participation in ties.
                      </p>
                    </>
                  )}
                </div>
              )}
            </section>
          </div>
          <section
            className="solver-preview"
            id="solver"
            aria-labelledby="solver-title"
          >
            <div className="solver-preview-copy">
              <div className="solver-preview-topline">
                <p className="eyebrow">SOLVER PROJECT</p>
                <span className="solver-status">IN DEVELOPMENT</span>
              </div>
              <h2 id="solver-title">GTO trainer</h2>
              <p>
                Try a ten-decision preflop drill powered by our own solver. It
                covers one clearly defined all-in spot with two active players
                at a six-seat table. The strategy and ranges are validation
                examples.
              </p>
              <a className="solver-demo-link" href="#trainer">Open trainer demo ↗</a>
              <p><a className="solver-demo-link" href="#river">River research drill ↗</a></p>
              <p><a className="solver-demo-link" href="#turn-river">Turn and river research drill ↗</a></p>
            </div>
            <ol className="solver-milestones">
              <li>
                <span>BUILT</span>
                <strong>CFR benchmark and all-in game model</strong>
              </li>
              <li>
                <span>DEMO</span>
                <strong>One exact-pack preflop drill</strong>
              </li>
              <li>
                <span>RESEARCH</span>
                <strong>Fixed-board river decision drill</strong>
              </li>
              <li>
                <span>RESEARCH</span>
                <strong>Turn-to-river chance game and drill</strong>
              </li>
              <li>
                <span>LATER</span>
                <strong>Reviewed ranges and connected-hand practice</strong>
              </li>
            </ol>
          </section>
          <section
            className="matchups"
            id="matchups"
            aria-labelledby="matchups-title"
          >
            <div className="matchups-heading">
              <div>
                <p className="eyebrow">EXAMPLE MATCHUPS</p>
                <h2 id="matchups-title">Hands worth comparing</h2>
                <p>
                  Load a setup, run it, then change one card or add a player to
                  see what moves the equity.
                </p>
              </div>
              <span>200,000 trials per example</span>
            </div>
            <div className="preset-grid">
              {presets.map((preset) => (
                <button
                  className={`preset-card ${loadedPreset?.id === preset.id ? "selected" : ""}`}
                  type="button"
                  key={preset.id}
                  onClick={() => loadPreset(preset)}
                  disabled={submitting}
                  aria-label={`Load ${preset.title}`}
                >
                  <span className="preset-topline">
                    <span>
                      {preset.street} · {preset.players.length} players
                    </span>
                    <span aria-hidden="true">↗</span>
                  </span>
                  <strong>{preset.title}</strong>
                  <span className="preset-hands">
                    {preset.players.map((player) => (
                      <span key={player.name}>
                        <span>{player.name}</span>
                        <code>{player.cards}</code>
                      </span>
                    ))}
                  </span>
                  {preset.board && (
                    <span className="preset-board">Board: {preset.board}</span>
                  )}
                  <span className="preset-question">{preset.question}</span>
                </button>
              ))}
            </div>
          </section>
          <section className="recent" id="recent">
            <div className="recent-heading">
              <h2>Recent simulations</h2>
              <span>YOUR LATEST RUNS</span>
            </div>
            {historyError ? (
              <p className="field-hint">
                History is unavailable. Check the API connection.
              </p>
            ) : history.length === 0 ? (
              <p className="field-hint">
                No simulations yet. Completed and recent runs will appear here.
              </p>
            ) : (
              <div className="history-grid">
                {history.map((run) => (
                  <button
                    className={`history-item ${selected === run.simulationId ? "selected" : ""}`}
                    onClick={() => {
                      setLoadedPreset(null);
                      setSelected(run.simulationId);
                    }}
                    key={run.simulationId}
                  >
                    <span className="history-title">
                      {run.configuration.players
                        .map((p) => p.name)
                        .join(" vs ")}
                    </span>
                    <span>
                      {number.format(run.requestedIterations)} trials{" "}
                      <span className="history-status">
                        {run.status.toLowerCase()}
                      </span>
                    </span>
                  </button>
                ))}
              </div>
            )}
          </section>
          <footer>
            Monte Carlo estimates for exact Texas Hold’em hands.
            <span>POKERLAB CLOUD</span>
          </footer>
        </div>
      </main>
    </div>
  );
}
