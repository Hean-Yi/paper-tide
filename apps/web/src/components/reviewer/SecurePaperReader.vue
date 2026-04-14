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
    error.value = err instanceof Error ? err.message : "Paper is unavailable.";
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
    error.value = err instanceof Error ? err.message : "Page is unavailable.";
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
        <p class="eyebrow">Secure Paper Reader</p>
        <h2>Online reading only</h2>
        <p class="body">Original PDF download is unavailable for reviewer assignments.</p>
      </div>
      <el-tag type="warning">Rendered page</el-tag>
    </div>

    <el-skeleton v-if="loading && !pageUrl" :rows="8" animated />
    <el-alert v-else-if="error" :title="error" type="warning" :closable="false" />
    <template v-else-if="metadata">
      <div class="reader-toolbar">
        <el-button :disabled="!canGoPrevious" @click="previousPage">Previous</el-button>
        <span>Page {{ pageNo }} / {{ metadata.pageCount }}</span>
        <el-button :disabled="!canGoNext" @click="nextPage">Next</el-button>
        <el-button @click="zoomOut">Zoom out</el-button>
        <span>{{ zoom }}%</span>
        <el-button @click="zoomIn">Zoom in</el-button>
      </div>
      <div class="paper-page-frame">
        <img
          v-if="pageUrl"
          class="secure-paper-page"
          :src="pageUrl"
          :alt="`Rendered page ${pageNo}`"
          loading="lazy"
          :style="{ width: `${zoom}%` }"
        />
      </div>
    </template>
    <el-empty v-else description="Paper is unavailable." />
  </section>
</template>
