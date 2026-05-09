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

  it("exposes public CFP, chair conference, admin approval, and reviewer bidding routes", async () => {
    const router = createAppRouter();

    expect(router.getRoutes().some((route) => route.path === "/cfp")).toBe(true);
    expect(router.getRoutes().some((route) => route.name === "chair-conferences")).toBe(true);
    expect(router.getRoutes().some((route) => route.name === "admin-conference-approvals")).toBe(true);
    expect(router.getRoutes().some((route) => route.name === "chair-conference-detail")).toBe(true);
    expect(router.getRoutes().some((route) => route.name === "chair-conference-paper-detail")).toBe(true);
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

  it("keeps conference approval in a dedicated admin page", async () => {
    installAuth(["ADMIN"]);
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/admin/conferences/pending") {
        return Promise.resolve(jsonResponse([
          {
            conferenceId: 501,
            name: "Agent Review Conference",
            acronym: "ARC",
            year: 2026,
            status: "PENDING_APPROVAL",
            blindMode: "DOUBLE_BLIND",
            publicSlug: "arc-2026",
            submissionOpenAt: "2026-05-01T00:00:00Z",
            submissionCloseAt: "2026-06-01T00:00:00Z"
          }
        ]));
      }
      if (path === "/admin/conferences/501") {
        return Promise.resolve(jsonResponse({
          conferenceId: 501,
          name: "Agent Review Conference",
          acronym: "ARC",
          year: 2026,
          status: "PENDING_APPROVAL",
          blindMode: "DOUBLE_BLIND",
          publicSlug: "arc-2026",
          cfpText: "Detailed CFP for agent review.",
          topicAreas: ["agents", "systems"],
          targetReviewsPerPaper: 3,
          defaultReviewerMaxLoad: 4,
          cfpPublished: false,
          phase: {
            submissionOpenAt: "2026-05-01T00:00:00Z",
            abstractSubmissionCloseAt: "2026-05-20T00:00:00Z",
            submissionCloseAt: "2026-06-01T00:00:00Z",
            biddingOpenAt: "2026-06-02T00:00:00Z",
            biddingCloseAt: "2026-06-10T00:00:00Z",
            reviewDeadlineAt: "2026-07-01T00:00:00Z",
            decisionReleaseAt: "2026-07-15T00:00:00Z"
          }
        }));
      }
      if (path === "/admin/conferences/501/approve" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({
          conferenceId: 501,
          name: "Agent Review Conference",
          status: "OPEN_FOR_SUBMISSION"
        }));
      }
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountRoute("/admin/conference-approvals");

    expect(wrapper.text()).toContain("会议审批");
    expect(wrapper.text()).toContain("Agent Review Conference");
    expect(wrapper.text()).not.toContain("创建征稿启事草稿");
    await clickButton(wrapper, "查看详情");

    expect(document.body.textContent).toContain("Detailed CFP for agent review.");
    expect(fetch).toHaveBeenCalledWith(
      "/api/admin/conferences/501",
      expect.objectContaining({ headers: expect.any(Object) })
    );
    await clickButton(wrapper, "审批通过");

    expect(fetch).toHaveBeenCalledWith(
      "/api/admin/conferences/501/approve",
      expect.objectContaining({ method: "POST" })
    );
  });

  it("requires an admin rejection reason and sends rejected conferences back to draft", async () => {
    installAuth(["ADMIN"]);
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/admin/conferences/pending") {
        return Promise.resolve(jsonResponse([
          {
            conferenceId: 501,
            name: "Agent Review Conference",
            acronym: "ARC",
            year: 2026,
            status: "PENDING_APPROVAL",
            blindMode: "DOUBLE_BLIND",
            publicSlug: "arc-2026",
            submissionOpenAt: "2026-05-01T00:00:00Z",
            submissionCloseAt: "2026-06-01T00:00:00Z"
          }
        ]));
      }
      if (path === "/admin/conferences/501") {
        return Promise.resolve(jsonResponse({
          conferenceId: 501,
          name: "Agent Review Conference",
          acronym: "ARC",
          year: 2026,
          status: "PENDING_APPROVAL",
          blindMode: "DOUBLE_BLIND",
          publicSlug: "arc-2026",
          cfpText: "Detailed CFP for agent review.",
          topicAreas: ["agents"],
          targetReviewsPerPaper: 3,
          defaultReviewerMaxLoad: 4,
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
      if (path === "/admin/conferences/501/reject" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({
          conferenceId: 501,
          name: "Agent Review Conference",
          status: "DRAFT",
          rejectionReason: "Please clarify submission scope."
        }));
      }
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountRoute("/admin/conference-approvals");
    await clickButton(wrapper, "查看详情");
    await clickButton(wrapper, "拒绝审批");

    expect(document.body.textContent).toContain("请填写拒绝理由");
    expect(fetch.mock.calls.some(([input]) => String(input).endsWith("/admin/conferences/501/reject"))).toBe(false);

    await wrapper.get('[data-test="conference-rejection-reason"]').setValue("Please clarify submission scope.");
    await clickButton(wrapper, "拒绝审批");

    const rejectCall = fetch.mock.calls.find(([input, init]) =>
      String(input) === "/api/admin/conferences/501/reject" && init?.method === "POST"
    );
    expect(rejectCall).toBeTruthy();
    expect(JSON.parse((rejectCall![1] as RequestInit).body as string)).toEqual({
      rejectionReason: "Please clarify submission scope."
    });
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
    const schedulePickers = wrapper.findAllComponents({ name: "ElDatePicker" });
    expect(schedulePickers).toHaveLength(5);
    expect(wrapper.text()).toContain("投稿开放时间");
    expect(wrapper.text()).toContain("摘要提交截止日期");
    expect(wrapper.text()).toContain("论文提交截止日期");
    expect(wrapper.text()).toContain("评审截止时间");
    expect(wrapper.text()).toContain("决策发布时间");
    expect(wrapper.text()).not.toContain("竞标开放时间");
    expect(wrapper.text()).not.toContain("竞标截止时间");
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
    expect(createBody.phase).toEqual(expect.objectContaining({
      submissionOpenAt: "2026-05-01T00:00:00Z",
      abstractSubmissionCloseAt: "2026-05-20T00:00:00Z",
      submissionCloseAt: "2026-06-01T00:00:00Z",
      biddingOpenAt: "2026-06-02T00:00:00Z",
      biddingCloseAt: "2026-06-30T00:00:00Z",
      reviewDeadlineAt: "2026-07-01T00:00:00Z",
      decisionReleaseAt: "2026-07-15T00:00:00Z"
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

  it("presents reviewer pool setup as a conference workflow instead of raw ids", async () => {
    installAuth(["CHAIR"]);
    const fetch = vi.fn((input: RequestInfo | URL) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/chair/conferences") {
        return Promise.resolve(jsonResponse([
          {
            conferenceId: 501,
            name: "Agent Review Conference",
            acronym: "ARC",
            year: 2026,
            status: "OPEN_FOR_SUBMISSION",
            blindMode: "DOUBLE_BLIND",
            publicSlug: "arc-2026",
            submissionOpenAt: "2026-05-01T00:00:00Z",
            submissionCloseAt: "2026-06-01T00:00:00Z"
          }
        ]));
      }
      if (path === "/chair/reviewers/search?query=Reviewer") {
        return Promise.resolve(jsonResponse([
          {
            reviewerId: 1002,
            realName: "Reviewer Demo",
            email: "reviewer_demo@example.com",
            institution: "Nanjing University",
            researchAreas: [{ areaCode: "NLP", areaName: "Natural Language Processing" }]
          }
        ]));
      }
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountRoute("/chair/conferences");
    const reviewerSelect = wrapper.findAllComponents({ name: "ElSelect" })
      .find((component) => component.props("placeholder") === "输入真实姓名或邮箱搜索");
    expect(reviewerSelect).toBeTruthy();
    await reviewerSelect!.props("remoteMethod")("Reviewer");
    await flushPromises();

    expect(wrapper.text()).toContain("会议审稿人池准备");
    expect(wrapper.text()).toContain("选择会议");
    expect(wrapper.text()).toContain("搜索审稿人");
    expect(document.body.textContent).toContain("Reviewer Demo");
    expect(document.body.textContent).toContain("reviewer_demo@example.com");
    expect(wrapper.text()).toContain("进入审稿人分配");
    expect(wrapper.text()).not.toContain("会议 ID");
    expect(wrapper.text()).not.toContain("审稿人 ID");
    expect(fetch).toHaveBeenCalledWith(
      "/api/chair/reviewers/search?query=Reviewer",
      expect.objectContaining({ headers: expect.any(Object) })
    );
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
            averageOverallScore: 4.5,
            reviewerScores: [
              {
                assignmentId: 101,
                reviewerId: 1002,
                reviewerName: "Reviewer Demo",
                taskStatus: "SUBMITTED",
                overallScore: 5,
                recommendation: "ACCEPT",
                submittedAt: "2026-06-20T00:00:00Z"
              },
              {
                assignmentId: 102,
                reviewerId: 1013,
                reviewerName: "Second Reviewer",
                taskStatus: "SUBMITTED",
                overallScore: 4,
                recommendation: "MINOR_REVISION",
                submittedAt: "2026-06-21T00:00:00Z"
              }
            ],
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
            averageOverallScore: null,
            reviewerScores: [],
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
    expect(wrapper.text()).toContain("均分 4.5");
    expect(wrapper.text()).toContain("Reviewer Demo: 5");
    expect(wrapper.text()).toContain("Second Reviewer: 4");
    expect(wrapper.find('a[href="/chair/conferences/501/papers/11"]').exists()).toBe(true);
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

  it("shows rejected draft feedback, lets chairs edit, and resubmit", async () => {
    installAuth(["CHAIR"]);
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/chair/conferences/501") {
        return Promise.resolve(jsonResponse({
          conferenceId: 501,
          name: "Agent Review Conference",
          acronym: "ARC",
          year: 2026,
          status: "DRAFT",
          blindMode: "DOUBLE_BLIND",
          publicSlug: "arc-2026",
          cfpText: "Original CFP.",
          topicAreas: ["agents", "systems"],
          targetReviewsPerPaper: 3,
          defaultReviewerMaxLoad: 4,
          cfpPublished: false,
          rejectedBy: 1004,
          rejectedAt: "2026-05-06T00:00:00Z",
          rejectionReason: "Please clarify submission scope.",
          phase: {
            submissionOpenAt: "2026-05-01T00:00:00Z",
            abstractSubmissionCloseAt: "2026-05-20T00:00:00Z",
            submissionCloseAt: "2026-06-01T00:00:00Z",
            biddingOpenAt: "2026-06-02T00:00:00Z",
            biddingCloseAt: "2026-06-10T00:00:00Z",
            reviewDeadlineAt: "2026-07-01T00:00:00Z",
            decisionReleaseAt: "2026-07-15T00:00:00Z"
          }
        }));
      }
      if (path === "/chair/conferences/501/papers") {
        return Promise.resolve(jsonResponse([]));
      }
      if (path === "/chair/conferences/501" && init?.method === "PUT") {
        return Promise.resolve(jsonResponse({
          conferenceId: 501,
          name: "Agent Review Conference Revised",
          acronym: "ARC",
          year: 2026,
          status: "DRAFT",
          blindMode: "DOUBLE_BLIND",
          publicSlug: "arc-2026",
          cfpText: "Updated CFP.",
          topicAreas: ["agents", "systems"],
          targetReviewsPerPaper: 3,
          defaultReviewerMaxLoad: 4,
          rejectionReason: "Please clarify submission scope.",
          phase: {}
        }));
      }
      if (path === "/chair/conferences/501/submit-approval" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({ conferenceId: 501, status: "PENDING_APPROVAL" }));
      }
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountRoute("/chair/conferences/501");

    expect(wrapper.text()).toContain("退回理由");
    expect(wrapper.text()).toContain("Please clarify submission scope.");
    expect(wrapper.findAllComponents({ name: "ElDatePicker" })).toHaveLength(5);
    expect(wrapper.text()).not.toContain("竞标开放");
    expect(wrapper.text()).not.toContain("竞标截止");
    await wrapper.get('[data-test="edit-conference-name"]').setValue("Agent Review Conference Revised");
    await wrapper.get('[data-test="edit-conference-cfp"]').setValue("Updated CFP.");
    await clickButton(wrapper, "保存修改");
    await clickButton(wrapper, "再次提交审批");

    const updateCall = fetch.mock.calls.find(([input, init]) =>
      String(input) === "/api/chair/conferences/501" && init?.method === "PUT"
    );
    expect(updateCall).toBeTruthy();
    expect(JSON.parse((updateCall![1] as RequestInit).body as string)).toEqual(expect.objectContaining({
      name: "Agent Review Conference Revised",
      cfpText: "Updated CFP.",
      topicAreas: ["agents", "systems"]
    }));
    expect(fetch).toHaveBeenCalledWith(
      "/api/chair/conferences/501/submit-approval",
      expect.objectContaining({ method: "POST" })
    );
  });

  it("lets chairs manually close abstract submission from the conference detail page", async () => {
    installAuth(["CHAIR"]);
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/chair/conferences/501") {
        return Promise.resolve(jsonResponse({
          conferenceId: 501,
          name: "Agent Review Conference",
          acronym: "ARC",
          year: 2026,
          status: "OPEN_FOR_SUBMISSION",
          blindMode: "DOUBLE_BLIND",
          publicSlug: "arc-2026",
          cfpPublished: true,
          phase: {
            submissionOpenAt: "2026-05-01T00:00:00Z",
            abstractSubmissionCloseAt: "2026-05-20T00:00:00Z",
            submissionCloseAt: "2026-06-01T00:00:00Z",
            biddingOpenAt: "2026-06-02T00:00:00Z",
            biddingCloseAt: "2026-06-10T00:00:00Z",
            reviewDeadlineAt: "2026-07-01T00:00:00Z",
            decisionReleaseAt: "2026-07-15T00:00:00Z"
          }
        }));
      }
      if (path === "/chair/conferences/501/advance" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({
          conferenceId: 501,
          name: "Agent Review Conference",
          acronym: "ARC",
          year: 2026,
          status: "SUBMISSION_CLOSED",
          blindMode: "DOUBLE_BLIND",
          publicSlug: "arc-2026",
          cfpPublished: true,
          phase: {}
        }));
      }
      if (path === "/chair/conferences/501/papers") {
        return Promise.resolve(jsonResponse([]));
      }
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountRoute("/chair/conferences/501");

    expect(wrapper.text()).toContain("推进到 提交论文阶段");
    await clickButton(wrapper, "推进到 提交论文阶段");

    const advanceCall = fetch.mock.calls.find(([input, init]) =>
      String(input) === "/api/chair/conferences/501/advance" && init?.method === "POST"
    );
    expect(advanceCall).toBeTruthy();
    expect(JSON.parse((advanceCall![1] as RequestInit).body as string)).toEqual({
      status: "SUBMISSION_CLOSED"
    });
  });

  it("lets chairs read a paper PDF beside switchable reviewer evaluations", async () => {
    installAuth(["CHAIR"]);
    const imageBlob = new Blob(["png"], { type: "image/png" });
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: vi.fn(() => "blob:chair-paper-page")
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: vi.fn()
    });
    const createObjectUrl = vi.mocked(URL.createObjectURL);
    const fetch = vi.fn((input: RequestInfo | URL) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/chair/conferences/501/papers/11/review-detail") {
        return Promise.resolve(jsonResponse({
          manuscriptId: 11,
          versionId: 21,
          versionNo: 1,
          roundId: 7,
          roundNo: 1,
          title: "Auditable Agent Review",
          abstractText: "Audit-ready review workflow.",
          keywords: "agents,review",
          pdfFileName: "paper.pdf",
          currentStatus: "UNDER_REVIEW",
          roundStatus: "IN_PROGRESS",
          submittedAt: "2026-05-10T00:00:00Z",
          pageCount: 2,
          averageOverallScore: 4.5,
          reviews: [
            {
              assignmentId: 101,
              reviewerId: 1002,
              reviewerName: "Reviewer Demo",
              institution: "Fudan University",
              taskStatus: "SUBMITTED",
              assignedAt: "2026-06-01T00:00:00Z",
              deadlineAt: "2026-07-01T00:00:00Z",
              assignmentSubmittedAt: "2026-06-20T00:00:00Z",
              reviewId: 9001,
              noveltyScore: 5,
              methodScore: 4,
              experimentScore: 4,
              writingScore: 5,
              overallScore: 5,
              confidenceLevel: "HIGH",
              strengths: "Strong audit trail.",
              weaknesses: "Needs broader deployment.",
              commentsToAuthor: "Clear contribution.",
              commentsToChair: "Recommend acceptance.",
              recommendation: "ACCEPT",
              reviewSubmittedAt: "2026-06-20T00:00:00Z"
            },
            {
              assignmentId: 102,
              reviewerId: 1013,
              reviewerName: "Second Reviewer",
              institution: "Nanjing University",
              taskStatus: "SUBMITTED",
              assignedAt: "2026-06-01T00:00:00Z",
              deadlineAt: "2026-07-01T00:00:00Z",
              assignmentSubmittedAt: "2026-06-21T00:00:00Z",
              reviewId: 9002,
              noveltyScore: 4,
              methodScore: 4,
              experimentScore: 3,
              writingScore: 4,
              overallScore: 4,
              confidenceLevel: "MEDIUM",
              strengths: "Useful system framing.",
              weaknesses: "Experiments are limited.",
              commentsToAuthor: "Add baselines.",
              commentsToChair: "Minor revision is enough.",
              recommendation: "MINOR_REVISION",
              reviewSubmittedAt: "2026-06-21T00:00:00Z"
            }
          ]
        }));
      }
      if (path === "/chair/conferences/501/papers/11/paper/pages/1") {
        return Promise.resolve({
          ok: true,
          status: 200,
          blob: () => Promise.resolve(imageBlob)
        });
      }
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountRoute("/chair/conferences/501/papers/11");

    expect(wrapper.text()).toContain("论文评审详情");
    expect(wrapper.text()).toContain("Auditable Agent Review");
    expect(wrapper.text()).toContain("均分 4.5");
    expect(wrapper.text()).toContain("Reviewer Demo");
    expect(wrapper.text()).toContain("Strong audit trail.");
    expect(wrapper.text()).toContain("Recommend acceptance.");
    expect(wrapper.find('[data-test="chair-paper-reader"]').exists()).toBe(true);
    expect(createObjectUrl).toHaveBeenCalledWith(imageBlob);
    wrapper.findComponent({ name: "ElSelect" }).vm.$emit("update:modelValue", 102);
    await flushPromises();
    expect(wrapper.text()).toContain("Experiments are limited.");
    expect(wrapper.text()).toContain("Minor revision is enough.");
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
    installAuth(["CHAIR", "REVIEWER"]);
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
    const links = wrapper.findAll("a");
    expect(links.some((link) => link.text().includes("会议管理") && link.attributes("href") === "/chair/conferences")).toBe(true);

    resetAuthForTests();
    installAuth(["ADMIN"]);
    const adminWrapper = mount(DashboardView, {
      global: {
        plugins: [ElementPlus, router]
      }
    });

    expect(adminWrapper.text()).toContain("会议审批");
    expect(adminWrapper.text()).toContain("会议管理");
    expect(adminWrapper.text()).not.toContain("竞标投票");
    expect(adminWrapper.text()).not.toContain("初筛队列");
    const adminLinks = adminWrapper.findAll("a");
    expect(adminLinks.some((link) => link.text().includes("会议审批") && link.attributes("href") === "/admin/conference-approvals")).toBe(true);
  });

  it("keeps admin conference detail read-only without paper content actions", async () => {
    installAuth(["ADMIN"]);
    const fetch = vi.fn((input: RequestInfo | URL) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/chair/conferences/501") {
        return Promise.resolve(jsonResponse({
          conferenceId: 501,
          name: "Agent Review Conference",
          acronym: "ARC",
          year: 2026,
          status: "REVIEWING",
          blindMode: "DOUBLE_BLIND",
          publicSlug: "arc-2026",
          phase: {
            submissionOpenAt: "2026-05-01T00:00:00Z",
            submissionCloseAt: "2026-06-01T00:00:00Z",
            reviewDeadlineAt: "2026-07-01T00:00:00Z"
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
            title: "Admin Visible Status Only",
            currentStatus: "UNDER_REVIEW",
            roundStatus: "IN_PROGRESS",
            assignmentCount: 2,
            submittedReviewCount: 1,
            lastDecisionCode: null,
            submittedAt: "2026-05-04T00:00:00Z",
            averageOverallScore: 4,
            reviewerScores: []
          }
        ]));
      }
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountRoute("/chair/conferences/501");

    expect(wrapper.text()).toContain("Agent Review Conference");
    expect(wrapper.text()).toContain("Admin Visible Status Only");
    expect(wrapper.text()).not.toContain("查看评审详情");
    expect(wrapper.text()).not.toContain("Accept");
    expect(wrapper.text()).not.toContain("Desk reject");
    expect(wrapper.findAll("a").some((link) => link.attributes("href") === "/chair/conferences/501/papers/11")).toBe(false);
  });

  it("keeps admin conference management limited to status and detail links", async () => {
    installAuth(["ADMIN"]);
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

    expect(wrapper.text()).toContain("Agent Review Conference");
    expect(wrapper.text()).toContain("查看详情");
    expect(wrapper.text()).not.toContain("创建征稿启事草稿");
    expect(wrapper.text()).not.toContain("会议审稿人池准备");
    expect(wrapper.text()).not.toContain("进入审稿人分配");
  });
});
