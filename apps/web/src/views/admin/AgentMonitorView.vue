<script setup lang="ts">
import { onMounted, ref } from "vue";

import { useApiError } from "../../composables/useApiError";
import { listAdminAnalysisMonitor, type AdminAnalysisMonitorItem } from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";

const rows = ref<AdminAnalysisMonitorItem[]>([]);
const loading = ref(false);
const { showApiError } = useApiError();

onMounted(loadMonitor);

async function loadMonitor() {
  loading.value = true;
  try {
    rows.value = await listAdminAnalysisMonitor();
  } catch (error) {
    showApiError(error, "Analysis monitor could not be loaded.");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">Admin</p>
        <h1>Agent monitor</h1>
        <p class="body">Inspect analysis intents, projection state, and execution identifiers.</p>
      </div>
      <el-button :loading="loading" @click="loadMonitor">Refresh</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" empty-text="No analysis intents are available yet.">
      <el-table-column prop="intentId" label="Intent" width="100" />
      <el-table-column prop="analysisType" label="Analysis" width="180">
        <template #default="{ row }">
          {{ workflowLabel(row.analysisType) }}
        </template>
      </el-table-column>
      <el-table-column prop="businessStatus" label="Status" width="150">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.businessStatus)">{{ workflowLabel(row.businessStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="anchorLabel" label="Anchor" min-width="200" />
      <el-table-column prop="jobId" label="Job" min-width="180">
        <template #default="{ row }">{{ row.jobId || "Pending runtime binding" }}</template>
      </el-table-column>
      <el-table-column prop="summaryText" label="Summary" min-width="220">
        <template #default="{ row }">{{ row.summaryText || "No projection summary yet." }}</template>
      </el-table-column>
      <el-table-column prop="projectionUpdatedAt" label="Projection updated" min-width="180">
        <template #default="{ row }">{{ formatDateTime(row.projectionUpdatedAt) }}</template>
      </el-table-column>
      <template #empty>
        <el-empty description="No analysis intents are available yet." />
      </template>
    </el-table>
  </section>
</template>
