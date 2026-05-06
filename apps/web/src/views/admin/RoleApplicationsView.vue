<script setup lang="ts">
import { onMounted, ref } from "vue";

import { ApiError, apiRequest } from "../../lib/api";

interface RoleApplication {
  applicationId: number;
  userId: number;
  registrationType: string;
  status: string;
  rejectionReason?: string | null;
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
      <el-table-column prop="userId" label="User" width="120" />
      <el-table-column prop="registrationType" label="Type" width="150" />
      <el-table-column prop="status" label="Status" />
      <el-table-column label="Actions" width="220">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="approve(row.applicationId)">Approve</el-button>
          <el-button size="small" @click="reject(row.applicationId)">Reject</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
