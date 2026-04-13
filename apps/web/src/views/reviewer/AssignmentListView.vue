<script setup lang="ts">
import { ElMessage } from "element-plus";
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";

import {
  acceptAssignment,
  declineAssignment,
  listReviewerAssignments,
  type ReviewerAssignment
} from "../../lib/workflow-api";

const router = useRouter();
const loading = ref(false);
const assignments = ref<ReviewerAssignment[]>([]);
const declineDialogOpen = ref(false);
const declineForm = ref({ assignmentId: 0, reason: "", conflictDeclared: false });

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
  declineForm.value = { assignmentId: row.assignmentId, reason: "", conflictDeclared: false };
  declineDialogOpen.value = true;
}

async function submitDecline() {
  await declineAssignment(
    declineForm.value.assignmentId,
    declineForm.value.reason,
    declineForm.value.conflictDeclared
  );
  declineDialogOpen.value = false;
  ElMessage.success("Assignment declined.");
  await loadAssignments();
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
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
          <el-tag>{{ row.taskStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deadlineAt" label="Deadline" min-width="180" />
      <el-table-column prop="recommendation" label="Recommendation" width="170" />
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
    </el-table>

    <el-dialog v-model="declineDialogOpen" title="Decline assignment" width="520px">
      <el-form label-position="top">
        <el-form-item label="Reason">
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
