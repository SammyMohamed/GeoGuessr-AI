import { beforeEach, describe, expect, it, vi } from "vitest";
import { startGame, submitGuess, uploadImage } from "./api";

function mockFetchOnce(body: unknown, ok = true, status = 200) {
  globalThis.fetch = vi.fn().mockResolvedValue({
    ok,
    status,
    json: async () => body,
  }) as unknown as typeof fetch;
}

describe("api", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("uploadImage posts multipart form data and returns the parsed response", async () => {
    const responseBody = {
      imageId: 1,
      predictions: { resnet50: [], clip: [], ensemble: [] },
    };
    mockFetchOnce(responseBody);

    const result = await uploadImage(new File(["abc"], "test.png", { type: "image/png" }));

    expect(result).toEqual(responseBody);
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8080/images",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("uploadImage throws the server's error message on a non-OK response", async () => {
    mockFetchOnce({ error: "Invalid image" }, false, 400);

    await expect(uploadImage(new File(["abc"], "bad.txt"))).rejects.toThrow("Invalid image");
  });

  it("startGame fetches the random-image endpoint", async () => {
    const responseBody = { sessionId: 5, imageId: 9 };
    mockFetchOnce(responseBody);

    const result = await startGame();

    expect(result).toEqual(responseBody);
    expect(fetch).toHaveBeenCalledWith("http://localhost:8080/game/random-image");
  });

  it("submitGuess posts a JSON body with the guessed country", async () => {
    const responseBody = {
      sessionId: 5,
      userGuess: "Kenya",
      modelGuess: "Kenya",
      actualCountry: "Kenya",
      correct: true,
    };
    mockFetchOnce(responseBody);

    const result = await submitGuess(5, "Kenya");

    expect(result).toEqual(responseBody);
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8080/game/5/guess",
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ guessedCountry: "Kenya" }),
      }),
    );
  });

  it("falls back to a generic error message when the error body isn't JSON", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => {
        throw new Error("not json");
      },
    }) as unknown as typeof fetch;

    await expect(startGame()).rejects.toThrow("Request failed with status 500");
  });
});
