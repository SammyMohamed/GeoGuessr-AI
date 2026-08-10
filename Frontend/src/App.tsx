import { useRef, useState } from "react";
import { imageFileUrl, startGame, submitGuess, uploadImage } from "./api";
import type { GuessResultResponse, RandomGameImageResponse, RankedPrediction, UploadImageResponse } from "./types";

type Mode = "idle" | "upload" | "game";
type AsyncStatus = "idle" | "loading" | "done" | "error";

function ReadoutBar({ prediction }: { prediction: RankedPrediction }) {
  const pct = Math.round(prediction.confidence * 100);
  return (
    <div className="readout-row">
      <span className="readout-rank">{prediction.rank}</span>
      <span className="readout-country">{prediction.country}</span>
      <div className="readout-track">
        <div className="readout-fill" style={{ width: `${pct}%` }} />
      </div>
      <span className="readout-pct">{pct}%</span>
    </div>
  );
}

export default function App() {
  const [mode, setMode] = useState<Mode>("idle");
  const fileInputRef = useRef<HTMLInputElement>(null);

  // --- upload flow state ---
  const [uploadStatus, setUploadStatus] = useState<AsyncStatus>("idle");
  const [uploadResult, setUploadResult] = useState<UploadImageResponse | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  // --- game flow state ---
  const [gameStatus, setGameStatus] = useState<AsyncStatus>("idle");
  const [session, setSession] = useState<RandomGameImageResponse | null>(null);
  const [guessInput, setGuessInput] = useState("");
  const [guessResult, setGuessResult] = useState<GuessResultResponse | null>(null);
  const [gameError, setGameError] = useState<string | null>(null);

  function openUploadPanel() {
    setMode("upload");
    fileInputRef.current?.click();
  }

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    setPreviewUrl(URL.createObjectURL(file));
    setUploadStatus("loading");
    setUploadError(null);
    setUploadResult(null);

    try {
      const result = await uploadImage(file);
      setUploadResult(result);
      setUploadStatus("done");
    } catch (err) {
      setUploadError(err instanceof Error ? err.message : "Something went wrong");
      setUploadStatus("error");
    }
  }

  async function handleStartGame() {
    setMode("game");
    setGameStatus("loading");
    setGameError(null);
    setGuessResult(null);
    setGuessInput("");

    try {
      const result = await startGame();
      setSession(result);
      setGameStatus("done"); // "ready to guess" — reusing done to mean "loaded"
    } catch (err) {
      setGameError(err instanceof Error ? err.message : "Something went wrong");
      setGameStatus("error");
    }
  }

  async function handleSubmitGuess() {
    if (!session || !guessInput.trim()) return;
    setGameStatus("loading");
    setGameError(null);

    try {
      const result = await submitGuess(session.sessionId, guessInput.trim());
      setGuessResult(result);
      setGameStatus("done");
    } catch (err) {
      setGameError(err instanceof Error ? err.message : "Something went wrong");
      setGameStatus("error");
    }
  }

  return (
    <div className="page">
      <header className="header">
        <p className="eyebrow">Field Classifier — Country ID</p>
        <h1>Where was this taken?</h1>
        <p className="subhead">Upload a street-view photo, or try to beat the model yourself.</p>
      </header>

      <div className="action-row">
        <button className="action-btn" onClick={openUploadPanel}>
          Identify a Photo
        </button>
        <button className="action-btn action-btn--secondary" onClick={handleStartGame}>
          Play Against the Model
        </button>
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        data-testid="file-input"
        onChange={handleFileChange}
        style={{ display: "none" }}
      />

      {mode === "upload" && (
        <section className="panel">
          {uploadStatus === "loading" && <p className="status-line">Reading the image…</p>}
          {uploadStatus === "error" && <p className="status-line status-line--error">{uploadError}</p>}

          {uploadStatus === "done" && uploadResult && (
            <div className="result-layout">
              {previewUrl && <img className="preview-image" src={previewUrl} alt="Uploaded" />}
              <div className="readout-panel">
                <p className="readout-title">Model's top guesses</p>
                {uploadResult.predictions.ensemble.map((p) => (
                  <ReadoutBar key={p.rank} prediction={p} />
                ))}
              </div>
            </div>
          )}
        </section>
      )}

      {mode === "game" && (
        <section className="panel">
          {gameStatus === "loading" && !session && <p className="status-line">Pulling an image…</p>}
          {gameStatus === "error" && <p className="status-line status-line--error">{gameError}</p>}

          {session && (
            <div className="result-layout">
              {/* Fetched from the backend by imageId — whichever image it randomly picked. */}
              <img className="preview-image" src={imageFileUrl(session.imageId)} alt="Guess the location" />

              {!guessResult && (
                <div className="guess-form">
                  <label htmlFor="guess" className="readout-title">
                    Your guess
                  </label>
                  <input
                    id="guess"
                    className="guess-input"
                    placeholder="Country name"
                    value={guessInput}
                    onChange={(e) => setGuessInput(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && handleSubmitGuess()}
                  />
                  <button
                    className="action-btn"
                    onClick={handleSubmitGuess}
                    disabled={!guessInput.trim() || gameStatus === "loading"}
                  >
                    {gameStatus === "loading" ? "Submitting…" : "Submit Guess"}
                  </button>
                </div>
              )}

              {guessResult && (
                <div className="readout-panel">
                  <p className={`verdict ${guessResult.correct ? "verdict--correct" : "verdict--incorrect"}`}>
                    {guessResult.correct ? "Correct" : "Not quite"}
                  </p>
                  <dl className="verdict-list">
                    <dt>Your guess</dt>
                    <dd>{guessResult.userGuess}</dd>
                    <dt>Model's guess</dt>
                    <dd>{guessResult.modelGuess}</dd>
                    <dt>Actual location</dt>
                    <dd>{guessResult.actualCountry}</dd>
                  </dl>
                  <button className="action-btn action-btn--secondary" onClick={handleStartGame}>
                    Play Again
                  </button>
                </div>
              )}
            </div>
          )}
        </section>
      )}
    </div>
  );
}