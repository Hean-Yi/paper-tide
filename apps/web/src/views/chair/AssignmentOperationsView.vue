<script setup lang="ts">
import { ElMessage } from "element-plus";
import { onMounted, reactive, ref } from "vue";

import { useApiError } from "../../composables/useApiError";
import { useAsyncAction } from "../../composables/useAsyncAction";
import {
  confirmAssignmentProposalDrafts,
  confirmMatchingScoreImport,
  confirmReviewerInvitationImport,
  getAssignmentOperations,
  previewMatchingScoreImport,
  previewReviewerInvitationImport,
  type AssignmentOperations,
  type ImportBatchOperationRow,
  type ImportPreviewResponse
} from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";

const actions = useAsyncAction();
const { showApiError } = useApiError();
const loading = ref(false);
const conferenceId = ref(0);
const operations = ref<AssignmentOperations | null>(null);
const lastPreview = ref<ImportPreviewResponse | null>(null);
const invitationImport = reactive({
  csvText: "reviewerId,invitationMessage,expiresAt\n1002,Please join,2099-01-01T00:00:00Z"
});
const matchingImport = reactive({
  manuscriptId: 0,
  csvText: "reviewerId,scoreSource,matchingScore,rationale\n1002,TPMS_IMPORT,0.80,Relevant topic match"
});

onMounted(loadOperations);

async function loadOperations() {
  loading.value = true;
  try {
    operations.value = await getAssignmentOperations(Number(conferenceId.value));
  } catch (error) {
    showApiError(error, "Assignment operations could not be loaded.");
  } finally {
    loading.value = false;
  }
}

async function previewInvitations() {
  await actions.run("preview-invitations", async () => {
    try {
      lastPreview.value = await previewReviewerInvitationImport(Number(conferenceId.value), invitationImport.csvText);
      ElMessage.success("Invitation import preview created.");
      await loadOperations();
    } catch (error) {
      showApiError(error, "Invitation import could not be previewed.");
    }
  });
}

async function previewMatchingScores() {
  await actions.run("preview-matching-scores", async () => {
    try {
      lastPreview.value = await previewMatchingScoreImport(Number(matchingImport.manuscriptId), matchingImport.csvText);
      ElMessage.success("Matching-score import preview created.");
      await loadOperations();
    } catch (error) {
      showApiError(error, "Matching-score import could not be previewed.");
    }
  });
}

async function confirmImport(row: ImportBatchOperationRow) {
  await actions.run(`confirm-import:${row.batchId}`, async () => {
    try {
      if (row.importType === "REVIEWER_INVITATIONS") {
        await confirmReviewerInvitationImport(row.batchId);
      } else if (row.importType === "MATCHING_SCORES") {
        await confirmMatchingScoreImport(row.batchId);
      }
      ElMessage.success("Import applied.");
      await loadOperations();
    } catch (error) {
      showApiError(error, "Import could not be confirmed.");
    }
  });
}

async function confirmProposal(bundleId: number) {
  await actions.run(`confirm-proposal:${bundleId}`, async () => {
    try {
      const result = await confirmAssignmentProposalDrafts(bundleId);
      ElMessage.success(`${result.createdCount} assignment drafts created.`);
      await loadOperations();
    } catch (error) {
      showApiError(error, "Assignment proposal could not be confirmed.");
    }
  });
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Chair operations</p>
        <h1>Assignment and COI</h1>
        <p class="body">Manage reviewer invitations, delegations, matching imports, and advisory proposals.</p>
      </div>
      <div class="action-row">
        <el-input-number v-model="conferenceId" :min="0" />
        <el-button :loading="loading" @click="loadOperations">Refresh</el-button>
      </div>
    </div>

    <section class="workflow-section">
      <h2>Bulk imports</h2>
      <div class="two-column-grid">
        <el-form class="workflow-form" label-position="top" @submit.prevent="previewInvitations">
          <el-form-item label="Reviewer invitation CSV">
            <el-input v-model="invitationImport.csvText" type="textarea" :rows="5" />
          </el-form-item>
          <el-button type="primary" native-type="submit" :loading="actions.isPending('preview-invitations')">
            Preview invitations
          </el-button>
        </el-form>
        <el-form class="workflow-form" label-position="top" @submit.prevent="previewMatchingScores">
          <el-form-item label="Manuscript id">
            <el-input-number v-model="matchingImport.manuscriptId" :min="0" />
          </el-form-item>
          <el-form-item label="Matching-score CSV">
            <el-input v-model="matchingImport.csvText" type="textarea" :rows="5" />
          </el-form-item>
          <el-button type="primary" native-type="submit" :loading="actions.isPending('preview-matching-scores')">
            Preview matching scores
          </el-button>
        </el-form>
      </div>
      <el-alert
        v-if="lastPreview"
        :title="`${lastPreview.validRowCount} valid rows, ${lastPreview.errorCount} errors in batch ${lastPreview.batchId}`"
        type="info"
        :closable="false"
      />
    </section>

    <section class="workflow-section">
      <h2>Import batches</h2>
      <el-table v-loading="loading" :data="operations?.importBatches ?? []" empty-text="No import batches.">
        <el-table-column prop="batchId" label="Batch" width="90" />
        <el-table-column prop="importType" label="Type" min-width="180" />
        <el-table-column label="Status" width="150">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.batchStatus)">{{ workflowLabel(row.batchStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Rows" width="160">
          <template #default="{ row }">{{ row.validRowCount }} / {{ row.rowCount }} valid</template>
        </el-table-column>
        <el-table-column label="Created" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="Actions" width="150">
          <template #default="{ row }">
            <el-button
              size="small"
              :disabled="row.batchStatus !== 'PREVIEWED'"
              :loading="actions.isPending(`confirm-import:${row.batchId}`)"
              @click="confirmImport(row)"
            >
              Confirm import
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="workflow-section">
      <h2>Assignment proposals</h2>
      <el-table v-loading="loading" :data="operations?.assignmentProposals ?? []" empty-text="No proposal bundles.">
        <el-table-column prop="proposalName" label="Proposal" min-width="200" />
        <el-table-column prop="manuscriptId" label="Manuscript" width="120" />
        <el-table-column prop="proposalCount" label="Candidates" width="120" />
        <el-table-column label="Status" width="150">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.bundleStatus)">{{ workflowLabel(row.bundleStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="150">
          <template #default="{ row }">
            <el-button
              size="small"
              :disabled="row.bundleStatus !== 'PROPOSED'"
              :loading="actions.isPending(`confirm-proposal:${row.bundleId}`)"
              @click="confirmProposal(row.bundleId)"
            >
              Confirm drafts
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="workflow-section">
      <h2>Reviewer operations</h2>
      <div class="two-column-grid">
        <el-table :data="operations?.reviewerInvitations ?? []" empty-text="No reviewer invitations.">
          <el-table-column prop="reviewerId" label="Reviewer" width="110" />
          <el-table-column label="Status">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.invitationStatus)">{{ workflowLabel(row.invitationStatus) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-table :data="operations?.externalDelegations ?? []" empty-text="No external delegations.">
          <el-table-column prop="externalEmail" label="External reviewer" min-width="220" />
          <el-table-column label="Status" width="140">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.delegationStatus)">{{ workflowLabel(row.delegationStatus) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="workflow-section">
      <h2>Matching scores</h2>
      <el-table :data="operations?.matchingScores ?? []" empty-text="No matching scores.">
        <el-table-column prop="manuscriptId" label="Manuscript" width="120" />
        <el-table-column prop="reviewerId" label="Reviewer" width="110" />
        <el-table-column prop="scoreSource" label="Source" width="160" />
        <el-table-column prop="matchingScore" label="Score" width="100" />
        <el-table-column prop="rationale" label="Rationale" min-width="220" />
      </el-table>
    </section>
  </section>
</template>
