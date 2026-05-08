<script setup lang="ts">
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";

import {
  acceptAssignment,
  declineAssignment,
  listReviewerAssignments,
  type ReviewerAssignment
} from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";

const router = useRouter();
const loading = ref(false);
const assignments = ref<ReviewerAssignment[]>([]);
const declineDialogOpen = ref(false);
const declineFormRef = ref<FormInstance>();
const declineForm = reactive({ assignmentId: 0, reason: "", conflictDeclared: false });
const declineRules: FormRules = {
  reason: [{ required: true, message: "请输入拒绝原因", trigger: "blur" }]
};

onMounted(loadAssignments);

async function loadAssignments() {
  loading.value = true;
  try {
    assignments.value = await listReviewerAssignments();
  } finally {
    loading.value = false;
  }
}

async function accept(row: ReviewerAssignment) {
  await acceptAssignment(row.assignmentId);
  ElMessage.success("任务已接受。");
  await loadAssignments();
}

function openDecline(row: ReviewerAssignment) {
  Object.assign(declineForm, { assignmentId: row.assignmentId, reason: "", conflictDeclared: false });
  declineDialogOpen.value = true;
}

async function submitDecline() {
  const valid = await declineFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      "拒绝将把此任务归还内院长，且在此界面不可撤销。是否继续？",
      "确认拒绝",
      { type: "warning", confirmButtonText: "拒绝" }
    );
  } catch {
    return;
  }
  await declineAssignment(
    declineForm.assignmentId,
    declineForm.reason,
    declineForm.conflictDeclared
  );
  declineDialogOpen.value = false;
  ElMessage.success("任务已拒绝。");
  await loadAssignments();
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">审稿人</p>
        <h1>评审任务</h1>
        <p class="body">接受、拒绝或打开分配给您的论文。</p>
      </div>
      <el-button @click="loadAssignments">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="assignments" empty-text="暂无评审任务。">
      <el-table-column prop="assignmentId" label="任务" width="120" />
      <el-table-column prop="title" label="标题" min-width="220" />
      <el-table-column prop="taskStatus" label="状态" width="140">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.taskStatus)">{{ workflowLabel(row.taskStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deadlineAt" label="截止日期" min-width="180">
        <template #default="{ row }">{{ formatDateTime(row.deadlineAt) }}</template>
      </el-table-column>
      <el-table-column prop="recommendation" label="建议" width="170">
        <template #default="{ row }">{{ workflowLabel(row.recommendation) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="300">
        <template #default="{ row }">
          <div class="action-row">
            <el-button size="small" @click="accept(row)">接受</el-button>
            <el-button size="small" @click="openDecline(row)">拒绝</el-button>
            <el-button size="small" type="primary" @click="router.push(`/reviewer/reviews/${row.assignmentId}`)">
              打开评审
            </el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无评审任务。" />
      </template>
    </el-table>

    <el-dialog v-model="declineDialogOpen" title="拒绝任务" width="520px">
      <el-form ref="declineFormRef" :model="declineForm" :rules="declineRules" label-position="top">
        <el-form-item label="拒绝原因" prop="reason">
          <el-input v-model="declineForm.reason" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="declineForm.conflictDeclared">我与该稿件存在利益冲突。</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="declineDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="submitDecline">拒绝</el-button>
      </template>
    </el-dialog>
  </section>
</template>
