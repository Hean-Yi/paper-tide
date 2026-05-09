import type { ConferencePhaseInput } from "./workflow-types";

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

export interface VisibleConferenceScheduleInput {
  submissionOpenAt: string | null;
  abstractSubmissionCloseAt: string | null;
  submissionCloseAt: string | null;
  reviewDeadlineAt: string | null;
  decisionReleaseAt: string | null;
}

export function conferenceSchedulePayload(input: VisibleConferenceScheduleInput): ConferencePhaseInput {
  return {
    submissionOpenAt: input.submissionOpenAt,
    abstractSubmissionCloseAt: input.abstractSubmissionCloseAt,
    submissionCloseAt: input.submissionCloseAt,
    biddingOpenAt: offsetDateTime(input.submissionCloseAt, ONE_DAY_MS),
    biddingCloseAt: offsetDateTime(input.reviewDeadlineAt, -ONE_DAY_MS),
    reviewDeadlineAt: input.reviewDeadlineAt,
    decisionReleaseAt: input.decisionReleaseAt
  };
}

function offsetDateTime(value: string | null, offsetMs: number): string | null {
  if (!value) {
    return null;
  }
  const parsed = Date.parse(value);
  if (!Number.isFinite(parsed)) {
    return null;
  }
  return new Date(parsed + offsetMs).toISOString().replace(".000Z", "Z");
}
