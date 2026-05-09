<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";

import { apiErrorMessage } from "../../composables/useApiError";
import { conferenceSchedulePayload } from "../../lib/conference-schedule";
import {
  addConferenceReviewer,
  createConferenceDraft,
  listChairConferences,
  searchPlatformReviewers,
  submitConferenceForApproval,
  type ConferenceCfpSummary,
  type ConferenceDetail,
  type PlatformReviewerSearchResult
} from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";
import { hasRole } from "../../stores/auth";

const loading = ref(false);
const error = ref("");
const createdConference = ref<ConferenceDetail | null>(null);
const chairConferences = ref<ConferenceCfpSummary[]>([]);
const conferenceHistoryLoading = ref(false);
const reviewerOptions = ref<PlatformReviewerSearchResult[]>([]);
const reviewerSearchLoading = ref(false);
const canOperateConferences = computed(() => hasRole("CHAIR"));

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
  abstractSubmissionCloseAt: "2026-05-20T00:00:00Z",
  submissionCloseAt: "2026-06-01T00:00:00Z",
  reviewDeadlineAt: "2026-07-01T00:00:00Z",
  decisionReleaseAt: "2026-07-15T00:00:00Z"
});

const reviewerForm = reactive({
  conferenceId: undefined as number | undefined,
  reviewerId: undefined as number | undefined,
  maxLoad: 3
});

const selectedReviewerConference = computed(() =>
  chairConferences.value.find((conference) => conference.conferenceId === reviewerForm.conferenceId) ?? null
);

function reviewerOptionLabel(reviewer: PlatformReviewerSearchResult) {
  const institution = reviewer.institution ? ` · ${reviewer.institution}` : "";
  return `${reviewer.realName} · ${reviewer.email}${institution}`;
}

function reviewerAreaText(reviewer: PlatformReviewerSearchResult) {
  return reviewer.researchAreas.map((area) => area.areaName || area.areaCode).join(", ") || "暂无研究方向";
}

onMounted(() => {
  void loadChairConferences();
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
      phase: conferenceSchedulePayload({
        submissionOpenAt: form.submissionOpenAt,
        abstractSubmissionCloseAt: form.abstractSubmissionCloseAt,
        submissionCloseAt: form.submissionCloseAt,
        reviewDeadlineAt: form.reviewDeadlineAt,
        decisionReleaseAt: form.decisionReleaseAt
      })
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
  if (!reviewerForm.conferenceId || !reviewerForm.reviewerId) {
    error.value = "请选择会议并搜索选择审稿人。";
    return;
  }
  error.value = "";
  try {
    await addConferenceReviewer(
      Number(reviewerForm.conferenceId),
      Number(reviewerForm.reviewerId),
      Number(reviewerForm.maxLoad)
    );
    ElMessage.success("审稿人已加入会议审稿人池。");
  } catch (err) {
    error.value = apiErrorMessage(err, "审稿人加入失败。");
  }
}

async function searchReviewers(query: string) {
  reviewerSearchLoading.value = true;
  try {
    reviewerOptions.value = await searchPlatformReviewers(query);
  } catch (err) {
    error.value = apiErrorMessage(err, "审稿人搜索失败。");
    reviewerOptions.value = [];
  } finally {
    reviewerSearchLoading.value = false;
  }
}

async function loadChairConferences() {
  conferenceHistoryLoading.value = true;
  try {
    chairConferences.value = await listChairConferences();
    if (!reviewerForm.conferenceId && chairConferences.value.length > 0) {
      reviewerForm.conferenceId = chairConferences.value[0].conferenceId;
    }
  } catch (err) {
    error.value = apiErrorMessage(err, "会议列表加载失败。");
  } finally {
    conferenceHistoryLoading.value = false;
  }
}

</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">{{ canOperateConferences ? "主席控制台" : "会议运营辅助" }}</p>
        <h1>会议管理</h1>
        <p class="body">
          {{ canOperateConferences ? "创建征稿启事草稿、管理过往会议状态并进入会议论文审核。" : "查看所有会议的状态和详情；论文内容与运营动作由会议主席处理。" }}
        </p>
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
        <el-table-column label="摘要截止" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.abstractSubmissionCloseAt) }}</template>
        </el-table-column>
        <el-table-column label="论文截止" min-width="170">
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

    <section v-if="canOperateConferences" class="workflow-section">
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
          <el-form-item label="投稿开放时间">
            <el-date-picker
              v-model="form.submissionOpenAt"
              data-test="conference-submission-open"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss[Z]"
              placeholder="选择投稿开放时间"
            />
          </el-form-item>
          <el-form-item label="摘要提交截止日期">
            <el-date-picker
              v-model="form.abstractSubmissionCloseAt"
              data-test="conference-abstract-deadline"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss[Z]"
              placeholder="选择摘要截止日期"
            />
          </el-form-item>
          <el-form-item label="论文提交截止日期">
            <el-date-picker
              v-model="form.submissionCloseAt"
              data-test="conference-paper-deadline"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss[Z]"
              placeholder="选择论文截止日期"
            />
          </el-form-item>
          <el-form-item label="评审截止时间">
            <el-date-picker
              v-model="form.reviewDeadlineAt"
              data-test="conference-review-deadline"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss[Z]"
              placeholder="选择评审截止时间"
            />
          </el-form-item>
          <el-form-item label="决策发布时间">
            <el-date-picker
              v-model="form.decisionReleaseAt"
              data-test="conference-decision-release"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss[Z]"
              placeholder="选择决策发布时间"
            />
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

    <section v-if="canOperateConferences" class="workflow-section">
      <div class="subsection-heading">
        <div>
          <h2>会议审稿人池准备</h2>
          <p class="muted-line">把已批准的平台审稿人加入指定会议候选池；后续投标、利益冲突校验、随机分配预览和确认分配都会使用这批候选人。</p>
        </div>
        <RouterLink class="text-link" :to="{ name: 'chair-assignment-operations' }">进入审稿人分配</RouterLink>
      </div>
      <el-form class="workflow-form" label-position="top" @submit.prevent="addReviewerToConference">
        <div class="score-grid">
          <el-form-item label="选择会议">
            <el-select v-model="reviewerForm.conferenceId" data-test="reviewer-pool-conference" placeholder="选择会议">
              <el-option
                v-for="conference in chairConferences"
                :key="conference.conferenceId"
                :label="`${conference.name} ${conference.year}`"
                :value="conference.conferenceId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="搜索审稿人">
            <el-select
              v-model="reviewerForm.reviewerId"
              data-test="reviewer-pool-reviewer"
              filterable
              remote
              reserve-keyword
              :remote-method="searchReviewers"
              :loading="reviewerSearchLoading"
              placeholder="输入真实姓名或邮箱搜索"
            >
              <el-option
                v-for="reviewer in reviewerOptions"
                :key="reviewer.reviewerId"
                :label="reviewerOptionLabel(reviewer)"
                :value="reviewer.reviewerId"
              >
                <div>
                  <strong>{{ reviewer.realName }}</strong>
                  <p class="muted-line">{{ reviewer.email }} · {{ reviewer.institution || "未知机构" }}</p>
                  <p class="muted-line">研究方向：{{ reviewerAreaText(reviewer) }}</p>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="最大负荷">
            <el-input-number v-model="reviewerForm.maxLoad" :min="1" :max="20" />
          </el-form-item>
        </div>
        <el-alert
          v-if="selectedReviewerConference"
          class="workflow-alert compact-alert"
          type="info"
          :closable="false"
          :title="`${selectedReviewerConference.name} 当前状态：${workflowLabel(selectedReviewerConference.status)}`"
        />
        <el-button type="primary" native-type="submit" :disabled="!reviewerForm.conferenceId || !reviewerForm.reviewerId">
          加入会议候选池
        </el-button>
      </el-form>
    </section>

  </section>
</template>
