<script setup lang="ts">
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";

import { useApiError } from "../../composables/useApiError";
import { useAsyncAction } from "../../composables/useAsyncAction";
import { conferenceSchedulePayload } from "../../lib/conference-schedule";
import {
  advanceConference,
  decide,
  getChairConference,
  listConferencePapers,
  submitConferenceForApproval,
  updateConferenceDraft,
  type ConferenceDetail,
  type ConferencePaperItem
} from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";
import { hasRole } from "../../stores/auth";

const route = useRoute();
const loading = ref(false);
const conference = ref<ConferenceDetail | null>(null);
const papers = ref<ConferencePaperItem[]>([]);
const decisionDialogOpen = ref(false);
const decisionFormRef = ref<FormInstance>();
const actions = useAsyncAction();
const { showApiError } = useApiError();
const editError = ref("");

const decisionForm = reactive({
  manuscriptId: 0,
  versionId: 0,
  roundId: 0,
  decisionCode: "ACCEPT",
  decisionReason: ""
});

const editForm = reactive({
  name: "",
  acronym: "",
  year: new Date().getFullYear(),
  publicSlug: "",
  blindMode: "DOUBLE_BLIND",
  cfpText: "",
  topicAreas: "",
  targetReviewsPerPaper: 3,
  defaultReviewerMaxLoad: 4,
  submissionOpenAt: "",
  abstractSubmissionCloseAt: "",
  submissionCloseAt: "",
  reviewDeadlineAt: "",
  decisionReleaseAt: ""
});

const decisionRules: FormRules = {
  decisionCode: [{ required: true, message: "决策为必选", trigger: "change" }],
  decisionReason: [{ required: true, message: "原因为必填", trigger: "blur" }]
};

const conferenceId = computed(() => Number(route.params.conferenceId));
const nextStatus = computed(() => nextConferenceStatus(conference.value?.status));
const canManageConferencePapers = computed(() => hasRole("CHAIR"));
const canEditConferenceDraft = computed(() => canManageConferencePapers.value && conference.value?.status === "DRAFT");
const activePaperCount = computed(() => papers.value.filter((paper) =>
  ["UNDER_SCREENING", "UNDER_REVIEW", "REVISION_REQUIRED", "REVISED_SUBMITTED"].includes(paper.currentStatus)
).length);
const decidedPaperCount = computed(() => papers.value.filter((paper) =>
  ["ACCEPTED", "REJECTED", "DESK_REJECTED"].includes(paper.currentStatus)
).length);

onMounted(loadDetail);

async function loadDetail() {
  if (!Number.isFinite(conferenceId.value)) {
    return;
  }
  loading.value = true;
  try {
    const [conferenceDetail, conferencePapers] = await Promise.all([
      getChairConference(conferenceId.value),
      listConferencePapers(conferenceId.value)
    ]);
    conference.value = conferenceDetail;
    papers.value = conferencePapers;
    populateEditForm(conferenceDetail);
  } catch (error) {
    showApiError(error, "会议详情加载失败。");
  } finally {
    loading.value = false;
  }
}

function populateEditForm(detail: ConferenceDetail) {
  Object.assign(editForm, {
    name: detail.name ?? "",
    acronym: detail.acronym ?? "",
    year: detail.year ?? new Date().getFullYear(),
    publicSlug: detail.publicSlug ?? "",
    blindMode: detail.blindMode ?? "DOUBLE_BLIND",
    cfpText: detail.cfpText ?? "",
    topicAreas: detail.topicAreas?.join(", ") ?? "",
    targetReviewsPerPaper: detail.targetReviewsPerPaper ?? 3,
    defaultReviewerMaxLoad: detail.defaultReviewerMaxLoad ?? 4,
    submissionOpenAt: detail.phase?.submissionOpenAt ?? "",
    abstractSubmissionCloseAt: detail.phase?.abstractSubmissionCloseAt ?? "",
    submissionCloseAt: detail.phase?.submissionCloseAt ?? "",
    reviewDeadlineAt: detail.phase?.reviewDeadlineAt ?? "",
    decisionReleaseAt: detail.phase?.decisionReleaseAt ?? ""
  });
}

function editPayload() {
  return {
    name: editForm.name.trim(),
    acronym: editForm.acronym.trim(),
    year: Number(editForm.year),
    blindMode: editForm.blindMode,
    cfpText: editForm.cfpText.trim(),
    topicAreas: editForm.topicAreas.split(",").map((entry) => entry.trim()).filter(Boolean),
    targetReviewsPerPaper: Number(editForm.targetReviewsPerPaper),
    defaultReviewerMaxLoad: Number(editForm.defaultReviewerMaxLoad),
    publicSlug: editForm.publicSlug.trim(),
    phase: conferenceSchedulePayload({
      submissionOpenAt: editForm.submissionOpenAt,
      abstractSubmissionCloseAt: editForm.abstractSubmissionCloseAt,
      submissionCloseAt: editForm.submissionCloseAt,
      reviewDeadlineAt: editForm.reviewDeadlineAt,
      decisionReleaseAt: editForm.decisionReleaseAt
    })
  };
}

function validateEditForm() {
  const payload = editPayload();
  if (!payload.name || !payload.acronym || !payload.publicSlug || !payload.cfpText) {
    return "会议名称、缩写、公开标识和征稿文本为必填。";
  }
  if (payload.topicAreas.length === 0) {
    return "至少填写一个主题领域。";
  }
  if (Object.values(payload.phase).some((value) => !value)) {
    return "请补齐会议阶段时间。";
  }
  return "";
}

async function saveConferenceDraft() {
  if (!conference.value || !canEditConferenceDraft.value) {
    return;
  }
  const validationError = validateEditForm();
  if (validationError) {
    editError.value = validationError;
    return;
  }
  editError.value = "";
  await actions.run("save-conference-draft", async () => {
    try {
      conference.value = await updateConferenceDraft(conference.value!.conferenceId, editPayload());
      populateEditForm(conference.value);
      ElMessage.success("会议草稿已保存。");
    } catch (error) {
      showApiError(error, "会议草稿保存失败。");
    }
  });
}

async function resubmitConferenceForApproval() {
  if (!conference.value || !canEditConferenceDraft.value) {
    return;
  }
  await actions.run("submit-conference-approval", async () => {
    try {
      conference.value = await submitConferenceForApproval(conference.value!.conferenceId);
      ElMessage.success("会议已再次提交审批。");
    } catch (error) {
      showApiError(error, "会议提交审批失败。");
    }
  });
}

async function advanceToNextStatus() {
  if (!conference.value || !nextStatus.value) {
    return;
  }
  await actions.run("advance-conference", async () => {
    try {
      conference.value = await advanceConference(conference.value!.conferenceId, nextStatus.value!);
      ElMessage.success("会议状态已更新。");
    } catch (error) {
      showApiError(error, "会议状态推进失败。");
    }
  });
}

function openDecision(row: ConferencePaperItem, code: "ACCEPT" | "DESK_REJECT") {
  Object.assign(decisionForm, {
    manuscriptId: row.manuscriptId,
    versionId: row.versionId,
    roundId: row.roundId ?? 0,
    decisionCode: code,
    decisionReason: ""
  });
  decisionDialogOpen.value = true;
}

async function submitDecision() {
  const valid = await decisionFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (!decisionForm.roundId) {
    ElMessage.error("该论文缺少可决策的评审轮次。");
    return;
  }
  if (decisionForm.decisionCode === "DESK_REJECT") {
    try {
      await ElMessageBox.confirm("Desk reject 会直接关闭该论文的当前审核流程，确认继续？", "确认 Desk reject", {
        type: "warning",
        confirmButtonText: "Desk reject"
      });
    } catch {
      return;
    }
  }
  await actions.run("conference-paper-decision", async () => {
    try {
      await decide({ ...decisionForm });
      decisionDialogOpen.value = false;
      ElMessage.success("论文决策已提交。");
      await loadDetail();
    } catch (error) {
      showApiError(error, "论文决策提交失败。");
    }
  });
}

function canAccept(row: ConferencePaperItem) {
  return row.currentStatus === "UNDER_REVIEW" && !!row.roundId;
}

function canDeskReject(row: ConferencePaperItem) {
  return row.currentStatus === "UNDER_SCREENING" && !!row.roundId;
}

function scoreLabel(score: number | null | undefined) {
  return score ?? "未提交";
}

function nextConferenceStatus(status: string | null | undefined): string | null {
  const order = [
    "OPEN_FOR_SUBMISSION",
    "SUBMISSION_CLOSED",
    "BIDDING_OPEN",
    "REVIEW_ASSIGNMENT",
    "REVIEWING",
    "DECISION",
    "CLOSED"
  ];
  if (!status) {
    return null;
  }
  const index = order.indexOf(status);
  return index >= 0 && index < order.length - 1 ? order[index + 1] : null;
}

function nextConferenceStatusLabel(status: string | null | undefined): string {
  if (status === "SUBMISSION_CLOSED") {
    return "提交论文阶段";
  }
  return workflowLabel(status);
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">会议详情</p>
        <h1>{{ conference?.name ?? "会议" }}</h1>
        <p class="body">
          {{ conference?.acronym }} {{ conference?.year }} ·
          <el-tag v-if="conference" :type="statusTagType(conference.status)">
            {{ workflowLabel(conference.status) }}
          </el-tag>
        </p>
      </div>
      <div class="action-row">
        <el-button :loading="loading" @click="loadDetail">刷新</el-button>
        <el-button
          v-if="canManageConferencePapers && nextStatus"
          type="primary"
          :loading="actions.isPending('advance-conference')"
          @click="advanceToNextStatus"
        >
          推进到 {{ nextConferenceStatusLabel(nextStatus) }}
        </el-button>
      </div>
    </div>

    <section v-if="conference" class="workflow-section">
      <el-alert
        v-if="conference.status === 'DRAFT' && conference.rejectionReason"
        class="workflow-alert compact-alert"
        type="warning"
        :closable="false"
        title="退回理由"
      >
        <p class="review-text">{{ conference.rejectionReason }}</p>
        <p v-if="conference.rejectedAt" class="muted-line">退回时间：{{ formatDateTime(conference.rejectedAt) }}</p>
      </el-alert>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="当前状态">
          <el-tag :type="statusTagType(conference.status)">{{ workflowLabel(conference.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审稿模式">{{ workflowLabel(conference.blindMode) }}</el-descriptions-item>
        <el-descriptions-item label="公开标识">{{ conference.publicSlug }}</el-descriptions-item>
        <el-descriptions-item label="投稿开放">{{ formatDateTime(conference.phase?.submissionOpenAt) }}</el-descriptions-item>
        <el-descriptions-item label="摘要截止">{{ formatDateTime(conference.phase?.abstractSubmissionCloseAt) }}</el-descriptions-item>
        <el-descriptions-item label="论文截止">{{ formatDateTime(conference.phase?.submissionCloseAt) }}</el-descriptions-item>
        <el-descriptions-item label="评审截止">{{ formatDateTime(conference.phase?.reviewDeadlineAt) }}</el-descriptions-item>
        <el-descriptions-item label="论文总数">{{ papers.length }}</el-descriptions-item>
        <el-descriptions-item label="进行中">{{ activePaperCount }}</el-descriptions-item>
        <el-descriptions-item label="已决策">{{ decidedPaperCount }}</el-descriptions-item>
      </el-descriptions>
    </section>

    <section v-if="conference && canEditConferenceDraft" class="workflow-section">
      <div class="subsection-heading">
        <div>
          <h2>编辑会议草稿</h2>
          <p class="muted-line">被拒绝的会议会保留已填写信息，修改后可再次提交审批。</p>
        </div>
      </div>
      <el-alert v-if="editError" :title="editError" type="error" :closable="false" />
      <el-form class="workflow-form" label-position="top" @submit.prevent="saveConferenceDraft">
        <div class="score-grid">
          <el-form-item label="名称">
            <el-input v-model="editForm.name" data-test="edit-conference-name" />
          </el-form-item>
          <el-form-item label="缩写">
            <el-input v-model="editForm.acronym" data-test="edit-conference-acronym" />
          </el-form-item>
          <el-form-item label="年份">
            <el-input-number v-model="editForm.year" data-test="edit-conference-year" :min="2000" :max="2100" />
          </el-form-item>
          <el-form-item label="公开标识">
            <el-input v-model="editForm.publicSlug" data-test="edit-conference-slug" />
          </el-form-item>
          <el-form-item label="主题领域">
            <el-input v-model="editForm.topicAreas" data-test="edit-conference-topics" placeholder="示例：agents,systems" />
          </el-form-item>
          <el-form-item label="审稿模式">
            <el-select v-model="editForm.blindMode" data-test="edit-conference-blind-mode">
              <el-option label="双盲" value="DOUBLE_BLIND" />
              <el-option label="单盲" value="SINGLE_BLIND" />
              <el-option label="开放评审" value="OPEN" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标评审数">
            <el-input-number v-model="editForm.targetReviewsPerPaper" :min="1" :max="10" />
          </el-form-item>
          <el-form-item label="默认审稿负荷">
            <el-input-number v-model="editForm.defaultReviewerMaxLoad" :min="1" :max="20" />
          </el-form-item>
        </div>
        <el-form-item label="征稿文本">
          <el-input v-model="editForm.cfpText" data-test="edit-conference-cfp" type="textarea" :rows="4" />
        </el-form-item>
        <div class="score-grid">
          <el-form-item label="投稿开放">
            <el-date-picker
              v-model="editForm.submissionOpenAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss[Z]"
              placeholder="选择投稿开放时间"
            />
          </el-form-item>
          <el-form-item label="摘要提交截止">
            <el-date-picker
              v-model="editForm.abstractSubmissionCloseAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss[Z]"
              placeholder="选择摘要截止日期"
            />
          </el-form-item>
          <el-form-item label="论文提交截止">
            <el-date-picker
              v-model="editForm.submissionCloseAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss[Z]"
              placeholder="选择论文截止日期"
            />
          </el-form-item>
          <el-form-item label="评审截止">
            <el-date-picker
              v-model="editForm.reviewDeadlineAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss[Z]"
              placeholder="选择评审截止时间"
            />
          </el-form-item>
          <el-form-item label="决策发布">
            <el-date-picker
              v-model="editForm.decisionReleaseAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss[Z]"
              placeholder="选择决策发布时间"
            />
          </el-form-item>
        </div>
        <div class="action-row">
          <el-button type="primary" native-type="submit" :loading="actions.isPending('save-conference-draft')">
            保存修改
          </el-button>
          <el-button
            :loading="actions.isPending('submit-conference-approval')"
            @click="resubmitConferenceForApproval"
          >
            再次提交审批
          </el-button>
        </div>
      </el-form>
    </section>

    <section class="workflow-section">
      <div class="subsection-heading">
        <h2>会议论文条目</h2>
        <span class="muted-line">
          {{ canManageConferencePapers ? "查看每篇论文的审核状态，并直接执行接收或 Desk reject。" : "查看会议论文状态；论文内容和评审详情仅会议主席可见。" }}
        </span>
      </div>
      <el-table v-loading="loading" :data="papers" row-key="manuscriptId" empty-text="暂无会议论文。">
        <el-table-column prop="manuscriptId" label="稿件" width="100" />
        <el-table-column label="论文" min-width="240">
          <template #default="{ row }">
            <strong>{{ row.title }}</strong>
            <p class="muted-line">
              v{{ row.versionNo }} · Round {{ row.roundNo ?? "未创建" }} · {{ formatDateTime(row.submittedAt) }}
            </p>
          </template>
        </el-table-column>
        <el-table-column label="稿件状态" width="160">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.currentStatus)">{{ workflowLabel(row.currentStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="160">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.roundStatus)">{{ workflowLabel(row.roundStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评审进度" width="130">
          <template #default="{ row }">{{ row.submittedReviewCount }}/{{ row.assignmentCount }}</template>
        </el-table-column>
        <el-table-column label="评审评分" min-width="240">
          <template #default="{ row }">
            <div class="paper-score-preview">
              <strong>均分 {{ scoreLabel(row.averageOverallScore) }}</strong>
              <p v-if="!row.reviewerScores?.length" class="muted-line">暂无已分配审稿人。</p>
              <p
                v-for="score in row.reviewerScores"
                v-else
                :key="score.assignmentId"
                class="muted-line"
              >
                {{ score.reviewerName }}: {{ scoreLabel(score.overallScore) }}
                <span v-if="score.recommendation">· {{ workflowLabel(score.recommendation) }}</span>
              </p>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最近决策" width="140">
          <template #default="{ row }">{{ workflowLabel(row.lastDecisionCode) }}</template>
        </el-table-column>
        <el-table-column v-if="canManageConferencePapers" label="操作" width="320">
          <template #default="{ row }">
            <div class="action-row">
              <RouterLink :to="`/chair/conferences/${conferenceId}/papers/${row.manuscriptId}`">
                <el-button size="small">查看评审详情</el-button>
              </RouterLink>
              <el-button size="small" type="primary" :disabled="!canAccept(row)" @click="openDecision(row, 'ACCEPT')">
                Accept
              </el-button>
              <el-button size="small" type="danger" :disabled="!canDeskReject(row)" @click="openDecision(row, 'DESK_REJECT')">
                Desk reject
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="decisionDialogOpen" title="论文审核决策" width="560px">
      <el-form ref="decisionFormRef" :model="decisionForm" :rules="decisionRules" label-position="top">
        <el-form-item label="决策" prop="decisionCode">
          <el-select v-model="decisionForm.decisionCode">
            <el-option label="Accept" value="ACCEPT" />
            <el-option label="Desk reject" value="DESK_REJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因" prop="decisionReason">
          <el-input
            v-model="decisionForm.decisionReason"
            data-test="conference-decision-reason"
            type="textarea"
            :rows="4"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="actions.isPending('conference-paper-decision')" @click="decisionDialogOpen = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="actions.isPending('conference-paper-decision')"
          @click="submitDecision"
        >
          Submit decision
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
