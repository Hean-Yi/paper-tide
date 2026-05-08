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
    error.value = apiErrorMessage(err, "会议 CFP 暂时无法加载。");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">征稿启事</p>
        <h1>会议 CFP</h1>
        <p class="body">在创建作者账号或提交论文之前，浏览开放中的会场。</p>
      </div>
      <el-button @click="loadCfps">刷新</el-button>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" />

    <el-table v-loading="loading" :data="conferences" empty-text="暂无公开 CFP。">
      <el-table-column prop="acronym" label="缩写" width="120" />
      <el-table-column label="会议">
        <template #default="{ row }">
          <strong>{{ row.name }}</strong>
          <p class="muted-line">{{ row.year }} · {{ row.publicSlug }}</p>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="180">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ workflowLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审稿模式" width="170">
        <template #default="{ row }">{{ workflowLabel(row.blindMode) }}</template>
      </el-table-column>
      <el-table-column label="投稿时间窗口" min-width="260">
        <template #default="{ row }">
          {{ formatDateTime(row.submissionOpenAt) }} 至 {{ formatDateTime(row.submissionCloseAt) }}
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
