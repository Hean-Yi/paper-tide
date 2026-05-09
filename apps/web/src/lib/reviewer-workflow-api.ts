import { apiBlob, apiRequest } from "./api";
import type {
  AnalysisIntentResponse,
  AssignmentPaper,
  ReviewFormPackage,
  ReviewFormRevision,
  ReviewerAssignment,
  ReviewerAssistState,
  ReviewerBiddingItem,
  ReviewReportForm
} from "./workflow-types";

export function listReviewerAssignments() {
  return apiRequest<ReviewerAssignment[]>("/review-assignments");
}

export interface ReviewerInterfaceChoice {
  shouldPrompt: boolean;
  activeAssignmentCount: number;
}

export function getReviewerInterfaceChoice() {
  return apiRequest<ReviewerInterfaceChoice>("/reviewer/interface-choice");
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

export function getReviewForm(assignmentId: number) {
  return apiRequest<ReviewFormPackage>(`/review-assignments/${assignmentId}/review-form`);
}

export function listReviewFormRevisions(assignmentId: number) {
  return apiRequest<ReviewFormRevision[]>(`/review-assignments/${assignmentId}/review-form/revisions`);
}

export function saveReviewFormResponse(
  assignmentId: number,
  payload: { formId: number; responseStatus: string; answers: Record<string, unknown> }
) {
  return apiRequest(`/review-assignments/${assignmentId}/form-response`, { method: "POST", json: payload });
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

export function listReviewerBiddingItems(conferenceId: number) {
  return apiRequest<ReviewerBiddingItem[]>(`/reviewer/conferences/${conferenceId}/bids/open`);
}

export function submitReviewerBid(conferenceId: number, payload: {
  manuscriptId: number;
  bidValue: string;
  conflictDeclared: boolean;
  conflictType?: string | null;
  conflictDescription?: string | null;
}) {
  return apiRequest(`/reviewer/conferences/${conferenceId}/bids`, { method: "POST", json: payload });
}
