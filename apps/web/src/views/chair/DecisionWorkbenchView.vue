<script setup lang="ts">
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage } from "element-plus";
import { computed, onMounted, onUnmounted, reactive, ref } from "vue";

import { useApiError } from "../../composables/useApiError";
import { useAsyncAction } from "../../composables/useAsyncAction";
import {
  confirmAssignmentDrafts,
  assignReviewer,
  decide,
  generateAssignmentDrafts,
  getAssignmentAssist,
  listAssignmentCandidates,
  listDecisionWorkbench,
  markOverdue,
  runAssignmentAssist,
  triggerConflictAnalysis,
  type AnalysisIntentResponse,
  type AssignmentAssistState,
  type AssignmentCandidate,
  type AssignmentDraft,
  type AnalysisProjectionResponse,
  type DecisionWorkbenchItem
} from "../../lib/workflow-api";
import { formatDateTime, printableTrace, statusTagType, workflowLabel } from "../../lib/workflow-format";

const loading = ref(false);
const rows = ref<DecisionWorkbenchItem[]>([]);
const draftsByRound = reactive<Record<number, AssignmentDraft[]>>({});
const assignmentAssistByRound = reactive<Record<number, AssignmentAssistState>>({});
const draftDeadlineByRound = reactive<Record<number, string>>({});
const localConflictIntentByRound = reactive<Record<number, AnalysisIntentResponse>>({});
const conflictPollTimers = new Map<number, number>();
const conflictAnalysisPanelOpen = ref(false);
const selectedConflictRoundId = ref<number | null>(null);
const actions = useAsyncAction();
const { showApiError } = useApiError();
const assignDialogOpen = ref(false);
const decisionDialogOpen = ref(false);
const assignFormRef = ref<FormInstance>();
const assignForm = reactive({ roundId: 0, reviewerId: 1002, deadlineAt: "" });
const assignmentCandidates = ref<AssignmentCandidate[]>([]);
const decisionFormRef = ref<FormInstance>();
const decisionForm = reactive({
  manuscriptId: 0,
  versionId: 0,
  roundId: 0,
  decisionCode: "MINOR_REVISION",
  decisionReason: ""
});
const assignRules: FormRules = {
  reviewerId: [{ required: true, message: "审稿人 ID 为必填", trigger: "blur" }],
  deadlineAt: [{ required: true, message: "截止日期为必填", trigger: "change" }]
};
const decisionRules: FormRules = {
  decisionCode: [{ required: true, message: "决策为必选", trigger: "change" }],
  decisionReason: [{ required: true, message: "原因为必填", trigger: "blur" }]
};

onMounted(loadWorkbench);
onUnmounted(stopAllConflictPolling);

const selectedConflictRow = computed(() => {
  if (selectedConflictRoundId.value == null) {
    return null;
  }
  return rows.value.find((row) => row.roundId === selectedConflictRoundId.value) ?? null;
});

const selectedConflictProjections = computed(() =>
  selectedConflictRow.value ? conflictProjections(selectedConflictRow.value) : []
);

async function loadWorkbench(showLoading = true) {
  if (showLoading) {
    loading.value = true;
  }
  try {
    rows.value = await listDecisionWorkbench();
    syncConflictPolling();
  } catch (error) {
    showApiError(error, "决策工作台加载失败。");
  } finally {
    if (showLoading) {
      loading.value = false;
    }
  }
}

function openAssign(row: DecisionWorkbenchItem) {
  Object.assign(assignForm, {
    roundId: row.roundId,
    reviewerId: 0,
    deadlineAt: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString()
  });
  assignDialogOpen.value = true;
  void loadAssignmentCandidates(row.roundId);
}

async function loadAssignmentCandidates(roundId: number) {
  assignmentCandidates.value = [];
  await actions.run(`assignment-candidates:${roundId}`, async () => {
    try {
      assignmentCandidates.value = await listAssignmentCandidates(roundId);
      if (assignmentCandidates.value.length && !assignForm.reviewerId) {
        assignForm.reviewerId = assignmentCandidates.value[0].reviewerId;
      }
    } catch (error) {
      showApiError(error, "可分配审稿人加载失败。");
    }
  });
}

async function submitAssign() {
  const valid = await assignFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  await actions.run("assign-reviewer", async () => {
    try {
      await assignReviewer(assignForm.roundId, assignForm.reviewerId, assignForm.deadlineAt);
      assignDialogOpen.value = false;
      ElMessage.success("审稿人已分配。");
      await loadWorkbench();
    } catch (error) {
      showApiError(error, "审稿人分配失败。");
    }
  });
}

async function overdue(assignmentId: number) {
  await actions.run(`overdue:${assignmentId}`, async () => {
    try {
      await markOverdue(assignmentId);
      ElMessage.success("任务已标记为过期。");
      await loadWorkbench();
    } catch (error) {
      showApiError(error, "任务标记过期失败。");
    }
  });
}

async function conflict(row: DecisionWorkbenchItem) {
  if (hasAvailableConflictAnalysis(row)) {
    openConflictAnalysis(row);
    return;
  }
  if (isConflictInProgress(row)) {
    return;
  }
  await actions.run(`conflict:${row.roundId}`, async () => {
    try {
      localConflictIntentByRound[row.roundId] = await triggerConflictAnalysis(row.roundId);
      startConflictPolling(row.roundId);
      await loadWorkbench();
    } catch (error) {
      showApiError(error, "冲突分析请求失败。");
    }
  });
}

async function generateDrafts(row: DecisionWorkbenchItem) {
  await actions.run(`drafts:${row.roundId}`, async () => {
    try {
      draftsByRound[row.roundId] = await generateAssignmentDrafts(row.roundId, 5);
      if (!draftDeadlineByRound[row.roundId]) {
        draftDeadlineByRound[row.roundId] = defaultReviewDeadline();
      }
      ElMessage.success("分配草稿已生成。");
    } catch (error) {
      showApiError(error, "分配草稿生成失败。");
    }
  });
}

async function assignmentAssist(row: DecisionWorkbenchItem) {
  await actions.run(`assignment-assist:${row.roundId}`, async () => {
    try {
      await runAssignmentAssist(row.roundId);
      assignmentAssistByRound[row.roundId] = await getAssignmentAssist(row.roundId);
      ElMessage.success("分配辅助已请求。");
    } catch (error) {
      showApiError(error, "分配辅助请求失败。");
    }
  });
}

async function confirmDrafts(row: DecisionWorkbenchItem) {
  const draftIds = pendingDrafts(row.roundId).map((draft) => draft.draftId);
  if (!draftIds.length) {
    return;
  }
  await actions.run(`confirm-drafts:${row.roundId}`, async () => {
    try {
      await confirmAssignmentDrafts(row.roundId, draftIds, draftDeadlineByRound[row.roundId] || defaultReviewDeadline());
      ElMessage.success("分配草稿已确认。");
      await loadWorkbench();
    } catch (error) {
      showApiError(error, "分配草稿确认失败。");
    }
  });
}

function conflictProjections(row: DecisionWorkbenchItem): AnalysisProjectionResponse[] {
  return row.conflictProjections ?? [];
}

function conflictIntent(row: DecisionWorkbenchItem) {
  return row.conflictIntent ?? localConflictIntentByRound[row.roundId] ?? null;
}

function hasAvailableConflictAnalysis(row: DecisionWorkbenchItem): boolean {
  return conflictProjections(row).some((projection) => !projection.superseded && projection.businessStatus === "AVAILABLE");
}

function isConflictInProgress(row: DecisionWorkbenchItem): boolean {
  const status = conflictIntent(row)?.businessStatus;
  return !hasAvailableConflictAnalysis(row) && (status === "REQUESTED" || status === "PROCESSING" || status === "PENDING");
}

function isConflictButtonDisabled(row: DecisionWorkbenchItem): boolean {
  return actions.isPending(`conflict:${row.roundId}`) || isConflictInProgress(row);
}

function isConflictFailed(row: DecisionWorkbenchItem): boolean {
  return conflictIntent(row)?.businessStatus === "FAILED_VISIBLE";
}

function conflictButtonLabel(row: DecisionWorkbenchItem): string {
  if (actions.isPending(`conflict:${row.roundId}`) || isConflictInProgress(row)) {
    return "LLM 分析中";
  }
  if (hasAvailableConflictAnalysis(row)) {
    return "查看冲突分析";
  }
  if (isConflictFailed(row)) {
    return "重试冲突分析";
  }
  return "冲突分析";
}

function openConflictAnalysis(row: DecisionWorkbenchItem) {
  selectedConflictRoundId.value = row.roundId;
  conflictAnalysisPanelOpen.value = true;
}

function closeConflictAnalysis() {
  conflictAnalysisPanelOpen.value = false;
}

function startConflictPolling(roundId: number) {
  if (conflictPollTimers.has(roundId)) {
    return;
  }
  const timer = window.setInterval(() => {
    void loadWorkbench(false);
  }, 2000);
  conflictPollTimers.set(roundId, timer);
}

function stopConflictPolling(roundId: number) {
  const timer = conflictPollTimers.get(roundId);
  if (!timer) {
    return;
  }
  window.clearInterval(timer);
  conflictPollTimers.delete(roundId);
}

function stopAllConflictPolling() {
  Array.from(conflictPollTimers.keys()).forEach(stopConflictPolling);
}

function syncConflictPolling() {
  const activeRoundIds = new Set(
    rows.value
      .filter((row) => isConflictInProgress(row))
      .map((row) => row.roundId)
  );
  activeRoundIds.forEach(startConflictPolling);
  Array.from(conflictPollTimers.keys()).forEach((roundId) => {
    if (!activeRoundIds.has(roundId)) {
      stopConflictPolling(roundId);
    }
  });
}

function pendingDrafts(roundId: number): AssignmentDraft[] {
  return (draftsByRound[roundId] ?? []).filter((draft) => draft.draftStatus === "PROPOSED");
}

function assignmentAssistProjections(row: DecisionWorkbenchItem): AnalysisProjectionResponse[] {
  return assignmentAssistByRound[row.roundId]?.projections ?? [];
}

function structuredConflictResult(projection: AnalysisProjectionResponse): {
  decisionSummary: string;
  consensusPoints: string[];
  conflictPoints: string[];
  highRiskIssues: string[];
  confidence: number | null;
} {
  const result = projection.redactedResult ?? {};
  return {
    decisionSummary: stringValue(result.decisionSummary) || projection.summaryText || "暂无冲突分析摘要。",
    consensusPoints: stringList(result.consensusPoints),
    conflictPoints: stringList(result.conflictPoints),
    highRiskIssues: stringList(result.highRiskIssues),
    confidence: numberValue(result.confidence)
  };
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function stringList(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((entry): entry is string => typeof entry === "string" && entry.trim().length > 0)
    : [];
}

function numberValue(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function formatConfidence(value: number | null): string {
  if (value == null) {
    return "未提供";
  }
  return `${Math.round(value * 100)}%`;
}

function defaultReviewDeadline(): string {
  return new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString();
}

function openDecision(row: DecisionWorkbenchItem) {
  Object.assign(decisionForm, {
    manuscriptId: row.manuscriptId,
    versionId: row.versionId,
    roundId: row.roundId,
    decisionCode: "MINOR_REVISION",
    decisionReason: ""
  });
  decisionDialogOpen.value = true;
}

async function submitDecision() {
  const valid = await decisionFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  await actions.run("submit-decision", async () => {
    try {
      await decide(decisionForm);
      decisionDialogOpen.value = false;
      ElMessage.success("决策已提交。");
      await loadWorkbench();
    } catch (error) {
      showApiError(error, "决策提交失败。");
    }
  });
}

</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">主席</p>
        <h1>决策工作台</h1>
        <p class="body">在做出决策前查看评审轮次状态、冲突检查及 Agent 分析证据。</p>
      </div>
      <el-button :loading="loading" @click="loadWorkbench">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" row-key="roundId" empty-text="暂无活跃的评审轮次。">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expanded-panel">
            <el-descriptions title="轮次详情" :column="3" border>
              <el-descriptions-item label="分配">{{ row.assignmentCount }}</el-descriptions-item>
              <el-descriptions-item label="已提交评审">{{ row.submittedReviewCount }}</el-descriptions-item>
              <el-descriptions-item label="冲突">{{ row.conflictCount }}</el-descriptions-item>
              <el-descriptions-item label="截止日期">{{ formatDateTime(row.deadlineAt) }}</el-descriptions-item>
              <el-descriptions-item label="最近决策">{{ workflowLabel(row.lastDecisionCode) }}</el-descriptions-item>
            </el-descriptions>

            <h2>分配</h2>
            <el-table :data="row.assignments" size="small">
              <el-table-column prop="assignmentId" label="分配 ID" width="120" />
              <el-table-column prop="reviewerId" label="审稿人" width="120" />
              <el-table-column prop="taskStatus" label="状态" width="140">
                <template #default="{ row: assignment }">
                  <el-tag :type="statusTagType(assignment.taskStatus)">{{ workflowLabel(assignment.taskStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="deadlineAt" label="截止日期" min-width="170">
                <template #default="{ row: assignment }">{{ formatDateTime(assignment.deadlineAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="180">
                <template #default="{ row: assignment }">
                  <el-button
                    size="small"
                    :loading="actions.isPending(`overdue:${assignment.assignmentId}`)"
                    @click="overdue(assignment.assignmentId)"
                  >
                    标记过期
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <section class="workflow-section">
              <div class="subsection-heading">
                <h2>分配草稿</h2>
                <div class="action-row">
                  <el-button
                    size="small"
                    :loading="actions.isPending(`drafts:${row.roundId}`)"
                    @click="generateDrafts(row)"
                  >
                    生成草稿
                  </el-button>
                  <el-button
                    size="small"
                    :disabled="!pendingDrafts(row.roundId).length"
                    :loading="actions.isPending(`assignment-assist:${row.roundId}`)"
                    @click="assignmentAssist(row)"
                  >
                    运行分配辅助
                  </el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :disabled="!pendingDrafts(row.roundId).length"
                    :loading="actions.isPending(`confirm-drafts:${row.roundId}`)"
                    @click="confirmDrafts(row)"
                  >
                    确认草稿
                  </el-button>
                </div>
              </div>
              <el-table :data="draftsByRound[row.roundId] ?? []" size="small" empty-text="暂无分配草稿。">
                <el-table-column prop="rankOrder" label="排名" width="80" />
                <el-table-column label="审稿人" width="140">
                  <template #default="{ row: draft }">审稿人 {{ draft.reviewerId }}</template>
                </el-table-column>
                <el-table-column label="负荷" width="120">
                  <template #default="{ row: draft }">{{ draft.currentLoad }}/{{ draft.maxLoad }}</template>
                </el-table-column>
                <el-table-column prop="bidValue" label="投标" width="160">
                  <template #default="{ row: draft }">{{ workflowLabel(draft.bidValue) }}</template>
                </el-table-column>
                <el-table-column prop="reason" label="原因" min-width="220" />
              </el-table>
              <article
                v-for="projection in assignmentAssistProjections(row)"
                :key="projection.projectionId"
                class="trace-entry"
              >
                <div class="trace-entry-heading">
                  <strong>{{ workflowLabel(projection.analysisType) }}</strong>
                  <el-tag :type="statusTagType(projection.businessStatus)">
                    {{ workflowLabel(projection.businessStatus) }}
                  </el-tag>
                </div>
                <p v-if="projection.summaryText" class="body">{{ projection.summaryText }}</p>
                <pre class="json-block">{{ printableTrace(projection.redactedResult) }}</pre>
              </article>
            </section>

            <h2>冲突分析投影</h2>
            <el-alert
              v-if="!conflictProjections(row).length"
              title="暂无冲突分析投影。"
              type="info"
              :closable="false"
            />
            <article v-for="projection in conflictProjections(row)" :key="projection.projectionId" class="trace-entry">
              <div class="trace-entry-heading">
                <strong>{{ workflowLabel(projection.analysisType) }}</strong>
                <el-tag :type="statusTagType(projection.businessStatus)">
                  {{ workflowLabel(projection.businessStatus) }}
                </el-tag>
              </div>
              <div class="conflict-result">
                <div class="conflict-result-summary">
                  <h3>决策摘要</h3>
                  <p>{{ structuredConflictResult(projection).decisionSummary }}</p>
                  <el-tag type="info">置信度 {{ formatConfidence(structuredConflictResult(projection).confidence) }}</el-tag>
                </div>
                <div class="conflict-result-grid">
                  <section>
                    <h3>共识点</h3>
                    <ul v-if="structuredConflictResult(projection).consensusPoints.length">
                      <li
                        v-for="(item, index) in structuredConflictResult(projection).consensusPoints"
                        :key="`consensus-${projection.projectionId}-${index}`"
                      >
                        {{ item }}
                      </li>
                    </ul>
                    <p v-else class="muted-line">暂无明确共识。</p>
                  </section>
                  <section>
                    <h3>分歧点</h3>
                    <ul v-if="structuredConflictResult(projection).conflictPoints.length">
                      <li
                        v-for="(item, index) in structuredConflictResult(projection).conflictPoints"
                        :key="`conflict-${projection.projectionId}-${index}`"
                      >
                        {{ item }}
                      </li>
                    </ul>
                    <p v-else class="muted-line">暂无显著分歧。</p>
                  </section>
                  <section>
                    <h3>高风险事项</h3>
                    <ul v-if="structuredConflictResult(projection).highRiskIssues.length">
                      <li
                        v-for="(item, index) in structuredConflictResult(projection).highRiskIssues"
                        :key="`risk-${projection.projectionId}-${index}`"
                      >
                        {{ item }}
                      </li>
                    </ul>
                    <p v-else class="muted-line">暂无高风险事项。</p>
                  </section>
                </div>
              </div>
            </article>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="roundId" label="轮次" width="100" />
      <el-table-column prop="title" label="标题" min-width="220" />
      <el-table-column prop="roundStatus" label="轮次状态" width="150">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.roundStatus)">{{ workflowLabel(row.roundStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentStatus" label="稿件状态" width="170">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.currentStatus)">{{ workflowLabel(row.currentStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="计数" width="180">
        <template #default="{ row }">
          {{ row.submittedReviewCount }}/{{ row.assignmentCount }} 评审，{{ row.conflictCount }} 冲突
        </template>
      </el-table-column>
      <el-table-column label="操作" width="360">
        <template #default="{ row }">
          <div class="action-row">
            <el-button size="small" @click="openAssign(row)">分配审稿人</el-button>
            <el-button
              size="small"
              :disabled="isConflictButtonDisabled(row)"
              :loading="actions.isPending(`conflict:${row.roundId}`)"
              @click="conflict(row)"
            >
              {{ conflictButtonLabel(row) }}
            </el-button>
            <div v-if="isConflictInProgress(row)" class="conflict-progress" aria-label="LLM 冲突分析进度">
              <span />
            </div>
            <el-button size="small" type="primary" @click="openDecision(row)">提交决策</el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无活跃的评审轮次。" />
      </template>
    </el-table>

    <section class="agent-trace-panel">
      <div class="agent-trace-header">
        <div>
        <p class="eyebrow">Agent 跟踪</p>
        <h2>Agent 分析覆盖情况</h2>
        </div>
        <el-tag type="warning">仅主席可见</el-tag>
      </div>
      <el-alert
        v-if="!rows.some((row) => conflictProjections(row).length)"
        title="当前轮次暂无冲突分析投影。"
        type="info"
        :closable="false"
      />
      <template v-for="row in rows" :key="row.roundId">
        <article v-if="conflictProjections(row).length" class="trace-entry">
          <div class="trace-entry-heading">
            <strong>Round {{ row.roundNo }} · Manuscript {{ row.manuscriptId }}</strong>
            <span>{{ conflictProjections(row).length }} 个投影</span>
          </div>
          <div class="action-row">
            <el-tag
              v-for="projection in conflictProjections(row)"
              :key="projection.projectionId"
              :type="statusTagType(projection.businessStatus)"
            >
              {{ workflowLabel(projection.analysisType) }}
            </el-tag>
          </div>
        </article>
      </template>
    </section>

    <el-dialog v-model="assignDialogOpen" title="分配审稿人" width="520px">
      <el-form ref="assignFormRef" :model="assignForm" :rules="assignRules" label-position="top">
        <el-form-item label="审稿人 ID" prop="reviewerId">
          <el-select
            v-model="assignForm.reviewerId"
            data-test="assignment-candidate-select"
            filterable
            :loading="actions.isPending(`assignment-candidates:${assignForm.roundId}`)"
            placeholder="选择可分配审稿人"
          >
            <el-option
              v-for="candidate in assignmentCandidates"
              :key="candidate.reviewerId"
              :label="`${candidate.reviewerName} · ${candidate.institution || '未知机构'} · ${candidate.currentLoad}/${candidate.maxLoad}`"
              :value="candidate.reviewerId"
            >
              <span>{{ candidate.reviewerName }}</span>
              <span class="option-meta">
                ID {{ candidate.reviewerId }} · {{ candidate.institution || "未知机构" }} ·
                负荷 {{ candidate.currentLoad }}/{{ candidate.maxLoad }} · {{ workflowLabel(candidate.bidValue) }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-table :data="assignmentCandidates" size="small" empty-text="暂无可分配审稿人。">
          <el-table-column prop="reviewerName" label="审稿人" min-width="140" />
          <el-table-column prop="institution" label="机构" min-width="160" />
          <el-table-column label="负荷" width="100">
            <template #default="{ row }">{{ row.currentLoad }}/{{ row.maxLoad }}</template>
          </el-table-column>
          <el-table-column label="投标" width="130">
            <template #default="{ row }">{{ workflowLabel(row.bidValue) }}</template>
          </el-table-column>
        </el-table>
        <el-form-item label="截止日期" prop="deadlineAt">
          <el-date-picker
            v-model="assignForm.deadlineAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss[Z]"
            placeholder="选择截止日期"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="actions.isPending('assign-reviewer')" @click="assignDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="actions.isPending('assign-reviewer')" @click="submitAssign">确认分配</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="decisionDialogOpen" title="提交决策" width="560px">
      <el-form ref="decisionFormRef" :model="decisionForm" :rules="decisionRules" label-position="top">
        <el-form-item label="决策" prop="decisionCode">
          <el-select v-model="decisionForm.decisionCode">
            <el-option label="接收" value="ACCEPT" />
            <el-option label="小修" value="MINOR_REVISION" />
            <el-option label="大修" value="MAJOR_REVISION" />
            <el-option label="拒稿" value="REJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因" prop="decisionReason">
          <el-input v-model="decisionForm.decisionReason" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="actions.isPending('submit-decision')" @click="decisionDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="actions.isPending('submit-decision')" @click="submitDecision">提交决策</el-button>
      </template>
    </el-dialog>

    <Transition name="conflict-panel">
      <div
        v-if="conflictAnalysisPanelOpen && selectedConflictRow"
        class="conflict-analysis-backdrop"
        @click.self="closeConflictAnalysis"
      >
        <section class="conflict-analysis-panel" role="dialog" aria-modal="true" aria-label="冲突分析结果">
          <div class="conflict-analysis-header">
            <div>
              <p class="eyebrow">LLM 冲突分析</p>
              <h2>{{ selectedConflictRow.title }}</h2>
              <p class="body">Round {{ selectedConflictRow.roundNo }} · Manuscript {{ selectedConflictRow.manuscriptId }}</p>
            </div>
            <el-button circle aria-label="关闭冲突分析" @click="closeConflictAnalysis">×</el-button>
          </div>
          <article
            v-for="projection in selectedConflictProjections"
            :key="projection.projectionId"
            class="trace-entry"
          >
            <div class="trace-entry-heading">
              <strong>{{ workflowLabel(projection.analysisType) }}</strong>
              <el-tag :type="statusTagType(projection.businessStatus)">
                {{ workflowLabel(projection.businessStatus) }}
              </el-tag>
            </div>
            <p v-if="projection.summaryText" class="body">{{ projection.summaryText }}</p>
            <div class="conflict-result">
              <div class="conflict-result-summary">
                <h3>决策摘要</h3>
                <p>{{ structuredConflictResult(projection).decisionSummary }}</p>
                <el-tag type="info">置信度 {{ formatConfidence(structuredConflictResult(projection).confidence) }}</el-tag>
              </div>
              <div class="conflict-result-grid">
                <section>
                  <h3>共识点</h3>
                  <ul v-if="structuredConflictResult(projection).consensusPoints.length">
                    <li
                      v-for="(item, index) in structuredConflictResult(projection).consensusPoints"
                      :key="`panel-consensus-${projection.projectionId}-${index}`"
                    >
                      {{ item }}
                    </li>
                  </ul>
                  <p v-else class="muted-line">暂无明确共识。</p>
                </section>
                <section>
                  <h3>分歧点</h3>
                  <ul v-if="structuredConflictResult(projection).conflictPoints.length">
                    <li
                      v-for="(item, index) in structuredConflictResult(projection).conflictPoints"
                      :key="`panel-conflict-${projection.projectionId}-${index}`"
                    >
                      {{ item }}
                    </li>
                  </ul>
                  <p v-else class="muted-line">暂无显著分歧。</p>
                </section>
                <section>
                  <h3>高风险事项</h3>
                  <ul v-if="structuredConflictResult(projection).highRiskIssues.length">
                    <li
                      v-for="(item, index) in structuredConflictResult(projection).highRiskIssues"
                      :key="`panel-risk-${projection.projectionId}-${index}`"
                    >
                      {{ item }}
                    </li>
                  </ul>
                  <p v-else class="muted-line">暂无高风险事项。</p>
                </section>
              </div>
            </div>
          </article>
        </section>
      </div>
    </Transition>
  </section>
</template>
