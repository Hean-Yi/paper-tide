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
    showApiError(error, "分析监控加载失败。");
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
        <p class="eyebrow">管理员</p>
        <h1>Agent 监控</h1>
        <p class="body">检查分析意图、投影状态及执行标识符。</p>
      </div>
      <el-button :loading="loading" @click="loadMonitor">刷新</el-button>
    </div>

    <el-card class="filter-card" shadow="never">
      <div class="filter-row">
        <label>
          分析类型
          <select v-model="filters.analysisType" data-test="analysis-type-filter">
            <option value="">全部分析类型</option>
            <option value="SCREENING">初筛</option>
            <option value="REVIEWER_ASSIST">审稿辅助</option>
            <option value="REVIEWER_ASSIGNMENT_ASSIST">分配辅助</option>
            <option value="CONFLICT_ANALYSIS">冲突分析</option>
          </select>
        </label>
        <label>
          状态
          <select v-model="filters.businessStatus" data-test="business-status-filter">
            <option value="">全部状态</option>
            <option value="REQUESTED">已请求</option>
            <option value="PROCESSING">处理中</option>
            <option value="AVAILABLE">可用</option>
            <option value="FAILED_VISIBLE">失败（可见）</option>
          </select>
        </label>
        <el-button type="primary" :loading="loading" @click="applyFilters">应用筛选</el-button>
        <el-button :disabled="!hasFilters || loading" @click="clearFilters">清除</el-button>
      </div>
    </el-card>

    <el-table v-loading="loading" :data="rows" empty-text="暂无分析意图。">
      <el-table-column prop="intentId" label="意图" width="100" />
      <el-table-column prop="analysisType" label="分析类型" width="180">
        <template #default="{ row }">
          {{ workflowLabel(row.analysisType) }}
        </template>
      </el-table-column>
      <el-table-column prop="businessStatus" label="状态" width="150">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.businessStatus)">{{ workflowLabel(row.businessStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="anchorLabel" label="锤点" min-width="200" />
      <el-table-column prop="jobId" label="任务" min-width="180">
        <template #default="{ row }">{{ row.jobId || "等待运行时绑定" }}</template>
      </el-table-column>
      <el-table-column prop="summaryText" label="摘要" min-width="220">
        <template #default="{ row }">{{ row.summaryText || "暂无投影摘要。" }}</template>
      </el-table-column>
      <el-table-column prop="projectionUpdatedAt" label="投影更新时间" min-width="180">
        <template #default="{ row }">{{ formatDateTime(row.projectionUpdatedAt) }}</template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无分析意图。" />
      </template>
    </el-table>

    <div class="pagination-row">
      <span>{{ total }} 条意图</span>
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
