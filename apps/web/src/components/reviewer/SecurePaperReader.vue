<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";

import {
  getAssignmentPaper,
  getAssignmentPaperPage,
  type AssignmentPaper
} from "../../lib/workflow-api";

const props = defineProps<{
  assignmentId: number;
}>();

const metadata = ref<AssignmentPaper | null>(null);
const pageNo = ref(1);
const pageUrl = ref("");
const loading = ref(false);
const error = ref("");
const zoom = ref(100);

const canGoPrevious = computed(() => pageNo.value > 1);
const canGoNext = computed(() => metadata.value !== null && pageNo.value < metadata.value.pageCount);

onMounted(loadPaper);
onBeforeUnmount(revokePageUrl);

watch(() => props.assignmentId, () => {
  void loadPaper();
});

watch(pageNo, () => {
  void loadPage();
});

async function loadPaper() {
  loading.value = true;
  error.value = "";
  try {
    metadata.value = await getAssignmentPaper(props.assignmentId);
    if (pageNo.value === 1) {
      await loadPage();
    } else {
      pageNo.value = 1;
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : "论文暂时不可用。";;
    metadata.value = null;
    revokePageUrl();
  } finally {
    loading.value = false;
  }
}

async function loadPage() {
  if (!metadata.value) {
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    const blob = await getAssignmentPaperPage(props.assignmentId, pageNo.value);
    revokePageUrl();
    pageUrl.value = URL.createObjectURL(blob);
  } catch (err) {
    error.value = err instanceof Error ? err.message : "页面暂时不可用。";;
    revokePageUrl();
  } finally {
    loading.value = false;
  }
}

function revokePageUrl() {
  if (pageUrl.value) {
    URL.revokeObjectURL(pageUrl.value);
    pageUrl.value = "";
  }
}

function nextPage() {
  if (canGoNext.value) {
    pageNo.value += 1;
  }
}

function previousPage() {
  if (canGoPrevious.value) {
    pageNo.value -= 1;
  }
}

function zoomIn() {
  zoom.value = Math.min(160, zoom.value + 10);
}

function zoomOut() {
  zoom.value = Math.max(80, zoom.value - 10);
}
</script>

<template>
  <section class="secure-paper-reader">
    <div class="reader-header">
      <div>
        <p class="eyebrow">安全论文阅读器</p>
        <h2>仅支持在线阅读</h2>
        <p class="body">评审任务不支持下载原始 PDF。</p>
      </div>
      <el-tag type="warning">渲染页面</el-tag>
    </div>

    <el-skeleton v-if="loading && !pageUrl" :rows="8" animated />
    <el-alert v-else-if="error" :title="error" type="warning" :closable="false" />
    <template v-else-if="metadata">
      <div class="reader-toolbar">
        <el-button :disabled="!canGoPrevious" @click="previousPage">上一页</el-button>
        <span>第 {{ pageNo }} 页 / 共 {{ metadata.pageCount }} 页</span>
        <el-button :disabled="!canGoNext" @click="nextPage">下一页</el-button>
        <el-button @click="zoomOut">缩小</el-button>
        <span>{{ zoom }}%</span>
        <el-button @click="zoomIn">放大</el-button>
      </div>
      <div class="paper-page-frame">
        <img
          v-if="pageUrl"
          class="secure-paper-page"
          :src="pageUrl"
          :alt="`渲染页面 ${pageNo}`"
          loading="lazy"
          :style="{ width: `${zoom}%` }"
        />
      </div>
    </template>
    <el-empty v-else description="论文暂时不可用。" />
  </section>
</template>
