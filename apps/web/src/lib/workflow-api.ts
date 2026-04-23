import { apiBlob, apiRequest } from "./api";

export interface AuthorInput {
  authorName: string;
  email: string;
  institution: string;
  authorOrder: number;
  userId?: number | null;
  isCorresponding: boolean;
  isExternal: boolean;
}

export interface ManuscriptSummary {
  manuscriptId: number;
  currentVersionId: number;
  currentStatus: string;
  currentRoundNo: number;
  blindMode: string;
  submittedAt: string | null;
  lastDecisionCode: string | null;
  currentVersionTitle: string;
  currentVersionNo: number;
}

export interface VersionSummary {
  versionId: number;
  versionNo: number;
  versionType: string;
  title: string;
  submittedAt: string | null;
  pdfFileName: string | null;
  pdfFileSize: number | null;
}

export interface ReviewerAssignment {
  assignmentId: number;
  roundId: number;
  manuscriptId: number;
  versionId: number;
  reviewerId?: number;
  versionNo: number;
  title: string;
  abstractText?: string;
  keywords?: string;
  pdfFileName?: string | null;
  pdfFileSize?: number | null;
  taskStatus: string;
  assignedAt?: string | null;
  acceptedAt?: string | null;
  declinedAt?: string | null;
  deadlineAt?: string | null;
  submittedAt?: string | null;
  recommendation?: string | null;
}

export interface ScreeningQueueItem {
  manuscriptId: number;
  versionId: number;
  versionNo: number;
  title: string;
  currentStatus: string;
  currentRoundNo: number;
  blindMode: string;
  submittedAt: string | null;
  pdfFileName: string | null;
  pdfFileSize: number | null;
}

export interface DecisionAssignment {
  assignmentId: number;
  reviewerId: number;
  taskStatus: string;
  assignedAt?: string | null;
  acceptedAt?: string | null;
  deadlineAt?: string | null;
  submittedAt?: string | null;
  reassignedFromId?: number | null;
}

export interface DecisionWorkbenchItem {
  roundId: number;
  manuscriptId: number;
  versionId: number;
  versionNo: number;
  roundNo: number;
  title: string;
  currentStatus: string;
  roundStatus: string;
  deadlineAt?: string | null;
  assignmentCount: number;
  submittedReviewCount: number;
  conflictCount: number;
  lastDecisionCode?: string | null;
  assignments: DecisionAssignment[];
  conflictIntent?: AnalysisIntentResponse | null;
  conflictProjections: AnalysisProjectionResponse[];
}

export interface AssignmentPaper {
  assignmentId: number;
  manuscriptId: number;
  versionId: number;
  title: string;
  pageCount: number;
  pdfFileName: string | null;
  downloadAllowed: boolean;
}

export interface ReviewerAssistState {
  intent: AnalysisIntentResponse | null;
  projections: AnalysisProjectionResponse[];
}

export interface AnalysisIntentResponse {
  intentId: number;
  analysisType: string;
  businessStatus: string;
}

export interface AnalysisProjectionResponse {
  projectionId: number;
  analysisType: string;
  businessStatus: string;
  summaryText: string | null;
  redactedResult: Record<string, unknown> | null;
  superseded: boolean;
  updatedAt: string | null;
}

export interface ReviewReportForm {
  noveltyScore: number;
  methodScore: number;
  experimentScore: number;
  writingScore: number;
  overallScore: number;
  confidenceLevel: string;
  strengths: string;
  weaknesses: string;
  commentsToAuthor: string;
  commentsToChair: string;
  recommendation: string;
}

export function listManuscripts() {
  return apiRequest<ManuscriptSummary[]>("/manuscripts");
}

export function listVersions(manuscriptId: number) {
  return apiRequest<VersionSummary[]>(`/manuscripts/${manuscriptId}/versions`);
}

export function createManuscript(payload: {
  title: string;
  abstract: string;
  keywords: string;
  blindMode: string;
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

export function listReviewerAssignments() {
  return apiRequest<ReviewerAssignment[]>("/review-assignments");
}

export function getReviewerAssignment(assignmentId: number) {
  return apiRequest<ReviewerAssignment>(`/review-assignments/${assignmentId}`);
}

export function getAssignmentPaper(assignmentId: number) {
  return apiRequest<AssignmentPaper>(`/review-assignments/${assignmentId}/paper`);
}

export function getAssignmentPaperPage(assignmentId: number, pageNo: number) {
  return apiBlob(`/review-assignments/${assignmentId}/paper/pages/${pageNo}`);
}

export function acceptAssignment(assignmentId: number) {
  return apiRequest<ReviewerAssignment>(`/review-assignments/${assignmentId}/accept`, { method: "POST" });
}

export function declineAssignment(assignmentId: number, reason: string, conflictDeclared: boolean) {
  return apiRequest<ReviewerAssignment>(`/review-assignments/${assignmentId}/decline`, {
    method: "POST",
    json: { reason, conflictDeclared }
  });
}

export function submitReviewReport(assignmentId: number, payload: ReviewReportForm) {
  return apiRequest(`/review-assignments/${assignmentId}/review-report`, { method: "POST", json: payload });
}

export function runReviewerAssist(assignmentId: number, force = false) {
  return apiRequest<AnalysisIntentResponse>(`/review-assignments/${assignmentId}/agent-assist`, {
    method: "POST",
    json: { force }
  });
}

export function getReviewerAssist(assignmentId: number) {
  return apiRequest<ReviewerAssistState>(`/review-assignments/${assignmentId}/agent-assist`);
}

export function listScreeningQueue() {
  return apiRequest<ScreeningQueueItem[]>("/chair/screening-queue");
}

export function startScreening(manuscriptId: number, versionId: number) {
  return apiRequest<ManuscriptSummary>(`/manuscripts/${manuscriptId}/versions/${versionId}/start-screening`, { method: "POST" });
}

export function requestScreeningAnalysis(manuscriptId: number, versionId: number, force = false) {
  return apiRequest<AnalysisIntentResponse>(`/manuscripts/${manuscriptId}/versions/${versionId}/screening-analysis`, {
    method: "POST",
    json: { force }
  });
}

export function listDecisionWorkbench() {
  return apiRequest<DecisionWorkbenchItem[]>("/chair/decision-workbench");
}

export function createReviewRound(payload: {
  manuscriptId: number;
  versionId: number;
  assignmentStrategy: string;
  screeningRequired: boolean;
  deadlineAt: string;
}) {
  return apiRequest("/review-rounds", { method: "POST", json: payload });
}

export function assignReviewer(roundId: number, reviewerId: number, deadlineAt: string) {
  return apiRequest(`/review-rounds/${roundId}/assignments`, { method: "POST", json: { reviewerId, deadlineAt } });
}

export function markOverdue(assignmentId: number) {
  return apiRequest(`/review-assignments/${assignmentId}/mark-overdue`, { method: "POST" });
}

export function triggerConflictAnalysis(roundId: number, force = false) {
  return apiRequest<AnalysisIntentResponse>(`/review-rounds/${roundId}/conflict-analysis`, { method: "POST", json: { force } });
}

export function decide(payload: {
  manuscriptId: number;
  roundId: number;
  versionId: number;
  decisionCode: string;
  decisionReason: string;
}) {
  return apiRequest("/decisions", { method: "POST", json: payload });
}
