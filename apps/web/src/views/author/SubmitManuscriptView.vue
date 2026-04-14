<script setup lang="ts">
import type { FormInstance, FormItemRule, FormRules, UploadFile } from "element-plus";
import { ElMessage } from "element-plus";
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";

import {
  createManuscript,
  submitVersion,
  uploadPdf,
  type AuthorInput,
  type ManuscriptSummary
} from "../../lib/workflow-api";
import { statusTagType, workflowLabel } from "../../lib/workflow-format";

const router = useRouter();
const submitting = ref(false);
const created = ref<ManuscriptSummary | null>(null);
const selectedPdf = ref<File | null>(null);
const draftFormRef = ref<FormInstance>();
const form = reactive({
  title: "",
  abstract: "",
  keywords: "",
  blindMode: "DOUBLE_BLIND",
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
const requiredTrimmed = (message: string): FormItemRule => ({
  trigger: "blur",
  validator: (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
    if (String(value ?? "").trim()) {
      callback();
      return;
    }
    callback(new Error(message));
  }
});
const draftRules: FormRules = {
  title: [requiredTrimmed("Title is required")],
  abstract: [requiredTrimmed("Abstract is required")],
  keywords: [requiredTrimmed("Keywords are required")],
  blindMode: [{ required: true, message: "Blind mode is required", trigger: "change" }]
};

function hasRequiredDraftValues() {
  return Boolean(form.title.trim() && form.abstract.trim() && form.keywords.trim() && form.blindMode);
}

function addAuthor() {
  form.authors.push({
    authorName: "",
    email: "",
    institution: "",
    authorOrder: form.authors.length + 1,
    userId: null,
    isCorresponding: false,
    isExternal: true
  });
}

function removeAuthor(index: number) {
  form.authors.splice(index, 1);
  form.authors.forEach((author, authorIndex) => {
    author.authorOrder = authorIndex + 1;
  });
}

function selectPdf(file: UploadFile) {
  selectedPdf.value = file.raw ?? null;
}

async function createDraft() {
  const valid = await draftFormRef.value?.validate().catch(() => false);
  if (!valid || !hasRequiredDraftValues()) {
    return;
  }
  submitting.value = true;
  try {
    created.value = await createManuscript(form);
    ElMessage.success("Manuscript created.");
  } finally {
    submitting.value = false;
  }
}

async function uploadSelectedPdf() {
  if (!created.value || !selectedPdf.value) {
    return;
  }
  await uploadPdf(created.value.manuscriptId, created.value.currentVersionId, selectedPdf.value);
  ElMessage.success("PDF uploaded.");
}

async function submitCurrentVersion() {
  if (!created.value) {
    return;
  }
  await submitVersion(created.value.manuscriptId, created.value.currentVersionId);
  ElMessage.success("Manuscript submitted.");
  await router.push("/author/manuscripts");
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">Author</p>
        <h1>Submit manuscript</h1>
        <p class="body">Create the manuscript record, upload the PDF, then submit the current version.</p>
      </div>
    </div>

    <el-form ref="draftFormRef" class="workflow-form" :model="form" :rules="draftRules" label-position="top" @submit.prevent="createDraft">
      <el-form-item label="Title" prop="title">
        <el-input v-model="form.title" />
      </el-form-item>
      <el-form-item label="Abstract" prop="abstract">
        <el-input v-model="form.abstract" type="textarea" :rows="5" />
      </el-form-item>
      <el-form-item label="Keywords" prop="keywords">
        <el-input v-model="form.keywords" placeholder="comma,separated,keywords" />
      </el-form-item>
      <el-form-item label="Blind mode" prop="blindMode">
        <el-select v-model="form.blindMode">
          <el-option label="Double blind" value="DOUBLE_BLIND" />
          <el-option label="Single blind" value="SINGLE_BLIND" />
          <el-option label="Open" value="OPEN" />
        </el-select>
      </el-form-item>

      <section class="subsection">
        <div class="subsection-heading">
          <h2>Authors</h2>
          <el-button @click="addAuthor">Add author</el-button>
        </div>
        <div v-for="(author, index) in form.authors" :key="index" class="author-row">
          <el-input v-model="author.authorName" placeholder="Name" />
          <el-input v-model="author.email" placeholder="Email" />
          <el-input v-model="author.institution" placeholder="Institution" />
          <el-checkbox v-model="author.isCorresponding">Corresponding</el-checkbox>
          <el-button :disabled="form.authors.length === 1" @click="removeAuthor(index)">Remove</el-button>
        </div>
      </section>

      <el-button type="primary" native-type="submit" :loading="submitting" :disabled="!!created">
        Create manuscript
      </el-button>
    </el-form>

    <el-alert
      v-if="created"
      class="workflow-alert"
      type="success"
      :closable="false"
    >
      <template #title>
        Manuscript record created.
        <el-tag class="inline-status" :type="statusTagType(created.currentStatus)">
          {{ workflowLabel(created.currentStatus) }}
        </el-tag>
      </template>
      Upload the PDF before final submission.
    </el-alert>

    <div v-if="created" class="upload-actions">
      <el-upload :auto-upload="false" :limit="1" :on-change="selectPdf">
        <el-button>Select PDF</el-button>
      </el-upload>
      <el-button :disabled="!selectedPdf" @click="uploadSelectedPdf">Upload PDF</el-button>
      <el-button type="primary" @click="submitCurrentVersion">Submit version</el-button>
    </div>
  </section>
</template>
