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
  confidenceLevel: [{ required: true, message: "Confidence is required", trigger: "change" }],
  recommendation: [{ required: true, message: "Recommendation is required", trigger: "change" }],
  strengths: [requiredTrimmed("Strengths are required")],
  weaknesses: [requiredTrimmed("Weaknesses are required")],
  commentsToAuthor: [requiredTrimmed("Comments to author are required")]
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
    ElMessage.success(responseStatus === "DRAFT" ? "Review draft saved." : "Configured review submitted.");
    await loadDynamicReviewForm();
  } finally {
    dynamicSubmitting.value = false;
  }
}

function revisionAnswerPreview(revision: ReviewFormRevision): string {
  const entries = Object.entries(revision.answers ?? {});
  if (!entries.length) {
    return "No answers captured.";
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
    ElMessage.success("Review submitted.");
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
        <p class="eyebrow">Reviewer</p>
        <h1>Review editor</h1>
        <p class="body">Read the assignment details and submit one complete review report.</p>
      </div>
      <div class="header-actions">
        <el-button @click="sidePanelCollapsed = !sidePanelCollapsed">
          {{ sidePanelCollapsed ? "Show assist panel" : "Collapse assist panel" }}
        </el-button>
        <el-button @click="router.push('/reviewer/assignments')">Back to assignments</el-button>
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
                  <span>Assignment</span>
                  <strong>{{ assignment.title }}</strong>
                  <el-tag :type="statusTagType(assignment.taskStatus)">
                    {{ workflowLabel(assignment.taskStatus) }}
                  </el-tag>
                </div>
              </template>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="Title">{{ assignment.title }}</el-descriptions-item>
                <el-descriptions-item label="Status">
                  <el-tag :type="statusTagType(assignment.taskStatus)">{{ workflowLabel(assignment.taskStatus) }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="Version">v{{ assignment.versionNo }}</el-descriptions-item>
                <el-descriptions-item label="Deadline">{{ formatDateTime(assignment.deadlineAt) }}</el-descriptions-item>
                <el-descriptions-item label="Keywords">{{ assignment.keywords || "None" }}</el-descriptions-item>
                <el-descriptions-item label="Abstract">{{ assignment.abstractText }}</el-descriptions-item>
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
                <el-button :loading="dynamicSubmitting" @click="saveDynamicReview('DRAFT')">Save draft</el-button>
                <el-button type="primary" native-type="submit" :loading="dynamicSubmitting">Submit configured review</el-button>
              </div>
            </el-form>
            <section class="revision-history">
              <div class="subsection-heading">
                <h3>Review revision history</h3>
                <el-tag>{{ reviewFormRevisions.length }}</el-tag>
              </div>
              <el-empty v-if="!reviewFormRevisions.length" description="No submitted configured reviews yet." />
              <div v-else class="stacked-list">
                <el-card v-for="revision in reviewFormRevisions" :key="revision.revisionId" shadow="never">
                  <template #header>
                    Revision {{ revision.revisionNo }} · {{ formatDateTime(revision.submittedAt) }}
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
              <el-form-item label="Novelty">
                <el-input-number v-model="form.noveltyScore" :min="1" :max="5" />
              </el-form-item>
              <el-form-item label="Method">
                <el-input-number v-model="form.methodScore" :min="1" :max="5" />
              </el-form-item>
              <el-form-item label="Experiment">
                <el-input-number v-model="form.experimentScore" :min="1" :max="5" />
              </el-form-item>
              <el-form-item label="Writing">
                <el-input-number v-model="form.writingScore" :min="1" :max="5" />
              </el-form-item>
              <el-form-item label="Overall">
                <el-input-number v-model="form.overallScore" :min="1" :max="5" />
              </el-form-item>
            </div>

            <el-form-item label="Confidence" prop="confidenceLevel">
              <el-select v-model="form.confidenceLevel">
                <el-option label="Low" value="LOW" />
                <el-option label="Medium" value="MEDIUM" />
                <el-option label="High" value="HIGH" />
              </el-select>
            </el-form-item>
            <el-form-item label="Recommendation" prop="recommendation">
              <el-select v-model="form.recommendation">
                <el-option label="Accept" value="ACCEPT" />
                <el-option label="Minor revision" value="MINOR_REVISION" />
                <el-option label="Major revision" value="MAJOR_REVISION" />
                <el-option label="Reject" value="REJECT" />
              </el-select>
            </el-form-item>
            <el-form-item label="Strengths" prop="strengths">
              <el-input v-model="form.strengths" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="Weaknesses" prop="weaknesses">
              <el-input v-model="form.weaknesses" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="Comments to author" prop="commentsToAuthor">
              <el-input v-model="form.commentsToAuthor" type="textarea" :rows="4" />
            </el-form-item>
            <el-form-item label="Confidential comments to chair">
              <el-input v-model="form.commentsToChair" type="textarea" :rows="4" />
            </el-form-item>
            <el-button type="primary" native-type="submit" :loading="submitting">Submit review</el-button>
          </el-form>
        </aside>
      </div>
    </template>
  </section>
</template>
