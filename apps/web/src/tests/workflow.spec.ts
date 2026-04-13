import { flushPromises, mount } from "@vue/test-utils";
import ElementPlus, { ElMessageBox } from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createRouter, createWebHistory } from "vue-router";

import { formatDateTime, statusTagType, workflowLabel } from "../lib/workflow-format";
import { initializeAuth, resetAuthForTests } from "../stores/auth";
import ManuscriptListView from "../views/author/ManuscriptListView.vue";
import ScreeningQueueView from "../views/chair/ScreeningQueueView.vue";
import DecisionWorkbenchView from "../views/chair/DecisionWorkbenchView.vue";
import AssignmentListView from "../views/reviewer/AssignmentListView.vue";
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

function blobResponse(body: Blob) {
  return {
    ok: true,
    status: 200,
    blob: () => Promise.resolve(body),
    json: () => Promise.resolve({})
  };
}

function mockApi(responses: Record<string, unknown>, blobs: Record<string, Blob> = {}) {
  vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
    const url = String(input);
    const path = url.replace(/^\/api/, "");
    if (blobs[path]) {
      return Promise.resolve(blobResponse(blobs[path]));
    }
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
    attachTo: document.body,
    global: {
      plugins: [ElementPlus, router]
    }
  });
  await flushPromises();
  return wrapper;
}

describe("workflow screens", () => {
  beforeEach(() => {
    document.body.innerHTML = "";
    resetAuthForTests();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    document.body.innerHTML = "";
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

  it("uses date pickers for chair deadline inputs", async () => {
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
    await clickButton(wrapper, "Create round");
    await flushPromises();

    expect(document.body.querySelector(".el-date-editor")).not.toBeNull();
  });

  it("confirms desk reject before submitting the decision", async () => {
    installAuth(["CHAIR"]);
    const confirm = vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
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
    await clickButton(wrapper, "Desk reject");
    await clickBodyButton("Desk reject");

    expect(confirm).toHaveBeenCalled();
  });

  it("confirms reviewer decline before submitting the action", async () => {
    installAuth(["REVIEWER"]);
    const confirm = vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
    mockApi({
      "/review-assignments": [
        {
          assignmentId: 9,
          manuscriptId: 11,
          versionId: 21,
          versionNo: 1,
          title: "Workflow Seed",
          taskStatus: "ASSIGNED",
          deadlineAt: "2026-05-01T12:00:00Z"
        }
      ]
    });

    const wrapper = await mountWithRouter(AssignmentListView);
    await clickButton(wrapper, "Decline");
    await clickBodyButton("Decline");

    expect(confirm).toHaveBeenCalled();
  });

  it("revokes object URLs after opening downloaded PDFs", async () => {
    installAuth(["AUTHOR"]);
    mockApi({
      "/manuscripts": [
        {
          manuscriptId: 11,
          currentVersionId: 21,
          currentStatus: "DRAFT",
          currentRoundNo: 0,
          blindMode: "DOUBLE_BLIND",
          submittedAt: null,
          lastDecisionCode: null,
          currentVersionTitle: "Workflow Seed",
          currentVersionNo: 1
        }
      ]
    }, {
      "/manuscripts/11/versions/21/pdf": new Blob(["%PDF-1.4"])
    });
    const revokeObjectURL = vi.fn();
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:workflow-pdf"),
      revokeObjectURL
    });
    vi.stubGlobal("open", vi.fn());

    const wrapper = await mountWithRouter(ManuscriptListView);
    await clickButton(wrapper, "Download PDF");
    await flushPromises();

    expect(revokeObjectURL).toHaveBeenCalledWith("blob:workflow-pdf");
  });

  it("formats workflow labels, dates, and status tag types", () => {
    expect(workflowLabel("UNDER_SCREENING")).toBe("Under screening");
    expect(workflowLabel("DECISION_CONFLICT_ANALYSIS")).toBe("Decision conflict analysis");
    expect(formatDateTime(null)).toBe("Not set");
    expect(statusTagType("SUCCESS")).toBe("success");
    expect(statusTagType("FAILED")).toBe("danger");
    expect(statusTagType("UNDER_SCREENING")).toBe("warning");
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

    expect(wrapper.find(".agent-trace-panel").exists()).toBe(true);
    expect(wrapper.text()).toContain("Review assist analysis");
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

    expect(wrapper.find(".agent-trace-panel").exists()).toBe(true);
    expect(wrapper.text()).toContain("Decision conflict analysis");
    expect(wrapper.text()).toContain("Workflow Seed");
    await wrapper.get(".el-table__expand-icon").trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("Chair raw signal");
  });
});

async function clickButton(wrapper: ReturnType<typeof mount>, label: string) {
  const button = wrapper.findAll("button").find((entry) => entry.text().includes(label));
  expect(button).toBeTruthy();
  await button!.trigger("click");
  await flushPromises();
}

async function clickBodyButton(label: string) {
  const buttons = Array.from(document.body.querySelectorAll("button")).reverse();
  const button = buttons.find((entry) => entry.textContent?.includes(label));
  expect(button).toBeTruthy();
  button!.click();
  await flushPromises();
}
