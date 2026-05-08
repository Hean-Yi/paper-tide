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
    showApiError(error, "出版操作加载失败。");
  } finally {
    loading.value = false;
  }
}

async function exportMetadata(exportBatchId: number) {
  await actions.run(`export:${exportBatchId}`, async () => {
    try {
      downloadMetadata.value = await proceedingsExportDownloadMetadata(exportBatchId);
      ElMessage.success("论文集导出元数据已生成。");
      await loadOperations();
    } catch (error) {
      showApiError(error, "论文集导出元数据生成失败。");
    }
  });
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">主席操作</p>
        <h1>出版与通信</h1>
        <p class="body">查看通信历史、离线评审导入、终稿文件及论文集导出。</p>
      </div>
      <div class="action-row">
        <el-input-number v-model="conferenceId" :min="0" />
        <el-button :loading="loading" @click="loadOperations">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="downloadMetadata"
        :title="`${downloadMetadata.downloadFileName} 已就绪，共 ${downloadMetadata.paperCount} 篇论文。`"
      type="success"
      :closable="false"
    />

    <section class="workflow-section">
      <h2>通信控制台</h2>
      <div class="two-column-grid">
        <el-table v-loading="loading" :data="operations?.emailTemplates ?? []" empty-text="暂无邮件模板。">
          <el-table-column prop="templateKey" label="模板标识" min-width="180" />
          <el-table-column prop="activeVersionId" label="当前版本" width="150" />
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <el-table v-loading="loading" :data="operations?.emailHistory ?? []" empty-text="暂无邮件历史。">
          <el-table-column prop="templateKey" label="模板" width="160" />
          <el-table-column prop="recipientEmail" label="收件人" min-width="220" />
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.deliveryStatus)">{{ workflowLabel(row.deliveryStatus) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="workflow-section">
      <h2>离线评审</h2>
      <el-table v-loading="loading" :data="operations?.offlineReviewImports ?? []" empty-text="暂无离线评审导入。">
        <el-table-column prop="batchId" label="批次" width="90" />
        <el-table-column prop="assignmentId" label="分配" width="120" />
        <el-table-column prop="reviewerId" label="审稿人" width="110" />
        <el-table-column label="行数" width="150">
          <template #default="{ row }">{{ row.validRowCount }} / {{ row.rowCount }} 有效</template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.batchStatus)">{{ workflowLabel(row.batchStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="workflow-section">
      <h2>终稿文件</h2>
      <el-table v-loading="loading" :data="operations?.cameraReadyFiles ?? []" empty-text="暂无终稿文件。">
        <el-table-column prop="manuscriptId" label="稿件" width="120" />
        <el-table-column prop="fileName" label="文件名" min-width="220" />
        <el-table-column label="大小" width="130">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.fileStatus)">{{ workflowLabel(row.fileStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="workflow-section">
      <h2>出版元数据</h2>
      <el-table v-loading="loading" :data="operations?.publicationMetadata ?? []" empty-text="暂无出版元数据。">
        <el-table-column prop="manuscriptId" label="稿件" width="120" />
        <el-table-column prop="doi" label="DOI" min-width="200" />
        <el-table-column prop="indexKeywords" label="索引关键词" min-width="220" />
        <el-table-column label="状态" width="180">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.publicationStatus)">{{ workflowLabel(row.publicationStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="workflow-section">
      <h2>论文集导出</h2>
      <el-table v-loading="loading" :data="operations?.proceedingsExports ?? []" empty-text="暂无论文集导出。">
        <el-table-column prop="exportName" label="导出名称" min-width="220" />
        <el-table-column prop="paperCount" label="论文数" width="100" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.exportStatus)">{{ workflowLabel(row.exportStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button
              size="small"
              :disabled="row.exportStatus !== 'PREVIEWED' && row.exportStatus !== 'EXPORTED'"
              :loading="actions.isPending(`export:${row.exportBatchId}`)"
              @click="exportMetadata(row.exportBatchId)"
            >
              导出元数据
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>
