<script setup lang="ts">
import { onMounted, ref } from "vue";

import { apiErrorMessage } from "../composables/useApiError";
import { formatDateTime, statusTagType, workflowLabel } from "../lib/workflow-format";
import { listPublicCfps, type ConferenceCfpSummary } from "../lib/workflow-api";

const conferences = ref<ConferenceCfpSummary[]>([]);
const loading = ref(false);
const error = ref("");

onMounted(loadCfps);

async function loadCfps() {
  loading.value = true;
  error.value = "";
  try {
    conferences.value = await listPublicCfps();
  } catch (err) {
    error.value = apiErrorMessage(err, "Conference CFPs are unavailable.");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Call for papers</p>
        <h1>Conference CFPs</h1>
        <p class="body">Browse open venues before creating an author account or submitting a paper.</p>
      </div>
      <el-button @click="loadCfps">Refresh</el-button>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" />

    <el-table v-loading="loading" :data="conferences" empty-text="No public CFPs.">
      <el-table-column prop="acronym" label="Acronym" width="120" />
      <el-table-column label="Conference">
        <template #default="{ row }">
          <strong>{{ row.name }}</strong>
          <p class="muted-line">{{ row.year }} · {{ row.publicSlug }}</p>
        </template>
      </el-table-column>
      <el-table-column label="Status" width="180">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ workflowLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Blind mode" width="170">
        <template #default="{ row }">{{ workflowLabel(row.blindMode) }}</template>
      </el-table-column>
      <el-table-column label="Submission window" min-width="260">
        <template #default="{ row }">
          {{ formatDateTime(row.submissionOpenAt) }} to {{ formatDateTime(row.submissionCloseAt) }}
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
