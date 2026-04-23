import { afterEach, describe, expect, it, vi } from "vitest";

import { getReviewerAssist, runReviewerAssist } from "../lib/workflow-api";

function jsonResponse(body: unknown) {
  return {
    ok: true,
    status: 200,
    json: () => Promise.resolve(body)
  };
}

describe("agent projection API", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("creates reviewer assist intent instead of legacy task summary", async () => {
    const fetch = vi.fn(() => Promise.resolve(jsonResponse({
      intentId: 88,
      analysisType: "REVIEWER_ASSIST",
      businessStatus: "REQUESTED"
    })));
    vi.stubGlobal("fetch", fetch);

    const response = await runReviewerAssist(9);

    expect(response).toEqual({
      intentId: 88,
      analysisType: "REVIEWER_ASSIST",
      businessStatus: "REQUESTED"
    });
    expect(fetch).toHaveBeenCalledWith(
      "/api/review-assignments/9/agent-assist",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ force: false })
      })
    );
    expect(JSON.stringify(response)).not.toContain("taskStatus");
  });

  it("reads reviewer assist projections", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(jsonResponse({
      intent: {
        intentId: 88,
        analysisType: "REVIEWER_ASSIST",
        businessStatus: "AVAILABLE"
      },
      projections: [
        {
          projectionId: 1,
          analysisType: "REVIEWER_ASSIST",
          businessStatus: "AVAILABLE",
          summaryText: "Reviewer-visible signal",
          redactedResult: { paperSummary: "Reviewer-visible signal" },
          superseded: false,
          updatedAt: "2026-04-22T12:00:00Z"
        }
      ]
    }))));

    const state = await getReviewerAssist(9);

    expect(state.intent?.businessStatus).toBe("AVAILABLE");
    expect(state.projections[0].redactedResult).toEqual({ paperSummary: "Reviewer-visible signal" });
  });
});
