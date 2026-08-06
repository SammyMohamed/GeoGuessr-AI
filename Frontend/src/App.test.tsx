import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "./App";
import * as api from "./api";

vi.mock("./api");

describe("App", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("renders the header and both action buttons", () => {
    render(<App />);

    expect(screen.getByText("Where was this taken?")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Identify a Photo" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Play Against the Model" })).toBeInTheDocument();
  });

  it("uploads a file and displays the ensemble predictions", async () => {
    vi.mocked(api.uploadImage).mockResolvedValue({
      imageId: 1,
      predictions: {
        resnet50: [],
        clip: [],
        ensemble: [{ country: "France", confidence: 0.5, rank: 1 }],
      },
    });

    render(<App />);
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Identify a Photo" }));
    const fileInput = screen.getByTestId("file-input") as HTMLInputElement;

    await user.upload(fileInput, new File(["abc"], "photo.png", { type: "image/png" }));

    await waitFor(() => expect(screen.getByText("France")).toBeInTheDocument());
    expect(screen.getByText("50%")).toBeInTheDocument();
    expect(api.uploadImage).toHaveBeenCalledTimes(1);
  });

  it("shows an error message when the upload fails", async () => {
    vi.mocked(api.uploadImage).mockRejectedValue(new Error("Invalid image"));

    render(<App />);
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Identify a Photo" }));
    const fileInput = screen.getByTestId("file-input") as HTMLInputElement;

    await user.upload(fileInput, new File(["abc"], "bad.png", { type: "image/png" }));

    await waitFor(() => expect(screen.getByText("Invalid image")).toBeInTheDocument());
  });

  it("plays a round and shows the verdict", async () => {
    vi.mocked(api.startGame).mockResolvedValue({ sessionId: 7, imageId: 3 });
    vi.mocked(api.submitGuess).mockResolvedValue({
      sessionId: 7,
      userGuess: "Kenya",
      modelGuess: "Tanzania",
      actualCountry: "Kenya",
      correct: true,
    });

    render(<App />);
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Play Against the Model" }));
    await waitFor(() => expect(screen.getByLabelText("Your guess")).toBeInTheDocument());

    await user.type(screen.getByLabelText("Your guess"), "Kenya");
    await user.click(screen.getByRole("button", { name: "Submit Guess" }));

    await waitFor(() => expect(screen.getByText("Correct")).toBeInTheDocument());
    expect(screen.getByText("Tanzania")).toBeInTheDocument();
    expect(api.submitGuess).toHaveBeenCalledWith(7, "Kenya");
  });

  it("shows an error message when starting a game fails", async () => {
    vi.mocked(api.startGame).mockRejectedValue(new Error("Service unavailable"));

    render(<App />);
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Play Against the Model" }));

    await waitFor(() => expect(screen.getByText("Service unavailable")).toBeInTheDocument());
  });

  it("disables the submit button until a guess is typed", async () => {
    vi.mocked(api.startGame).mockResolvedValue({ sessionId: 1, imageId: 1 });

    render(<App />);
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Play Against the Model" }));
    await waitFor(() => expect(screen.getByLabelText("Your guess")).toBeInTheDocument());

    expect(screen.getByRole("button", { name: "Submit Guess" })).toBeDisabled();

    await user.type(screen.getByLabelText("Your guess"), "Peru");

    expect(screen.getByRole("button", { name: "Submit Guess" })).toBeEnabled();
  });
});
