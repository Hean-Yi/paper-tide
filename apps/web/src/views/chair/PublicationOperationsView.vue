<script setup lang="ts">
import { ElMessage } from "element-plus";
import { onMounted, ref } from "vue";

import { useApiError } from "../../composables/useApiError";
import { useAsyncAction } from "../../composables/useAsyncAction";
import {
  getPublicationOperations,
  proceedingsExportDownloadMetadata,
  type ProceedingsExportDownloadMetadataResponse,
  type PublicationOperations
} from "../../lib/workflow-api";
import { formatDateTime, formatFileSize, statusTagType, workflowLabel } from "../../lib/workflow-format";

const actions = useAsyncAction();
const { showApiError } = useApiError();
const loading = ref(false);
const conferenceId = ref(0);
const operations = ref<PublicationOperations | null>(null);
const downloadMetadata = ref<ProceedingsExportDownloadMetadataResponse | null>(null);

onMounted(loadOperations);

async function loadOperations() {
  loading.value = true;
  try {
    operations.value = await getPublicationOperations(Number(conferenceId.value));
  } catch (error) {
    showApiError(error, "Publication operations could not be loaded.");
  } finally {
    loading.value = false;
  }
}

async function exportMetadata(exportBatchId: number) {
  await actions.run(`export:${exportBatchId}`, async () => {
    try {
      downloadMetadata.value = await proceedingsExportDownloadMetadata(exportBatchId);
      ElMessage.success("Proceedings export metadata generated.");
      await loadOperations();
    } catch (error) {
      showApiError(error, "Proceedings export metadata could not be generated.");
    }
  });
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Chair operations</p>
        <h1>Publication and communication</h1>
        <p class="body">Review communication history, offline review imports, camera-ready files, and proceedings exports.</p>
      </div>
      <div class="action-row">
        <el-input-number v-model="conferenceId" :min="0" />
        <el-button :loading="loading" @click="loadOperations">Refresh</el-button>
      </div>
    </div>

    <el-alert
      v-if="downloadMetadata"
      :title="`${downloadMetadata.downloadFileName} is ready with ${downloadMetadata.paperCount} papers.`"
      type="success"
      :closable="false"
    />

    <section class="workflow-section">
      <h2>Communication console</h2>
      <div class="two-column-grid">
        <el-table v-loading="loading" :data="operations?.emailTemplates ?? []" empty-text="No email templates.">
          <el-table-column prop="templateKey" label="Template key" min-width="180" />
          <el-table-column prop="activeVersionId" label="Active version" width="150" />
          <el-table-column label="Created" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <el-table v-loading="loading" :data="operations?.emailHistory ?? []" empty-text="No email history.">
          <el-table-column prop="templateKey" label="Template" width="160" />
          <el-table-column prop="recipientEmail" label="Recipient" min-width="220" />
          <el-table-column label="Status" width="130">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.deliveryStatus)">{{ workflowLabel(row.deliveryStatus) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="workflow-section">
      <h2>Offline reviews</h2>
      <el-table v-loading="loading" :data="operations?.offlineReviewImports ?? []" empty-text="No offline review imports.">
        <el-table-column prop="batchId" label="Batch" width="90" />
        <el-table-column prop="assignmentId" label="Assignment" width="120" />
        <el-table-column prop="reviewerId" label="Reviewer" width="110" />
        <el-table-column label="Rows" width="150">
          <template #default="{ row }">{{ row.validRowCount }} / {{ row.rowCount }} valid</template>
        </el-table-column>
        <el-table-column label="Status" width="140">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.batchStatus)">{{ workflowLabel(row.batchStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="workflow-section">
      <h2>Camera-ready files</h2>
      <el-table v-loading="loading" :data="operations?.cameraReadyFiles ?? []" empty-text="No camera-ready files.">
        <el-table-column prop="manuscriptId" label="Manuscript" width="120" />
        <el-table-column prop="fileName" label="File" min-width="220" />
        <el-table-column label="Size" width="130">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="Status" width="140">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.fileStatus)">{{ workflowLabel(row.fileStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="workflow-section">
      <h2>Publication metadata</h2>
      <el-table v-loading="loading" :data="operations?.publicationMetadata ?? []" empty-text="No publication metadata.">
        <el-table-column prop="manuscriptId" label="Manuscript" width="120" />
        <el-table-column prop="doi" label="DOI" min-width="200" />
        <el-table-column prop="indexKeywords" label="Index keywords" min-width="220" />
        <el-table-column label="Status" width="180">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.publicationStatus)">{{ workflowLabel(row.publicationStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="workflow-section">
      <h2>Proceedings exports</h2>
      <el-table v-loading="loading" :data="operations?.proceedingsExports ?? []" empty-text="No proceedings exports.">
        <el-table-column prop="exportName" label="Export" min-width="220" />
        <el-table-column prop="paperCount" label="Papers" width="100" />
        <el-table-column label="Status" width="140">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.exportStatus)">{{ workflowLabel(row.exportStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="170">
          <template #default="{ row }">
            <el-button
              size="small"
              :disabled="row.exportStatus !== 'PREVIEWED' && row.exportStatus !== 'EXPORTED'"
              :loading="actions.isPending(`export:${row.exportBatchId}`)"
              @click="exportMetadata(row.exportBatchId)"
            >
              Export metadata
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>
