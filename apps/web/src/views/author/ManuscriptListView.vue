<script setup lang="ts">
import type { UploadFile } from "element-plus";
import { ElMessage } from "element-plus";
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";

import {
  createRevision,
  downloadPdf,
  listManuscripts,
  submitVersion,
  uploadPdf,
  type AuthorInput,
  type ManuscriptSummary
} from "../../lib/workflow-api";

const router = useRouter();
const loading = ref(false);
const manuscripts = ref<ManuscriptSummary[]>([]);
const revisionDialogOpen = ref(false);
const revisionManuscriptId = ref<number | null>(null);
const revisionForm = ref({
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

onMounted(loadManuscripts);

async function loadManuscripts() {
  loading.value = true;
  try {
    manuscripts.value = await listManuscripts();
  } finally {
    loading.value = false;
  }
}

async function selectPdf(row: ManuscriptSummary, file: UploadFile) {
  if (!file.raw) {
    return;
  }
  await uploadPdf(row.manuscriptId, row.currentVersionId, file.raw);
  ElMessage.success("PDF uploaded.");
  await loadManuscripts();
}

function selectPdfForRow(row: ManuscriptSummary) {
  return (file: UploadFile) => selectPdf(row, file);
}

async function submit(row: ManuscriptSummary) {
  await submitVersion(row.manuscriptId, row.currentVersionId);
  ElMessage.success("Version submitted.");
  await loadManuscripts();
}

async function download(row: ManuscriptSummary) {
  const blob = await downloadPdf(row.manuscriptId, row.currentVersionId);
  const url = URL.createObjectURL(blob);
  window.open(url, "_blank", "noopener");
}

function openRevision(row: ManuscriptSummary) {
  revisionManuscriptId.value = row.manuscriptId;
  revisionForm.value = {
    title: row.currentVersionTitle,
    abstract: "",
    keywords: "",
    authors: revisionForm.value.authors
  };
  revisionDialogOpen.value = true;
}

async function submitRevision() {
  if (revisionManuscriptId.value == null) {
    return;
  }
  await createRevision(revisionManuscriptId.value, revisionForm.value);
  revisionDialogOpen.value = false;
  ElMessage.success("Revision draft created.");
  await loadManuscripts();
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Author</p>
        <h1>My manuscripts</h1>
        <p class="body">Track submissions, upload PDFs, and submit draft versions.</p>
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
          <el-tag>{{ row.currentStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastDecisionCode" label="Last decision" width="160" />
      <el-table-column label="Actions" width="410">
        <template #default="{ row }">
          <div class="action-row">
            <el-upload :auto-upload="false" :show-file-list="false" :on-change="selectPdfForRow(row)">
              <el-button size="small">Upload PDF</el-button>
            </el-upload>
            <el-button size="small" @click="download(row)">Download PDF</el-button>
            <el-button size="small" type="primary" @click="submit(row)">Submit</el-button>
            <el-button
              size="small"
              :disabled="row.currentStatus !== 'REVISION_REQUIRED'"
              @click="openRevision(row)"
            >
              Create revision
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="revisionDialogOpen" title="Create revision" width="640px">
      <el-form label-position="top">
        <el-form-item label="Title">
          <el-input v-model="revisionForm.title" />
        </el-form-item>
        <el-form-item label="Abstract">
          <el-input v-model="revisionForm.abstract" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="Keywords">
          <el-input v-model="revisionForm.keywords" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="revisionDialogOpen = false">Cancel</el-button>
        <el-button type="primary" @click="submitRevision">Create revision</el-button>
      </template>
    </el-dialog>
  </section>
</template>
