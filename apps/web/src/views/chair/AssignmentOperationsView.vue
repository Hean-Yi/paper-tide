<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";

import { useApiError } from "../../composables/useApiError";
import { useAsyncAction } from "../../composables/useAsyncAction";
import {
  confirmRandomConferenceAssignmentPreview,
  confirmAssignmentProposalDrafts,
  confirmMatchingScoreImport,
  confirmReviewerInvitationImport,
  getAssignmentOperations,
  listAssignmentCandidates,
  listChairConferences,
  listConferencePapers,
  previewRandomConferenceAssignments,
  previewMatchingScoreImport,
  previewReviewerInvitationImport,
  type AssignmentCandidate,
  type AssignmentDraft,
  type AssignmentOperations,
  type ConferenceCfpSummary,
  type ConferencePaperItem,
  type ImportBatchOperationRow,
  type ImportPreviewResponse
} from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";

const actions = useAsyncAction();
const { showApiError } = useApiError();
const loading = ref(false);
const conferenceId = ref(0);
const conferences = ref<ConferenceCfpSummary[]>([]);
const papers = ref<ConferencePaperItem[]>([]);
const candidates = ref<AssignmentCandidate[]>([]);
const operations = ref<AssignmentOperations | null>(null);
const lastPreview = ref<ImportPreviewResponse | null>(null);
const randomPreview = ref<{ createdDraftCount: number; drafts: AssignmentDraft[] } | null>(null);
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

const selectedConference = computed(() =>
  conferences.value.find((conference) => conference.conferenceId === conferenceId.value) ?? null
);

const assignablePapers = computed(() =>
  papers.value.filter((paper) => paper.roundId && paper.assignmentCount < autoAssign.reviewsPerPaper)
);

onMounted(loadConferences);

async function loadConferences() {
  loading.value = true;
  try {
    conferences.value = await listChairConferences();
    if (!conferenceId.value && conferences.value.length > 0) {
      conferenceId.value = conferences.value[0].conferenceId;
    }
    await loadAssignmentWorkspace();
  } catch (error) {
    showApiError(error, "会议列表加载失败。");
  } finally {
    loading.value = false;
  }
}

async function loadAssignmentWorkspace(clearPreview = true) {
  if (!conferenceId.value) {
    return;
  }
  if (clearPreview) {
    randomPreview.value = null;
  }
  operations.value = await getAssignmentOperations(Number(conferenceId.value));
  papers.value = await listConferencePapers(Number(conferenceId.value));
  const roundIds = papers.value
    .map((paper) => paper.roundId)
    .filter((roundId): roundId is number => Boolean(roundId));
  const candidateGroups = await Promise.all(roundIds.map((roundId) => listAssignmentCandidates(roundId)));
  const byReviewerId = new Map<number, AssignmentCandidate>();
  for (const candidate of candidateGroups.flat()) {
    byReviewerId.set(candidate.reviewerId, candidate);
  }
  candidates.value = Array.from(byReviewerId.values());
}

async function loadOperations() {
  loading.value = true;
  try {
    await loadAssignmentWorkspace();
  } catch (error) {
    showApiError(error, "审稿人分配工作区加载失败。");
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

async function generateRandomPreview() {
  await actions.run("assignment-preview", async () => {
    try {
      randomPreview.value = await previewRandomConferenceAssignments(
        Number(conferenceId.value),
        Number(autoAssign.reviewsPerPaper),
        autoAssign.deadlineAt
      );
      ElMessage.success(`已生成 ${randomPreview.value.createdDraftCount} 个随机分配预览。`);
      await loadAssignmentWorkspace(false);
    } catch (error) {
      showApiError(error, "随机分配预览失败。");
    }
  });
}

async function confirmRandomPreview() {
  await actions.run("assignment-preview-confirm", async () => {
    try {
      const result = await confirmRandomConferenceAssignmentPreview(Number(conferenceId.value), autoAssign.deadlineAt);
      ElMessage.success(`已确认 ${result.createdCount} 个审稿分配。`);
      randomPreview.value = null;
      await loadAssignmentWorkspace();
    } catch (error) {
      showApiError(error, "随机分配确认失败。");
    }
  });
}
</script>

<template>
  <section class="workflow-page assignment-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">主席操作</p>
        <h1>审稿人分配</h1>
        <p class="body">选择会议，查看可用审稿人与待分配论文，先生成随机预览，再确认正式分配。</p>
      </div>
      <div class="action-row">
        <el-select
          v-model="conferenceId"
          data-test="assignment-conference"
          placeholder="选择会议"
          style="width: 280px"
          @change="loadOperations"
        >
          <el-option
            v-for="conference in conferences"
            :key="conference.conferenceId"
            :label="`${conference.name} ${conference.year}`"
            :value="conference.conferenceId"
          />
        </el-select>
        <el-button :loading="loading" @click="loadOperations">刷新</el-button>
      </div>
    </div>

    <section class="workflow-section">
      <h2>随机分配预览</h2>
      <el-alert
        v-if="selectedConference"
        class="workflow-alert compact-alert"
        type="info"
        :closable="false"
        :title="`${selectedConference.name} 当前状态：${workflowLabel(selectedConference.status)}`"
      />
      <el-form class="workflow-form compact-form" label-position="top" @submit.prevent="generateRandomPreview">
        <div class="action-row">
          <el-form-item label="每篇论文最多审稿人数">
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
            :disabled="!conferenceId || assignablePapers.length === 0"
            :loading="actions.isPending('assignment-preview')"
            data-test="assignment-preview-submit"
          >
            生成随机预览
          </el-button>
          <el-button
            type="success"
            :disabled="!randomPreview || randomPreview.createdDraftCount === 0"
            :loading="actions.isPending('assignment-preview-confirm')"
            data-test="assignment-preview-confirm"
            @click="confirmRandomPreview"
          >
            确认预览并分配
          </el-button>
        </div>
      </el-form>
      <el-alert
        v-if="randomPreview"
        :title="`已按每篇最多 ${autoAssign.reviewsPerPaper} 人生成 ${randomPreview.createdDraftCount} 个随机分配预览。`"
        type="success"
        :closable="false"
      />
    </section>

    <section class="workflow-section">
      <h2>待分配论文</h2>
      <div class="table-scroll">
        <el-table v-loading="loading" :data="assignablePapers" empty-text="暂无需要分配的论文。">
          <el-table-column prop="title" label="论文" min-width="220" />
          <el-table-column prop="roundId" label="轮次" width="100" />
          <el-table-column label="已分配/目标" width="140">
            <template #default="{ row }">{{ row.assignmentCount }} / {{ autoAssign.reviewsPerPaper }}</template>
          </el-table-column>
          <el-table-column label="状态" width="160">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.roundStatus || row.currentStatus)">
                {{ workflowLabel(row.roundStatus || row.currentStatus) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="workflow-section">
      <h2>可用审稿人</h2>
      <div class="table-scroll">
        <el-table v-loading="loading" :data="candidates" empty-text="暂无可用审稿人。">
          <el-table-column prop="reviewerName" label="审稿人" min-width="180" />
          <el-table-column prop="institution" label="机构" min-width="180" />
          <el-table-column label="负荷" width="110">
            <template #default="{ row }">{{ row.currentLoad }} / {{ row.maxLoad }}</template>
          </el-table-column>
          <el-table-column label="投标" width="150">
            <template #default="{ row }">{{ workflowLabel(row.bidValue || "NEUTRAL") }}</template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="workflow-section">
      <h2>随机预览结果</h2>
      <div class="table-scroll">
        <el-table :data="randomPreview?.drafts ?? []" empty-text="尚未生成随机分配预览。">
          <el-table-column prop="manuscriptId" label="稿件" width="110" />
          <el-table-column prop="reviewerId" label="审稿人" width="120" />
          <el-table-column prop="rankOrder" label="顺序" width="90" />
          <el-table-column prop="reason" label="说明" min-width="220" />
        </el-table>
      </div>
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
      <div class="table-scroll">
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
      </div>
    </section>

    <section class="workflow-section">
      <h2>分配提案</h2>
      <div class="table-scroll">
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
      </div>
    </section>

    <section class="workflow-section">
      <h2>审稿人操作</h2>
      <div class="two-column-grid">
        <div class="table-scroll">
          <el-table :data="operations?.reviewerInvitations ?? []" empty-text="暂无审稿人邀请。">
            <el-table-column prop="reviewerId" label="审稿人" width="110" />
            <el-table-column label="状态">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.invitationStatus)">{{ workflowLabel(row.invitationStatus) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="table-scroll">
          <el-table :data="operations?.externalDelegations ?? []" empty-text="暂无外部委托。">
            <el-table-column prop="externalEmail" label="外部审稿人" min-width="220" />
            <el-table-column label="状态" width="140">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.delegationStatus)">{{ workflowLabel(row.delegationStatus) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </section>

    <section class="workflow-section">
      <h2>匹配分数</h2>
      <div class="table-scroll">
        <el-table :data="operations?.matchingScores ?? []" empty-text="暂无匹配分数。">
          <el-table-column prop="manuscriptId" label="稿件" width="120" />
          <el-table-column prop="reviewerId" label="审稿人" width="110" />
          <el-table-column prop="scoreSource" label="来源" width="160" />
          <el-table-column prop="matchingScore" label="分数" width="100" />
          <el-table-column prop="rationale" label="理由" min-width="220" />
        </el-table>
      </div>
    </section>
  </section>
</template>
