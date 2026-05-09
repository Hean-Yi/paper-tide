import { apiRequest } from "./api";
import type {
  AdminAnalysisMonitorFilters,
  AdminAnalysisMonitorPage,
  ConferenceCfpSummary,
  ConferenceDetail
} from "./workflow-types";

export function listPendingConferenceApprovals() {
  return apiRequest<ConferenceCfpSummary[]>("/admin/conferences/pending");
}

export function getAdminConference(conferenceId: number) {
  return apiRequest<ConferenceDetail>(`/admin/conferences/${conferenceId}`);
}

export function approveConference(conferenceId: number) {
  return apiRequest<ConferenceDetail>(`/admin/conferences/${conferenceId}/approve`, { method: "POST" });
}

export function rejectConference(conferenceId: number, rejectionReason: string) {
  return apiRequest<ConferenceDetail>(`/admin/conferences/${conferenceId}/reject`, {
    method: "POST",
    json: { rejectionReason }
  });
}

export function listAdminAnalysisMonitor(filters: AdminAnalysisMonitorFilters = {}) {
  const params = new URLSearchParams();
  params.set("page", String(filters.page ?? 1));
  params.set("size", String(filters.size ?? 20));
  if (filters.analysisType) {
    params.set("analysisType", filters.analysisType);
  }
  if (filters.businessStatus) {
    params.set("businessStatus", filters.businessStatus);
  }
  return apiRequest<AdminAnalysisMonitorPage>(`/admin/analysis-monitor?${params.toString()}`);
}
