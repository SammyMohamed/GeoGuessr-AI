import type { ErrorResponse, GuessResultResponse, RandomGameImageResponse, UploadImageResponse } from "./types";

// TODO: move this to an env var (import.meta.env.VITE_API_BASE_URL) once
// we set up local running / deployment — hardcoded for now per the current
// scope (frontend only, not worrying about running/deploying yet).
const API_BASE = "http://localhost:8080";

async function parseErrorOrThrow(response: Response): Promise<never> {
  let message = `Request failed with status ${response.status}`;
  try {
    const body = (await response.json()) as ErrorResponse;
    if (body.error) message = body.error;
  } catch {
    // response wasn't JSON — fall back to the generic message above
  }
  throw new Error(message);
}

export async function uploadImage(file: File): Promise<UploadImageResponse> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`${API_BASE}/images`, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) return parseErrorOrThrow(response);
  return response.json();
}

export async function startGame(): Promise<RandomGameImageResponse> {
  const response = await fetch(`${API_BASE}/game/random-image`);
  if (!response.ok) return parseErrorOrThrow(response);
  return response.json();
}

export async function submitGuess(sessionId: number, guessedCountry: string): Promise<GuessResultResponse> {
  const response = await fetch(`${API_BASE}/game/${sessionId}/guess`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ guessedCountry }),
  });

  if (!response.ok) return parseErrorOrThrow(response);
  return response.json();
}
