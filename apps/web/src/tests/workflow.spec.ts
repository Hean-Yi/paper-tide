import { flushPromises, mount } from "@vue/test-utils";
import ElementPlus, { ElMessageBox } from "element-plus";
import { readFileSync } from "node:fs";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createRouter, createWebHistory } from "vue-router";

import { formatDateTime, statusTagType, workflowLabel } from "../lib/workflow-format";
import { initializeAuth, resetAuthForTests } from "../stores/auth";
import ManuscriptListView from "../views/author/ManuscriptListView.vue";
import SubmitManuscriptView from "../views/author/SubmitManuscriptView.vue";
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
          currentRoundNo: 1,
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
    await setBodyTextarea("Out of scope for this venue.");
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
    await setBodyTextarea("Conflict with the manuscript.");
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

  it("validates author manuscript form before creating a draft", async () => {
    installAuth(["AUTHOR"]);
    const fetch = vi.fn(() => Promise.resolve(jsonResponse({})));
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(SubmitManuscriptView);
    await clickButton(wrapper, "Create manuscript");

    expect(fetch).not.toHaveBeenCalled();
  });

  it("validates reviewer report form before submitting", async () => {
    installAuth(["REVIEWER"]);
    const fetch = vi.fn((input: RequestInfo | URL) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/review-assignments/9") {
        return Promise.resolve(jsonResponse({
          assignmentId: 9,
          manuscriptId: 11,
          versionId: 21,
          versionNo: 1,
          title: "Workflow Seed",
          abstractText: "workflow abstract",
          keywords: "workflow,pdf",
          pdfFileName: "workflow.pdf",
          taskStatus: "ACCEPTED"
        }));
      }
      if (path === "/manuscripts/11/versions/21/agent-results") {
        return Promise.resolve(jsonResponse([]));
      }
      return Promise.resolve(jsonResponse({}));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(ReviewEditorView, "/reviewer/reviews/9");
    await clickButton(wrapper, "Submit review");

    expect(fetch).not.toHaveBeenCalledWith(
      expect.stringContaining("/review-assignments/9/review-report"),
      expect.anything()
    );
  });

  it("renders reviewer paper online instead of a PDF download link", async () => {
    installAuth(["REVIEWER"]);
    const revokeObjectURL = vi.fn();
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:reader-page-1"),
      revokeObjectURL
    });
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
      "/review-assignments/9/paper": {
        assignmentId: 9,
        manuscriptId: 11,
        versionId: 21,
        title: "Workflow Seed",
        pageCount: 2,
        pdfFileName: "workflow.pdf",
        downloadAllowed: false
      },
      "/review-assignments/9/agent-assist": {
        task: null,
        results: []
      }
    }, {
      "/review-assignments/9/paper/pages/1": new Blob(["png"], { type: "image/png" })
    });

    const wrapper = await mountWithRouter(ReviewEditorView, "/reviewer/reviews/9");

    expect(wrapper.text()).toContain("Online reading only");
    expect(wrapper.text()).toContain("Original PDF download is unavailable");
    expect(wrapper.findAll("button").some((button) => button.text().includes("workflow.pdf"))).toBe(false);
    expect(wrapper.find("img.secure-paper-page").attributes("src")).toBe("blob:reader-page-1");
  });

  it("revokes rendered page object URLs when reviewer changes pages", async () => {
    installAuth(["REVIEWER"]);
    const revokeObjectURL = vi.fn();
    const createObjectURL = vi.fn()
      .mockReturnValueOnce("blob:reader-page-1")
      .mockReturnValueOnce("blob:reader-page-2");
    vi.stubGlobal("URL", { createObjectURL, revokeObjectURL });
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
      "/review-assignments/9/paper": {
        assignmentId: 9,
        manuscriptId: 11,
        versionId: 21,
        title: "Workflow Seed",
        pageCount: 2,
        pdfFileName: "workflow.pdf",
        downloadAllowed: false
      },
      "/review-assignments/9/agent-assist": {
        task: null,
        results: []
      }
    }, {
      "/review-assignments/9/paper/pages/1": new Blob(["page1"], { type: "image/png" }),
      "/review-assignments/9/paper/pages/2": new Blob(["page2"], { type: "image/png" })
    });

    const wrapper = await mountWithRouter(ReviewEditorView, "/reviewer/reviews/9");
    await clickButton(wrapper, "Next");

    expect(revokeObjectURL).toHaveBeenCalledWith("blob:reader-page-1");
    expect(wrapper.find("img.secure-paper-page").attributes("src")).toBe("blob:reader-page-2");
  });

  it("runs reviewer assistant through the assignment-scoped endpoint", async () => {
    installAuth(["REVIEWER"]);
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:reader-page-1"),
      revokeObjectURL: vi.fn()
    });
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/review-assignments/9") {
        return Promise.resolve(jsonResponse({
          assignmentId: 9,
          manuscriptId: 11,
          versionId: 21,
          versionNo: 1,
          title: "Workflow Seed",
          abstractText: "workflow abstract",
          keywords: "workflow,pdf",
          pdfFileName: "workflow.pdf",
          taskStatus: "ACCEPTED"
        }));
      }
      if (path === "/review-assignments/9/paper") {
        return Promise.resolve(jsonResponse({
          assignmentId: 9,
          manuscriptId: 11,
          versionId: 21,
          title: "Workflow Seed",
          pageCount: 1,
          pdfFileName: "workflow.pdf",
          downloadAllowed: false
        }));
      }
      if (path === "/review-assignments/9/paper/pages/1") {
        return Promise.resolve(blobResponse(new Blob(["page"], { type: "image/png" })));
      }
      if (path === "/review-assignments/9/agent-assist" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({
          taskId: 88,
          externalTaskId: "external-assist",
          taskType: "REVIEW_ASSIST_ANALYSIS",
          taskStatus: "PENDING",
          step: "queued"
        }));
      }
      if (path === "/review-assignments/9/agent-assist") {
        return Promise.resolve(jsonResponse({ task: null, results: [] }));
      }
      return Promise.resolve(jsonResponse({}));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(ReviewEditorView, "/reviewer/reviews/9");
    await clickButton(wrapper, "Run review assistant");

    expect(fetch).toHaveBeenCalledWith(
      "/api/review-assignments/9/agent-assist",
      expect.objectContaining({ method: "POST" })
    );
  });

  it("uses Element Plus validation as the single form validation surface", () => {
    const submitView = readFileSync("src/views/author/SubmitManuscriptView.vue", "utf8");
    const reviewView = readFileSync("src/views/reviewer/ReviewEditorView.vue", "utf8");

    expect(submitView).not.toContain("validateDraftFields");
    expect(submitView).not.toContain("draftValidation");
    expect(reviewView).not.toContain("validateReviewFields");
    expect(reviewView).not.toContain("reviewValidation");
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
      "/review-assignments/9/paper": {
        assignmentId: 9,
        manuscriptId: 11,
        versionId: 21,
        title: "Workflow Seed",
        pageCount: 1,
        pdfFileName: "workflow.pdf",
        downloadAllowed: false
      },
      "/review-assignments/9/agent-assist": {
        task: {
          taskId: 88,
          externalTaskId: "external-assist",
          taskType: "REVIEW_ASSIST_ANALYSIS",
          taskStatus: "SUCCESS",
          step: "completed"
        },
        results: [
          {
            resultId: 1,
            resultType: "REVIEW_ASSIST_ANALYSIS",
            redactedResult: { paperSummary: "Reviewer-visible signal" }
          }
        ]
      }
    }, {
      "/review-assignments/9/paper/pages/1": new Blob(["page"], { type: "image/png" })
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

async function setBodyTextarea(value: string) {
  const textarea = document.body.querySelector("textarea") as HTMLTextAreaElement | null;
  expect(textarea).toBeTruthy();
  textarea!.value = value;
  textarea!.dispatchEvent(new Event("input", { bubbles: true }));
  await flushPromises();
}
