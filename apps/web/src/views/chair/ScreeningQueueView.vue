<script setup lang="ts">
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, reactive, ref } from "vue";

import { useApiError } from "../../composables/useApiError";
import { useAsyncAction } from "../../composables/useAsyncAction";
import {
  createReviewRound,
  downloadPdf,
  listScreeningQueue,
  screeningDeskReject,
  startScreening,
  type ScreeningQueueItem
} from "../../lib/workflow-api";
import { formatDateTime, formatFileSize, statusTagType, workflowLabel } from "../../lib/workflow-format";

const loading = ref(false);
const queue = ref<ScreeningQueueItem[]>([]);
const actions = useAsyncAction();
const { showApiError } = useApiError();
const screeningDialogOpen = ref(false);
const screeningDialogRow = ref<ScreeningQueueItem | null>(null);
const roundFormRef = ref<FormInstance>();
const roundForm = reactive({ manuscriptId: 0, versionId: 0, deadlineAt: "" });
const roundDialogOpen = ref(false);
const deskRejectFormRef = ref<FormInstance>();
const deskRejectForm = reactive({ manuscriptId: 0, versionId: 0, decisionReason: "" });
const deskRejectDialogOpen = ref(false);
const roundRules: FormRules = {
  deadlineAt: [{ required: true, message: "截止日期为必填", trigger: "change" }]
};
const deskRejectRules: FormRules = {
  decisionReason: [{ required: true, message: "原因为必填", trigger: "blur" }]
};

onMounted(loadQueue);

async function loadQueue() {
  loading.value = true;
  try {
    queue.value = await listScreeningQueue();
  } catch (error) {
    showApiError(error, "初筛队列加载失败。");
  } finally {
    loading.value = false;
  }
}

async function start(row: ScreeningQueueItem) {
  await actions.run(`start:${row.manuscriptId}:${row.versionId}`, async () => {
    try {
      if (row.currentStatus !== "UNDER_SCREENING") {
        await startScreening(row.manuscriptId, row.versionId);
        row.currentStatus = "UNDER_SCREENING";
        ElMessage.success("初筛已开始。");
      }
      screeningDialogRow.value = { ...row };
      screeningDialogOpen.value = true;
    } catch (error) {
      showApiError(error, "Screening could not be started.");
    }
  });
}

async function download(row: ScreeningQueueItem) {
  await actions.run(`download:${row.manuscriptId}:${row.versionId}`, async () => {
    try {
      const blob = await downloadPdf(row.manuscriptId, row.versionId);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener");
      URL.revokeObjectURL(url);
    } catch (error) {
      showApiError(error, "PDF 无法打开。");
    }
  });
}

function openRound(row: ScreeningQueueItem) {
  Object.assign(roundForm, {
    manuscriptId: row.manuscriptId,
    versionId: row.versionId,
    deadlineAt: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString()
  });
  roundDialogOpen.value = true;
}

function openRoundFromScreeningDialog() {
  if (!screeningDialogRow.value) {
    return;
  }
  screeningDialogOpen.value = false;
  openRound(screeningDialogRow.value);
}

async function submitRound() {
  const valid = await roundFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  await actions.run("create-round", async () => {
    try {
      await createReviewRound({
        ...roundForm,
        assignmentStrategy: "REALLOCATE_REVIEWERS",
        screeningRequired: true
      });
      roundDialogOpen.value = false;
      ElMessage.success("评审轮次已创建。");
      await loadQueue();
    } catch (error) {
      showApiError(error, "评审轮次创建失败。");
    }
  });
}

function openDeskReject(row: ScreeningQueueItem) {
  Object.assign(deskRejectForm, {
    manuscriptId: row.manuscriptId,
    versionId: row.versionId,
    decisionReason: ""
  });
  deskRejectDialogOpen.value = true;
}

function openDeskRejectFromScreeningDialog() {
  if (!screeningDialogRow.value) {
    return;
  }
  screeningDialogOpen.value = false;
  openDeskReject(screeningDialogRow.value);
}

function screeningEntryLabel(row: ScreeningQueueItem) {
  return row.currentStatus === "UNDER_SCREENING" ? "查看初筛" : "开始初筛";
}

async function submitDeskReject() {
  const valid = await deskRejectFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      "桌面拒稿将在外部评审前关闭此稿件，确认继续？",
      "确认桌面拒稿",
      { type: "warning", confirmButtonText: "拒稿" }
    );
  } catch {
    return;
  }
  await actions.run("desk-reject", async () => {
    try {
      await screeningDeskReject({
        manuscriptId: deskRejectForm.manuscriptId,
        versionId: deskRejectForm.versionId,
        decisionReason: deskRejectForm.decisionReason
      });
      deskRejectDialogOpen.value = false;
      ElMessage.success("桌面拒稿已记录。");
      await loadQueue();
    } catch (error) {
      showApiError(error, "桌面拒稿记录失败。");
    }
  });
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">主席</p>
        <h1>初筛队列</h1>
        <p class="body">在正式评审轮次前对新投稿进行初筛。</p>
      </div>
      <el-button :loading="loading" @click="loadQueue">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="queue" empty-text="暂无等待初筛的稿件。">
      <el-table-column prop="manuscriptId" label="稿件" width="120" />
      <el-table-column prop="title" label="标题" min-width="220" />
      <el-table-column prop="currentStatus" label="状态" width="160">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.currentStatus)">{{ workflowLabel(row.currentStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="blindMode" label="审稿模式" width="150">
        <template #default="{ row }">{{ workflowLabel(row.blindMode) }}</template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="投稿时间" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
      </el-table-column>
      <el-table-column label="PDF" min-width="160">
        <template #default="{ row }">
          <el-button
            v-if="row.pdfFileName"
            link
            :loading="actions.isPending(`download:${row.manuscriptId}:${row.versionId}`)"
            @click="download(row)"
          >
            {{ row.pdfFileName }} · {{ formatFileSize(row.pdfFileSize) }}
          </el-button>
          <span v-else>缺失</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250">
        <template #default="{ row }">
          <div class="action-row">
            <el-button
              size="small"
              :loading="actions.isPending(`start:${row.manuscriptId}:${row.versionId}`)"
              @click="start(row)"
            >
              {{ screeningEntryLabel(row) }}
            </el-button>
            <el-button size="small" type="danger" @click="openDeskReject(row)">桌面拒稿</el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无等待初筛的稿件。" />
      </template>
    </el-table>

    <el-dialog
      v-model="screeningDialogOpen"
      class="screening-dialog"
      title="投稿信息"
      width="min(640px, calc(100vw - 32px))"
    >
      <template v-if="screeningDialogRow">
        <div class="screening-dialog-body">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="稿件 ID">{{ screeningDialogRow.manuscriptId }}</el-descriptions-item>
            <el-descriptions-item label="版本">v{{ screeningDialogRow.versionNo }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{ screeningDialogRow.title }}</el-descriptions-item>
            <el-descriptions-item label="摘要">
              <div class="preformatted-text">{{ screeningDialogRow.abstractText || "未填写" }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="关键词">{{ screeningDialogRow.keywords || "未填写" }}</el-descriptions-item>
            <el-descriptions-item label="当前状态">
              <el-tag :type="statusTagType(screeningDialogRow.currentStatus)">
                {{ workflowLabel(screeningDialogRow.currentStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="审稿模式">
              {{ workflowLabel(screeningDialogRow.blindMode) }}
            </el-descriptions-item>
            <el-descriptions-item label="投稿时间">
              {{ formatDateTime(screeningDialogRow.submittedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="PDF">
              <el-button
                v-if="screeningDialogRow.pdfFileName"
                class="screening-file-button"
                link
                :loading="actions.isPending(`download:${screeningDialogRow.manuscriptId}:${screeningDialogRow.versionId}`)"
                @click="download(screeningDialogRow)"
              >
                <span class="screening-file-name">
                  {{ screeningDialogRow.pdfFileName }} · {{ formatFileSize(screeningDialogRow.pdfFileSize) }}
                </span>
              </el-button>
              <span v-else>缺失</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </template>
      <template #footer>
        <el-button @click="screeningDialogOpen = false">关闭</el-button>
        <el-button type="primary" @click="openRoundFromScreeningDialog">创建轮次</el-button>
        <el-button type="danger" @click="openDeskRejectFromScreeningDialog">桌面拒稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roundDialogOpen" title="创建评审轮次" width="520px">
      <el-form ref="roundFormRef" :model="roundForm" :rules="roundRules" label-position="top">
        <el-form-item label="截止日期" prop="deadlineAt">
          <el-date-picker
            v-model="roundForm.deadlineAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss[Z]"
            placeholder="选择截止日期"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="actions.isPending('create-round')" @click="roundDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="actions.isPending('create-round')" @click="submitRound">创建轮次</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="deskRejectDialogOpen" title="桌面拒稿" width="520px">
      <el-form ref="deskRejectFormRef" :model="deskRejectForm" :rules="deskRejectRules" label-position="top">
        <el-form-item label="原因" prop="decisionReason">
          <el-input v-model="deskRejectForm.decisionReason" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="actions.isPending('desk-reject')" @click="deskRejectDialogOpen = false">取消</el-button>
        <el-button type="danger" :loading="actions.isPending('desk-reject')" @click="submitDeskReject">确认拒稿</el-button>
      </template>
    </el-dialog>
  </section>
</template>
