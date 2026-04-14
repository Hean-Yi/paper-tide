<script setup lang="ts">
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, reactive, ref } from "vue";

import {
  createAgentTask,
  createReviewRound,
  decide,
  downloadPdf,
  listScreeningQueue,
  startScreening,
  type ScreeningQueueItem
} from "../../lib/workflow-api";
import { formatDateTime, formatFileSize, statusTagType, workflowLabel } from "../../lib/workflow-format";

const loading = ref(false);
const queue = ref<ScreeningQueueItem[]>([]);
const roundFormRef = ref<FormInstance>();
const roundForm = reactive({ manuscriptId: 0, versionId: 0, deadlineAt: "" });
const roundDialogOpen = ref(false);
const deskRejectFormRef = ref<FormInstance>();
const deskRejectForm = reactive({ manuscriptId: 0, versionId: 0, roundId: 0, decisionReason: "" });
const deskRejectDialogOpen = ref(false);
const roundRules: FormRules = {
  deadlineAt: [{ required: true, message: "Deadline is required", trigger: "change" }]
};
const deskRejectRules: FormRules = {
  roundId: [{ required: true, message: "Round id is required", trigger: "blur" }],
  decisionReason: [{ required: true, message: "Reason is required", trigger: "blur" }]
};

onMounted(loadQueue);

async function loadQueue() {
  loading.value = true;
  try {
    queue.value = await listScreeningQueue();
  } finally {
    loading.value = false;
  }
}

async function start(row: ScreeningQueueItem) {
  await startScreening(row.manuscriptId, row.versionId);
  ElMessage.success("Screening started.");
  await loadQueue();
}

async function triggerAgent(row: ScreeningQueueItem) {
  await createAgentTask(row.manuscriptId, row.versionId, "SCREENING_ANALYSIS");
  ElMessage.success("Screening analysis requested.");
}

async function download(row: ScreeningQueueItem) {
  const blob = await downloadPdf(row.manuscriptId, row.versionId);
  const url = URL.createObjectURL(blob);
  window.open(url, "_blank", "noopener");
  URL.revokeObjectURL(url);
}

function openRound(row: ScreeningQueueItem) {
  Object.assign(roundForm, {
    manuscriptId: row.manuscriptId,
    versionId: row.versionId,
    deadlineAt: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString()
  });
  roundDialogOpen.value = true;
}

async function submitRound() {
  const valid = await roundFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  await createReviewRound({
    ...roundForm,
    assignmentStrategy: "REALLOCATE_REVIEWERS",
    screeningRequired: true
  });
  roundDialogOpen.value = false;
  ElMessage.success("Review round created.");
  await loadQueue();
}

function openDeskReject(row: ScreeningQueueItem) {
  Object.assign(deskRejectForm, {
    manuscriptId: row.manuscriptId,
    versionId: row.versionId,
    roundId: row.currentRoundNo,
    decisionReason: ""
  });
  deskRejectDialogOpen.value = true;
}

async function submitDeskReject() {
  const valid = await deskRejectFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      "Desk reject will close this manuscript before external review. Continue?",
      "Confirm desk reject",
      { type: "warning", confirmButtonText: "Desk reject" }
    );
  } catch {
    return;
  }
  await decide({
    manuscriptId: deskRejectForm.manuscriptId,
    versionId: deskRejectForm.versionId,
    roundId: deskRejectForm.roundId,
    decisionCode: "DESK_REJECT",
    decisionReason: deskRejectForm.decisionReason
  });
  deskRejectDialogOpen.value = false;
  ElMessage.success("Desk reject recorded.");
  await loadQueue();
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">Chair</p>
        <h1>Screening queue</h1>
        <p class="body">Review new submissions before a full review round.</p>
      </div>
      <el-button @click="loadQueue">Refresh</el-button>
    </div>

    <el-table v-loading="loading" :data="queue" empty-text="No manuscripts are waiting for screening.">
      <el-table-column prop="manuscriptId" label="Manuscript" width="120" />
      <el-table-column prop="title" label="Title" min-width="220" />
      <el-table-column prop="currentStatus" label="Status" width="160">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.currentStatus)">{{ workflowLabel(row.currentStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="blindMode" label="Blind mode" width="150">
        <template #default="{ row }">{{ workflowLabel(row.blindMode) }}</template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="Submitted" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
      </el-table-column>
      <el-table-column label="PDF" min-width="160">
        <template #default="{ row }">
          <el-button v-if="row.pdfFileName" link @click="download(row)">
            {{ row.pdfFileName }} · {{ formatFileSize(row.pdfFileSize) }}
          </el-button>
          <span v-else>Missing</span>
        </template>
      </el-table-column>
      <el-table-column label="Actions" width="430">
        <template #default="{ row }">
          <div class="action-row">
            <el-button size="small" @click="start(row)">Start screening</el-button>
            <el-button size="small" @click="triggerAgent(row)">Run agent</el-button>
            <el-button size="small" @click="openRound(row)">Create round</el-button>
            <el-button size="small" type="danger" @click="openDeskReject(row)">Desk reject</el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="No manuscripts are waiting for screening." />
      </template>
    </el-table>

    <el-dialog v-model="roundDialogOpen" title="Create review round" width="520px">
      <el-form ref="roundFormRef" :model="roundForm" :rules="roundRules" label-position="top">
        <el-form-item label="Deadline" prop="deadlineAt">
          <el-date-picker
            v-model="roundForm.deadlineAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss[Z]"
            placeholder="Select deadline"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roundDialogOpen = false">Cancel</el-button>
        <el-button type="primary" @click="submitRound">Create round</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="deskRejectDialogOpen" title="Desk reject" width="520px">
      <el-form ref="deskRejectFormRef" :model="deskRejectForm" :rules="deskRejectRules" label-position="top">
        <el-form-item label="Round id" prop="roundId">
          <el-input-number v-model="deskRejectForm.roundId" :min="0" />
        </el-form-item>
        <el-form-item label="Reason" prop="decisionReason">
          <el-input v-model="deskRejectForm.decisionReason" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deskRejectDialogOpen = false">Cancel</el-button>
        <el-button type="danger" @click="submitDeskReject">Desk reject</el-button>
      </template>
    </el-dialog>
  </section>
</template>
