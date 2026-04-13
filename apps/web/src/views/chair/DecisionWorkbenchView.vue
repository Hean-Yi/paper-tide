<script setup lang="ts">
import { ElMessage } from "element-plus";
import { onMounted, ref } from "vue";

import {
  assignReviewer,
  decide,
  listAgentResults,
  listDecisionWorkbench,
  markOverdue,
  triggerConflictAnalysis,
  type AgentResult,
  type DecisionWorkbenchItem
} from "../../lib/workflow-api";
import { formatDateTime, printableTrace, statusTagType, workflowLabel } from "../../lib/workflow-format";

const loading = ref(false);
const rows = ref<DecisionWorkbenchItem[]>([]);
const agentResults = ref<Record<number, AgentResult[]>>({});
const assignDialogOpen = ref(false);
const decisionDialogOpen = ref(false);
const assignForm = ref({ roundId: 0, reviewerId: 1002, deadlineAt: "" });
const decisionForm = ref({
  manuscriptId: 0,
  versionId: 0,
  roundId: 0,
  decisionCode: "MINOR_REVISION",
  decisionReason: ""
});

onMounted(loadWorkbench);

async function loadWorkbench() {
  loading.value = true;
  try {
    rows.value = await listDecisionWorkbench();
    const entries = await Promise.all(rows.value.map(async (row) => [
      row.roundId,
      await listAgentResults(row.manuscriptId, row.versionId)
    ] as const));
    agentResults.value = Object.fromEntries(entries);
  } finally {
    loading.value = false;
  }
}

function openAssign(row: DecisionWorkbenchItem) {
  assignForm.value = {
    roundId: row.roundId,
    reviewerId: 1002,
    deadlineAt: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString()
  };
  assignDialogOpen.value = true;
}

async function submitAssign() {
  await assignReviewer(assignForm.value.roundId, assignForm.value.reviewerId, assignForm.value.deadlineAt);
  assignDialogOpen.value = false;
  ElMessage.success("Reviewer assigned.");
  await loadWorkbench();
}

async function overdue(assignmentId: number) {
  await markOverdue(assignmentId);
  ElMessage.success("Assignment marked overdue.");
  await loadWorkbench();
}

async function conflict(row: DecisionWorkbenchItem) {
  await triggerConflictAnalysis(row.roundId);
  ElMessage.success("Conflict analysis requested.");
}

function openDecision(row: DecisionWorkbenchItem) {
  decisionForm.value = {
    manuscriptId: row.manuscriptId,
    versionId: row.versionId,
    roundId: row.roundId,
    decisionCode: "MINOR_REVISION",
    decisionReason: ""
  };
  decisionDialogOpen.value = true;
}

async function submitDecision() {
  await decide(decisionForm.value);
  decisionDialogOpen.value = false;
  ElMessage.success("Decision submitted.");
  await loadWorkbench();
}

</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">Chair</p>
        <h1>Decision workbench</h1>
        <p class="body">Review round status, conflict checks, and agent evidence before a decision.</p>
      </div>
      <el-button @click="loadWorkbench">Refresh</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" row-key="roundId" empty-text="No active review rounds.">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expanded-panel">
            <el-descriptions title="Round details" :column="3" border>
              <el-descriptions-item label="Assignments">{{ row.assignmentCount }}</el-descriptions-item>
              <el-descriptions-item label="Submitted reviews">{{ row.submittedReviewCount }}</el-descriptions-item>
              <el-descriptions-item label="Conflicts">{{ row.conflictCount }}</el-descriptions-item>
              <el-descriptions-item label="Deadline">{{ formatDateTime(row.deadlineAt) }}</el-descriptions-item>
              <el-descriptions-item label="Last decision">{{ workflowLabel(row.lastDecisionCode) }}</el-descriptions-item>
            </el-descriptions>

            <h2>Assignments</h2>
            <el-table :data="row.assignments" size="small">
              <el-table-column prop="assignmentId" label="Assignment" width="120" />
              <el-table-column prop="reviewerId" label="Reviewer" width="120" />
              <el-table-column prop="taskStatus" label="Status" width="140">
                <template #default="{ row: assignment }">
                  <el-tag :type="statusTagType(assignment.taskStatus)">{{ workflowLabel(assignment.taskStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="deadlineAt" label="Deadline" min-width="170">
                <template #default="{ row: assignment }">{{ formatDateTime(assignment.deadlineAt) }}</template>
              </el-table-column>
              <el-table-column label="Actions" width="180">
                <template #default="{ row: assignment }">
                  <el-button size="small" @click="overdue(assignment.assignmentId)">Mark overdue</el-button>
                </template>
              </el-table-column>
            </el-table>

            <h2>Agent results</h2>
            <el-alert
              v-if="!agentResults[row.roundId]?.length"
              title="No agent results yet."
              type="info"
              :closable="false"
            />
            <article v-for="result in agentResults[row.roundId]" :key="result.resultId" class="trace-entry">
              <div class="trace-entry-heading">
                <strong>{{ workflowLabel(result.resultType) }}</strong>
                <el-tag :type="statusTagType(result.resultType)">Raw</el-tag>
              </div>
              <pre class="json-block">{{ printableTrace(result.rawResult || result.redactedResult) }}</pre>
            </article>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="roundId" label="Round" width="100" />
      <el-table-column prop="title" label="Title" min-width="220" />
      <el-table-column prop="roundStatus" label="Round status" width="150">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.roundStatus)">{{ workflowLabel(row.roundStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentStatus" label="Manuscript status" width="170">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.currentStatus)">{{ workflowLabel(row.currentStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Counts" width="180">
        <template #default="{ row }">
          {{ row.submittedReviewCount }}/{{ row.assignmentCount }} reviews, {{ row.conflictCount }} conflicts
        </template>
      </el-table-column>
      <el-table-column label="Actions" width="360">
        <template #default="{ row }">
          <div class="action-row">
            <el-button size="small" @click="openAssign(row)">Assign reviewer</el-button>
            <el-button size="small" @click="conflict(row)">Conflict analysis</el-button>
            <el-button size="small" type="primary" @click="openDecision(row)">Submit decision</el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="No active review rounds." />
      </template>
    </el-table>

    <section class="agent-trace-panel">
      <div class="agent-trace-header">
        <div>
          <p class="eyebrow">Agent Trace</p>
          <h2>Raw agent results</h2>
        </div>
        <el-tag type="warning">Chair only</el-tag>
      </div>
      <el-alert
        v-if="!Object.values(agentResults).some((results) => results.length)"
        title="No raw agent results are available for active rounds."
        type="info"
        :closable="false"
      />
      <template v-for="row in rows" :key="row.roundId">
        <article v-for="result in agentResults[row.roundId]" :key="result.resultId" class="trace-entry">
          <div class="trace-entry-heading">
            <strong>{{ workflowLabel(result.resultType) }}</strong>
            <span>Round {{ row.roundNo }} · Manuscript {{ row.manuscriptId }}</span>
          </div>
          <pre class="json-block">{{ printableTrace(result.rawResult || result.redactedResult) }}</pre>
        </article>
      </template>
    </section>

    <el-dialog v-model="assignDialogOpen" title="Assign reviewer" width="520px">
      <el-form label-position="top">
        <el-form-item label="Reviewer id">
          <el-input-number v-model="assignForm.reviewerId" :min="1" />
        </el-form-item>
        <el-form-item label="Deadline">
          <el-input v-model="assignForm.deadlineAt" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogOpen = false">Cancel</el-button>
        <el-button type="primary" @click="submitAssign">Assign</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="decisionDialogOpen" title="Submit decision" width="560px">
      <el-form label-position="top">
        <el-form-item label="Decision">
          <el-select v-model="decisionForm.decisionCode">
            <el-option label="Accept" value="ACCEPT" />
            <el-option label="Minor revision" value="MINOR_REVISION" />
            <el-option label="Major revision" value="MAJOR_REVISION" />
            <el-option label="Reject" value="REJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="Reason">
          <el-input v-model="decisionForm.decisionReason" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="decisionDialogOpen = false">Cancel</el-button>
        <el-button type="primary" @click="submitDecision">Submit decision</el-button>
      </template>
    </el-dialog>
  </section>
</template>
