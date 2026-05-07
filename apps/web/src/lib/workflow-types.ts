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
  submissionCloseAt: string | null;
}

export interface ConferencePhaseInput {
  submissionOpenAt: string | null;
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
  phase?: ConferencePhaseInput | null;
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

export interface AssignmentAssistState {
  intent: AnalysisIntentResponse | null;
  projections: AnalysisProjectionResponse[];
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
