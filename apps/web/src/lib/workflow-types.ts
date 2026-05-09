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
  submissionNumber?: string;
  conferenceId: number | null;
  currentVersionId: number;
  currentStatus: string;
  currentRoundNo: number;
  blindMode: string;
  submittedAt: string | null;
  lastDecisionCode: string | null;
  currentVersionTitle: string;
  currentVersionNo: number;
}

export interface DecisionPackageReview {
  reviewId: number;
  reviewerLabel: string;
  overallScore: number;
  confidenceLevel: string;
  strengths: string | null;
  weaknesses: string | null;
  commentsToAuthor: string | null;
  recommendation: string;
}

export interface DecisionPackage {
  manuscriptId: number;
  roundId: number;
  versionId: number;
  versionNo: number;
  title: string;
  decisionCode: string;
  decisionReason: string | null;
  decidedAt: string | null;
  reviews: DecisionPackageReview[];
}

export interface CameraReadySubmission {
  cameraReadyId: number;
  manuscriptId: number;
  versionId: number;
  fileName: string;
  fileSize: number;
  copyrightConfirmed: boolean;
  licenseType: string | null;
  status: string;
  submittedAt: string | null;
  updatedAt: string | null;
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
  abstractText: string | null;
  keywords: string | null;
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

export interface AdminAnalysisMonitorItem {
  intentId: number;
  analysisType: string;
  businessStatus: string;
  jobId: string | null;
  anchorType: string;
  anchorLabel: string;
  summaryText: string | null;
  projectionUpdatedAt: string | null;
}

export interface AdminAnalysisMonitorPage {
  items: AdminAnalysisMonitorItem[];
  page: number;
  size: number;
  total: number;
}

export interface AdminAnalysisMonitorFilters {
  page?: number;
  size?: number;
  analysisType?: string;
  businessStatus?: string;
}

export interface ConferenceCfpSummary {
  conferenceId: number;
  name: string;
  acronym: string;
  year: number;
  status: string;
  blindMode: string;
  publicSlug: string;
  submissionOpenAt: string | null;
  abstractSubmissionCloseAt: string | null;
  submissionCloseAt: string | null;
}

export interface ConferencePhaseInput {
  submissionOpenAt: string | null;
  abstractSubmissionCloseAt: string | null;
  submissionCloseAt: string | null;
  biddingOpenAt: string | null;
  biddingCloseAt: string | null;
  reviewDeadlineAt: string | null;
  decisionReleaseAt: string | null;
}

export interface ConferenceDetail extends ConferenceCfpSummary {
  organizerUserId?: number;
  cfpText?: string;
  topicAreas?: string[];
  targetReviewsPerPaper?: number;
  defaultReviewerMaxLoad?: number;
  cfpPublished?: boolean;
  approvedBy?: number | null;
  approvedAt?: string | null;
  rejectedBy?: number | null;
  rejectedAt?: string | null;
  rejectionReason?: string | null;
  phase?: ConferencePhaseInput | null;
}

export interface PlatformReviewerSearchResult {
  reviewerId: number;
  realName: string;
  email: string;
  institution: string | null;
  researchAreas: Array<{ areaCode: string; areaName: string }>;
}

export interface ConferencePaperItem {
  manuscriptId: number;
  versionId: number;
  versionNo: number;
  roundId: number | null;
  roundNo: number | null;
  title: string;
  currentStatus: string;
  roundStatus: string | null;
  assignmentCount: number;
  submittedReviewCount: number;
  lastDecisionCode: string | null;
  submittedAt: string | null;
  averageOverallScore: number | null;
  reviewerScores: ConferencePaperReviewerScore[];
}

export interface ConferencePaperReviewerScore {
  assignmentId: number;
  reviewerId: number;
  reviewerName: string;
  taskStatus: string;
  overallScore: number | null;
  recommendation: string | null;
  submittedAt: string | null;
}

export interface ChairConferencePaperReviewDetail {
  manuscriptId: number;
  versionId: number;
  versionNo: number;
  roundId: number | null;
  roundNo: number | null;
  title: string;
  abstractText: string;
  keywords: string;
  pdfFileName: string | null;
  currentStatus: string;
  roundStatus: string | null;
  submittedAt: string | null;
  pageCount: number;
  averageOverallScore: number | null;
  reviews: ChairConferencePaperReview[];
}

export interface ChairConferencePaperReview {
  assignmentId: number;
  reviewerId: number;
  reviewerName: string;
  institution: string | null;
  taskStatus: string;
  assignedAt: string | null;
  deadlineAt: string | null;
  assignmentSubmittedAt: string | null;
  reviewId: number | null;
  noveltyScore: number | null;
  methodScore: number | null;
  experimentScore: number | null;
  writingScore: number | null;
  overallScore: number | null;
  confidenceLevel: string | null;
  strengths: string | null;
  weaknesses: string | null;
  commentsToAuthor: string | null;
  commentsToChair: string | null;
  recommendation: string | null;
  reviewSubmittedAt: string | null;
}

export interface ReviewerBiddingItem {
  manuscriptId: number;
  versionId: number;
  title: string;
  abstractText: string;
  keywords: string;
  bidValue: string | null;
  conflictDeclared: boolean;
}

export interface AssignmentDraft {
  draftId: number;
  roundId: number;
  manuscriptId: number;
  versionId: number;
  reviewerId: number;
  rankOrder: number;
  score: number;
  currentLoad: number;
  maxLoad: number;
  bidValue: string | null;
  reason: string | null;
  draftStatus: string;
}

export interface AssignmentCandidate {
  reviewerId: number;
  reviewerName: string;
  institution: string | null;
  currentLoad: number;
  maxLoad: number;
  bidValue: string | null;
  score: number;
  reason: string | null;
}

export interface AssignmentAction {
  assignmentId: number;
  taskStatus: string;
  reviewerId: number;
  reassignedFromId?: number | null;
}

export interface AutoAssignResponse {
  conferenceId: number;
  requestedReviewsPerPaper: number;
  createdCount: number;
  assignments: AssignmentAction[];
}

export interface RandomAssignmentPreviewResponse {
  conferenceId: number;
  requestedReviewsPerPaper: number;
  createdDraftCount: number;
  drafts: AssignmentDraft[];
}

export interface ConfirmAssignmentPreviewResponse {
  conferenceId: number;
  createdCount: number;
  assignments: AssignmentAction[];
}

export interface AssignmentAssistState {
  intent: AnalysisIntentResponse | null;
  projections: AnalysisProjectionResponse[];
}

export interface ReviewerInvitationOperationRow {
  invitationId: number;
  reviewerId: number;
  invitationStatus: string;
  invitedAt?: string | null;
  expiresAt?: string | null;
}

export interface ExternalDelegationOperationRow {
  delegationId: number;
  assignmentId: number;
  manuscriptId: number;
  externalName?: string | null;
  externalEmail: string;
  delegationStatus: string;
  requestedAt?: string | null;
}

export interface ImportBatchOperationRow {
  batchId: number;
  importType: string;
  batchStatus: string;
  rowCount: number;
  validRowCount: number;
  errorCount: number;
  createdAt?: string | null;
}

export interface AssignmentProposalOperationRow {
  bundleId: number;
  roundId: number;
  manuscriptId: number;
  proposalName: string;
  bundleStatus: string;
  proposalCount: number;
  createdAt?: string | null;
}

export interface MatchingScoreOperationRow {
  matchingScoreId: number;
  manuscriptId: number;
  reviewerId: number;
  scoreSource: string;
  matchingScore: number;
  rationale?: string | null;
  importedAt?: string | null;
}

export interface AssignmentOperations {
  conferenceId: number;
  reviewerInvitations: ReviewerInvitationOperationRow[];
  externalDelegations: ExternalDelegationOperationRow[];
  importBatches: ImportBatchOperationRow[];
  assignmentProposals: AssignmentProposalOperationRow[];
  matchingScores: MatchingScoreOperationRow[];
}

export interface ImportPreviewResponse {
  batchId: number;
  rowCount: number;
  validRowCount: number;
  errorCount: number;
}

export interface ImportConfirmResponse {
  batchId: number;
  appliedCount: number;
}

export interface AssignmentProposalConfirmDraftsResponse {
  bundleId: number;
  createdCount: number;
}

export interface DynamicFormField {
  fieldId: number;
  fieldKey: string;
  fieldLabel: string;
  fieldType: string;
  required: boolean;
  visibility: string;
  displayOrder: number;
}

export interface DynamicFormDefinition {
  formId: number;
  conferenceId: number;
  formType: string;
  formName: string;
  fields: DynamicFormField[];
}

export interface ReviewFormResponse {
  responseId: number;
  formId: number;
  assignmentId: number;
  responseStatus: string;
  answers: Record<string, unknown>;
  submittedAt?: string | null;
}

export interface ReviewFormPackage {
  form: DynamicFormDefinition;
  currentResponse?: ReviewFormResponse | null;
}

export interface WorkflowFormResponse {
  responseId: number;
  formId: number;
  subjectType: string;
  subjectId: number;
  responseStatus: string;
  answers: Record<string, unknown>;
  submittedAt?: string | null;
}

export interface WorkflowFormPackage {
  form: DynamicFormDefinition;
  currentResponse?: WorkflowFormResponse | null;
}

export interface ReviewFormRevision {
  revisionId: number;
  revisionNo: number;
  answers: Record<string, unknown>;
  submittedAt?: string | null;
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
