import { apiRequest } from "./api";
import type {
  AnalysisIntentResponse,
  AssignmentAssistState,
  AssignmentDraft,
  ConferenceDetail,
  ConferencePhaseInput,
  DecisionWorkbenchItem,
  ManuscriptSummary,
  ScreeningQueueItem
} from "./workflow-types";

export function createConferenceDraft(payload: {
  name: string;
  acronym: string;
  year: number;
  blindMode: string;
  cfpText: string;
  topicAreas: string[];
  targetReviewsPerPaper: number;
  defaultReviewerMaxLoad: number;
  publicSlug: string;
  phase: ConferencePhaseInput;
}) {
  return apiRequest<ConferenceDetail>("/chair/conferences", { method: "POST", json: payload });
}

export function submitConferenceForApproval(conferenceId: number) {
  return apiRequest<ConferenceDetail>(`/chair/conferences/${conferenceId}/submit-approval`, { method: "POST" });
}

export function advanceConference(conferenceId: number, status: string) {
  return apiRequest<ConferenceDetail>(`/chair/conferences/${conferenceId}/advance`, { method: "POST", json: { status } });
}

export function addConferenceReviewer(conferenceId: number, reviewerId: number, maxLoad: number) {
  return apiRequest(`/chair/conferences/${conferenceId}/reviewers`, { method: "POST", json: { reviewerId, maxLoad } });
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

export function generateAssignmentDrafts(roundId: number, limit = 5) {
  return apiRequest<AssignmentDraft[]>(`/review-rounds/${roundId}/assignment-drafts/generate`, {
    method: "POST",
    json: { limit }
  });
}

export function listAssignmentDrafts(roundId: number) {
  return apiRequest<AssignmentDraft[]>(`/review-rounds/${roundId}/assignment-drafts`);
}

export function confirmAssignmentDrafts(roundId: number, draftIds: number[], deadlineAt: string) {
  return apiRequest(`/review-rounds/${roundId}/assignment-drafts/confirm`, {
    method: "POST",
    json: { draftIds, deadlineAt }
  });
}

export function requestAssignmentAssist(roundId: number, force = false) {
  return apiRequest<AnalysisIntentResponse>(`/review-rounds/${roundId}/assignment-assist`, {
    method: "POST",
    json: { force }
  });
}

export const runAssignmentAssist = requestAssignmentAssist;

export function getAssignmentAssist(roundId: number) {
  return apiRequest<AssignmentAssistState>(`/review-rounds/${roundId}/assignment-assist`);
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
