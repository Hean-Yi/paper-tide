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
    error.value = apiError instanceof ApiError ? apiError.message : "无法加载申请列表。";
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
        <p class="eyebrow">管理员</p>
        <h1>角色申请</h1>
      </div>
      <el-button :loading="loading" @click="loadApplications">刷新</el-button>
    </header>

    <el-alert v-if="error" type="error" :title="error" show-icon />

    <el-table v-loading="loading" :data="applications" class="workflow-table">
      <el-table-column prop="applicationId" label="申请" width="130" />
      <el-table-column label="申请人" min-width="220">
        <template #default="{ row }">
          <div class="stacked-cell">
            <strong>{{ row.realName || row.username || `User ${row.userId}` }}</strong>
            <span>{{ row.email || "暂无邮箱" }}</span>
            <span>{{ row.institution || "暂无机构" }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="registrationType" label="类型" width="150" />
      <el-table-column prop="status" label="状态" />
      <el-table-column label="申请详情" min-width="360">
        <template #default="{ row }">
          <div class="stacked-cell">
            <span v-if="profileLinks(row).length">学术主页：{{ profileLinks(row).join(" | ") }}</span>
            <span v-if="row.representativeWorks?.length">代表作：{{ row.representativeWorks.join("; ") }}</span>
            <span v-if="row.conflictDomains?.length">冲突域：{{ row.conflictDomains.join(", ") }}</span>
            <span v-if="row.plannedConferenceTitle">会议：{{ row.plannedConferenceTitle }}</span>
            <span>研究领域：{{ researchAreaText(row) }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="approve(row.applicationId)">审批通过</el-button>
          <el-button size="small" @click="reject(row.applicationId)">审批拒绝</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
