<script setup lang="ts">
import type { FormInstance, FormItemRule, FormRules, UploadFile } from "element-plus";
import { ElMessage } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";

import { useApiError } from "../../composables/useApiError";
import { useAsyncAction } from "../../composables/useAsyncAction";
import {
  createManuscript,
  listPublicCfps,
  submitVersion,
  uploadPdf,
  type AuthorInput,
  type ConferenceCfpSummary,
  type ManuscriptSummary
} from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";
import { PDF_UPLOAD_LIMIT_ERROR, PDF_UPLOAD_LIMIT_HINT, PDF_UPLOAD_MAX_BYTES } from "../../lib/upload";

const router = useRouter();
const submitting = ref(false);
const actions = useAsyncAction();
const { showApiError } = useApiError();
const created = ref<ManuscriptSummary | null>(null);
const conferences = ref<ConferenceCfpSummary[]>([]);
const loadingConferences = ref(false);
const selectedPdf = ref<File | null>(null);
const draftFormRef = ref<FormInstance>();
const form = reactive({
  conferenceId: undefined as number | undefined,
  title: "",
  abstract: "",
  keywords: "",
  blindMode: "",
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
  conferenceId: [{ required: true, message: "Conference is required", trigger: "change" }],
  title: [requiredTrimmed("Title is required")],
  abstract: [requiredTrimmed("Abstract is required")],
  keywords: [requiredTrimmed("Keywords are required")]
};

const selectedConference = computed(() =>
  conferences.value.find((conference) => conference.conferenceId === form.conferenceId) ?? null
);

function hasRequiredDraftValues() {
  return Boolean(form.conferenceId && form.title.trim() && form.abstract.trim() && form.keywords.trim());
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
  const raw = file.raw ?? null;
  if (!raw) {
    selectedPdf.value = null;
    return;
  }
  if (raw.size > PDF_UPLOAD_MAX_BYTES) {
    selectedPdf.value = null;
    ElMessage.error(PDF_UPLOAD_LIMIT_ERROR);
    return;
  }
  selectedPdf.value = raw;
}

async function createDraft() {
  const valid = await draftFormRef.value?.validate().catch(() => false);
  if (!valid || !hasRequiredDraftValues()) {
    return;
  }
  submitting.value = true;
  try {
    if (!form.conferenceId) {
      return;
    }
    created.value = await createManuscript({
      conferenceId: form.conferenceId,
      title: form.title,
      abstract: form.abstract,
      keywords: form.keywords,
      authors: form.authors
    });
    ElMessage.success("Manuscript created.");
  } catch (error) {
    showApiError(error, "Manuscript could not be created.");
  } finally {
    submitting.value = false;
  }
}

async function uploadSelectedPdf() {
  if (!created.value || !selectedPdf.value) {
    return;
  }
  await actions.run("upload-pdf", async () => {
    try {
      await uploadPdf(created.value!.manuscriptId, created.value!.currentVersionId, selectedPdf.value!);
      ElMessage.success("PDF uploaded.");
    } catch (error) {
      showApiError(error, "PDF could not be uploaded.");
    }
  });
}

async function submitCurrentVersion() {
  if (!created.value) {
    return;
  }
  await actions.run("submit-version", async () => {
    try {
      await submitVersion(created.value!.manuscriptId, created.value!.currentVersionId);
      ElMessage.success("Manuscript submitted.");
      await router.push("/author/manuscripts");
    } catch (error) {
      showApiError(error, "Manuscript could not be submitted.");
    }
  });
}

async function loadConferences() {
  loadingConferences.value = true;
  try {
    conferences.value = await listPublicCfps();
    if (!form.conferenceId && conferences.value.length > 0) {
      form.conferenceId = conferences.value[0].conferenceId;
    }
  } catch (error) {
    showApiError(error, "Conferences could not be loaded.");
  } finally {
    loadingConferences.value = false;
  }
}

function formatDeadline(value: string | null | undefined) {
  return formatDateTime(value);
}

onMounted(() => {
  void loadConferences();
});
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">Author</p>
        <h1>Submit manuscript</h1>
        <p class="body">Create the manuscript record, upload the PDF, then submit the current version.</p>
        <p class="body">{{ PDF_UPLOAD_LIMIT_HINT }}</p>
      </div>
    </div>

    <el-form ref="draftFormRef" class="workflow-form" :model="form" :rules="draftRules" label-position="top" @submit.prevent="createDraft">
      <el-form-item label="Conference" prop="conferenceId">
        <el-select v-model="form.conferenceId" data-test="manuscript-conference" :loading="loadingConferences" placeholder="Select conference">
          <el-option
            v-for="conference in conferences"
            :key="conference.conferenceId"
            :label="`${conference.name} ${conference.year}`"
            :value="conference.conferenceId"
          />
        </el-select>
      </el-form-item>
      <el-alert v-if="selectedConference" class="workflow-alert compact-alert" type="info" :closable="false">
        <template #title>
          {{ selectedConference.name }} uses {{ workflowLabel(selectedConference.blindMode) }} review.
        </template>
        Submission closes {{ formatDeadline(selectedConference.submissionCloseAt) }}.
      </el-alert>
      <el-form-item label="Title" prop="title">
        <el-input v-model="form.title" placeholder="Title" />
      </el-form-item>
      <el-form-item label="Abstract" prop="abstract">
        <el-input v-model="form.abstract" type="textarea" :rows="5" />
      </el-form-item>
      <el-form-item label="Keywords" prop="keywords">
        <el-input v-model="form.keywords" placeholder="comma,separated,keywords" />
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
      <p class="body">{{ PDF_UPLOAD_LIMIT_HINT }}</p>
      <el-upload :auto-upload="false" :limit="1" :on-change="selectPdf">
        <el-button>Select PDF</el-button>
      </el-upload>
      <el-button :disabled="!selectedPdf" :loading="actions.isPending('upload-pdf')" @click="uploadSelectedPdf">Upload PDF</el-button>
      <el-button type="primary" :loading="actions.isPending('submit-version')" @click="submitCurrentVersion">Submit version</el-button>
    </div>
  </section>
</template>
