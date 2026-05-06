<script setup lang="ts">
import { onMounted, ref } from "vue";

import { ApiError, apiRequest } from "../../lib/api";

interface RoleApplication {
  applicationId: number;
  userId: number;
  registrationType: string;
  status: string;
  rejectionReason?: string | null;
  submittedAt?: string | null;
  username?: string | null;
  realName?: string | null;
  email?: string | null;
  institution?: string | null;
  homepageUrl?: string | null;
  orcid?: string | null;
  dblpUrl?: string | null;
  googleScholarUrl?: string | null;
  representativeWorks?: string[];
  conflictDomains?: string[];
  plannedConferenceTitle?: string | null;
  researchAreas?: Array<{ areaCode: string; areaName: string }>;
}

const applications = ref<RoleApplication[]>([]);
const loading = ref(false);
const error = ref("");

onMounted(loadApplications);

async function loadApplications() {
  loading.value = true;
  error.value = "";
  try {
    applications.value = await apiRequest<RoleApplication[]>("/admin/role-applications");
  } catch (apiError) {
    error.value = apiError instanceof ApiError ? apiError.message : "Unable to load applications.";
  } finally {
    loading.value = false;
  }
}

async function approve(applicationId: number) {
  await apiRequest(`/admin/role-applications/${applicationId}/approve`, { method: "POST", json: {} });
  await loadApplications();
}

async function reject(applicationId: number) {
  await apiRequest(`/admin/role-applications/${applicationId}/reject`, {
    method: "POST",
    json: { rejectionReason: "Rejected by admin" }
  });
  await loadApplications();
}

function profileLinks(row: RoleApplication): string[] {
  return [row.homepageUrl, row.orcid, row.dblpUrl, row.googleScholarUrl]
    .filter((value): value is string => Boolean(value));
}

function researchAreaText(row: RoleApplication): string {
  return row.researchAreas?.map((area) => area.areaName || area.areaCode).join(", ") || "None";
}
</script>

<template>
  <section class="page-section">
    <header class="page-header">
      <div>
        <p class="eyebrow">Admin</p>
        <h1>Role applications</h1>
      </div>
      <el-button :loading="loading" @click="loadApplications">Refresh</el-button>
    </header>

    <el-alert v-if="error" type="error" :title="error" show-icon />

    <el-table v-loading="loading" :data="applications" class="workflow-table">
      <el-table-column prop="applicationId" label="Application" width="130" />
      <el-table-column label="Applicant" min-width="220">
        <template #default="{ row }">
          <div class="stacked-cell">
            <strong>{{ row.realName || row.username || `User ${row.userId}` }}</strong>
            <span>{{ row.email || "No email" }}</span>
            <span>{{ row.institution || "No institution" }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="registrationType" label="Type" width="150" />
      <el-table-column prop="status" label="Status" />
      <el-table-column label="Payload summary" min-width="360">
        <template #default="{ row }">
          <div class="stacked-cell">
            <span v-if="profileLinks(row).length">Profile: {{ profileLinks(row).join(" | ") }}</span>
            <span v-if="row.representativeWorks?.length">Works: {{ row.representativeWorks.join("; ") }}</span>
            <span v-if="row.conflictDomains?.length">Conflicts: {{ row.conflictDomains.join(", ") }}</span>
            <span v-if="row.plannedConferenceTitle">Conference: {{ row.plannedConferenceTitle }}</span>
            <span>Areas: {{ researchAreaText(row) }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="Actions" width="220">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="approve(row.applicationId)">Approve</el-button>
          <el-button size="small" @click="reject(row.applicationId)">Reject</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
