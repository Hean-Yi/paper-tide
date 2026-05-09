import { apiBlob, apiRequest } from "./api";
import type {
  AnalysisIntentResponse,
  AssignmentCandidate,
  AssignmentAssistState,
  AssignmentDraft,
  AutoAssignResponse,
  AssignmentOperations,
  AssignmentProposalConfirmDraftsResponse,
  ChairConferencePaperReviewDetail,
  ConferenceDetail,
  ConferencePhaseInput,
  ConferencePaperItem,
  ConferenceCfpSummary,
  ConfirmAssignmentPreviewResponse,
  DecisionWorkbenchItem,
  ImportPreviewResponse,
  ManuscriptSummary,
  PlatformReviewerSearchResult,
  RandomAssignmentPreviewResponse,
  ScreeningQueueItem
} from "./workflow-types";

export interface ConferenceDraftPayload {
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
}

export function createConferenceDraft(payload: ConferenceDraftPayload) {
  return apiRequest<ConferenceDetail>("/chair/conferences", { method: "POST", json: payload });
}

export function updateConferenceDraft(conferenceId: number, payload: ConferenceDraftPayload) {
  return apiRequest<ConferenceDetail>(`/chair/conferences/${conferenceId}`, { method: "PUT", json: payload });
}

export function listChairConferences() {
  return apiRequest<ConferenceCfpSummary[]>("/chair/conferences");
}

export function getChairConference(conferenceId: number) {
  return apiRequest<ConferenceDetail>(`/chair/conferences/${conferenceId}`);
}

export function listConferencePapers(conferenceId: number) {
  return apiRequest<ConferencePaperItem[]>(`/chair/conferences/${conferenceId}/papers`);
}

export function getChairConferencePaperReviewDetail(conferenceId: number, manuscriptId: number) {
  return apiRequest<ChairConferencePaperReviewDetail>(
    `/chair/conferences/${conferenceId}/papers/${manuscriptId}/review-detail`
  );
}

export function getChairConferencePaperPage(conferenceId: number, manuscriptId: number, pageNo: number) {
  return apiBlob(`/chair/conferences/${conferenceId}/papers/${manuscriptId}/paper/pages/${pageNo}`);
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

export function searchPlatformReviewers(query: string) {
  return apiRequest<PlatformReviewerSearchResult[]>(`/chair/reviewers/search?query=${encodeURIComponent(query.trim())}`);
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

export function listAssignmentCandidates(roundId: number) {
  return apiRequest<AssignmentCandidate[]>(`/review-rounds/${roundId}/assignment-candidates`);
}

export function autoAssignConferenceReviewers(conferenceId: number, reviewsPerPaper: number, deadlineAt: string) {
  return apiRequest<AutoAssignResponse>(`/conferences/${conferenceId}/auto-assignments`, {
    method: "POST",
    json: { reviewsPerPaper, deadlineAt }
  });
}

export function previewRandomConferenceAssignments(conferenceId: number, reviewsPerPaper: number, deadlineAt: string) {
  return apiRequest<RandomAssignmentPreviewResponse>(`/conferences/${conferenceId}/assignment-previews/random`, {
    method: "POST",
    json: { reviewsPerPaper, deadlineAt }
  });
}

export function confirmRandomConferenceAssignmentPreview(conferenceId: number, deadlineAt: string) {
  return apiRequest<ConfirmAssignmentPreviewResponse>(`/conferences/${conferenceId}/assignment-previews/confirm`, {
    method: "POST",
    json: { deadlineAt }
  });
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

export function screeningDeskReject(payload: {
  manuscriptId: number;
  versionId: number;
  decisionReason: string;
}) {
  return apiRequest("/decisions/screening-desk-reject", { method: "POST", json: payload });
}

export function getAssignmentOperations(conferenceId: number) {
  return apiRequest<AssignmentOperations>(`/conferences/${conferenceId}/assignment-operations`);
}

export function previewReviewerInvitationImport(conferenceId: number, csvText: string) {
  return apiRequest<ImportPreviewResponse>(`/conferences/${conferenceId}/reviewer-invitations/imports/preview`, {
    method: "POST",
    json: { csvText }
  });
}

export function previewMatchingScoreImport(manuscriptId: number, csvText: string) {
  return apiRequest<ImportPreviewResponse>(`/manuscripts/${manuscriptId}/matching-scores/imports/preview`, {
    method: "POST",
    json: { csvText }
  });
}

export function confirmReviewerInvitationImport(batchId: number) {
  return apiRequest(`/reviewer-invitation-imports/${batchId}/confirm`, { method: "POST" });
}

export function confirmMatchingScoreImport(batchId: number) {
  return apiRequest(`/matching-score-imports/${batchId}/confirm`, { method: "POST" });
}

export function confirmAssignmentProposalDrafts(bundleId: number) {
  return apiRequest<AssignmentProposalConfirmDraftsResponse>(`/assignment-proposals/${bundleId}/confirm-drafts`, { method: "POST" });
}
