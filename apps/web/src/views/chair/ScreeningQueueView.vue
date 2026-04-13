<script setup lang="ts">
import { ElMessage } from "element-plus";
import { onMounted, ref } from "vue";

import {
  createAgentTask,
  createReviewRound,
  decide,
  downloadPdf,
  listScreeningQueue,
  startScreening,
  type ScreeningQueueItem
} from "../../lib/workflow-api";

const loading = ref(false);
const queue = ref<ScreeningQueueItem[]>([]);
const roundForm = ref({ manuscriptId: 0, versionId: 0, deadlineAt: "" });
const roundDialogOpen = ref(false);
const deskRejectForm = ref({ manuscriptId: 0, versionId: 0, roundId: 0, decisionReason: "" });
const deskRejectDialogOpen = ref(false);

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
}

function openRound(row: ScreeningQueueItem) {
  roundForm.value = {
    manuscriptId: row.manuscriptId,
    versionId: row.versionId,
    deadlineAt: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString()
  };
  roundDialogOpen.value = true;
}

async function submitRound() {
  await createReviewRound({
    ...roundForm.value,
    assignmentStrategy: "REALLOCATE_REVIEWERS",
    screeningRequired: true
  });
  roundDialogOpen.value = false;
  ElMessage.success("Review round created.");
  await loadQueue();
}

function openDeskReject(row: ScreeningQueueItem) {
  deskRejectForm.value = {
    manuscriptId: row.manuscriptId,
    versionId: row.versionId,
    roundId: row.currentRoundNo,
    decisionReason: ""
  };
  deskRejectDialogOpen.value = true;
}

async function submitDeskReject() {
  await decide({
    manuscriptId: deskRejectForm.value.manuscriptId,
    versionId: deskRejectForm.value.versionId,
    roundId: deskRejectForm.value.roundId,
    decisionCode: "DESK_REJECT",
    decisionReason: deskRejectForm.value.decisionReason
  });
  deskRejectDialogOpen.value = false;
  ElMessage.success("Desk reject recorded.");
  await loadQueue();
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
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
          <el-tag>{{ row.currentStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="blindMode" label="Blind mode" width="150" />
      <el-table-column label="PDF" min-width="160">
        <template #default="{ row }">
          <el-button v-if="row.pdfFileName" link @click="download(row)">{{ row.pdfFileName }}</el-button>
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
    </el-table>

    <el-dialog v-model="roundDialogOpen" title="Create review round" width="520px">
      <el-form label-position="top">
        <el-form-item label="Deadline">
          <el-input v-model="roundForm.deadlineAt" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roundDialogOpen = false">Cancel</el-button>
        <el-button type="primary" @click="submitRound">Create round</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="deskRejectDialogOpen" title="Desk reject" width="520px">
      <el-form label-position="top">
        <el-form-item label="Round id">
          <el-input-number v-model="deskRejectForm.roundId" :min="0" />
        </el-form-item>
        <el-form-item label="Reason">
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
