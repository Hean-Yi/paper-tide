<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";

import { useApiError } from "../../composables/useApiError";
import { listAdminAnalysisMonitor, type AdminAnalysisMonitorItem } from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";

const rows = ref<AdminAnalysisMonitorItem[]>([]);
const page = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const filters = reactive({
  analysisType: "",
  businessStatus: ""
});
const { showApiError } = useApiError();
const hasFilters = computed(() => Boolean(filters.analysisType || filters.businessStatus));

onMounted(loadMonitor);

async function loadMonitor() {
  loading.value = true;
  try {
    const result = await listAdminAnalysisMonitor({
      page: page.value,
      size: size.value,
      analysisType: filters.analysisType || undefined,
      businessStatus: filters.businessStatus || undefined
    });
    rows.value = result.items;
    page.value = result.page;
    size.value = result.size;
    total.value = result.total;
  } catch (error) {
    showApiError(error, "Analysis monitor could not be loaded.");
  } finally {
    loading.value = false;
  }
}

async function applyFilters() {
  page.value = 1;
  await loadMonitor();
}

async function clearFilters() {
  filters.analysisType = "";
  filters.businessStatus = "";
  await applyFilters();
}

async function changePage(nextPage: number) {
  page.value = nextPage;
  await loadMonitor();
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

    <el-card class="filter-card" shadow="never">
      <div class="filter-row">
        <label>
          Analysis
          <select v-model="filters.analysisType" data-test="analysis-type-filter">
            <option value="">All analysis types</option>
            <option value="SCREENING">Screening</option>
            <option value="REVIEWER_ASSIST">Reviewer assist</option>
            <option value="REVIEWER_ASSIGNMENT_ASSIST">Assignment assist</option>
            <option value="CONFLICT_ANALYSIS">Conflict analysis</option>
          </select>
        </label>
        <label>
          Status
          <select v-model="filters.businessStatus" data-test="business-status-filter">
            <option value="">All statuses</option>
            <option value="REQUESTED">Requested</option>
            <option value="PROCESSING">Processing</option>
            <option value="AVAILABLE">Available</option>
            <option value="FAILED_VISIBLE">Failed visible</option>
          </select>
        </label>
        <el-button type="primary" :loading="loading" @click="applyFilters">Apply filters</el-button>
        <el-button :disabled="!hasFilters || loading" @click="clearFilters">Clear</el-button>
      </div>
    </el-card>

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

    <div class="pagination-row">
      <span>{{ total }} intents</span>
      <el-pagination
        layout="prev, pager, next"
        :current-page="page"
        :page-size="size"
        :total="total"
        :disabled="loading"
        @current-change="changePage"
      />
    </div>
  </section>
</template>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

.filter-row {
  align-items: end;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.filter-row label {
  color: var(--muted);
  display: grid;
  font-size: 0.84rem;
  gap: 6px;
}

.filter-row select {
  border: 1px solid var(--line);
  border-radius: 10px;
  min-width: 180px;
  padding: 8px 10px;
}

.pagination-row {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
}
</style>
