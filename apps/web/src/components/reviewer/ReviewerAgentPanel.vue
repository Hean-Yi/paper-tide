<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";

import {
  getReviewerAssist,
  runReviewerAssist,
  type ReviewerAssistState
} from "../../lib/workflow-api";
import { apiErrorMessage } from "../../composables/useApiError";
import { useAsyncAction } from "../../composables/useAsyncAction";
import { printableTrace, statusTagType, workflowLabel } from "../../lib/workflow-format";

const props = defineProps<{
  assignmentId: number;
}>();

const POLL_INTERVAL_MS = 3000;
const PENDING_STATUSES = new Set(["REQUESTED", "PENDING", "PROCESSING", "IN_PROGRESS"]);

const assist = ref<ReviewerAssistState>({ intent: null, projections: [] });
const error = ref("");
const actions = useAsyncAction();
let pollTimer: number | undefined;

const latestStatus = computed(() => assist.value.intent?.businessStatus ?? null);
const hasProjection = computed(() => assist.value.projections.length > 0);
const assistFailed = computed(() => isFailedStatus(latestStatus.value));
const assistPending = computed(() => actions.isPending("run") || isPendingStatus(latestStatus.value));
const showProgress = computed(() => assistPending.value && !hasProjection.value && !assistFailed.value);
const showEmpty = computed(() => !actions.isPending("refresh") && !showProgress.value && !assistFailed.value && !hasProjection.value);
const visibleError = computed(() => error.value || (assistFailed.value ? "Review assistant failed. Try again." : ""));

onMounted(loadAssist);
onBeforeUnmount(stopPolling);

watch(() => props.assignmentId, () => {
  stopPolling();
  assist.value = { intent: null, projections: [] };
  void loadAssist();
});

async function loadAssist(options: { preserveIntent?: boolean } = {}) {
  await actions.run("refresh", async () => {
    try {
      const state = await getReviewerAssist(props.assignmentId);
      assist.value = {
        intent: state.intent ?? (options.preserveIntent ? assist.value.intent : null),
        projections: state.projections ?? []
      };
      error.value = "";
      syncPolling();
    } catch (err) {
      error.value = apiErrorMessage(err, "Reviewer assistance is unavailable.");
      assist.value = { intent: null, projections: [] };
      stopPolling();
    }
  });
}

async function runAssist(force = false) {
  stopPolling();
  await actions.run("run", async () => {
    error.value = "";
    try {
      const intent = await runReviewerAssist(props.assignmentId, force);
      assist.value = { ...assist.value, intent };
      syncPolling();
      await loadAssist({ preserveIntent: true });
    } catch (err) {
      error.value = apiErrorMessage(err, "Reviewer assistance could not be started.");
      stopPolling();
    } finally {
      syncPolling();
    }
  });
}

function syncPolling() {
  if (shouldPoll()) {
    startPolling();
    return;
  }
  stopPolling();
}

function shouldPoll() {
  return Boolean(assist.value.intent && isPendingStatus(assist.value.intent.businessStatus) && !hasProjection.value);
}

function startPolling() {
  if (pollTimer) {
    return;
  }
  pollTimer = window.setInterval(() => {
    void loadAssist({ preserveIntent: true });
  }, POLL_INTERVAL_MS);
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer);
    pollTimer = undefined;
  }
}

function isPendingStatus(status: string | null) {
  return Boolean(status && PENDING_STATUSES.has(status));
}

function isFailedStatus(status: string | null) {
  return Boolean(status?.startsWith("FAILED"));
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

    <el-alert v-if="visibleError" :title="visibleError" type="warning" :closable="false" />

    <div class="action-row">
      <el-button type="primary" :loading="actions.isPending('run')" @click="runAssist(false)">Run review assistant</el-button>
      <el-button v-if="assist.intent?.businessStatus === 'FAILED_VISIBLE'" :loading="actions.isPending('run')" @click="runAssist(true)">Retry</el-button>
      <el-button :loading="actions.isPending('refresh')" @click="loadAssist">Refresh</el-button>
      <el-tag v-if="assist.intent" :type="statusTagType(assist.intent.businessStatus)">
        {{ workflowLabel(assist.intent.businessStatus) }}
      </el-tag>
    </div>

    <div v-if="showProgress" class="assist-progress" aria-live="polite">
      <div>
        <strong>Analysis in progress</strong>
        <p>Request submitted. The assistant is reading the assignment and preparing a checklist.</p>
      </div>
      <div class="assist-progress-animation" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
    </div>

    <el-alert
      v-if="showEmpty"
      title="No reviewer assistance is available for this assignment."
      type="info"
      :closable="false"
    />
    <article v-for="projection in assist.projections" :key="projection.projectionId" class="trace-entry">
      <div class="trace-entry-heading">
        <strong>{{ workflowLabel(projection.analysisType) }}</strong>
        <el-tag :type="statusTagType(projection.businessStatus)">Reviewer safe</el-tag>
      </div>
      <p v-if="projection.summaryText" class="trace-summary">{{ projection.summaryText }}</p>
      <pre class="json-block">{{ printableTrace(projection.redactedResult) }}</pre>
    </article>
  </section>
</template>
