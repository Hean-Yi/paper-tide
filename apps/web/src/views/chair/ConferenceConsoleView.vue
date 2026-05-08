<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";

import { apiErrorMessage } from "../../composables/useApiError";
import { authState } from "../../stores/auth";
import {
  addConferenceReviewer,
  approveConference,
  createConferenceDraft,
  listChairConferences,
  listPendingConferenceApprovals,
  submitConferenceForApproval,
  type ConferenceCfpSummary,
  type ConferenceDetail
} from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";

const isAdmin = computed(() => authState.user?.roles.includes("ADMIN") ?? false);
const loading = ref(false);
const pendingLoading = ref(false);
const error = ref("");
const createdConference = ref<ConferenceDetail | null>(null);
const pendingConferences = ref<ConferenceCfpSummary[]>([]);
const chairConferences = ref<ConferenceCfpSummary[]>([]);
const conferenceHistoryLoading = ref(false);

const form = reactive({
  name: "",
  acronym: "",
  year: new Date().getFullYear(),
  publicSlug: "",
  blindMode: "DOUBLE_BLIND",
  cfpText: "",
  topicAreas: "",
  targetReviewsPerPaper: 3,
  defaultReviewerMaxLoad: 4,
  submissionOpenAt: "2026-05-01T00:00:00Z",
  submissionCloseAt: "2026-06-01T00:00:00Z",
  biddingOpenAt: "2026-06-02T00:00:00Z",
  biddingCloseAt: "2026-06-10T00:00:00Z",
  reviewDeadlineAt: "2026-07-01T00:00:00Z",
  decisionReleaseAt: "2026-07-15T00:00:00Z"
});

const reviewerForm = reactive({
  conferenceId: 0,
  reviewerId: 0,
  maxLoad: 3
});

onMounted(() => {
  void loadChairConferences();
  if (isAdmin.value) {
    void loadPending();
  }
});

async function createDraft() {
  loading.value = true;
  error.value = "";
  try {
    createdConference.value = await createConferenceDraft({
      name: form.name.trim(),
      acronym: form.acronym.trim(),
      year: Number(form.year),
      blindMode: form.blindMode,
      cfpText: form.cfpText.trim() || `${form.acronym} ${form.year} call for papers`,
      topicAreas: form.topicAreas.split(",").map((entry) => entry.trim()).filter(Boolean),
      targetReviewsPerPaper: Number(form.targetReviewsPerPaper),
      defaultReviewerMaxLoad: Number(form.defaultReviewerMaxLoad),
      publicSlug: form.publicSlug.trim(),
      phase: {
        submissionOpenAt: form.submissionOpenAt,
        submissionCloseAt: form.submissionCloseAt,
        biddingOpenAt: form.biddingOpenAt,
        biddingCloseAt: form.biddingCloseAt,
        reviewDeadlineAt: form.reviewDeadlineAt,
        decisionReleaseAt: form.decisionReleaseAt
      }
    });
    reviewerForm.conferenceId = createdConference.value.conferenceId;
    ElMessage.success("会议草稿已创建。");
    await loadChairConferences();
  } catch (err) {
    error.value = apiErrorMessage(err, "会议草稿创建失败。");
  } finally {
    loading.value = false;
  }
}

async function submitCreatedConference() {
  if (!createdConference.value) {
    return;
  }
  createdConference.value = await submitConferenceForApproval(createdConference.value.conferenceId);
  await loadChairConferences();
  ElMessage.success("会议已提交审批。");
}

async function addReviewerToConference() {
  await addConferenceReviewer(
    Number(reviewerForm.conferenceId),
    Number(reviewerForm.reviewerId),
    Number(reviewerForm.maxLoad)
  );
  ElMessage.success("审稿人已加入会议审稿人池。");
}

async function loadPending() {
  pendingLoading.value = true;
  try {
    pendingConferences.value = await listPendingConferenceApprovals();
  } finally {
    pendingLoading.value = false;
  }
}

async function loadChairConferences() {
  conferenceHistoryLoading.value = true;
  try {
    chairConferences.value = await listChairConferences();
  } catch (err) {
    error.value = apiErrorMessage(err, "会议列表加载失败。");
  } finally {
    conferenceHistoryLoading.value = false;
  }
}

async function approve(row: ConferenceCfpSummary) {
  await approveConference(row.conferenceId);
  await loadChairConferences();
  await loadPending();
  ElMessage.success("会议已审批通过。");
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">主席控制台</p>
        <h1>会议管理</h1>
        <p class="body">创建征稿启事草稿、管理过往会议状态并进入会议论文审核。</p>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" />

    <section class="workflow-section">
      <div class="subsection-heading">
        <h2>过往会议状态</h2>
        <el-button :loading="conferenceHistoryLoading" @click="loadChairConferences">刷新</el-button>
      </div>
      <el-table v-loading="conferenceHistoryLoading" :data="chairConferences" empty-text="暂无可管理会议。">
        <el-table-column prop="acronym" label="缩写" width="120" />
        <el-table-column label="会议" min-width="220">
          <template #default="{ row }">
            <strong>{{ row.name }}</strong>
            <p class="muted-line">{{ row.year }} · {{ row.publicSlug }}</p>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="180">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ workflowLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="投稿截止" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.submissionCloseAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <RouterLink class="text-link" :to="{ name: 'chair-conference-detail', params: { conferenceId: row.conferenceId } }">
              查看详情
            </RouterLink>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="workflow-section">
      <h2>创建征稿启事草稿</h2>
      <el-form class="workflow-form" label-position="top" @submit.prevent="createDraft">
        <div class="score-grid">
          <el-form-item label="名称">
            <el-input v-model="form.name" data-test="conference-name" />
          </el-form-item>
          <el-form-item label="缩写">
            <el-input v-model="form.acronym" data-test="conference-acronym" />
          </el-form-item>
          <el-form-item label="年份">
            <el-input-number v-model="form.year" data-test="conference-year" :min="2026" />
          </el-form-item>
          <el-form-item label="公开标识">
            <el-input v-model="form.publicSlug" data-test="conference-slug" />
          </el-form-item>
          <el-form-item label="主题领域">
            <el-input v-model="form.topicAreas" data-test="conference-topics" placeholder="示例：agents,systems" />
          </el-form-item>
          <el-form-item label="审稿模式">
            <el-select v-model="form.blindMode">
              <el-option label="双盲" value="DOUBLE_BLIND" />
              <el-option label="单盲" value="SINGLE_BLIND" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="征稿文本">
          <el-input v-model="form.cfpText" type="textarea" :rows="3" />
        </el-form-item>
        <div class="action-row">
          <el-button type="primary" native-type="submit" :loading="loading">创建草稿</el-button>
          <el-button :disabled="!createdConference" @click="submitCreatedConference">提交审批</el-button>
        </div>
      </el-form>
      <el-alert
        v-if="createdConference"
        :title="`${createdConference.name} is ${workflowLabel(createdConference.status)}`"
        type="success"
        :closable="false"
      />
    </section>

    <section class="workflow-section">
      <h2>审稿人池</h2>
      <el-form class="workflow-form" label-position="top" @submit.prevent="addReviewerToConference">
        <div class="score-grid">
          <el-form-item label="会议 ID">
            <el-input-number v-model="reviewerForm.conferenceId" :min="1" />
          </el-form-item>
          <el-form-item label="审稿人 ID">
            <el-input-number v-model="reviewerForm.reviewerId" :min="1" />
          </el-form-item>
          <el-form-item label="最大负荷">
            <el-input-number v-model="reviewerForm.maxLoad" :min="1" :max="20" />
          </el-form-item>
        </div>
        <el-button type="primary" native-type="submit">添加审稿人</el-button>
      </el-form>
    </section>

    <section v-if="isAdmin" class="workflow-section">
      <div class="subsection-heading">
        <h2>会议审批</h2>
        <el-button @click="loadPending">刷新</el-button>
      </div>
      <el-table v-loading="pendingLoading" :data="pendingConferences" empty-text="暂无待审批的会议。">
        <el-table-column prop="acronym" label="缩写" width="120" />
        <el-table-column label="会议">
          <template #default="{ row }">
            <strong>{{ row.name }}</strong>
            <p class="muted-line">{{ formatDateTime(row.submissionCloseAt) }}</p>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="180">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ workflowLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="approve(row)">审批通过</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>
