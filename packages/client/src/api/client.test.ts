import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "./client";

describe("api.updateServerSettings", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => ({
        settings: {
          serviceWorkerEnabled: true,
          persistRemoteSessionsToDisk: false,
        },
      }),
    } as Response);

    vi.stubGlobal("fetch", fetchMock);
    window.history.replaceState({}, "", "/");
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("serializes undefined setting values as null so clears reach the server", async () => {
    await api.updateServerSettings({
      globalInstructions: undefined,
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [, request] = fetchMock.mock.calls[0] ?? [];
    expect(request?.body).toBe(JSON.stringify({ globalInstructions: null }));
  });
});

describe("api.login", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal("fetch", fetchMock);
    window.history.replaceState({}, "", "/");
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("fails with a timeout error after 60 seconds", async () => {
    fetchMock.mockImplementation((_input, init) => {
      return new Promise<Response>((_resolve, reject) => {
        const signal = init?.signal as AbortSignal | undefined;
        signal?.addEventListener("abort", () => {
          reject(new Error("Aborted"));
        });
      });
    });

    const loginPromise = api.login("secret");
    const assertion = expect(loginPromise).rejects.toThrow(
      "Login timed out after 60 seconds",
    );
    await vi.advanceTimersByTimeAsync(60_000);

    await assertion;
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
