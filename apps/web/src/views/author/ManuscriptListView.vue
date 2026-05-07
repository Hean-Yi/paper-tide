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
  listManuscripts,
  submitCameraReady,
  submitVersion,
  uploadPdf,
  type AuthorInput,
  type DecisionPackage,
  type ManuscriptSummary
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
  title: [{ required: true, message: "Title is required", trigger: "blur" }],
  abstract: [{ required: true, message: "Abstract is required", trigger: "blur" }],
  keywords: [{ required: true, message: "Keywords are required", trigger: "blur" }]
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
    showApiError(error, "Manuscripts could not be loaded.");
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
      ElMessage.success("PDF uploaded.");
      await loadManuscripts();
    } catch (error) {
      showApiError(error, "PDF could not be uploaded.");
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
      ElMessage.success("Version submitted.");
      await loadManuscripts();
    } catch (error) {
      showApiError(error, "Version could not be submitted.");
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
      showApiError(error, "PDF could not be downloaded.");
    }
  });
}

async function openDecisionPackage(row: ManuscriptSummary) {
  await actions.run(decisionPackageKey(row.manuscriptId), async () => {
    try {
      decisionPackage.value = await getDecisionPackage(row.manuscriptId);
      decisionDialogOpen.value = true;
    } catch (error) {
      showApiError(error, "Decision package could not be loaded.");
    }
  });
}

function openCameraReady(row: ManuscriptSummary) {
  cameraReadyManuscript.value = row;
  Object.assign(cameraReadyForm, {
    fileName: `${row.currentVersionTitle || "paper"}-camera-ready.pdf`,
    fileSize: 0,
    copyrightConfirmed: false,
    licenseType: "CC-BY"
  });
  cameraReadyDialogOpen.value = true;
}

async function submitCameraReadyForm() {
  if (!cameraReadyManuscript.value) {
    return;
  }
  if (!cameraReadyForm.fileName.trim() || !cameraReadyForm.copyrightConfirmed) {
    ElMessage.error("Camera-ready file name and copyright confirmation are required.");
    return;
  }
  const manuscriptId = cameraReadyManuscript.value.manuscriptId;
  await actions.run(cameraReadyKey(manuscriptId), async () => {
    try {
      await submitCameraReady(manuscriptId, {
        fileName: cameraReadyForm.fileName.trim(),
        fileSize: cameraReadyForm.fileSize,
        copyrightConfirmed: cameraReadyForm.copyrightConfirmed,
        licenseType: cameraReadyForm.licenseType
      });
      cameraReadyDialogOpen.value = false;
      ElMessage.success("Camera-ready package submitted.");
    } catch (error) {
      showApiError(error, "Camera-ready package could not be submitted.");
    }
  });
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
      ElMessage.success("Revision draft created.");
      await loadManuscripts();
    } catch (error) {
      showApiError(error, "Revision could not be created.");
    }
  });
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">Author</p>
        <h1>My manuscripts</h1>
        <p class="body">Track submissions, upload PDFs, and submit draft versions.</p>
        <p class="body">{{ PDF_UPLOAD_LIMIT_HINT }}</p>
      </div>
      <div class="action-row">
        <el-button @click="loadManuscripts">Refresh</el-button>
        <el-button type="primary" @click="router.push('/author/submit')">Create manuscript</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="manuscripts" empty-text="No manuscripts yet.">
      <el-table-column prop="manuscriptId" label="Manuscript" width="120" />
      <el-table-column prop="currentVersionTitle" label="Title" min-width="220" />
      <el-table-column label="Version" width="110">
        <template #default="{ row }">v{{ row.currentVersionNo }}</template>
      </el-table-column>
      <el-table-column prop="currentStatus" label="Status" width="170">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.currentStatus)">{{ workflowLabel(row.currentStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="Submitted" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
      </el-table-column>
      <el-table-column prop="lastDecisionCode" label="Last decision" width="160">
        <template #default="{ row }">{{ workflowLabel(row.lastDecisionCode) }}</template>
      </el-table-column>
      <el-table-column label="Actions" width="620">
        <template #default="{ row }">
          <div class="action-row">
            <el-upload :auto-upload="false" :show-file-list="false" :on-change="selectPdfForRow(row)">
              <el-button size="small" :loading="actions.isPending(uploadKey(row.manuscriptId))">Upload PDF</el-button>
            </el-upload>
            <el-button size="small" :loading="actions.isPending(downloadKey(row.manuscriptId))" @click="download(row)">Download PDF</el-button>
            <el-button size="small" type="primary" :loading="actions.isPending(submitKey(row.manuscriptId))" @click="submit(row)">Submit</el-button>
            <el-button
              size="small"
              :disabled="row.currentStatus !== 'REVISION_REQUIRED'"
              :loading="actions.isPending(`author-create-revision:${row.manuscriptId}`)"
              @click="openRevision(row)"
            >
              Create revision
            </el-button>
            <el-button
              size="small"
              :disabled="!row.lastDecisionCode"
              :loading="actions.isPending(decisionPackageKey(row.manuscriptId))"
              @click="openDecisionPackage(row)"
            >
              Decision package
            </el-button>
            <el-button
              size="small"
              :disabled="row.currentStatus !== 'ACCEPTED'"
              :loading="actions.isPending(cameraReadyKey(row.manuscriptId))"
              @click="openCameraReady(row)"
            >
              Camera-ready
            </el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="No manuscripts yet." />
      </template>
    </el-table>

    <el-dialog v-model="revisionDialogOpen" title="Create revision" width="640px">
      <el-form ref="revisionFormRef" :model="revisionForm" :rules="revisionRules" label-position="top">
        <el-form-item label="Title" prop="title">
          <el-input v-model="revisionForm.title" />
        </el-form-item>
        <el-form-item label="Abstract" prop="abstract">
          <el-input v-model="revisionForm.abstract" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="Keywords" prop="keywords">
          <el-input v-model="revisionForm.keywords" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="revisionDialogOpen = false">Cancel</el-button>
        <el-button type="primary" @click="submitRevision">Create revision</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="decisionDialogOpen" title="Decision package" width="720px">
      <template v-if="decisionPackage">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="Decision">{{ workflowLabel(decisionPackage.decisionCode) }}</el-descriptions-item>
          <el-descriptions-item label="Reason">{{ decisionPackage.decisionReason || "No decision letter provided." }}</el-descriptions-item>
          <el-descriptions-item label="Decided at">{{ formatDateTime(decisionPackage.decidedAt) }}</el-descriptions-item>
        </el-descriptions>
        <div class="stacked-list">
          <el-card v-for="review in decisionPackage.reviews" :key="review.reviewId" shadow="never">
            <template #header>{{ review.reviewerLabel }} · {{ workflowLabel(review.recommendation) }}</template>
            <p><strong>Overall:</strong> {{ review.overallScore }} / 5 · {{ review.confidenceLevel }}</p>
            <p><strong>Strengths:</strong> {{ review.strengths || "Not provided." }}</p>
            <p><strong>Weaknesses:</strong> {{ review.weaknesses || "Not provided." }}</p>
            <p><strong>Comments:</strong> {{ review.commentsToAuthor || "Not provided." }}</p>
          </el-card>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="cameraReadyDialogOpen" title="Submit camera-ready package" width="520px">
      <el-form label-position="top">
        <el-form-item label="Final PDF file name" required>
          <el-input v-model="cameraReadyForm.fileName" />
        </el-form-item>
        <el-form-item label="File size in bytes">
          <el-input-number v-model="cameraReadyForm.fileSize" :min="0" />
        </el-form-item>
        <el-form-item label="License">
          <el-input v-model="cameraReadyForm.licenseType" />
        </el-form-item>
        <el-checkbox v-model="cameraReadyForm.copyrightConfirmed">
          I confirm the camera-ready package is final and publication rights are cleared.
        </el-checkbox>
      </el-form>
      <template #footer>
        <el-button @click="cameraReadyDialogOpen = false">Cancel</el-button>
        <el-button
          type="primary"
          :loading="cameraReadyManuscript ? actions.isPending(cameraReadyKey(cameraReadyManuscript.manuscriptId)) : false"
          @click="submitCameraReadyForm"
        >
          Submit camera-ready
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
