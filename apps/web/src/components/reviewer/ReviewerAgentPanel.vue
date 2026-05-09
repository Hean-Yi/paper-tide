<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";

import {
  getReviewerAssist,
  runReviewerAssist,
  type ReviewerAssistState
} from "../../lib/workflow-api";
import { apiErrorMessage } from "../../composables/useApiError";
import { useAsyncAction } from "../../composables/useAsyncAction";
import { statusTagType, workflowLabel } from "../../lib/workflow-format";

const props = defineProps<{
  assignmentId: number;
}>();

const POLL_INTERVAL_MS = 3000;
const PENDING_STATUSES = new Set(["REQUESTED", "PENDING", "PROCESSING", "IN_PROGRESS"]);
const REVIEW_ASSIST_SECTIONS = [
  { key: "claimedContributions", title: "主要贡献核查" },
  { key: "methodChecklist", title: "方法核查" },
  { key: "experimentChecklist", title: "实验核查" },
  { key: "evidenceToVerify", title: "证据核查" },
  { key: "potentialWeaknesses", title: "潜在弱点" },
  { key: "questionsForReviewer", title: "建议追问" },
  { key: "blindReviewRisks", title: "双盲风险" }
];

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
const visibleError = computed(() => error.value || (assistFailed.value ? "审稿助手失败，请重试。" : ""));

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
      error.value = apiErrorMessage(err, "审稿辅助暂时不可用。");
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
      error.value = apiErrorMessage(err, "审稿辅助启动失败。");
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

function resultRecord(result: Record<string, unknown> | null): Record<string, unknown> {
  return result && typeof result === "object" && !Array.isArray(result) ? result : {};
}

function resultText(result: Record<string, unknown> | null, key: string): string {
  const value = resultRecord(result)[key];
  return typeof value === "string" ? value.trim() : "";
}

function resultList(result: Record<string, unknown> | null, key: string): string[] {
  const value = resultRecord(result)[key];
  if (!Array.isArray(value)) {
    return [];
  }
  return [...new Set(value.map((entry) => String(entry ?? "").trim()).filter(Boolean))];
}

function resultConfidence(result: Record<string, unknown> | null): string {
  const value = resultRecord(result).confidence;
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return "";
  }
  const normalized = value <= 1 ? value * 100 : value;
  return `${Math.round(normalized)}%`;
}

function structuredSections(result: Record<string, unknown> | null) {
  return REVIEW_ASSIST_SECTIONS
    .map((section) => ({
      ...section,
      items: resultList(result, section.key)
    }))
    .filter((section) => section.items.length > 0);
}

function projectionSummary(projection: ReviewerAssistState["projections"][number]) {
  return projection.summaryText || resultText(projection.redactedResult, "paperSummary");
}
</script>

<template>
  <section class="agent-trace-panel">
    <div class="agent-trace-header">
      <div>
        <p class="eyebrow">Agent 分析追踪</p>
        <h2>审稿辅助分析</h2>
      </div>
      <el-tag type="info">审稿人可见</el-tag>
    </div>

    <el-alert v-if="visibleError" :title="visibleError" type="warning" :closable="false" />

    <div class="action-row">
      <el-button type="primary" :loading="actions.isPending('run')" @click="runAssist(false)">运行审稿助手</el-button>
      <el-button v-if="assist.intent?.businessStatus === 'FAILED_VISIBLE'" :loading="actions.isPending('run')" @click="runAssist(true)">重试</el-button>
      <el-button :loading="actions.isPending('refresh')" @click="loadAssist">刷新</el-button>
      <el-tag v-if="assist.intent" :type="statusTagType(assist.intent.businessStatus)">
        {{ workflowLabel(assist.intent.businessStatus) }}
      </el-tag>
    </div>

    <div v-if="showProgress" class="assist-progress" aria-live="polite">
      <div>
        <strong>分析进行中</strong>
        <p>请求已提交，助手正在阅读任务并准备分析清单。</p>
      </div>
      <div class="assist-progress-animation" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
    </div>

    <el-alert
      v-if="showEmpty"
      title="当前分配暂无审稿辅助。"
      type="info"
      :closable="false"
    />
    <article v-for="projection in assist.projections" :key="projection.projectionId" class="trace-entry">
      <div class="trace-entry-heading">
        <strong>{{ workflowLabel(projection.analysisType) }}</strong>
        <el-tag :type="statusTagType(projection.businessStatus)">审稿人可见</el-tag>
      </div>
      <div class="review-assist-result">
        <section v-if="projectionSummary(projection)" class="review-assist-summary">
          <div class="review-assist-section-title">
            <h3>论文摘要</h3>
            <el-tag v-if="resultConfidence(projection.redactedResult)" type="success">
              可信度 {{ resultConfidence(projection.redactedResult) }}
            </el-tag>
          </div>
          <p class="trace-summary">{{ projectionSummary(projection) }}</p>
        </section>

        <section
          v-for="section in structuredSections(projection.redactedResult)"
          :key="section.key"
          class="review-assist-section"
        >
          <h3>{{ section.title }}</h3>
          <ul class="review-assist-list">
            <li v-for="item in section.items" :key="item">{{ item }}</li>
          </ul>
        </section>

        <el-empty
          v-if="!projectionSummary(projection) && structuredSections(projection.redactedResult).length === 0"
          description="暂无可展示的结构化审稿辅助。"
        />
      </div>
    </article>
  </section>
</template>
