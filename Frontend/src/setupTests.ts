import "@testing-library/jest-dom";
import { vi } from "vitest";

// jsdom doesn't implement createObjectURL — stub it so App's preview-image
// logic doesn't throw during tests.
globalThis.URL.createObjectURL = vi.fn(() => "blob:mock-url");
