<script setup lang="ts">
import { onMounted, ref, watch } from "vue";

import {
  getReviewerAssist,
  runReviewerAssist,
  type ReviewerAssistState
} from "../../lib/workflow-api";
import { printableTrace, statusTagType, workflowLabel } from "../../lib/workflow-format";

const props = defineProps<{
  assignmentId: number;
}>();

const assist = ref<ReviewerAssistState>({ intent: null, projections: [] });
const loading = ref(false);
const running = ref(false);
const error = ref("");

onMounted(loadAssist);

watch(() => props.assignmentId, () => {
  void loadAssist();
});

async function loadAssist() {
  loading.value = true;
  error.value = "";
  try {
    const state = await getReviewerAssist(props.assignmentId);
    assist.value = {
      intent: state.intent ?? null,
      projections: state.projections ?? []
    };
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Reviewer assistance is unavailable.";
    assist.value = { intent: null, projections: [] };
  } finally {
    loading.value = false;
  }
}

async function runAssist(force = false) {
  running.value = true;
  error.value = "";
  try {
    const intent = await runReviewerAssist(props.assignmentId, force);
    assist.value = { ...assist.value, intent };
    await loadAssist();
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Reviewer assistance could not be started.";
  } finally {
    running.value = false;
  }
}
</script>

<template>
  <section class="agent-trace-panel">
    <div class="agent-trace-header">
      <div>
        <p class="eyebrow">Agent Trace</p>
        <h2>Review assist analysis</h2>
      </div>
      <el-tag type="info">Reviewer safe</el-tag>
    </div>

    <el-alert v-if="error" :title="error" type="warning" :closable="false" />

    <div class="action-row">
      <el-button type="primary" :loading="running" @click="runAssist(false)">Run review assistant</el-button>
      <el-button v-if="assist.intent?.businessStatus === 'FAILED_VISIBLE'" :loading="running" @click="runAssist(true)">Retry</el-button>
      <el-button :loading="loading" @click="loadAssist">Refresh</el-button>
      <el-tag v-if="assist.intent" :type="statusTagType(assist.intent.businessStatus)">
        {{ workflowLabel(assist.intent.businessStatus) }}
      </el-tag>
    </div>

    <el-alert
      v-if="!assist.projections.length"
      title="No reviewer assistance is available for this assignment."
      type="info"
      :closable="false"
    />
    <article v-for="projection in assist.projections" :key="projection.projectionId" class="trace-entry">
      <div class="trace-entry-heading">
        <strong>{{ workflowLabel(projection.analysisType) }}</strong>
        <el-tag :type="statusTagType(projection.businessStatus)">Reviewer safe</el-tag>
      </div>
      <pre class="json-block">{{ printableTrace(projection.redactedResult) }}</pre>
    </article>
  </section>
</template>
