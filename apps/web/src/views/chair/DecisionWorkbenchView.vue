<script setup lang="ts">
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage } from "element-plus";
import { onMounted, reactive, ref } from "vue";

import { useApiError } from "../../composables/useApiError";
import { useAsyncAction } from "../../composables/useAsyncAction";
import {
  assignReviewer,
  decide,
  listDecisionWorkbench,
  markOverdue,
  triggerConflictAnalysis,
  type AnalysisProjectionResponse,
  type DecisionWorkbenchItem
} from "../../lib/workflow-api";
import { formatDateTime, printableTrace, statusTagType, workflowLabel } from "../../lib/workflow-format";

const loading = ref(false);
const rows = ref<DecisionWorkbenchItem[]>([]);
const actions = useAsyncAction();
const { showApiError } = useApiError();
const assignDialogOpen = ref(false);
const decisionDialogOpen = ref(false);
const assignFormRef = ref<FormInstance>();
const assignForm = reactive({ roundId: 0, reviewerId: 1002, deadlineAt: "" });
const decisionFormRef = ref<FormInstance>();
const decisionForm = reactive({
  manuscriptId: 0,
  versionId: 0,
  roundId: 0,
  decisionCode: "MINOR_REVISION",
  decisionReason: ""
});
const assignRules: FormRules = {
  reviewerId: [{ required: true, message: "Reviewer id is required", trigger: "blur" }],
  deadlineAt: [{ required: true, message: "Deadline is required", trigger: "change" }]
};
const decisionRules: FormRules = {
  decisionCode: [{ required: true, message: "Decision is required", trigger: "change" }],
  decisionReason: [{ required: true, message: "Reason is required", trigger: "blur" }]
};

onMounted(loadWorkbench);

async function loadWorkbench() {
  loading.value = true;
  try {
    rows.value = await listDecisionWorkbench();
  } catch (error) {
    showApiError(error, "Decision workbench could not be loaded.");
  } finally {
    loading.value = false;
  }
}

function openAssign(row: DecisionWorkbenchItem) {
  Object.assign(assignForm, {
    roundId: row.roundId,
    reviewerId: 1002,
    deadlineAt: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString()
  });
  assignDialogOpen.value = true;
}

async function submitAssign() {
  const valid = await assignFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  await actions.run("assign-reviewer", async () => {
    try {
      await assignReviewer(assignForm.roundId, assignForm.reviewerId, assignForm.deadlineAt);
      assignDialogOpen.value = false;
      ElMessage.success("Reviewer assigned.");
      await loadWorkbench();
    } catch (error) {
      showApiError(error, "Reviewer could not be assigned.");
    }
  });
}

async function overdue(assignmentId: number) {
  await actions.run(`overdue:${assignmentId}`, async () => {
    try {
      await markOverdue(assignmentId);
      ElMessage.success("Assignment marked overdue.");
      await loadWorkbench();
    } catch (error) {
      showApiError(error, "Assignment could not be marked overdue.");
    }
  });
}

async function conflict(row: DecisionWorkbenchItem) {
  await triggerConflictAnalysis(row.roundId);
  ElMessage.success("Conflict analysis requested.");
  await loadWorkbench();
}

function conflictProjections(row: DecisionWorkbenchItem): AnalysisProjectionResponse[] {
  return row.conflictProjections ?? [];
}

function openDecision(row: DecisionWorkbenchItem) {
  Object.assign(decisionForm, {
    manuscriptId: row.manuscriptId,
    versionId: row.versionId,
    roundId: row.roundId,
    decisionCode: "MINOR_REVISION",
    decisionReason: ""
  });
  decisionDialogOpen.value = true;
}

async function submitDecision() {
  const valid = await decisionFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  await actions.run("submit-decision", async () => {
    try {
      await decide(decisionForm);
      decisionDialogOpen.value = false;
      ElMessage.success("Decision submitted.");
      await loadWorkbench();
    } catch (error) {
      showApiError(error, "Decision could not be submitted.");
    }
  });
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
      <el-button :loading="loading" @click="loadWorkbench">Refresh</el-button>
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
                  <el-button
                    size="small"
                    :loading="actions.isPending(`overdue:${assignment.assignmentId}`)"
                    @click="overdue(assignment.assignmentId)"
                  >
                    Mark overdue
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <h2>Conflict analysis projections</h2>
            <el-alert
              v-if="!conflictProjections(row).length"
              title="No conflict analysis projections yet."
              type="info"
              :closable="false"
            />
            <article v-for="projection in conflictProjections(row)" :key="projection.projectionId" class="trace-entry">
              <div class="trace-entry-heading">
                <strong>{{ workflowLabel(projection.analysisType) }}</strong>
                <el-tag :type="statusTagType(projection.businessStatus)">
                  {{ workflowLabel(projection.businessStatus) }}
                </el-tag>
              </div>
              <p v-if="projection.summaryText" class="body">{{ projection.summaryText }}</p>
              <pre class="json-block">{{ printableTrace(projection.redactedResult) }}</pre>
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
          <h2>Agent result coverage</h2>
        </div>
        <el-tag type="warning">Chair only</el-tag>
      </div>
      <el-alert
        v-if="!rows.some((row) => conflictProjections(row).length)"
        title="No conflict analysis projections are available for active rounds."
        type="info"
        :closable="false"
      />
      <template v-for="row in rows" :key="row.roundId">
        <article v-if="conflictProjections(row).length" class="trace-entry">
          <div class="trace-entry-heading">
            <strong>Round {{ row.roundNo }} · Manuscript {{ row.manuscriptId }}</strong>
            <span>{{ conflictProjections(row).length }} projection{{ conflictProjections(row).length === 1 ? "" : "s" }}</span>
          </div>
          <div class="action-row">
            <el-tag
              v-for="projection in conflictProjections(row)"
              :key="projection.projectionId"
              :type="statusTagType(projection.businessStatus)"
            >
              {{ workflowLabel(projection.analysisType) }}
            </el-tag>
          </div>
        </article>
      </template>
    </section>

    <el-dialog v-model="assignDialogOpen" title="Assign reviewer" width="520px">
      <el-form ref="assignFormRef" :model="assignForm" :rules="assignRules" label-position="top">
        <el-form-item label="Reviewer id" prop="reviewerId">
          <el-input-number v-model="assignForm.reviewerId" :min="1" />
        </el-form-item>
        <el-form-item label="Deadline" prop="deadlineAt">
          <el-date-picker
            v-model="assignForm.deadlineAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss[Z]"
            placeholder="Select deadline"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="actions.isPending('assign-reviewer')" @click="assignDialogOpen = false">Cancel</el-button>
        <el-button type="primary" :loading="actions.isPending('assign-reviewer')" @click="submitAssign">Assign</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="decisionDialogOpen" title="Submit decision" width="560px">
      <el-form ref="decisionFormRef" :model="decisionForm" :rules="decisionRules" label-position="top">
        <el-form-item label="Decision" prop="decisionCode">
          <el-select v-model="decisionForm.decisionCode">
            <el-option label="Accept" value="ACCEPT" />
            <el-option label="Minor revision" value="MINOR_REVISION" />
            <el-option label="Major revision" value="MAJOR_REVISION" />
            <el-option label="Reject" value="REJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="Reason" prop="decisionReason">
          <el-input v-model="decisionForm.decisionReason" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="actions.isPending('submit-decision')" @click="decisionDialogOpen = false">Cancel</el-button>
        <el-button type="primary" :loading="actions.isPending('submit-decision')" @click="submitDecision">Submit decision</el-button>
      </template>
    </el-dialog>
  </section>
</template>
