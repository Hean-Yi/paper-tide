<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";

import { apiErrorMessage } from "../../composables/useApiError";
import {
  approveConference,
  getAdminConference,
  listPendingConferenceApprovals,
  rejectConference,
  type ConferenceCfpSummary
} from "../../lib/workflow-api";
import type { ConferenceDetail } from "../../lib/workflow-types";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";

const loading = ref(false);
const error = ref("");
const pendingConferences = ref<ConferenceCfpSummary[]>([]);
const detailDialogOpen = ref(false);
const detailLoading = ref(false);
const selectedConference = ref<ConferenceDetail | null>(null);
const rejectionReason = ref("");
const rejectionError = ref("");

const selectedPhase = computed(() => selectedConference.value?.phase);

onMounted(() => {
  void loadPending();
});

async function loadPending() {
  loading.value = true;
  error.value = "";
  try {
    pendingConferences.value = await listPendingConferenceApprovals();
  } catch (err) {
    error.value = apiErrorMessage(err, "会议审批列表加载失败。");
  } finally {
    loading.value = false;
  }
}

async function approve(row: ConferenceCfpSummary) {
  error.value = "";
  try {
    await approveConference(row.conferenceId);
    detailDialogOpen.value = false;
    await loadPending();
    ElMessage.success("会议已审批通过。");
  } catch (err) {
    error.value = apiErrorMessage(err, "会议审批失败。");
  }
}

async function openDetail(row: ConferenceCfpSummary) {
  detailDialogOpen.value = true;
  detailLoading.value = true;
  rejectionReason.value = "";
  rejectionError.value = "";
  selectedConference.value = null;
  try {
    selectedConference.value = await getAdminConference(row.conferenceId);
  } catch (err) {
    error.value = apiErrorMessage(err, "会议详情加载失败。");
    detailDialogOpen.value = false;
  } finally {
    detailLoading.value = false;
  }
}

async function rejectSelectedConference() {
  if (!selectedConference.value) {
    return;
  }
  const reason = rejectionReason.value.trim();
  if (!reason) {
    rejectionError.value = "请填写拒绝理由。";
    return;
  }
  rejectionError.value = "";
  try {
    await rejectConference(selectedConference.value.conferenceId, reason);
    detailDialogOpen.value = false;
    await loadPending();
    ElMessage.success("会议已退回草稿。");
  } catch (err) {
    rejectionError.value = apiErrorMessage(err, "会议拒绝审批失败。");
  }
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">管理后台</p>
        <h1>会议审批</h1>
        <p class="body">集中审批主席提交的会议征稿启事；会议创建、审稿人池和后续运营仍在会议管理中处理。</p>
      </div>
      <el-button :loading="loading" @click="loadPending">刷新</el-button>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" />

    <section class="workflow-section">
      <el-table
        v-loading="loading"
        class="clickable-table"
        :data="pendingConferences"
        empty-text="暂无待审批的会议。"
        @row-click="openDetail"
      >
        <el-table-column prop="acronym" label="缩写" width="120" />
        <el-table-column label="会议">
          <template #default="{ row }">
            <strong>{{ row.name }}</strong>
            <p class="muted-line">{{ row.year }} · {{ row.publicSlug }}</p>
            <p class="muted-line">投稿截止：{{ formatDateTime(row.submissionCloseAt) }}</p>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="180">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ workflowLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button size="small" @click.stop="openDetail(row)">查看详情</el-button>
            <el-button size="small" type="primary" @click.stop="approve(row)">审批通过</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="detailDialogOpen"
      title="会议审批详情"
      width="760px"
      :teleported="false"
    >
      <div v-loading="detailLoading">
        <template v-if="selectedConference">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="会议名称">{{ selectedConference.name }}</el-descriptions-item>
            <el-descriptions-item label="缩写/年份">
              {{ selectedConference.acronym }} {{ selectedConference.year }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(selectedConference.status)">
                {{ workflowLabel(selectedConference.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="公开标识">{{ selectedConference.publicSlug }}</el-descriptions-item>
            <el-descriptions-item label="审稿模式">{{ workflowLabel(selectedConference.blindMode) }}</el-descriptions-item>
            <el-descriptions-item label="目标评审数">
              {{ selectedConference.targetReviewsPerPaper }} / 篇
            </el-descriptions-item>
            <el-descriptions-item label="默认审稿负荷">
              {{ selectedConference.defaultReviewerMaxLoad }}
            </el-descriptions-item>
            <el-descriptions-item label="主题领域">
              {{ selectedConference.topicAreas?.join(", ") || "未填写" }}
            </el-descriptions-item>
            <el-descriptions-item label="投稿开放">{{ formatDateTime(selectedPhase?.submissionOpenAt) }}</el-descriptions-item>
            <el-descriptions-item label="投稿截止">{{ formatDateTime(selectedPhase?.submissionCloseAt) }}</el-descriptions-item>
            <el-descriptions-item label="竞标开放">{{ formatDateTime(selectedPhase?.biddingOpenAt) }}</el-descriptions-item>
            <el-descriptions-item label="竞标截止">{{ formatDateTime(selectedPhase?.biddingCloseAt) }}</el-descriptions-item>
            <el-descriptions-item label="评审截止">{{ formatDateTime(selectedPhase?.reviewDeadlineAt) }}</el-descriptions-item>
            <el-descriptions-item label="决策发布时间">{{ formatDateTime(selectedPhase?.decisionReleaseAt) }}</el-descriptions-item>
          </el-descriptions>

          <section class="approval-detail-block">
            <h3>征稿文本</h3>
            <p class="body pre-wrap">{{ selectedConference.cfpText }}</p>
          </section>

          <section class="approval-detail-block">
            <h3>拒绝审批</h3>
            <p class="muted-line">拒绝后会议会自动退回主席可编辑的草稿状态，主席会在详情页看到此理由并可再次提交。</p>
            <el-input
              v-model="rejectionReason"
              data-test="conference-rejection-reason"
              type="textarea"
              :rows="3"
              placeholder="请填写需要主席修改的具体原因"
            />
            <p v-if="rejectionError" class="form-error">{{ rejectionError }}</p>
          </section>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailDialogOpen = false">关闭</el-button>
        <el-button
          v-if="selectedConference"
          type="danger"
          :disabled="detailLoading"
          @click="rejectSelectedConference"
        >
          拒绝审批
        </el-button>
        <el-button
          v-if="selectedConference"
          type="primary"
          :disabled="detailLoading"
          @click="approve(selectedConference)"
        >
          审批通过
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
