<script setup lang="ts">
import { ElMessage } from "element-plus";
import { onMounted, reactive, ref } from "vue";

import { useApiError } from "../../composables/useApiError";
import { useAsyncAction } from "../../composables/useAsyncAction";
import {
  autoAssignConferenceReviewers,
  confirmAssignmentProposalDrafts,
  confirmMatchingScoreImport,
  confirmReviewerInvitationImport,
  getAssignmentOperations,
  previewMatchingScoreImport,
  previewReviewerInvitationImport,
  type AssignmentOperations,
  type AutoAssignResponse,
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
const lastAutoAssign = ref<AutoAssignResponse | null>(null);
const autoAssign = reactive({
  reviewsPerPaper: 3,
  deadlineAt: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString()
});
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
    showApiError(error, "分配操作加载失败。");
  } finally {
    loading.value = false;
  }
}

async function previewInvitations() {
  await actions.run("preview-invitations", async () => {
    try {
      lastPreview.value = await previewReviewerInvitationImport(Number(conferenceId.value), invitationImport.csvText);
      ElMessage.success("邀请导入预览已创建。");
      await loadOperations();
    } catch (error) {
      showApiError(error, "邀请导入预览失败。");
    }
  });
}

async function previewMatchingScores() {
  await actions.run("preview-matching-scores", async () => {
    try {
      lastPreview.value = await previewMatchingScoreImport(Number(matchingImport.manuscriptId), matchingImport.csvText);
      ElMessage.success("匹配分数导入预览已创建。");
      await loadOperations();
    } catch (error) {
      showApiError(error, "匹配分数导入预览失败。");
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
      ElMessage.success("导入已应用。");
      await loadOperations();
    } catch (error) {
      showApiError(error, "导入确认失败。");
    }
  });
}

async function confirmProposal(bundleId: number) {
  await actions.run(`confirm-proposal:${bundleId}`, async () => {
    try {
      const result = await confirmAssignmentProposalDrafts(bundleId);
      ElMessage.success(`${result.createdCount} 个分配草稿已创建。`);
      await loadOperations();
    } catch (error) {
      showApiError(error, "分配提案确认失败。");
    }
  });
}

async function runAutoAssign() {
  await actions.run("auto-assign", async () => {
    try {
      lastAutoAssign.value = await autoAssignConferenceReviewers(
        Number(conferenceId.value),
        Number(autoAssign.reviewsPerPaper),
        autoAssign.deadlineAt
      );
      ElMessage.success(`已创建 ${lastAutoAssign.value.createdCount} 个审稿分配。`);
      await loadOperations();
    } catch (error) {
      showApiError(error, "一键分配失败。");
    }
  });
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">主席操作</p>
        <h1>分配与利益冲突</h1>
        <p class="body">管理审稿人邀请、委托、匹配分数导入及大题提案。</p>
      </div>
      <div class="action-row">
        <el-input-number v-model="conferenceId" :min="0" />
        <el-button :loading="loading" @click="loadOperations">刷新</el-button>
      </div>
    </div>

    <section class="workflow-section">
      <h2>一键分配</h2>
      <el-form class="workflow-form compact-form" label-position="top" @submit.prevent="runAutoAssign">
        <div class="action-row">
          <el-form-item label="每篇论文审稿人数">
            <el-input-number v-model="autoAssign.reviewsPerPaper" data-test="auto-assign-target" :min="1" :max="10" />
          </el-form-item>
          <el-form-item label="评审截止日期">
            <el-date-picker
              v-model="autoAssign.deadlineAt"
              data-test="auto-assign-deadline"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss[Z]"
              placeholder="选择截止日期"
            />
          </el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="actions.isPending('auto-assign')"
            data-test="auto-assign-submit"
          >
            一键分配
          </el-button>
        </div>
      </el-form>
      <el-alert
        v-if="lastAutoAssign"
        :title="`已按每篇 ${lastAutoAssign.requestedReviewsPerPaper} 人目标创建 ${lastAutoAssign.createdCount} 个分配。`"
        type="success"
        :closable="false"
      />
    </section>

    <section class="workflow-section">
      <h2>批量导入</h2>
      <div class="two-column-grid">
        <el-form class="workflow-form" label-position="top" @submit.prevent="previewInvitations">
          <el-form-item label="审稿人邀请 CSV">
            <el-input v-model="invitationImport.csvText" type="textarea" :rows="5" />
          </el-form-item>
          <el-button type="primary" native-type="submit" :loading="actions.isPending('preview-invitations')">
            预览邀请
          </el-button>
        </el-form>
        <el-form class="workflow-form" label-position="top" @submit.prevent="previewMatchingScores">
          <el-form-item label="稿件 ID">
            <el-input-number v-model="matchingImport.manuscriptId" :min="0" />
          </el-form-item>
          <el-form-item label="匹配分数 CSV">
            <el-input v-model="matchingImport.csvText" type="textarea" :rows="5" />
          </el-form-item>
          <el-button type="primary" native-type="submit" :loading="actions.isPending('preview-matching-scores')">
            预览匹配分数
          </el-button>
        </el-form>
      </div>
      <el-alert
        v-if="lastPreview"
        :title="`${lastPreview.validRowCount} 条有效行，${lastPreview.errorCount} 条错误，批次 ${lastPreview.batchId}`"
        type="info"
        :closable="false"
      />
    </section>

    <section class="workflow-section">
      <h2>导入批次</h2>
      <el-table v-loading="loading" :data="operations?.importBatches ?? []" empty-text="暂无导入批次。">
        <el-table-column prop="batchId" label="批次" width="90" />
        <el-table-column prop="importType" label="类型" min-width="180" />
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.batchStatus)">{{ workflowLabel(row.batchStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="行数" width="160">
          <template #default="{ row }">{{ row.validRowCount }} / {{ row.rowCount }} 有效</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button
              size="small"
              :disabled="row.batchStatus !== 'PREVIEWED'"
              :loading="actions.isPending(`confirm-import:${row.batchId}`)"
              @click="confirmImport(row)"
            >
              确认导入
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="workflow-section">
      <h2>分配提案</h2>
      <el-table v-loading="loading" :data="operations?.assignmentProposals ?? []" empty-text="暂无提案包。">
        <el-table-column prop="proposalName" label="提案" min-width="200" />
        <el-table-column prop="manuscriptId" label="稿件" width="120" />
        <el-table-column prop="proposalCount" label="候选人数" width="120" />
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.bundleStatus)">{{ workflowLabel(row.bundleStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button
              size="small"
              :disabled="row.bundleStatus !== 'PROPOSED'"
              :loading="actions.isPending(`confirm-proposal:${row.bundleId}`)"
              @click="confirmProposal(row.bundleId)"
            >
              确认草稿
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="workflow-section">
      <h2>审稿人操作</h2>
      <div class="two-column-grid">
        <el-table :data="operations?.reviewerInvitations ?? []" empty-text="暂无审稿人邀请。">
          <el-table-column prop="reviewerId" label="审稿人" width="110" />
          <el-table-column label="状态">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.invitationStatus)">{{ workflowLabel(row.invitationStatus) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-table :data="operations?.externalDelegations ?? []" empty-text="暂无外部委托。">
          <el-table-column prop="externalEmail" label="外部审稿人" min-width="220" />
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.delegationStatus)">{{ workflowLabel(row.delegationStatus) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="workflow-section">
      <h2>匹配分数</h2>
      <el-table :data="operations?.matchingScores ?? []" empty-text="暂无匹配分数。">
        <el-table-column prop="manuscriptId" label="稿件" width="120" />
        <el-table-column prop="reviewerId" label="审稿人" width="110" />
        <el-table-column prop="scoreSource" label="来源" width="160" />
        <el-table-column prop="matchingScore" label="分数" width="100" />
        <el-table-column prop="rationale" label="理由" min-width="220" />
      </el-table>
    </section>
  </section>
</template>
