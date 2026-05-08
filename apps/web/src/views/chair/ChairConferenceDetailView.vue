<script setup lang="ts">
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";

import { useApiError } from "../../composables/useApiError";
import { useAsyncAction } from "../../composables/useAsyncAction";
import {
  advanceConference,
  decide,
  getChairConference,
  listConferencePapers,
  type ConferenceDetail,
  type ConferencePaperItem
} from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";

const route = useRoute();
const loading = ref(false);
const conference = ref<ConferenceDetail | null>(null);
const papers = ref<ConferencePaperItem[]>([]);
const decisionDialogOpen = ref(false);
const decisionFormRef = ref<FormInstance>();
const actions = useAsyncAction();
const { showApiError } = useApiError();

const decisionForm = reactive({
  manuscriptId: 0,
  versionId: 0,
  roundId: 0,
  decisionCode: "ACCEPT",
  decisionReason: ""
});

const decisionRules: FormRules = {
  decisionCode: [{ required: true, message: "决策为必选", trigger: "change" }],
  decisionReason: [{ required: true, message: "原因为必填", trigger: "blur" }]
};

const conferenceId = computed(() => Number(route.params.conferenceId));
const nextStatus = computed(() => nextConferenceStatus(conference.value?.status));
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
  } catch (error) {
    showApiError(error, "会议详情加载失败。");
  } finally {
    loading.value = false;
  }
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
          v-if="nextStatus"
          type="primary"
          :loading="actions.isPending('advance-conference')"
          @click="advanceToNextStatus"
        >
          推进到 {{ workflowLabel(nextStatus) }}
        </el-button>
      </div>
    </div>

    <section v-if="conference" class="workflow-section">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="当前状态">
          <el-tag :type="statusTagType(conference.status)">{{ workflowLabel(conference.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审稿模式">{{ workflowLabel(conference.blindMode) }}</el-descriptions-item>
        <el-descriptions-item label="公开标识">{{ conference.publicSlug }}</el-descriptions-item>
        <el-descriptions-item label="投稿开放">{{ formatDateTime(conference.phase?.submissionOpenAt) }}</el-descriptions-item>
        <el-descriptions-item label="投稿截止">{{ formatDateTime(conference.phase?.submissionCloseAt) }}</el-descriptions-item>
        <el-descriptions-item label="评审截止">{{ formatDateTime(conference.phase?.reviewDeadlineAt) }}</el-descriptions-item>
        <el-descriptions-item label="论文总数">{{ papers.length }}</el-descriptions-item>
        <el-descriptions-item label="进行中">{{ activePaperCount }}</el-descriptions-item>
        <el-descriptions-item label="已决策">{{ decidedPaperCount }}</el-descriptions-item>
      </el-descriptions>
    </section>

    <section class="workflow-section">
      <div class="subsection-heading">
        <h2>会议论文条目</h2>
        <span class="muted-line">查看每篇论文的审核状态，并直接执行接收或 Desk reject。</span>
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
        <el-table-column label="最近决策" width="140">
          <template #default="{ row }">{{ workflowLabel(row.lastDecisionCode) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <div class="action-row">
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
