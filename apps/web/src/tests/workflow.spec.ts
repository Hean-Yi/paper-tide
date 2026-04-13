import { flushPromises, mount } from "@vue/test-utils";
import ElementPlus from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createRouter, createWebHistory } from "vue-router";

import { initializeAuth, resetAuthForTests } from "../stores/auth";
import ScreeningQueueView from "../views/chair/ScreeningQueueView.vue";
import DecisionWorkbenchView from "../views/chair/DecisionWorkbenchView.vue";
import ReviewEditorView from "../views/reviewer/ReviewEditorView.vue";

function token(roles: string[]): string {
  const encode = (value: object) => btoa(JSON.stringify(value)).replaceAll("=", "");
  return `${encode({ alg: "none" })}.${encode({
    sub: "workflow_demo",
    uid: 1002,
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

function mockApi(responses: Record<string, unknown>) {
  vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
    const url = String(input);
    const path = url.replace(/^\/api/, "");
    return Promise.resolve(jsonResponse(responses[path] ?? []));
  }));
}

async function mountWithRouter(component: object, path = "/") {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: "/", component },
      { path: "/reviewer/reviews/:assignmentId", component }
    ]
  });
  router.push(path);
  await router.isReady();
  const wrapper = mount(component, {
    global: {
      plugins: [ElementPlus, router]
    }
  });
  await flushPromises();
  return wrapper;
}

describe("workflow screens", () => {
  beforeEach(() => {
    resetAuthForTests();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    resetAuthForTests();
    vi.restoreAllMocks();
  });

  it("shows desk reject action in chair screening queue", async () => {
    installAuth(["CHAIR"]);
    mockApi({
      "/chair/screening-queue": [
        {
          manuscriptId: 11,
          versionId: 21,
          versionNo: 1,
          title: "Workflow Seed",
          currentStatus: "UNDER_SCREENING",
          blindMode: "DOUBLE_BLIND",
          submittedAt: "2026-04-13T05:00:00Z",
          pdfFileName: "workflow.pdf",
          pdfFileSize: 23
        }
      ]
    });

    const wrapper = await mountWithRouter(ScreeningQueueView);

    expect(wrapper.text()).toContain("Workflow Seed");
    expect(wrapper.text()).toContain("Start screening");
    expect(wrapper.text()).toContain("Desk reject");
  });

  it("shows redacted agent results for reviewer", async () => {
    installAuth(["REVIEWER"]);
    mockApi({
      "/review-assignments/9": {
        assignmentId: 9,
        manuscriptId: 11,
        versionId: 21,
        versionNo: 1,
        title: "Workflow Seed",
        abstractText: "workflow abstract",
        keywords: "workflow,pdf",
        pdfFileName: "workflow.pdf",
        taskStatus: "ACCEPTED"
      },
      "/manuscripts/11/versions/21/agent-results": [
        {
          resultId: 1,
          resultType: "REVIEW_ASSIST_ANALYSIS",
          rawResult: null,
          redactedResult: { summary: "Reviewer-visible signal" }
        }
      ]
    });

    const wrapper = await mountWithRouter(ReviewEditorView, "/reviewer/reviews/9");

    expect(wrapper.text()).toContain("Reviewer-visible signal");
    expect(wrapper.text()).not.toContain("rawResult");
  });

  it("shows raw agent results for chair decision workbench", async () => {
    installAuth(["CHAIR"]);
    mockApi({
      "/chair/decision-workbench": [
        {
          roundId: 7,
          manuscriptId: 11,
          versionId: 21,
          versionNo: 1,
          roundNo: 1,
          title: "Workflow Seed",
          currentStatus: "UNDER_REVIEW",
          roundStatus: "IN_PROGRESS",
          assignmentCount: 1,
          submittedReviewCount: 1,
          conflictCount: 1,
          assignments: [{ assignmentId: 9, reviewerId: 1002, taskStatus: "SUBMITTED" }]
        }
      ],
      "/manuscripts/11/versions/21/agent-results": [
        {
          resultId: 1,
          resultType: "DECISION_CONFLICT_ANALYSIS",
          rawResult: { summary: "Chair raw signal" },
          redactedResult: { summary: "Reviewer signal" }
        }
      ]
    });

    const wrapper = await mountWithRouter(DecisionWorkbenchView);

    expect(wrapper.text()).toContain("Workflow Seed");
    expect(wrapper.text()).toContain("Chair raw signal");
  });
});
