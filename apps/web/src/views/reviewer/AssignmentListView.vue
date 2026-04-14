<script setup lang="ts">
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";

import {
  acceptAssignment,
  declineAssignment,
  listReviewerAssignments,
  type ReviewerAssignment
} from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";

const router = useRouter();
const loading = ref(false);
const assignments = ref<ReviewerAssignment[]>([]);
const declineDialogOpen = ref(false);
const declineFormRef = ref<FormInstance>();
const declineForm = reactive({ assignmentId: 0, reason: "", conflictDeclared: false });
const declineRules: FormRules = {
  reason: [{ required: true, message: "Reason is required", trigger: "blur" }]
};

onMounted(loadAssignments);

async function loadAssignments() {
  loading.value = true;
  try {
    assignments.value = await listReviewerAssignments();
  } finally {
    loading.value = false;
  }
}

async function accept(row: ReviewerAssignment) {
  await acceptAssignment(row.assignmentId);
  ElMessage.success("Assignment accepted.");
  await loadAssignments();
}

function openDecline(row: ReviewerAssignment) {
  Object.assign(declineForm, { assignmentId: row.assignmentId, reason: "", conflictDeclared: false });
  declineDialogOpen.value = true;
}

async function submitDecline() {
  const valid = await declineFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      "Declining returns this assignment to the chair and cannot be undone from this screen. Continue?",
      "Confirm decline",
      { type: "warning", confirmButtonText: "Decline" }
    );
  } catch {
    return;
  }
  await declineAssignment(
    declineForm.assignmentId,
    declineForm.reason,
    declineForm.conflictDeclared
  );
  declineDialogOpen.value = false;
  ElMessage.success("Assignment declined.");
  await loadAssignments();
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">Reviewer</p>
        <h1>Review assignments</h1>
        <p class="body">Accept, decline, or open a paper assigned to you.</p>
      </div>
      <el-button @click="loadAssignments">Refresh</el-button>
    </div>

    <el-table v-loading="loading" :data="assignments" empty-text="No review assignments.">
      <el-table-column prop="assignmentId" label="Assignment" width="120" />
      <el-table-column prop="title" label="Title" min-width="220" />
      <el-table-column prop="taskStatus" label="Status" width="140">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.taskStatus)">{{ workflowLabel(row.taskStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deadlineAt" label="Deadline" min-width="180">
        <template #default="{ row }">{{ formatDateTime(row.deadlineAt) }}</template>
      </el-table-column>
      <el-table-column prop="recommendation" label="Recommendation" width="170">
        <template #default="{ row }">{{ workflowLabel(row.recommendation) }}</template>
      </el-table-column>
      <el-table-column label="Actions" width="300">
        <template #default="{ row }">
          <div class="action-row">
            <el-button size="small" @click="accept(row)">Accept</el-button>
            <el-button size="small" @click="openDecline(row)">Decline</el-button>
            <el-button size="small" type="primary" @click="router.push(`/reviewer/reviews/${row.assignmentId}`)">
              Open review
            </el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="No review assignments." />
      </template>
    </el-table>

    <el-dialog v-model="declineDialogOpen" title="Decline assignment" width="520px">
      <el-form ref="declineFormRef" :model="declineForm" :rules="declineRules" label-position="top">
        <el-form-item label="Reason" prop="reason">
          <el-input v-model="declineForm.reason" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="declineForm.conflictDeclared">I have a conflict with this manuscript.</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="declineDialogOpen = false">Cancel</el-button>
        <el-button type="primary" @click="submitDecline">Decline</el-button>
      </template>
    </el-dialog>
  </section>
</template>
