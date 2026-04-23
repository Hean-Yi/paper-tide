import { flushPromises, mount } from "@vue/test-utils";
import ElementPlus, { ElMessage, ElMessageBox } from "element-plus";
import { readFileSync } from "node:fs";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createRouter, createWebHistory } from "vue-router";

import { ApiError } from "../lib/api";
import { formatDateTime, statusTagType, workflowLabel } from "../lib/workflow-format";
import { apiErrorMessage } from "../composables/useApiError";
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

function errorResponse(status: number, message: string) {
  return {
    ok: false,
    status,
    statusText: message,
    json: () => Promise.resolve({ message })
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

  it("shows scoped loading while starting screening", async () => {
    installAuth(["CHAIR"]);
    let resolveStart: ((value: ReturnType<typeof jsonResponse>) => void) | undefined;
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/chair/screening-queue") {
        return Promise.resolve(jsonResponse([
          {
            manuscriptId: 11,
            versionId: 21,
            versionNo: 1,
            title: "Workflow Seed",
            currentStatus: "SUBMITTED",
            currentRoundNo: 0,
            blindMode: "DOUBLE_BLIND",
            submittedAt: "2026-04-13T05:00:00Z",
            pdfFileName: "workflow.pdf",
            pdfFileSize: 23
          }
        ]));
      }
      if (path === "/manuscripts/11/versions/21/start-screening" && init?.method === "POST") {
        return new Promise((resolve) => {
          resolveStart = resolve;
        });
      }
      return Promise.resolve(jsonResponse({}));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(ScreeningQueueView);
    await buttonByText(wrapper, "Start screening").trigger("click");
    await flushPromises();

    expect(buttonByText(wrapper, "Start screening").classes()).toContain("is-loading");
    expect(buttonByText(wrapper, "Run agent").classes()).not.toContain("is-loading");

    resolveStart?.(jsonResponse({}));
  });

  it("shows a clear non-agent API error when starting screening fails", async () => {
    installAuth(["CHAIR"]);
    const message = vi.spyOn(ElMessage, "error").mockImplementation(() => undefined as never);
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/chair/screening-queue") {
        return Promise.resolve(jsonResponse([
          {
            manuscriptId: 11,
            versionId: 21,
            versionNo: 1,
            title: "Workflow Seed",
            currentStatus: "SUBMITTED",
            currentRoundNo: 0,
            blindMode: "DOUBLE_BLIND",
            submittedAt: "2026-04-13T05:00:00Z",
            pdfFileName: "workflow.pdf",
            pdfFileSize: 23
          }
        ]));
      }
      if (path === "/manuscripts/11/versions/21/start-screening" && init?.method === "POST") {
        return Promise.resolve(errorResponse(409, "Manuscript is not ready for screening"));
      }
      return Promise.resolve(jsonResponse({}));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(ScreeningQueueView);
    await clickButton(wrapper, "Start screening");

    expect(message).toHaveBeenCalledWith("Manuscript is not ready for screening");
    expect(buttonByText(wrapper, "Start screening").classes()).not.toContain("is-loading");
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

  it("shows loading on chair dialog submit actions while requests are pending", async () => {
    installAuth(["CHAIR"]);
    let resolveRound: ((value: unknown) => void) | undefined;
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/chair/screening-queue") {
        return Promise.resolve(jsonResponse([
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
        ]));
      }
      if (path === "/review-rounds" && init?.method === "POST") {
        return new Promise((resolve) => {
          resolveRound = resolve;
        });
      }
      return Promise.resolve(jsonResponse({}));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(ScreeningQueueView);
    await clickButton(wrapper, "Create round");
    await clickBodyButton("Create round");

    expect(bodyButtonByText("Create round").classList.contains("is-loading")).toBe(true);

    resolveRound?.(jsonResponse({}));
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
        intent: null,
        projections: []
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
        intent: null,
        projections: []
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
          intentId: 88,
          analysisType: "REVIEWER_ASSIST",
          businessStatus: "REQUESTED"
        }));
      }
      if (path === "/review-assignments/9/agent-assist") {
        return Promise.resolve(jsonResponse({ intent: null, projections: [] }));
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

  it("keeps non-agent mutation submit buttons wired to loading state", () => {
    const submitView = readFileSync("src/views/author/SubmitManuscriptView.vue", "utf8");
    const screeningView = readFileSync("src/views/chair/ScreeningQueueView.vue", "utf8");
    const decisionView = readFileSync("src/views/chair/DecisionWorkbenchView.vue", "utf8");

    expect(submitView).toContain(":loading=\"actions.isPending('upload-pdf')\"");
    expect(submitView).toContain(":loading=\"actions.isPending('submit-version')\"");
    expect(screeningView).toContain(":loading=\"actions.isPending('create-round')\"");
    expect(screeningView).toContain(":loading=\"actions.isPending('desk-reject')\"");
    expect(decisionView).toContain(":loading=\"actions.isPending('assign-reviewer')\"");
    expect(decisionView).toContain(":loading=\"actions.isPending('submit-decision')\"");
  });

  it("formats common API errors for user-facing messages", () => {
    expect(apiErrorMessage(new ApiError(401, "Unauthorized"), "Fallback")).toBe("Your session has expired. Sign in again.");
    expect(apiErrorMessage(new ApiError(403, "Forbidden"), "Fallback")).toBe("You do not have permission to perform this action.");
    expect(apiErrorMessage(new ApiError(503, "Agent service returned HTTP 503"), "Fallback")).toBe("The service is temporarily unavailable. Try again later.");
    expect(apiErrorMessage(new Error("Network failed"), "Fallback")).toBe("Network failed");
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
            superseded: false,
            updatedAt: "2026-04-22T12:00:00Z",
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

  it("shows conflict projection summaries for chair decision workbench", async () => {
    installAuth(["CHAIR"]);
    const fetch = vi.fn((input: RequestInfo | URL) => {
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
            roundStatus: "IN_PROGRESS",
            assignmentCount: 1,
            submittedReviewCount: 1,
            conflictCount: 1,
            assignments: [{ assignmentId: 9, reviewerId: 1002, taskStatus: "SUBMITTED" }],
            conflictProjections: [
              {
                projectionId: 3,
                analysisType: "CONFLICT_ANALYSIS",
                businessStatus: "AVAILABLE",
                summaryText: "Projection conflict signal",
                redactedResult: { decisionSummary: "Projection conflict signal" },
                superseded: false,
                updatedAt: "2026-04-23T02:00:00Z"
              }
            ]
          }
        ]));
      }
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(DecisionWorkbenchView);

    expect(wrapper.find(".agent-trace-panel").exists()).toBe(true);
    expect(wrapper.text()).toContain("Conflict analysis");
    expect(wrapper.text()).toContain("Workflow Seed");
    await wrapper.get(".el-table__expand-icon").trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("Projection conflict signal");
    expect(fetch).not.toHaveBeenCalledWith(expect.stringContaining("/agent-results"), expect.anything());
  });

  it("posts conflict analysis requests as analysis intents", async () => {
    installAuth(["CHAIR"]);
    const success = vi.spyOn(ElMessage, "success").mockImplementation(() => undefined as never);
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
            roundStatus: "IN_PROGRESS",
            assignmentCount: 1,
            submittedReviewCount: 1,
            conflictCount: 1,
            assignments: [{ assignmentId: 9, reviewerId: 1002, taskStatus: "SUBMITTED" }],
            conflictProjections: []
          }
        ]));
      }
      if (path === "/review-rounds/7/conflict-analysis" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({
          intentId: 91,
          analysisType: "CONFLICT_ANALYSIS",
          businessStatus: "REQUESTED"
        }));
      }
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(DecisionWorkbenchView);
    await buttonByText(wrapper, "Conflict analysis").trigger("click");
    await flushPromises();

    expect(fetch).toHaveBeenCalledWith(
      "/api/review-rounds/7/conflict-analysis",
      expect.objectContaining({
        method: "POST"
      })
    );
    expect(success).toHaveBeenCalledWith("Conflict analysis requested.");
  });

  it("does not load legacy raw agent results for conflict analysis on the decision workbench", async () => {
    installAuth(["CHAIR"]);
    const fetch = vi.fn((input: RequestInfo | URL) => {
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
            roundStatus: "IN_PROGRESS",
            assignmentCount: 1,
            submittedReviewCount: 1,
            conflictCount: 1,
            assignments: [{ assignmentId: 9, reviewerId: 1002, taskStatus: "SUBMITTED" }],
            conflictProjections: []
          }
        ]));
      }
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(DecisionWorkbenchView);

    expect(wrapper.find(".agent-trace-panel").exists()).toBe(true);
    expect(wrapper.text()).toContain("Workflow Seed");
    await wrapper.get(".el-table__expand-icon").trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("No conflict analysis projections yet.");
    expect(fetch).not.toHaveBeenCalledWith(expect.stringContaining("/agent-results"), expect.anything());
  });

  it("shows scoped loading while chair marks an assignment overdue", async () => {
    installAuth(["CHAIR"]);
    let resolveOverdue: ((value: ReturnType<typeof jsonResponse>) => void) | undefined;
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
            roundStatus: "IN_PROGRESS",
            assignmentCount: 1,
            submittedReviewCount: 0,
            conflictCount: 0,
            assignments: [{ assignmentId: 9, reviewerId: 1002, taskStatus: "ACCEPTED" }]
          }
        ]));
      }
      if (path === "/manuscripts/11/versions/21/agent-results") {
        return Promise.resolve(jsonResponse([]));
      }
      if (path === "/review-assignments/9/mark-overdue" && init?.method === "POST") {
        return new Promise((resolve) => {
          resolveOverdue = resolve;
        });
      }
      return Promise.resolve(jsonResponse({}));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(DecisionWorkbenchView);
    await wrapper.get(".el-table__expand-icon").trigger("click");
    await flushPromises();
    await buttonByText(wrapper, "Mark overdue").trigger("click");
    await flushPromises();

    expect(buttonByText(wrapper, "Mark overdue").classes()).toContain("is-loading");

    resolveOverdue?.(jsonResponse({}));
  });
});

async function clickButton(wrapper: ReturnType<typeof mount>, label: string) {
  const button = buttonByText(wrapper, label);
  await button.trigger("click");
  await flushPromises();
}

function buttonByText(wrapper: ReturnType<typeof mount>, label: string) {
  const button = wrapper.findAll("button").find((entry) => entry.text().includes(label));
  expect(button).toBeTruthy();
  return button!;
}

function bodyButtonByText(label: string) {
  const buttons = Array.from(document.body.querySelectorAll("button")).reverse();
  const button = buttons.find((entry) => entry.textContent?.includes(label));
  expect(button).toBeTruthy();
  return button!;
}

async function clickBodyButton(label: string, waitForFlush = true) {
  const button = bodyButtonByText(label);
  button!.click();
  if (waitForFlush) {
    await flushPromises();
  }
}

async function setBodyTextarea(value: string) {
  const textarea = document.body.querySelector("textarea") as HTMLTextAreaElement | null;
  expect(textarea).toBeTruthy();
  textarea!.value = value;
  textarea!.dispatchEvent(new Event("input", { bubbles: true }));
  await flushPromises();
}
