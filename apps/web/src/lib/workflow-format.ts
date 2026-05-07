export type WorkflowTagType = "primary" | "success" | "warning" | "info" | "danger" | "";

const LABELS: Record<string, string> = {
  ACCEPT: "Accept",
  ACCEPTED: "Accepted",
  AVAILABLE: "Available",
  ASSIGNED: "Assigned",
  BIDDING_OPEN: "Bidding open",
  CAN_REVIEW: "Can review",
  CONFLICT: "Conflict",
  DECISION_CONFLICT_ANALYSIS: "Decision conflict analysis",
  DESK_REJECT: "Desk reject",
  DESK_REJECTED: "Desk rejected",
  DOUBLE_BLIND: "Double blind",
  DRAFT: "Draft",
  FAILED: "Failed",
  FAILED_VISIBLE: "Failed",
  IN_PROGRESS: "In progress",
  MAJOR_REVISION: "Major revision",
  MINOR_REVISION: "Minor revision",
  OPEN: "Open",
  OPEN_FOR_SUBMISSION: "Open for submission",
  OVERDUE: "Overdue",
  PENDING: "Pending",
  PROCESSING: "Processing",
  REQUESTED: "Requested",
  REALLOCATE_REVIEWERS: "Reallocate reviewers",
  REJECT: "Reject",
  REJECTED: "Rejected",
  REVIEW_ASSIST_ANALYSIS: "Review assist analysis",
  REVIEWER_ASSIGNMENT_ASSIST: "Reviewer assignment assist",
  REVISED_SUBMITTED: "Revised submitted",
  REVISION_REQUIRED: "Revision required",
  SCREENING_ANALYSIS: "Screening analysis",
  SINGLE_BLIND: "Single blind",
  SUBMITTED: "Submitted",
  SUCCESS: "Success",
  UNDER_REVIEW: "Under review",
  UNDER_SCREENING: "Under screening"
};

const SUCCESS_STATUSES = new Set(["ACCEPT", "ACCEPTED", "AVAILABLE", "OPEN_FOR_SUBMISSION", "SUCCESS", "COMPLETED", "WANT_TO_REVIEW"]);
const WARNING_STATUSES = new Set([
  "IN_PROGRESS",
  "MAJOR_REVISION",
  "MINOR_REVISION",
  "PENDING",
  "PROCESSING",
  "REQUESTED",
  "REVISED_SUBMITTED",
  "REVISION_REQUIRED",
  "SUBMITTED",
  "UNDER_REVIEW",
  "UNDER_SCREENING"
]);
const DANGER_STATUSES = new Set(["CONFLICT", "DECLINED", "DESK_REJECT", "DESK_REJECTED", "FAILED", "FAILED_VISIBLE", "OVERDUE", "REJECT", "REJECTED"]);
const INFO_STATUSES = new Set(["ASSIGNED", "CAN_REVIEW", "DRAFT", "OPEN", "REVIEW_ASSIST_ANALYSIS", "SCREENING_ANALYSIS", "DECISION_CONFLICT_ANALYSIS", "REVIEWER_ASSIGNMENT_ASSIST"]);

export function workflowLabel(value: string | null | undefined): string {
  if (!value) {
    return "Not set";
  }
  return LABELS[value] ?? value
    .toLowerCase()
    .split("_")
    .map((part, index) => (index === 0 ? capitalize(part) : part))
    .join(" ");
}

export function statusTagType(value: string | null | undefined): WorkflowTagType {
  if (!value) {
    return "info";
  }
  if (SUCCESS_STATUSES.has(value)) {
    return "success";
  }
  if (WARNING_STATUSES.has(value)) {
    return "warning";
  }
  if (DANGER_STATUSES.has(value)) {
    return "danger";
  }
  if (INFO_STATUSES.has(value)) {
    return "info";
  }
  return "primary";
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return "Not set";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(parsed);
}

export function formatFileSize(value: number | null | undefined): string {
  if (value == null) {
    return "Unknown size";
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

export function printableTrace(value: unknown): string {
  if (value == null) {
    return "No structured result.";
  }
  return JSON.stringify(value, null, 2);
}

function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}
