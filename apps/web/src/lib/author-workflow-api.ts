import { apiBlob, apiRequest } from "./api";
import type {
  AuthorInput,
  CameraReadySubmission,
  ConferenceCfpSummary,
  DecisionPackage,
  ManuscriptSummary,
  VersionSummary,
  WorkflowFormPackage,
  WorkflowFormResponse
} from "./workflow-types";

export function listManuscripts() {
  return apiRequest<ManuscriptSummary[]>("/manuscripts");
}

export function listPublicCfps() {
  return apiRequest<ConferenceCfpSummary[]>("/conferences/cfp");
}

export function listVersions(manuscriptId: number) {
  return apiRequest<VersionSummary[]>(`/manuscripts/${manuscriptId}/versions`);
}

export function createManuscript(payload: {
  conferenceId: number;
  title: string;
  abstract: string;
  keywords: string;
  blindMode?: string;
  authors: AuthorInput[];
}) {
  return apiRequest<ManuscriptSummary>("/manuscripts", { method: "POST", json: payload });
}

export function createRevision(manuscriptId: number, payload: {
  title: string;
  abstract: string;
  keywords: string;
  authors: AuthorInput[];
}) {
  return apiRequest<ManuscriptSummary>(`/manuscripts/${manuscriptId}/versions`, { method: "POST", json: payload });
}

export function uploadPdf(manuscriptId: number, versionId: number, file: File) {
  const form = new FormData();
  form.append("file", file);
  return apiRequest<void>(`/manuscripts/${manuscriptId}/versions/${versionId}/pdf`, {
    method: "POST",
    body: form
  });
}

export function submitVersion(manuscriptId: number, versionId: number) {
  return apiRequest<ManuscriptSummary>(`/manuscripts/${manuscriptId}/versions/${versionId}/submit`, { method: "POST" });
}

export function downloadPdf(manuscriptId: number, versionId: number) {
  return apiBlob(`/manuscripts/${manuscriptId}/versions/${versionId}/pdf`);
}

export function getDecisionPackage(manuscriptId: number) {
  return apiRequest<DecisionPackage>(`/decisions/manuscripts/${manuscriptId}/package`);
}

export function getManuscriptForm(manuscriptId: number, formType: string) {
  return apiRequest<WorkflowFormPackage>(`/manuscripts/${manuscriptId}/forms/${formType}`);
}

export function saveManuscriptFormResponse(
  manuscriptId: number,
  payload: { formId: number; responseStatus: string; answers: Record<string, unknown> }
) {
  return apiRequest<WorkflowFormResponse>(`/manuscripts/${manuscriptId}/form-response`, { method: "POST", json: payload });
}

export function submitCameraReady(manuscriptId: number, payload: {
  fileName: string;
  fileSize: number;
  copyrightConfirmed: boolean;
  licenseType?: string | null;
}) {
  return apiRequest<CameraReadySubmission>(`/manuscripts/${manuscriptId}/camera-ready`, {
    method: "POST",
    json: payload
  });
}
