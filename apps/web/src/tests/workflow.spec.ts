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
import AssignmentOperationsView from "../views/chair/AssignmentOperationsView.vue";
import PublicationOperationsView from "../views/chair/PublicationOperationsView.vue";
import AgentMonitorView from "../views/admin/AgentMonitorView.vue";
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

function legacyResultsPath(manuscriptId: number, versionId: number) {
  return ["/manuscripts", String(manuscriptId), "versions", String(versionId), "agent" + "-results"].join("/");
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
    vi.useRealTimers();
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

  it("requests screening analysis through the intent outbox endpoint", async () => {
    installAuth(["CHAIR"]);
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
      if (path === "/manuscripts/11/versions/21/screening-analysis" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({
          intentId: 89,
          analysisType: "SCREENING",
          businessStatus: "REQUESTED"
        }));
      }
      return Promise.resolve(errorResponse(404, `Unexpected path ${path}`));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(ScreeningQueueView);
    await clickButton(wrapper, "Run agent");

    expect(fetch).toHaveBeenCalledWith(
      "/api/manuscripts/11/versions/21/screening-analysis",
      expect.objectContaining({ method: "POST" })
    );
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

  it("shows author-facing API errors for manuscript download and submit actions", async () => {
    installAuth(["AUTHOR"]);
    const messageError = vi.spyOn(ElMessage, "error").mockImplementation(() => undefined as never);
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/manuscripts" && (!init || init.method === undefined)) {
        return Promise.resolve(jsonResponse([
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
        ]));
      }
      if (path === "/manuscripts/11/versions/21/pdf") {
        return Promise.resolve(errorResponse(404, "PDF not found"));
      }
      if (path === "/manuscripts/11/versions/21/submit") {
        return Promise.resolve(errorResponse(400, "A PDF is required before submission"));
      }
      return Promise.resolve(jsonResponse({}));
    }));

    const wrapper = await mountWithRouter(ManuscriptListView);

    await clickButton(wrapper, "Download PDF");
    await flushPromises();
    await clickButton(wrapper, "Submit");
    await flushPromises();

    expect(messageError).toHaveBeenCalledWith("PDF not found");
    expect(messageError).toHaveBeenCalledWith("A PDF is required before submission");
  });

  it("loads author decision packages without exposing reviewer identity", async () => {
    installAuth(["AUTHOR"]);
    const fetch = vi.fn((input: RequestInfo | URL) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/manuscripts") {
        return Promise.resolve(jsonResponse([
          {
            manuscriptId: 11,
            currentVersionId: 21,
            currentStatus: "ACCEPTED",
            currentRoundNo: 1,
            blindMode: "DOUBLE_BLIND",
            submittedAt: "2026-04-13T05:00:00Z",
            lastDecisionCode: "ACCEPT",
            currentVersionTitle: "Workflow Seed",
            currentVersionNo: 1
          }
        ]));
      }
      if (path === "/decisions/manuscripts/11/package") {
        return Promise.resolve(jsonResponse({
          manuscriptId: 11,
          roundId: 31,
          versionId: 21,
          versionNo: 1,
          title: "Workflow Seed",
          decisionCode: "ACCEPT",
          decisionReason: "Accepted with minor edits.",
          decidedAt: "2026-09-01T00:00:00Z",
          reviews: [
            {
              reviewId: 91,
              reviewerLabel: "Reviewer 1",
              overallScore: 4,
              confidenceLevel: "HIGH",
              strengths: "Strong method",
              weaknesses: "Small dataset",
              commentsToAuthor: "Clarify the dataset.",
              recommendation: "ACCEPT"
            }
          ]
        }));
      }
      return Promise.resolve(errorResponse(404, `Unexpected path ${path}`));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(ManuscriptListView);
    await clickButton(wrapper, "Decision package");

    expect(fetch).toHaveBeenCalledWith("/api/decisions/manuscripts/11/package", expect.anything());
    expect(document.body.textContent).toContain("Accepted with minor edits.");
    expect(document.body.textContent).toContain("Reviewer 1");
    expect(document.body.textContent).not.toContain("1002");
  });

  it("submits camera-ready metadata only for accepted author manuscripts", async () => {
    installAuth(["AUTHOR"]);
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/manuscripts") {
        return Promise.resolve(jsonResponse([
          {
            manuscriptId: 11,
            currentVersionId: 21,
            currentStatus: "ACCEPTED",
            currentRoundNo: 1,
            blindMode: "DOUBLE_BLIND",
            submittedAt: "2026-04-13T05:00:00Z",
            lastDecisionCode: "ACCEPT",
            currentVersionTitle: "Workflow Seed",
            currentVersionNo: 1
          }
        ]));
      }
      if (path === "/manuscripts/11/camera-ready" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({
          cameraReadyId: 41,
          manuscriptId: 11,
          versionId: 21,
          fileName: "Workflow Seed-camera-ready.pdf",
          fileSize: 0,
          copyrightConfirmed: true,
          licenseType: "CC-BY",
          status: "SUBMITTED"
        }));
      }
      return Promise.resolve(errorResponse(404, `Unexpected path ${path}`));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(ManuscriptListView);
    await clickButton(wrapper, "Camera-ready");
    const checkbox = document.body.querySelector(".el-checkbox") as HTMLElement;
    checkbox.click();
    await flushPromises();
    await clickBodyButton("Submit camera-ready");

    const submitCall = fetch.mock.calls.find(([input]) => String(input) === "/api/manuscripts/11/camera-ready");
    expect(submitCall).toBeTruthy();
    expect(JSON.parse(submitCall?.[1]?.body as string)).toEqual(expect.objectContaining({
      fileName: "Workflow Seed-camera-ready.pdf",
      copyrightConfirmed: true,
      licenseType: "CC-BY"
    }));
  });

  it("validates author manuscript form before creating a draft", async () => {
    installAuth(["AUTHOR"]);
    const fetch = vi.fn(() => Promise.resolve(jsonResponse([])));
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(SubmitManuscriptView);
    await clickButton(wrapper, "Create manuscript");

    expect(fetch).toHaveBeenCalledWith("/api/conferences/cfp", expect.anything());
    expect(fetch).not.toHaveBeenCalledWith("/api/manuscripts", expect.anything());
  });

  it("submits author manuscripts to a selected public conference", async () => {
    installAuth(["AUTHOR"]);
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/conferences/cfp") {
        return Promise.resolve(jsonResponse([
          {
            conferenceId: 501,
            name: "Review Systems 2026",
            acronym: "RS",
            year: 2026,
            status: "OPEN_FOR_SUBMISSION",
            blindMode: "SINGLE_BLIND",
            publicSlug: "review-systems-2026",
            submissionOpenAt: "2026-06-01T00:00:00Z",
            submissionCloseAt: "2026-07-01T00:00:00Z"
          }
        ]));
      }
      if (path === "/manuscripts" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({
          manuscriptId: 61,
          conferenceId: 501,
          currentVersionId: 71,
          currentStatus: "DRAFT",
          blindMode: "SINGLE_BLIND"
        }));
      }
      return Promise.resolve(errorResponse(404, `Unexpected path ${path}`));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(SubmitManuscriptView);
    await flushPromises();

    expect(wrapper.text()).toContain("Review Systems 2026");
    expect(wrapper.text()).toContain("Single blind");

    await wrapper.get('input[placeholder="Title"]').setValue("Conference Paper");
    await wrapper.get("textarea").setValue("Conference abstract");
    await wrapper.get('input[placeholder="comma,separated,keywords"]').setValue("conference,review");
    await clickButton(wrapper, "Create manuscript");
    await flushPromises();

    const createCall = fetch.mock.calls.find(([input]) => String(input) === "/api/manuscripts");
    expect(createCall).toBeTruthy();
    expect(JSON.parse(createCall?.[1]?.body as string)).toEqual(expect.objectContaining({
      conferenceId: 501,
      title: "Conference Paper"
    }));
  });

  it("shows the configured PDF upload limit on author upload screens", async () => {
    installAuth(["AUTHOR"]);
    mockApi({
      "/manuscripts": []
    });

    const submitWrapper = await mountWithRouter(SubmitManuscriptView);
    expect(submitWrapper.text()).toContain("100 MB");

    submitWrapper.unmount();

    const listWrapper = await mountWithRouter(ManuscriptListView);
    expect(listWrapper.text()).toContain("100 MB");
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

  it("renders configured reviewer form fields with saved draft answers", async () => {
    installAuth(["REVIEWER"]);
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:reader-page-1"),
      revokeObjectURL: vi.fn()
    });
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
      if (path === "/review-assignments/9/agent-assist") {
        return Promise.resolve(jsonResponse({ intent: null, projections: [] }));
      }
      if (path === "/review-assignments/9/review-form") {
        return Promise.resolve(jsonResponse({
          form: {
            formId: 51,
            conferenceId: 0,
            formType: "REVIEW",
            formName: "Configurable Review",
            fields: [
              { fieldId: 61, fieldKey: "summary", fieldLabel: "Summary", fieldType: "LONG_TEXT", required: true, visibility: "AUTHOR_VISIBLE", displayOrder: 1 }
            ]
          },
          currentResponse: {
            responseId: 71,
            formId: 51,
            assignmentId: 9,
            responseStatus: "DRAFT",
            answers: { summary: "Saved configurable draft" }
          }
        }));
      }
      return Promise.resolve(errorResponse(404, `Unexpected path ${path}`));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(ReviewEditorView, "/reviewer/reviews/9");

    expect(fetch).toHaveBeenCalledWith("/api/review-assignments/9/review-form", expect.anything());
    expect(wrapper.text()).toContain("Configurable Review");
    expect(wrapper.text()).toContain("Summary");
    expect((wrapper.find('[data-test="dynamic-review-summary"] textarea').element as HTMLTextAreaElement).value)
      .toBe("Saved configurable draft");
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

  it("shows reviewer assist progress immediately and polls until the projection appears", async () => {
    vi.useFakeTimers();
    installAuth(["REVIEWER"]);
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:reader-page-1"),
      revokeObjectURL: vi.fn()
    });
    let assistReads = 0;
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
        assistReads += 1;
        if (assistReads >= 3) {
          return Promise.resolve(jsonResponse({
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
                summaryText: "Checklist ready.",
                superseded: false,
                updatedAt: "2026-04-27T12:00:00Z",
                redactedResult: { checklist: ["Check baseline clarity"] }
              }
            ]
          }));
        }
        return Promise.resolve(jsonResponse({
          intent: assistReads === 1 ? null : {
            intentId: 88,
            analysisType: "REVIEWER_ASSIST",
            businessStatus: "REQUESTED"
          },
          projections: []
        }));
      }
      return Promise.resolve(jsonResponse({}));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(ReviewEditorView, "/reviewer/reviews/9");
    await clickButton(wrapper, "Run review assistant");

    expect(wrapper.text()).toContain("Analysis in progress");
    expect(wrapper.find(".assist-progress-animation").exists()).toBe(true);

    await vi.advanceTimersByTimeAsync(3000);
    await flushPromises();

    expect(wrapper.text()).toContain("Checklist ready.");
    expect(wrapper.text()).not.toContain("Analysis in progress");
  });

  it("shows reviewer assist failure feedback and retry", async () => {
    installAuth(["REVIEWER"]);
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:reader-page-1"),
      revokeObjectURL: vi.fn()
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
        pageCount: 1,
        pdfFileName: "workflow.pdf",
        downloadAllowed: false
      },
      "/review-assignments/9/agent-assist": {
        intent: {
          intentId: 88,
          analysisType: "REVIEWER_ASSIST",
          businessStatus: "FAILED_VISIBLE"
        },
        projections: []
      }
    }, {
      "/review-assignments/9/paper/pages/1": new Blob(["page"], { type: "image/png" })
    });

    const wrapper = await mountWithRouter(ReviewEditorView, "/reviewer/reviews/9");

    expect(wrapper.text()).toContain("Review assistant failed. Try again.");
    expect(buttonByText(wrapper, "Retry").exists()).toBe(true);
  });

  it("collapses assignment details by default and lets reviewers collapse the side panel", async () => {
    installAuth(["REVIEWER"]);
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:reader-page-1"),
      revokeObjectURL: vi.fn()
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
        pageCount: 1,
        pdfFileName: "workflow.pdf",
        downloadAllowed: false
      },
      "/review-assignments/9/agent-assist": {
        intent: null,
        projections: []
      }
    }, {
      "/review-assignments/9/paper/pages/1": new Blob(["page"], { type: "image/png" })
    });

    const wrapper = await mountWithRouter(ReviewEditorView, "/reviewer/reviews/9");

    expect(wrapper.find('[data-test="assignment-details"].is-collapsed').exists()).toBe(true);
    expect(wrapper.find('[data-test="review-side-panel"]').exists()).toBe(true);
    expect(wrapper.find(".review-workspace").classes()).not.toContain("is-side-collapsed");

    await clickButton(wrapper, "Collapse assist panel");

    expect(wrapper.find(".review-workspace").classes()).toContain("is-side-collapsed");
    expect(wrapper.find('[data-test="review-side-panel"]').exists()).toBe(false);
    expect(wrapper.find(".secure-paper-reader").exists()).toBe(true);
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
    expect(statusTagType("FAILED_VISIBLE")).toBe("danger");
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
    expect(fetch).not.toHaveBeenCalledWith(expect.stringContaining(legacyResultsPath(11, 21)), expect.anything());
  });

  it("loads admin analysis monitor rows from the governance endpoint", async () => {
    installAuth(["ADMIN"]);
    const fetch = vi.fn((input: RequestInfo | URL) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/admin/analysis-monitor?page=1&size=20") {
        return Promise.resolve(jsonResponse({
          items: [
            {
              intentId: 101,
              analysisType: "REVIEWER_ASSIST",
              businessStatus: "AVAILABLE",
              jobId: "job-1",
              anchorType: "ASSIGNMENT",
              anchorLabel: "Assignment #77",
              projectionUpdatedAt: "2026-04-23T08:00:00Z",
              summaryText: "Checklist ready."
            }
          ],
          page: 1,
          size: 20,
          total: 1
        }));
      }
      return Promise.resolve(errorResponse(404, `Unexpected path ${path}`));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(AgentMonitorView);

    expect(fetch).toHaveBeenCalledWith("/api/admin/analysis-monitor?page=1&size=20", expect.anything());
    expect(wrapper.text()).toContain("Checklist ready.");
    expect(wrapper.text()).toContain("Assignment #77");
    expect(wrapper.text()).toContain("job-1");
  });

  it("filters admin analysis monitor rows by analysis type and status", async () => {
    installAuth(["ADMIN"]);
    const fetch = vi.fn((input: RequestInfo | URL) => {
      const path = String(input).replace(/^\/api/, "");
      if (path.startsWith("/admin/analysis-monitor")) {
        return Promise.resolve(jsonResponse({ items: [], page: 1, size: 20, total: 0 }));
      }
      return Promise.resolve(errorResponse(404, `Unexpected path ${path}`));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(AgentMonitorView);
    await wrapper.find('[data-test="analysis-type-filter"]').setValue("REVIEWER_ASSIST");
    await wrapper.find('[data-test="business-status-filter"]').setValue("AVAILABLE");
    await clickButton(wrapper, "Apply filters");

    expect(fetch).toHaveBeenLastCalledWith(
      "/api/admin/analysis-monitor?page=1&size=20&analysisType=REVIEWER_ASSIST&businessStatus=AVAILABLE",
      expect.anything()
    );
  });

  it("uses shared async action state for reviewer assist run and refresh controls", async () => {
    installAuth(["REVIEWER"]);
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:reader-page-1"),
      revokeObjectURL: vi.fn()
    });
    let resolveRun: ((value: ReturnType<typeof jsonResponse>) => void) | undefined;
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
        return new Promise((resolve) => {
          resolveRun = resolve;
        });
      }
      if (path === "/review-assignments/9/agent-assist") {
        return Promise.resolve(jsonResponse({ intent: null, projections: [] }));
      }
      return Promise.resolve(jsonResponse({}));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(ReviewEditorView, "/reviewer/reviews/9");
    await buttonByText(wrapper, "Run review assistant").trigger("click");
    await flushPromises();

    expect(buttonByText(wrapper, "Run review assistant").classes()).toContain("is-loading");
    expect(buttonByText(wrapper, "Refresh").classes()).not.toContain("is-loading");

    resolveRun?.(jsonResponse({
      intentId: 88,
      analysisType: "REVIEWER_ASSIST",
      businessStatus: "REQUESTED"
    }));
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
    expect(fetch).not.toHaveBeenCalledWith(expect.stringContaining(legacyResultsPath(11, 21)), expect.anything());
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

  it("renders chair assignment operations and confirms proposal drafts", async () => {
    installAuth(["CHAIR"]);
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/conferences/0/assignment-operations") {
        return Promise.resolve(jsonResponse({
          conferenceId: 0,
          reviewerInvitations: [{ invitationId: 1, reviewerId: 1004, invitationStatus: "PENDING" }],
          externalDelegations: [{ delegationId: 2, assignmentId: 9, manuscriptId: 11, externalEmail: "external@example.com", delegationStatus: "REQUESTED" }],
          importBatches: [{ batchId: 3, importType: "MATCHING_SCORES", batchStatus: "PREVIEWED", rowCount: 2, validRowCount: 1, errorCount: 1 }],
          assignmentProposals: [{ bundleId: 4, roundId: 7, manuscriptId: 11, proposalName: "TPMS proposal", bundleStatus: "PROPOSED", proposalCount: 3 }],
          matchingScores: [{ matchingScoreId: 5, manuscriptId: 11, reviewerId: 1004, scoreSource: "TPMS_IMPORT", matchingScore: 0.91, rationale: "Strong match" }]
        }));
      }
      if (path === "/assignment-proposals/4/confirm-drafts" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({ bundleId: 4, createdCount: 3 }));
      }
      if (path === "/conferences/0/reviewer-invitations/imports/preview" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({ batchId: 6, rowCount: 1, validRowCount: 1, errorCount: 0 }));
      }
      return Promise.resolve(errorResponse(404, `Unexpected path ${path}`));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(AssignmentOperationsView);

    expect(wrapper.text()).toContain("TPMS proposal");
    expect(wrapper.text()).toContain("external@example.com");
    await clickButton(wrapper, "Confirm drafts");
    await clickButton(wrapper, "Preview invitations");

    expect(fetch).toHaveBeenCalledWith("/api/assignment-proposals/4/confirm-drafts", expect.objectContaining({ method: "POST" }));
    expect(fetch).toHaveBeenCalledWith(
      "/api/conferences/0/reviewer-invitations/imports/preview",
      expect.objectContaining({ method: "POST" })
    );
  });

  it("renders chair publication operations and exports proceedings metadata", async () => {
    installAuth(["CHAIR"]);
    const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/conferences/0/publication-operations") {
        return Promise.resolve(jsonResponse({
          conferenceId: 0,
          emailTemplates: [{ templateId: 10, activeVersionId: 11, templateKey: "decision_notice" }],
          emailHistory: [{ emailHistoryId: 12, templateKey: "decision_notice", recipientEmail: "chair@example.com", deliveryStatus: "RECORDED" }],
          offlineReviewImports: [{ batchId: 13, assignmentId: 9, reviewerId: 1002, batchStatus: "APPLIED", rowCount: 1, validRowCount: 1, errorCount: 0 }],
          cameraReadyFiles: [{ cameraReadyFileId: 14, manuscriptId: 11, fileName: "camera-ready.pdf", fileSize: 2048, fileStatus: "SUBMITTED" }],
          publicationMetadata: [{ publicationMetadataId: 15, manuscriptId: 11, doi: "10.5555/wave9", publicationStatus: "READY_FOR_PROCEEDINGS" }],
          proceedingsExports: [{ exportBatchId: 16, exportName: "Wave 9 Export", exportStatus: "PREVIEWED", paperCount: 1 }]
        }));
      }
      if (path === "/proceedings-exports/16/download-metadata" && init?.method === "POST") {
        return Promise.resolve(jsonResponse({
          exportBatchId: 16,
          exportStatus: "EXPORTED",
          downloadFileName: "wave-9-export.json",
          downloadUrl: "/api/proceedings-exports/16/files/wave-9-export.json",
          paperCount: 1
        }));
      }
      return Promise.resolve(errorResponse(404, `Unexpected path ${path}`));
    });
    vi.stubGlobal("fetch", fetch);

    const wrapper = await mountWithRouter(PublicationOperationsView);

    expect(wrapper.text()).toContain("decision_notice");
    expect(wrapper.text()).toContain("camera-ready.pdf");
    expect(wrapper.text()).toContain("Wave 9 Export");
    await clickButton(wrapper, "Export metadata");

    expect(fetch).toHaveBeenCalledWith(
      "/api/proceedings-exports/16/download-metadata",
      expect.objectContaining({ method: "POST" })
    );
    expect(wrapper.text()).toContain("wave-9-export.json");
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
