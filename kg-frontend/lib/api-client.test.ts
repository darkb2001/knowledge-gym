import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  __resetApiClientForTests,
  apiRequest,
  ensureAccessToken,
  getAccessToken,
  RefreshUnreachableError,
  setAccessToken,
} from "./api-client";
import { applyOAuthHash } from "./auth";

describe("api-client refresh coalescing", () => {
  beforeEach(() => {
    __resetApiClientForTests();
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("coalesces concurrent ensureAccessToken into one refresh POST", async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ accessToken: "tok-a", expiresIn: 900 }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    const [a, b] = await Promise.all([ensureAccessToken(), ensureAccessToken()]);

    expect(a).toBe(true);
    expect(b).toBe(true);
    expect(getAccessToken()).toBe("tok-a");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0][0])).toContain("/auth/refresh");
  });

  it("retries original request exactly once after successful refresh", async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock
      .mockResolvedValueOnce(new Response("{}", { status: 401 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ accessToken: "tok-b" }), {
          status: 200,
          headers: { "content-type": "application/json" },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { "content-type": "application/json" },
        }),
      );

    setAccessToken("stale");
    const data = await apiRequest<{ ok: boolean }>("/questions");

    expect(data).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(getAccessToken()).toBe("tok-b");
  });

  it("throws RefreshUnreachableError on network blip without clearing session", async () => {
    setAccessToken("keep-me");
    const fetchMock = vi.mocked(fetch);
    fetchMock
      .mockResolvedValueOnce(new Response("{}", { status: 401 }))
      .mockRejectedValueOnce(new TypeError("Failed to fetch"));

    await expect(apiRequest("/questions")).rejects.toBeInstanceOf(RefreshUnreachableError);
    expect(getAccessToken()).toBe("keep-me");
  });

  it("returns false (session dead) on definitive 401 from refresh", async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(new Response("{}", { status: 401 }));

    await expect(ensureAccessToken()).resolves.toBe(false);
    expect(getAccessToken()).toBeNull();
  });
});

describe("applyOAuthHash", () => {
  beforeEach(() => {
    __resetApiClientForTests();
  });

  it("parses valid hash and stores access token", () => {
    const parsed = applyOAuthHash(
      "#accessToken=abc.def&userId=11111111-1111-1111-1111-111111111111&role=USER",
    );
    expect(parsed).toEqual({
      accessToken: "abc.def",
      userId: "11111111-1111-1111-1111-111111111111",
      role: "USER",
    });
    expect(getAccessToken()).toBe("abc.def");
  });

  it("returns null when accessToken or userId missing", () => {
    expect(applyOAuthHash("#role=USER")).toBeNull();
    expect(applyOAuthHash("#accessToken=only")).toBeNull();
    expect(getAccessToken()).toBeNull();
  });
});
