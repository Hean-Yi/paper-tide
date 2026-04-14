<script setup lang="ts">
import type { FormInstance, FormItemRule, FormRules } from "element-plus";
import { ElMessage } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";

import {
  downloadPdf,
  getReviewerAssignment,
  listAgentResults,
  submitReviewReport,
  type AgentResult,
  type ReviewReportForm,
  type ReviewerAssignment
} from "../../lib/workflow-api";
import { formatDateTime, printableTrace, statusTagType, workflowLabel } from "../../lib/workflow-format";

const route = useRoute();
const router = useRouter();
const assignment = ref<ReviewerAssignment | null>(null);
const agentResults = ref<AgentResult[]>([]);
const loading = ref(false);
const submitting = ref(false);
const reviewFormRef = ref<FormInstance>();
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

async function loadAssignment() {
  loading.value = true;
  try {
    assignment.value = await getReviewerAssignment(assignmentId.value);
    agentResults.value = await listAgentResults(assignment.value.manuscriptId, assignment.value.versionId);
  } finally {
    loading.value = false;
  }
}

async function downloadCurrentPdf() {
  if (!assignment.value) {
    return;
  }
  const blob = await downloadPdf(assignment.value.manuscriptId, assignment.value.versionId);
  const url = URL.createObjectURL(blob);
  window.open(url, "_blank", "noopener");
  URL.revokeObjectURL(url);
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
      <el-button @click="router.push('/reviewer/assignments')">Back to assignments</el-button>
    </div>

    <el-skeleton v-if="loading && !assignment" :rows="6" animated />
    <template v-else-if="assignment">
      <el-descriptions title="Assignment" :column="2" border>
        <el-descriptions-item label="Title">{{ assignment.title }}</el-descriptions-item>
        <el-descriptions-item label="Status">
          <el-tag :type="statusTagType(assignment.taskStatus)">{{ workflowLabel(assignment.taskStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Version">v{{ assignment.versionNo }}</el-descriptions-item>
        <el-descriptions-item label="Deadline">{{ formatDateTime(assignment.deadlineAt) }}</el-descriptions-item>
        <el-descriptions-item label="Keywords">{{ assignment.keywords || "None" }}</el-descriptions-item>
        <el-descriptions-item label="PDF">
          <el-button v-if="assignment.pdfFileName" link @click="downloadCurrentPdf">{{ assignment.pdfFileName }}</el-button>
          <span v-else>Missing</span>
        </el-descriptions-item>
        <el-descriptions-item label="Abstract" :span="2">{{ assignment.abstractText }}</el-descriptions-item>
      </el-descriptions>

      <section class="agent-trace-panel">
        <div class="agent-trace-header">
          <div>
            <p class="eyebrow">Agent Trace</p>
            <h2>Review assist analysis</h2>
          </div>
          <el-tag type="info">Redacted</el-tag>
        </div>
        <el-alert
          v-if="!agentResults.length"
          title="No agent assistance is available for this version."
          type="info"
          :closable="false"
        />
        <article v-for="result in agentResults" :key="result.resultId" class="trace-entry">
          <div class="trace-entry-heading">
            <strong>{{ workflowLabel(result.resultType) }}</strong>
            <el-tag :type="statusTagType(result.resultType)">Reviewer safe</el-tag>
          </div>
          <pre class="json-block">{{ printableTrace(result.redactedResult) }}</pre>
        </article>
      </section>

      <el-form
        ref="reviewFormRef"
        class="workflow-form"
        :model="form"
        :rules="reviewRules"
        label-position="top"
        @submit.prevent="submitReport"
      >
        <div class="score-grid">
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
    </template>
  </section>
</template>
