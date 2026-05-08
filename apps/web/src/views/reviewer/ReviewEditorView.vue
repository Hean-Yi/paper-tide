<script setup lang="ts">
import type { FormInstance, FormItemRule, FormRules } from "element-plus";
import { ElMessage } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import ReviewerAgentPanel from "../../components/reviewer/ReviewerAgentPanel.vue";
import SecurePaperReader from "../../components/reviewer/SecurePaperReader.vue";
import {
  getReviewerAssignment,
  getReviewForm,
  listReviewFormRevisions,
  saveReviewFormResponse,
  submitReviewReport,
  type ReviewFormRevision,
  type ReviewFormPackage,
  type ReviewReportForm,
  type ReviewerAssignment
} from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";

const route = useRoute();
const router = useRouter();
const assignment = ref<ReviewerAssignment | null>(null);
const loading = ref(false);
const submitting = ref(false);
const dynamicSubmitting = ref(false);
const sidePanelCollapsed = ref(false);
const assignmentPanels = ref<string[]>([]);
const reviewFormRef = ref<FormInstance>();
const dynamicReviewForm = ref<ReviewFormPackage | null>(null);
const reviewFormRevisions = ref<ReviewFormRevision[]>([]);
const dynamicAnswers = reactive<Record<string, unknown>>({});
const form = reactive<ReviewReportForm>({
  noveltyScore: 3,
  methodScore: 3,
  experimentScore: 3,
  writingScore: 3,
  overallScore: 3,
  confidenceLevel: "MEDIUM",
  strengths: "",
  weaknesses: "",
  commentsToAuthor: "",
  commentsToChair: "",
  recommendation: "MINOR_REVISION"
});
const requiredTrimmed = (message: string): FormItemRule => ({
  trigger: "blur",
  validator: (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
    if (String(value ?? "").trim()) {
      callback();
      return;
    }
    callback(new Error(message));
  }
});
const reviewRules: FormRules = {
  confidenceLevel: [{ required: true, message: "请选择置信度", trigger: "change" }],
  recommendation: [{ required: true, message: "请选择建议结论", trigger: "change" }],
  strengths: [requiredTrimmed("请填写优势")],
  weaknesses: [requiredTrimmed("请填写不足")],
  commentsToAuthor: [requiredTrimmed("请填写对作者的评语")]
};

function hasRequiredReviewValues() {
  return Boolean(
    form.confidenceLevel &&
      form.recommendation &&
      form.strengths.trim() &&
      form.weaknesses.trim() &&
      form.commentsToAuthor.trim()
  );
}

const assignmentId = computed(() => Number(route.params.assignmentId));

onMounted(loadAssignment);

watch(assignmentId, () => {
  void loadAssignment();
});

async function loadAssignment() {
  loading.value = true;
  try {
    assignment.value = await getReviewerAssignment(assignmentId.value);
    await loadDynamicReviewForm();
  } finally {
    loading.value = false;
  }
}

async function loadDynamicReviewForm() {
  try {
    const [formPackage, revisions] = await Promise.all([
      getReviewForm(assignmentId.value),
      listReviewFormRevisions(assignmentId.value)
    ]);
    dynamicReviewForm.value = formPackage;
    reviewFormRevisions.value = revisions;
    Object.keys(dynamicAnswers).forEach((key) => delete dynamicAnswers[key]);
    for (const field of dynamicReviewForm.value.form.fields) {
      dynamicAnswers[field.fieldKey] = dynamicReviewForm.value.currentResponse?.answers?.[field.fieldKey] ?? "";
    }
  } catch {
    dynamicReviewForm.value = null;
    reviewFormRevisions.value = [];
  }
}

function dynamicFieldComponentType(fieldType: string) {
  return fieldType === "NUMBER" || fieldType === "SCORE" ? "number" : "text";
}

async function saveDynamicReview(responseStatus: "DRAFT" | "SUBMITTED") {
  if (!dynamicReviewForm.value) {
    return;
  }
  dynamicSubmitting.value = true;
  try {
    await saveReviewFormResponse(assignmentId.value, {
      formId: dynamicReviewForm.value.form.formId,
      responseStatus,
      answers: { ...dynamicAnswers }
    });
    ElMessage.success(responseStatus === "DRAFT" ? "评审草稿已保存。" : "定制评审已提交。");
    await loadDynamicReviewForm();
  } finally {
    dynamicSubmitting.value = false;
  }
}

function revisionAnswerPreview(revision: ReviewFormRevision): string {
  const entries = Object.entries(revision.answers ?? {});
  if (!entries.length) {
    return "暂无填写记录。";
  }
  return entries
    .map(([key, value]) => `${key}: ${String(value ?? "")}`)
    .join("\n");
}

async function submitReport() {
  const valid = await reviewFormRef.value?.validate().catch(() => false);
  if (!valid || !hasRequiredReviewValues()) {
    return;
  }
  submitting.value = true;
  try {
    await submitReviewReport(assignmentId.value, form);
    ElMessage.success("评审已提交。");
    await router.push("/reviewer/assignments");
  } finally {
    submitting.value = false;
  }
}

</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">审稿人</p>
        <h1>评审编辑器</h1>
        <p class="body">阅读任务详情并提交一份完整的评审报告。</p>
      </div>
      <div class="header-actions">
        <el-button @click="sidePanelCollapsed = !sidePanelCollapsed">
          {{ sidePanelCollapsed ? "显示辅助面板" : "收起辅助面板" }}
        </el-button>
        <el-button @click="router.push('/reviewer/assignments')">返回任务列表</el-button>
      </div>
    </div>

    <el-skeleton v-if="loading && !assignment" :rows="6" animated />
    <template v-else-if="assignment">
      <div class="review-workspace" :class="{ 'is-side-collapsed': sidePanelCollapsed }">
        <div class="review-reader-column">
          <SecurePaperReader :assignment-id="assignmentId" />
        </div>

        <aside v-if="!sidePanelCollapsed" class="review-side-panel" data-test="review-side-panel">
          <el-collapse
            v-model="assignmentPanels"
            data-test="assignment-details"
            class="assignment-details"
            :class="{ 'is-collapsed': !assignmentPanels.includes('assignment') }"
          >
            <el-collapse-item name="assignment">
              <template #title>
                <div class="assignment-summary">
                  <span>任务信息</span>
                  <strong>{{ assignment.title }}</strong>
                  <el-tag :type="statusTagType(assignment.taskStatus)">
                    {{ workflowLabel(assignment.taskStatus) }}
                  </el-tag>
                </div>
              </template>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="标题">{{ assignment.title }}</el-descriptions-item>
                <el-descriptions-item label="状态">
                  <el-tag :type="statusTagType(assignment.taskStatus)">{{ workflowLabel(assignment.taskStatus) }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="版本">v{{ assignment.versionNo }}</el-descriptions-item>
                <el-descriptions-item label="截止日期">{{ formatDateTime(assignment.deadlineAt) }}</el-descriptions-item>
                <el-descriptions-item label="关键词">{{ assignment.keywords || "无" }}</el-descriptions-item>
                <el-descriptions-item label="摘要">{{ assignment.abstractText }}</el-descriptions-item>
              </el-descriptions>
            </el-collapse-item>
          </el-collapse>

          <ReviewerAgentPanel :assignment-id="assignmentId" />

          <section v-if="dynamicReviewForm" class="workflow-form review-form-panel">
            <div class="subsection-heading">
              <h2>{{ dynamicReviewForm.form.formName }}</h2>
              <el-tag :type="statusTagType(dynamicReviewForm.currentResponse?.responseStatus)">
                {{ workflowLabel(dynamicReviewForm.currentResponse?.responseStatus || "DRAFT") }}
              </el-tag>
            </div>
            <el-form label-position="top" @submit.prevent="saveDynamicReview('SUBMITTED')">
              <el-form-item
                v-for="field in dynamicReviewForm.form.fields"
                :key="field.fieldId"
                :label="field.fieldLabel"
                :required="field.required"
                :data-test="`dynamic-review-${field.fieldKey}`"
              >
                <el-input
                  v-if="field.fieldType === 'LONG_TEXT' || field.fieldType === 'TEXT'"
                  v-model="dynamicAnswers[field.fieldKey]"
                  type="textarea"
                  :rows="field.fieldType === 'LONG_TEXT' ? 4 : 2"
                />
                <el-input-number
                  v-else-if="dynamicFieldComponentType(field.fieldType) === 'number'"
                  v-model="dynamicAnswers[field.fieldKey]"
                  :min="field.fieldType === 'SCORE' ? 1 : undefined"
                  :max="field.fieldType === 'SCORE' ? 5 : undefined"
                />
                <el-switch v-else-if="field.fieldType === 'BOOLEAN'" v-model="dynamicAnswers[field.fieldKey]" />
                <el-input v-else v-model="dynamicAnswers[field.fieldKey]" />
              </el-form-item>
              <div class="action-row">
                <el-button :loading="dynamicSubmitting" @click="saveDynamicReview('DRAFT')">保存草稿</el-button>
                <el-button type="primary" native-type="submit" :loading="dynamicSubmitting">提交定制评审</el-button>
              </div>
            </el-form>
            <section class="revision-history">
              <div class="subsection-heading">
                <h3>评审修订历史</h3>
                <el-tag>{{ reviewFormRevisions.length }}</el-tag>
              </div>
              <el-empty v-if="!reviewFormRevisions.length" description="暂无已提交的定制评审。" />
              <div v-else class="stacked-list">
                <el-card v-for="revision in reviewFormRevisions" :key="revision.revisionId" shadow="never">
                  <template #header>
                    修订 {{ revision.revisionNo }} · {{ formatDateTime(revision.submittedAt) }}
                  </template>
                  <pre class="json-block">{{ revisionAnswerPreview(revision) }}</pre>
                </el-card>
              </div>
            </section>
          </section>

          <el-form
            ref="reviewFormRef"
            class="workflow-form review-form-panel"
            :model="form"
            :rules="reviewRules"
            label-position="top"
            @submit.prevent="submitReport"
          >
            <div class="score-grid review-score-grid">
              <el-form-item label="创新性">
                <el-input-number v-model="form.noveltyScore" :min="1" :max="5" />
              </el-form-item>
              <el-form-item label="方法论">
                <el-input-number v-model="form.methodScore" :min="1" :max="5" />
              </el-form-item>
              <el-form-item label="实验">
                <el-input-number v-model="form.experimentScore" :min="1" :max="5" />
              </el-form-item>
              <el-form-item label="写作">
                <el-input-number v-model="form.writingScore" :min="1" :max="5" />
              </el-form-item>
              <el-form-item label="综合">
                <el-input-number v-model="form.overallScore" :min="1" :max="5" />
              </el-form-item>
            </div>

            <el-form-item label="置信度" prop="confidenceLevel">
              <el-select v-model="form.confidenceLevel">
                <el-option label="低" value="LOW" />
                <el-option label="中" value="MEDIUM" />
                <el-option label="高" value="HIGH" />
              </el-select>
            </el-form-item>
            <el-form-item label="建议结论" prop="recommendation">
              <el-select v-model="form.recommendation">
                <el-option label="接受" value="ACCEPT" />
                <el-option label="小修" value="MINOR_REVISION" />
                <el-option label="大修" value="MAJOR_REVISION" />
                <el-option label="拒稿" value="REJECT" />
              </el-select>
            </el-form-item>
            <el-form-item label="优点" prop="strengths">
              <el-input v-model="form.strengths" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="不足" prop="weaknesses">
              <el-input v-model="form.weaknesses" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="对作者的评语" prop="commentsToAuthor">
              <el-input v-model="form.commentsToAuthor" type="textarea" :rows="4" />
            </el-form-item>
            <el-form-item label="对主席的保密评语">
              <el-input v-model="form.commentsToChair" type="textarea" :rows="4" />
            </el-form-item>
            <el-button type="primary" native-type="submit" :loading="submitting">提交评审</el-button>
          </el-form>
        </aside>
      </div>
    </template>
  </section>
</template>
