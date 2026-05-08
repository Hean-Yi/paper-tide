import { flushPromises, mount } from "@vue/test-utils";
import ElementPlus from "element-plus";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { createAppRouter } from "../router";
import { initializeAuth, resetAuthForTests } from "../stores/auth";
import DashboardView from "../views/DashboardView.vue";
import ChairConferenceDetailView from "../views/chair/ChairConferenceDetailView.vue";
import DecisionWorkbenchView from "../views/chair/DecisionWorkbenchView.vue";

function token(roles: string[]): string {
  const encode = (value: object) => btoa(JSON.stringify(value)).replaceAll("=", "");
  return `${encode({ alg: "none" })}.${encode({
    sub: "conference_demo",
    uid: 1004,
    roles,
    exp: Math.floor(Date.now() / 1000) + 3600
  })}.signature`;
}

function installAuth(roles: string[]) {
  localStorage.setItem("review.auth.token", token(roles));
  initializeAuth();
}

function jsonResponse(body: unknown) {
  return {
    ok: true,
    status: 200,
    json: () => Promise.resolve(body)
  };
}

async function mountRoute(path: string) {
  const router = createAppRouter();
  router.push(path);
  await router.isReady();
  const component = router.currentRoute.value.matched.at(-1)?.components?.default;
  expect(component).toBeTruthy();
  const wrapper = mount(component as object, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus, router]
    }
  });
  await flushPromises();
  return wrapper;
}

async function clickButton(wrapper: ReturnType<typeof mount>, label: string) {
  const button = wrapper.findAll("button").find((entry) => entry.text().includes(label));
  expect(button).toBeTruthy();
  await button!.trigger("click");
  await flushPromises();
}

describe("conference frontend workflows", () => {
  beforeEach(() => {
    document.body.innerHTML = "";
    resetAuthForTests();
    vi.restoreAllMocks();
  });

  it("exposes public CFP, chair conference, and reviewer bidding routes", async () => {
    const router = createAppRouter();

    expect(router.getRoutes().some((route) => route.path === "/cfp")).toBe(true);
    expect(router.getRoutes().some((route) => route.name === "chair-conferences")).toBe(true);
    expect(router.getRoutes().some((route) => route.name === "chair-conference-detail")).toBe(true);
    expect(router.getRoutes().some((route) => route.name === "reviewer-bidding")).toBe(true);
  });

  it("renders public CFPs without requiring authentication", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse([
      {
        conferenceId: 501,
        name: "International Conference on Review Agents",
        acronym: "ICRA",
        year: 2026,
        status: "OPEN_FOR_SUBMISSION",
        blindMode: "DOUBLE_BLIND",
        publicSlug: "icra-2026",
        submissionOpenAt: "2026-05-01T00:00:00Z",
        submissionCloseAt: "2026-06-01T00:00:00Z"
      }
    ])));

    const wrapper = await mountRoute("/cfp");

    expect(wrapper.text()).toContain("International Conference on Review Agents");
    expect(wrapper.text()).toContain("Open for submission");
  });

  it("lets chairs create a conference draft and submit it for admin approval", async () => {
    installAuth(["CHAIR"]);
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/chair/conferences" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({
          conferenceId: 501,
          name: "Agent Review Conference",
          acronym: "ARC",
          year: 2026,
          status: "DRAFT",
          blindMode: "DOUBLE_BLIND",
          publicSlug: "arc-2026",
          cfpPublished: false,
          phase: {}
        }));
      }
      if (path === "/chair/conferences/501/submit-approval" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({ conferenceId: 501, status: "PENDING_APPROVAL" }));
      }
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountRoute("/chair/conferences");
    await wrapper.get('[data-test="conference-name"]').setValue("Agent Review Conference");
    await wrapper.get('[data-test="conference-acronym"]').setValue("ARC");
    await wrapper.get('[data-test="conference-year"] input').setValue("2026");
    await wrapper.get('[data-test="conference-slug"]').setValue("arc-2026");
    await wrapper.get('[data-test="conference-topics"]').setValue("agents,systems");
    await clickButton(wrapper, "创建草稿");
    await clickButton(wrapper, "提交审批");

    expect(fetch).toHaveBeenCalledWith(
      "/api/chair/conferences",
      expect.objectContaining({ method: "POST" })
    );
    const createCall = fetch.mock.calls.find((call) =>
      String(call[0]).endsWith("/chair/conferences") && call[1]?.method === "POST"
    );
    expect(createCall).toBeTruthy();
    const createBody = JSON.parse((createCall![1] as RequestInit).body as string);
    expect(createBody).toEqual(expect.objectContaining({
      name: "Agent Review Conference",
      acronym: "ARC",
      publicSlug: "arc-2026",
      topicAreas: ["agents", "systems"]
    }));
    expect(fetch).toHaveBeenCalledWith(
      "/api/chair/conferences/501/submit-approval",
      expect.objectContaining({ method: "POST" })
    );
  });

  it("shows manageable conference history and links to conference details", async () => {
    installAuth(["CHAIR"]);
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/chair/conferences") {
        return Promise.resolve(jsonResponse([
          {
            conferenceId: 501,
            name: "Agent Review Conference",
            acronym: "ARC",
            year: 2026,
            status: "REVIEWING",
            blindMode: "DOUBLE_BLIND",
            publicSlug: "arc-2026",
            submissionOpenAt: "2026-05-01T00:00:00Z",
            submissionCloseAt: "2026-06-01T00:00:00Z"
          }
        ]));
      }
      return Promise.resolve(jsonResponse([]));
    }));

    const wrapper = await mountRoute("/chair/conferences");

    expect(wrapper.text()).toContain("过往会议状态");
    expect(wrapper.text()).toContain("Agent Review Conference");
    const detailLink = wrapper.find('a[href="/chair/conferences/501"]');
    expect(detailLink.exists()).toBe(true);
  });

  it("shows conference papers and lets chairs make accept or desk-reject decisions from detail", async () => {
    installAuth(["CHAIR"]);
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/chair/conferences/501") {
        return Promise.resolve(jsonResponse({
          conferenceId: 501,
          name: "Agent Review Conference",
          acronym: "ARC",
          year: 2026,
          status: "DECISION",
          blindMode: "DOUBLE_BLIND",
          publicSlug: "arc-2026",
          cfpPublished: true,
          phase: {
            submissionOpenAt: "2026-05-01T00:00:00Z",
            submissionCloseAt: "2026-06-01T00:00:00Z",
            biddingOpenAt: "2026-06-02T00:00:00Z",
            biddingCloseAt: "2026-06-10T00:00:00Z",
            reviewDeadlineAt: "2026-07-01T00:00:00Z",
            decisionReleaseAt: "2026-07-15T00:00:00Z"
          }
        }));
      }
      if (path === "/chair/conferences/501/papers") {
        return Promise.resolve(jsonResponse([
          {
            manuscriptId: 11,
            versionId: 21,
            versionNo: 1,
            roundId: 7,
            roundNo: 1,
            title: "Auditable Agent Review",
            currentStatus: "UNDER_REVIEW",
            roundStatus: "IN_PROGRESS",
            assignmentCount: 2,
            submittedReviewCount: 2,
            lastDecisionCode: null,
            submittedAt: "2026-05-10T00:00:00Z"
          },
          {
            manuscriptId: 12,
            versionId: 22,
            versionNo: 1,
            roundId: 8,
            roundNo: 1,
            title: "Early Rejection Candidate",
            currentStatus: "UNDER_SCREENING",
            roundStatus: "PENDING",
            assignmentCount: 0,
            submittedReviewCount: 0,
            lastDecisionCode: null,
            submittedAt: "2026-05-11T00:00:00Z"
          }
        ]));
      }
      if (path === "/decisions" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({ decisionId: 99, decisionCode: "ACCEPT", currentStatus: "ACCEPTED" }));
      }
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal("fetch", fetch);

    const router = createAppRouter();
    router.push("/chair/conferences/501");
    await router.isReady();
    const wrapper = mount(ChairConferenceDetailView, {
      attachTo: document.body,
      global: {
        plugins: [ElementPlus, router]
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain("Agent Review Conference");
    expect(wrapper.text()).toContain("Auditable Agent Review");
    await clickButton(wrapper, "Accept");
    await wrapper.get('[data-test="conference-decision-reason"]').setValue("Program committee approved.");
    await clickButton(wrapper, "Submit decision");

    const decisionCall = fetch.mock.calls.find(([input, init]) =>
      String(input) === "/api/decisions" && init?.method === "POST"
    );
    expect(decisionCall).toBeTruthy();
    expect(JSON.parse((decisionCall![1] as RequestInit).body as string)).toEqual(expect.objectContaining({
      manuscriptId: 11,
      versionId: 21,
      roundId: 7,
      decisionCode: "ACCEPT",
      decisionReason: "Program committee approved."
    }));
    expect(wrapper.text()).toContain("Desk reject");
  });

  it("lets reviewers load bidding papers and submit conflict-aware bids", async () => {
    installAuth(["REVIEWER"]);
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/conferences/cfp") {
        return Promise.resolve(jsonResponse([
          {
            conferenceId: 501,
            name: "Agent Review Conference",
            acronym: "ARC",
            year: 2026,
            status: "BIDDING_OPEN",
            blindMode: "DOUBLE_BLIND",
            publicSlug: "arc-2026"
          }
        ]));
      }
      if (path === "/reviewer/conferences/501/bids/open") {
        return Promise.resolve(jsonResponse([
          {
            manuscriptId: 11,
            versionId: 21,
            title: "De-identified Agent Paper",
            abstractText: "A double-blind abstract.",
            keywords: "agents",
            bidValue: null,
            conflictDeclared: false
          }
        ]));
      }
      if (path === "/reviewer/conferences/501/bids" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({ bidId: 77, manuscriptId: 11, bidValue: "CONFLICT" }));
      }
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountRoute("/reviewer/bidding");
    await clickButton(wrapper, "加载论文");
    await clickButton(wrapper, "声明利益冲突");

    expect(wrapper.text()).toContain("De-identified Agent Paper");
    expect(fetch).toHaveBeenCalledWith(
      "/api/reviewer/conferences/501/bids",
      expect.objectContaining({ method: "POST" })
    );
    const bidCall = fetch.mock.calls.find((call) => String(call[0]).endsWith("/reviewer/conferences/501/bids"));
    expect(bidCall).toBeTruthy();
    const bidBody = JSON.parse((bidCall![1] as RequestInit).body as string);
    expect(bidBody).toEqual(expect.objectContaining({
      manuscriptId: 11,
      bidValue: "CONFLICT",
      conflictDeclared: true
    }));
  });

  it("lets chairs generate assignment drafts, request assignment assist, and confirm drafts", async () => {
    installAuth(["CHAIR"]);
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/chair/decision-workbench") {
        return Promise.resolve(jsonResponse([
          {
            roundId: 7,
            manuscriptId: 11,
            versionId: 21,
            versionNo: 1,
            roundNo: 1,
            title: "Workflow Seed",
            currentStatus: "UNDER_REVIEW",
            roundStatus: "PENDING",
            assignmentCount: 0,
            submittedReviewCount: 0,
            conflictCount: 0,
            assignments: [],
            conflictProjections: []
          }
        ]));
      }
      if (path === "/review-rounds/7/assignment-drafts/generate" && init?.method === "POST") {
        return Promise.resolve(jsonResponse([
          {
            draftId: 9001,
            roundId: 7,
            manuscriptId: 11,
            versionId: 21,
            reviewerId: 1002,
            rankOrder: 1,
            score: 104,
            currentLoad: 1,
            maxLoad: 3,
            bidValue: "WANT_TO_REVIEW",
            reason: "Bid match and available load.",
            draftStatus: "PROPOSED"
          }
        ]));
      }
      if (path === "/review-rounds/7/assignment-assist" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({
          intentId: 88,
          analysisType: "REVIEWER_ASSIGNMENT_ASSIST",
          businessStatus: "REQUESTED"
        }));
      }
      if (path === "/review-rounds/7/assignment-assist") {
        return Promise.resolve(jsonResponse({
          intent: {
            intentId: 88,
            analysisType: "REVIEWER_ASSIGNMENT_ASSIST",
            businessStatus: "AVAILABLE"
          },
          projections: [
            {
              projectionId: 1,
              analysisType: "REVIEWER_ASSIGNMENT_ASSIST",
              businessStatus: "AVAILABLE",
              summaryText: "Candidate ranking ready.",
              redactedResult: { rankedCandidates: [{ reviewerId: "1002", rank: 1 }] },
              superseded: false,
              updatedAt: "2026-05-07T01:00:00Z"
            }
          ]
        }));
      }
      if (path === "/review-rounds/7/assignment-drafts/confirm" && init?.method === "POST") {
        return Promise.resolve(jsonResponse([
          { assignmentId: 44, taskStatus: "ASSIGNED", reviewerId: 1002 }
        ]));
      }
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = mount(DecisionWorkbenchView, {
      attachTo: document.body,
      global: {
        plugins: [ElementPlus]
      }
    });
    await flushPromises();
    await wrapper.get(".el-table__expand-icon").trigger("click");
    await flushPromises();
    await clickButton(wrapper, "生成草稿");
    await clickButton(wrapper, "运行分配辅助");
    await clickButton(wrapper, "确认草稿");

    expect(wrapper.text()).toContain("审稿人 1002");
    expect(wrapper.text()).toContain("Candidate ranking ready.");
    expect(fetch).toHaveBeenCalledWith(
      "/api/review-rounds/7/assignment-drafts/confirm",
      expect.objectContaining({ method: "POST" })
    );
  });

  it("shows conference workflow links on the dashboard for each actor", async () => {
    installAuth(["CHAIR", "REVIEWER", "ADMIN"]);
    const router = createAppRouter();
    router.push("/dashboard");
    await router.isReady();

    const wrapper = mount(DashboardView, {
      global: {
        plugins: [ElementPlus, router]
      }
    });

    expect(wrapper.text()).toContain("会议管理");
    expect(wrapper.text()).toContain("竞标投票");
    expect(wrapper.text()).toContain("会议审批");
  });
});
