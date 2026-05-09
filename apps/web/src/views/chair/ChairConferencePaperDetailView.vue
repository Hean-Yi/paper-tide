<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import ChairPaperReader from "../../components/chair/ChairPaperReader.vue";
import { useApiError } from "../../composables/useApiError";
import {
  getChairConferencePaperReviewDetail,
  type ChairConferencePaperReview,
  type ChairConferencePaperReviewDetail
} from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";

const route = useRoute();
const router = useRouter();
const { showApiError } = useApiError();
const loading = ref(false);
const detail = ref<ChairConferencePaperReviewDetail | null>(null);
const selectedAssignmentId = ref<number | null>(null);

const conferenceId = computed(() => Number(route.params.conferenceId));
const manuscriptId = computed(() => Number(route.params.manuscriptId));
const selectedReview = computed<ChairConferencePaperReview | null>(() =>
  detail.value?.reviews.find((review) => review.assignmentId === selectedAssignmentId.value) ?? detail.value?.reviews[0] ?? null
);

onMounted(loadDetail);

watch(() => [route.params.conferenceId, route.params.manuscriptId], () => {
  void loadDetail();
});

async function loadDetail() {
  if (!Number.isFinite(conferenceId.value) || !Number.isFinite(manuscriptId.value)) {
    return;
  }
  loading.value = true;
  try {
    detail.value = await getChairConferencePaperReviewDetail(conferenceId.value, manuscriptId.value);
    selectedAssignmentId.value = detail.value.reviews[0]?.assignmentId ?? null;
  } catch (error) {
    showApiError(error, "论文评审详情加载失败。");
  } finally {
    loading.value = false;
  }
}

function scoreLabel(score: number | null | undefined) {
  return score ?? "未提交";
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">会议论文</p>
        <h1>论文评审详情</h1>
        <p class="body">
          {{ detail?.title ?? "论文" }} ·
          <el-tag v-if="detail" :type="statusTagType(detail.currentStatus)">
            {{ workflowLabel(detail.currentStatus) }}
          </el-tag>
        </p>
      </div>
      <div class="action-row">
        <el-button @click="router.push(`/chair/conferences/${conferenceId}`)">返回会议详情</el-button>
        <el-button :loading="loading" @click="loadDetail">刷新</el-button>
      </div>
    </div>

    <el-skeleton v-if="loading && !detail" :rows="8" animated />
    <template v-else-if="detail">
      <div class="review-workspace chair-paper-review-workspace">
        <div class="review-reader-column">
          <ChairPaperReader
            :conference-id="conferenceId"
            :manuscript-id="manuscriptId"
            :page-count="detail.pageCount"
            :title="detail.title"
          />
        </div>

        <aside class="review-side-panel">
          <section class="workflow-form review-form-panel">
            <div class="subsection-heading">
              <div>
                <h2>评审概览</h2>
                <p class="muted-line">
                  Round {{ detail.roundNo ?? "未创建" }} · {{ formatDateTime(detail.submittedAt) }}
                </p>
              </div>
              <el-tag type="success">均分 {{ scoreLabel(detail.averageOverallScore) }}</el-tag>
            </div>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
              <el-descriptions-item label="关键词">{{ detail.keywords || "无" }}</el-descriptions-item>
              <el-descriptions-item label="摘要">{{ detail.abstractText || "无" }}</el-descriptions-item>
            </el-descriptions>
          </section>

          <section class="workflow-form review-form-panel">
            <div class="subsection-heading">
              <h2>切换审稿人</h2>
              <el-tag>{{ detail.reviews.length }} 人</el-tag>
            </div>
            <el-select
              v-model="selectedAssignmentId"
              data-test="reviewer-select"
              placeholder="选择审稿人"
              class="full-width"
            >
              <el-option
                v-for="review in detail.reviews"
                :key="review.assignmentId"
                :label="`${review.reviewerName} · ${scoreLabel(review.overallScore)}`"
                :value="review.assignmentId"
              />
            </el-select>
          </section>

          <section v-if="selectedReview" class="workflow-form review-form-panel">
            <div class="subsection-heading">
              <div>
                <h2>{{ selectedReview.reviewerName }}</h2>
                <p class="muted-line">{{ selectedReview.institution || "未填写机构" }}</p>
              </div>
              <el-tag :type="statusTagType(selectedReview.taskStatus)">
                {{ workflowLabel(selectedReview.taskStatus) }}
              </el-tag>
            </div>

            <div class="score-grid review-score-grid">
              <el-statistic title="总体评分" :value="selectedReview.overallScore ?? 0" />
              <el-statistic title="创新性" :value="selectedReview.noveltyScore ?? 0" />
              <el-statistic title="方法" :value="selectedReview.methodScore ?? 0" />
              <el-statistic title="实验" :value="selectedReview.experimentScore ?? 0" />
              <el-statistic title="写作" :value="selectedReview.writingScore ?? 0" />
            </div>

            <el-descriptions :column="1" border>
              <el-descriptions-item label="建议">{{ workflowLabel(selectedReview.recommendation) }}</el-descriptions-item>
              <el-descriptions-item label="置信度">{{ workflowLabel(selectedReview.confidenceLevel) }}</el-descriptions-item>
              <el-descriptions-item label="提交时间">{{ formatDateTime(selectedReview.reviewSubmittedAt) }}</el-descriptions-item>
            </el-descriptions>

            <el-collapse model-value="strengths">
              <el-collapse-item title="优势" name="strengths">
                <p class="review-text">{{ selectedReview.strengths || "未提交。" }}</p>
              </el-collapse-item>
              <el-collapse-item title="不足" name="weaknesses">
                <p class="review-text">{{ selectedReview.weaknesses || "未提交。" }}</p>
              </el-collapse-item>
              <el-collapse-item title="给作者的评价" name="author">
                <p class="review-text">{{ selectedReview.commentsToAuthor || "未提交。" }}</p>
              </el-collapse-item>
              <el-collapse-item title="给 Chair 的评价" name="chair">
                <p class="review-text">{{ selectedReview.commentsToChair || "未提交。" }}</p>
              </el-collapse-item>
            </el-collapse>
          </section>
          <el-empty v-else description="暂无审稿人评价。" />
        </aside>
      </div>
    </template>
  </section>
</template>
