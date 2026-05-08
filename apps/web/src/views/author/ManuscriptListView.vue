<script setup lang="ts">
import type { FormInstance, FormRules, UploadFile } from "element-plus";
import { ElMessage } from "element-plus";
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";

import { useApiError } from "../../composables/useApiError";
import { useAsyncAction } from "../../composables/useAsyncAction";
import {
  createRevision,
  downloadPdf,
  getDecisionPackage,
  getManuscriptForm,
  listManuscripts,
  saveManuscriptFormResponse,
  submitCameraReady,
  submitVersion,
  uploadPdf,
  type AuthorInput,
  type DecisionPackage,
  type ManuscriptSummary,
  type WorkflowFormPackage
} from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";
import { PDF_UPLOAD_LIMIT_ERROR, PDF_UPLOAD_LIMIT_HINT, PDF_UPLOAD_MAX_BYTES } from "../../lib/upload";

const router = useRouter();
const actions = useAsyncAction();
const { showApiError } = useApiError();
const loading = ref(false);
const manuscripts = ref<ManuscriptSummary[]>([]);
const revisionDialogOpen = ref(false);
const revisionManuscriptId = ref<number | null>(null);
const decisionDialogOpen = ref(false);
const decisionPackage = ref<DecisionPackage | null>(null);
const cameraReadyDialogOpen = ref(false);
const cameraReadyManuscript = ref<ManuscriptSummary | null>(null);
const cameraReadyChecklist = ref<WorkflowFormPackage | null>(null);
const cameraReadyAnswers = reactive<Record<string, unknown>>({});
const revisionFormRef = ref<FormInstance>();
const revisionForm = reactive({
  title: "",
  abstract: "",
  keywords: "",
  authors: [
    {
      authorName: "Author Demo",
      email: "author_demo@example.com",
      institution: "",
      authorOrder: 1,
      userId: 1001,
      isCorresponding: true,
      isExternal: false
    }
  ] as AuthorInput[]
});
const revisionRules: FormRules = {
  title: [{ required: true, message: "请输入标题", trigger: "blur" }],
  abstract: [{ required: true, message: "请输入摘要", trigger: "blur" }],
  keywords: [{ required: true, message: "请输入关键词", trigger: "blur" }]
};
const cameraReadyForm = reactive({
  fileName: "",
  fileSize: 0,
  copyrightConfirmed: false,
  licenseType: "CC-BY"
});

onMounted(loadManuscripts);

function uploadKey(manuscriptId: number) {
  return `author-upload-pdf:${manuscriptId}`;
}

function downloadKey(manuscriptId: number) {
  return `author-download-pdf:${manuscriptId}`;
}

function submitKey(manuscriptId: number) {
  return `author-submit-version:${manuscriptId}`;
}

function decisionPackageKey(manuscriptId: number) {
  return `author-decision-package:${manuscriptId}`;
}

function cameraReadyKey(manuscriptId: number) {
  return `author-camera-ready:${manuscriptId}`;
}

async function loadManuscripts() {
  loading.value = true;
  try {
    manuscripts.value = await listManuscripts();
  } catch (error) {
    showApiError(error, "稿件加载失败。");
  } finally {
    loading.value = false;
  }
}

async function selectPdf(row: ManuscriptSummary, file: UploadFile) {
  if (!file.raw) {
    return;
  }
  if (file.raw.size > PDF_UPLOAD_MAX_BYTES) {
    ElMessage.error(PDF_UPLOAD_LIMIT_ERROR);
    return;
  }
  await actions.run(uploadKey(row.manuscriptId), async () => {
    try {
      await uploadPdf(row.manuscriptId, row.currentVersionId, file.raw!);
      ElMessage.success("PDF 已上传。");
      await loadManuscripts();
    } catch (error) {
      showApiError(error, "PDF 上传失败。");
    }
  });
}

function selectPdfForRow(row: ManuscriptSummary) {
  return (file: UploadFile) => selectPdf(row, file);
}

async function submit(row: ManuscriptSummary) {
  await actions.run(submitKey(row.manuscriptId), async () => {
    try {
      await submitVersion(row.manuscriptId, row.currentVersionId);
      ElMessage.success("版本已提交。");
      await loadManuscripts();
    } catch (error) {
      showApiError(error, "版本提交失败。");
    }
  });
}

async function download(row: ManuscriptSummary) {
  await actions.run(downloadKey(row.manuscriptId), async () => {
    try {
      const blob = await downloadPdf(row.manuscriptId, row.currentVersionId);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener");
      URL.revokeObjectURL(url);
    } catch (error) {
      showApiError(error, "PDF 下载失败。");
    }
  });
}

async function openDecisionPackage(row: ManuscriptSummary) {
  await actions.run(decisionPackageKey(row.manuscriptId), async () => {
    try {
      decisionPackage.value = await getDecisionPackage(row.manuscriptId);
      decisionDialogOpen.value = true;
    } catch (error) {
      showApiError(error, "决策包加载失败。");
    }
  });
}

async function openCameraReady(row: ManuscriptSummary) {
  cameraReadyManuscript.value = row;
  cameraReadyChecklist.value = null;
  Object.keys(cameraReadyAnswers).forEach((key) => delete cameraReadyAnswers[key]);
  Object.assign(cameraReadyForm, {
    fileName: `${row.currentVersionTitle || "paper"}-camera-ready.pdf`,
    fileSize: 0,
    copyrightConfirmed: false,
    licenseType: "CC-BY"
  });
  cameraReadyDialogOpen.value = true;
  try {
    cameraReadyChecklist.value = await getManuscriptForm(row.manuscriptId, "CAMERA_READY");
    for (const field of cameraReadyChecklist.value.form.fields) {
      cameraReadyAnswers[field.fieldKey] = cameraReadyChecklist.value.currentResponse?.answers?.[field.fieldKey] ?? "";
    }
  } catch {
    cameraReadyChecklist.value = null;
  }
}

async function submitCameraReadyForm() {
  if (!cameraReadyManuscript.value) {
    return;
  }
  if (!cameraReadyForm.fileName.trim() || !cameraReadyForm.copyrightConfirmed) {
    ElMessage.error("终稿 PDF 文件名称和版权确认为必喆。");
    return;
  }
  if (hasMissingCameraReadyAnswer()) {
    ElMessage.error("终稿核对清单必填项必须完成。");
    return;
  }
  const manuscriptId = cameraReadyManuscript.value.manuscriptId;
  await actions.run(cameraReadyKey(manuscriptId), async () => {
    try {
      if (cameraReadyChecklist.value) {
        await saveManuscriptFormResponse(manuscriptId, {
          formId: cameraReadyChecklist.value.form.formId,
          responseStatus: "SUBMITTED",
          answers: { ...cameraReadyAnswers }
        });
      }
      await submitCameraReady(manuscriptId, {
        fileName: cameraReadyForm.fileName.trim(),
        fileSize: cameraReadyForm.fileSize,
        copyrightConfirmed: cameraReadyForm.copyrightConfirmed,
        licenseType: cameraReadyForm.licenseType
      });
      cameraReadyDialogOpen.value = false;
      ElMessage.success("终稿套件已提交。");
    } catch (error) {
      showApiError(error, "终稿套件提交失败。");
    }
  });
}

function hasMissingCameraReadyAnswer() {
  return Boolean(cameraReadyChecklist.value?.form.fields.some((field) =>
    field.required && !String(cameraReadyAnswers[field.fieldKey] ?? "").trim()
  ));
}

function openRevision(row: ManuscriptSummary) {
  revisionManuscriptId.value = row.manuscriptId;
  Object.assign(revisionForm, {
    title: row.currentVersionTitle,
    abstract: "",
    keywords: "",
    authors: revisionForm.authors
  });
  revisionDialogOpen.value = true;
}

async function submitRevision() {
  if (revisionManuscriptId.value == null) {
    return;
  }
  const valid = await revisionFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  await actions.run(`author-create-revision:${revisionManuscriptId.value}`, async () => {
    try {
      await createRevision(revisionManuscriptId.value!, revisionForm);
      revisionDialogOpen.value = false;
      ElMessage.success("修订版草稿已创建。");
      await loadManuscripts();
    } catch (error) {
      showApiError(error, "修订版创建失败。");
    }
  });
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">作者</p>
        <h1>我的稿件</h1>
        <p class="body">跟踪投稿状态、上传 PDF 并提交草稿版本。</p>
        <p class="body">{{ PDF_UPLOAD_LIMIT_HINT }}</p>
      </div>
      <div class="action-row">
        <el-button @click="loadManuscripts">刷新</el-button>
        <el-button type="primary" @click="router.push('/author/submit')">创建稿件</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="manuscripts" empty-text="暂无稿件。">
      <el-table-column prop="manuscriptId" label="稿件" width="120" />
      <el-table-column prop="currentVersionTitle" label="标题" min-width="220" />
      <el-table-column label="版本" width="110">
        <template #default="{ row }">v{{ row.currentVersionNo }}</template>
      </el-table-column>
      <el-table-column prop="currentStatus" label="状态" width="170">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.currentStatus)">{{ workflowLabel(row.currentStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="提交时间" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
      </el-table-column>
      <el-table-column prop="lastDecisionCode" label="最新决定" width="160">
        <template #default="{ row }">{{ workflowLabel(row.lastDecisionCode) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="620">
        <template #default="{ row }">
          <div class="action-row">
            <el-upload :auto-upload="false" :show-file-list="false" :on-change="selectPdfForRow(row)">
              <el-button size="small" :loading="actions.isPending(uploadKey(row.manuscriptId))">上传 PDF</el-button>
            </el-upload>
            <el-button size="small" :loading="actions.isPending(downloadKey(row.manuscriptId))" @click="download(row)">下载 PDF</el-button>
            <el-button size="small" type="primary" :loading="actions.isPending(submitKey(row.manuscriptId))" @click="submit(row)">提交</el-button>
            <el-button
              size="small"
              :disabled="row.currentStatus !== 'REVISION_REQUIRED'"
              :loading="actions.isPending(`author-create-revision:${row.manuscriptId}`)"
              @click="openRevision(row)"
            >
              创建修订版
            </el-button>
            <el-button
              size="small"
              :disabled="!row.lastDecisionCode"
              :loading="actions.isPending(decisionPackageKey(row.manuscriptId))"
              @click="openDecisionPackage(row)"
            >
              决定包
            </el-button>
            <el-button
              size="small"
              :disabled="row.currentStatus !== 'ACCEPTED'"
              :loading="actions.isPending(cameraReadyKey(row.manuscriptId))"
              @click="openCameraReady(row)"
            >
              终稿
            </el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无稿件。" />
      </template>
    </el-table>

    <el-dialog v-model="revisionDialogOpen" title="创建修订版" width="640px">
      <el-form ref="revisionFormRef" :model="revisionForm" :rules="revisionRules" label-position="top">
        <el-form-item label="标题" prop="title">
          <el-input v-model="revisionForm.title" />
        </el-form-item>
        <el-form-item label="摘要" prop="abstract">
          <el-input v-model="revisionForm.abstract" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="关键词" prop="keywords">
          <el-input v-model="revisionForm.keywords" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="revisionDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="submitRevision">创建修订版</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="decisionDialogOpen" title="决定包" width="720px">
      <template v-if="decisionPackage">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="决定">{{ workflowLabel(decisionPackage.decisionCode) }}</el-descriptions-item>
          <el-descriptions-item label="理由">{{ decisionPackage.decisionReason || "未提供决定函。" }}</el-descriptions-item>
          <el-descriptions-item label="决定时间">{{ formatDateTime(decisionPackage.decidedAt) }}</el-descriptions-item>
        </el-descriptions>
        <div class="stacked-list">
          <el-card v-for="review in decisionPackage.reviews" :key="review.reviewId" shadow="never">
            <template #header>{{ review.reviewerLabel }} · {{ workflowLabel(review.recommendation) }}</template>
            <p><strong>综合评分：</strong> {{ review.overallScore }} / 5 · {{ review.confidenceLevel }}</p>
            <p><strong>优点：</strong> {{ review.strengths || "未提供。" }}</p>
            <p><strong>不足：</strong> {{ review.weaknesses || "未提供。" }}</p>
            <p><strong>评论：</strong> {{ review.commentsToAuthor || "未提供。" }}</p>
          </el-card>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="cameraReadyDialogOpen" title="提交终稿套件" width="520px">
      <el-form label-position="top">
        <el-form-item label="终稿 PDF 文件名" required>
          <el-input v-model="cameraReadyForm.fileName" />
        </el-form-item>
        <el-form-item label="文件大小（字节）">
          <el-input-number v-model="cameraReadyForm.fileSize" :min="0" />
        </el-form-item>
        <el-form-item label="许可证">
          <el-input v-model="cameraReadyForm.licenseType" />
        </el-form-item>
        <el-checkbox v-model="cameraReadyForm.copyrightConfirmed">
          我确认终稿套件已最终定稿，且出版权已迫。
        </el-checkbox>
        <section v-if="cameraReadyChecklist" class="workflow-form review-form-panel">
          <div class="subsection-heading">
            <h2>{{ cameraReadyChecklist.form.formName }}</h2>
            <el-tag :type="statusTagType(cameraReadyChecklist.currentResponse?.responseStatus)">
              {{ workflowLabel(cameraReadyChecklist.currentResponse?.responseStatus || "DRAFT") }}
            </el-tag>
          </div>
          <el-form-item
            v-for="field in cameraReadyChecklist.form.fields"
            :key="field.fieldId"
            :label="field.fieldLabel"
            :required="field.required"
            :data-test="`camera-ready-form-${field.fieldKey}`"
          >
            <el-input
              v-if="field.fieldType === 'LONG_TEXT' || field.fieldType === 'TEXT'"
              v-model="cameraReadyAnswers[field.fieldKey]"
              type="textarea"
              :rows="field.fieldType === 'LONG_TEXT' ? 4 : 2"
            />
            <el-input-number
              v-else-if="field.fieldType === 'NUMBER' || field.fieldType === 'SCORE'"
              v-model="cameraReadyAnswers[field.fieldKey]"
              :min="field.fieldType === 'SCORE' ? 1 : undefined"
              :max="field.fieldType === 'SCORE' ? 5 : undefined"
            />
            <el-switch v-else-if="field.fieldType === 'BOOLEAN'" v-model="cameraReadyAnswers[field.fieldKey]" />
            <el-input v-else v-model="cameraReadyAnswers[field.fieldKey]" />
          </el-form-item>
        </section>
      </el-form>
      <template #footer>
        <el-button @click="cameraReadyDialogOpen = false">取消</el-button>
        <el-button
          type="primary"
          :loading="cameraReadyManuscript ? actions.isPending(cameraReadyKey(cameraReadyManuscript.manuscriptId)) : false"
          @click="submitCameraReadyForm"
        >
          提交终稿
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
