<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";

import { getChairConferencePaperPage } from "../../lib/chair-workflow-api";

const props = defineProps<{
  conferenceId: number;
  manuscriptId: number;
  pageCount: number;
  title: string;
}>();

const pageNo = ref(1);
const pageUrl = ref("");
const loading = ref(false);
const error = ref("");
const zoom = ref(100);

const canGoPrevious = computed(() => pageNo.value > 1);
const canGoNext = computed(() => pageNo.value < props.pageCount);

onMounted(loadPage);
onBeforeUnmount(revokePageUrl);

watch(
  () => [props.conferenceId, props.manuscriptId, props.pageCount],
  () => {
    if (pageNo.value === 1) {
      void loadPage();
    } else {
      pageNo.value = 1;
    }
  }
);

watch(pageNo, () => {
  void loadPage();
});

async function loadPage() {
  if (!props.pageCount) {
    revokePageUrl();
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    const blob = await getChairConferencePaperPage(props.conferenceId, props.manuscriptId, pageNo.value);
    revokePageUrl();
    pageUrl.value = URL.createObjectURL(blob);
  } catch (err) {
    error.value = err instanceof Error ? err.message : "页面暂时不可用。";
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
  <section class="secure-paper-reader" data-test="chair-paper-reader">
    <div class="reader-header">
      <div>
        <p class="eyebrow">Chair 论文阅读器</p>
        <h2>{{ title }}</h2>
        <p class="body">按会议权限在线查看论文页面。</p>
      </div>
      <el-tag type="warning">渲染页面</el-tag>
    </div>

    <el-alert v-if="!pageCount" title="论文 PDF 暂时不可用。" type="warning" :closable="false" />
    <el-skeleton v-else-if="loading && !pageUrl" :rows="8" animated />
    <el-alert v-else-if="error" :title="error" type="warning" :closable="false" />
    <template v-else>
      <div class="reader-toolbar">
        <el-button :disabled="!canGoPrevious" @click="previousPage">上一页</el-button>
        <span>第 {{ pageNo }} 页 / 共 {{ pageCount }} 页</span>
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
  </section>
</template>
