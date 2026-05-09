<script setup lang="ts">
import type { FormInstance, FormItemRule, FormRules, UploadFile } from "element-plus";
import { ElMessage } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";

import { useApiError } from "../../composables/useApiError";
import { useAsyncAction } from "../../composables/useAsyncAction";
import {
  createManuscript,
  getManuscriptForm,
  listPublicCfps,
  saveManuscriptFormResponse,
  submitVersion,
  uploadPdf,
  type AuthorInput,
  type ConferenceCfpSummary,
  type ManuscriptSummary,
  type WorkflowFormPackage
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
const submissionChecklist = ref<WorkflowFormPackage | null>(null);
const checklistSubmitting = ref(false);
const checklistAnswers = reactive<Record<string, unknown>>({});
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
  conferenceId: [{ required: true, message: "请选择会议", trigger: "change" }],
  title: [requiredTrimmed("请输入标题")],
  abstract: [requiredTrimmed("请输入摘要")],
  keywords: [requiredTrimmed("请输入关键词")]
};

const selectedConference = computed(() =>
  submissionTargetConferences.value.find((conference) => conference.conferenceId === form.conferenceId) ?? null
);

const createdSubmissionNumber = computed(() =>
  created.value?.submissionNumber ?? (created.value ? `PAPER-${created.value.manuscriptId}` : "")
);

const submissionTargetConferences = computed(() =>
  conferences.value.filter(isOpenSubmissionConference)
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
    await loadSubmissionChecklist();
    ElMessage.success(`摘要已提交，取号编号：${createdSubmissionNumber.value}`);
  } catch (error) {
    showApiError(error, "摘要提交失败。");
  } finally {
    submitting.value = false;
  }
}

async function loadSubmissionChecklist() {
  if (!created.value) {
    return;
  }
  try {
    submissionChecklist.value = await getManuscriptForm(created.value.manuscriptId, "SUBMISSION");
    Object.keys(checklistAnswers).forEach((key) => delete checklistAnswers[key]);
    for (const field of submissionChecklist.value.form.fields) {
      checklistAnswers[field.fieldKey] = submissionChecklist.value.currentResponse?.answers?.[field.fieldKey] ?? "";
    }
  } catch {
    submissionChecklist.value = null;
  }
}

async function submitChecklist() {
  if (!created.value || !submissionChecklist.value) {
    return;
  }
  if (hasMissingRequiredChecklistAnswer()) {
    ElMessage.error("核对清单必填项必须完成。");
    return;
  }
  checklistSubmitting.value = true;
  try {
    await saveManuscriptFormResponse(created.value.manuscriptId, {
      formId: submissionChecklist.value.form.formId,
      responseStatus: "SUBMITTED",
      answers: { ...checklistAnswers }
    });
    ElMessage.success("投稿核对清单已提交。");
    await loadSubmissionChecklist();
  } catch (error) {
    showApiError(error, "投稿核对清单提交失败。");
  } finally {
    checklistSubmitting.value = false;
  }
}

function hasMissingRequiredChecklistAnswer() {
  return Boolean(submissionChecklist.value?.form.fields.some((field) =>
    field.required && !String(checklistAnswers[field.fieldKey] ?? "").trim()
  ));
}

async function uploadSelectedPdf() {
  if (!created.value || !selectedPdf.value) {
    return;
  }
  await actions.run("upload-pdf", async () => {
    try {
      await uploadPdf(created.value!.manuscriptId, created.value!.currentVersionId, selectedPdf.value!);
      ElMessage.success("PDF 已上传。");
    } catch (error) {
      showApiError(error, "PDF 上传失败。");
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
      ElMessage.success("稿件已提交。");
      await router.push("/author/manuscripts");
    } catch (error) {
      showApiError(error, "稿件提交失败。");
    }
  });
}

async function loadConferences() {
  loadingConferences.value = true;
  try {
    conferences.value = await listPublicCfps();
    if (!submissionTargetConferences.value.some((conference) => conference.conferenceId === form.conferenceId)) {
      form.conferenceId = submissionTargetConferences.value[0]?.conferenceId;
    }
  } catch (error) {
    showApiError(error, "会议加载失败。");
  } finally {
    loadingConferences.value = false;
  }
}

function formatDeadline(value: string | null | undefined) {
  return formatDateTime(value);
}

function isOpenSubmissionConference(conference: ConferenceCfpSummary) {
  if (conference.status !== "OPEN_FOR_SUBMISSION") {
    return false;
  }
  const abstractCloseAt = conference.abstractSubmissionCloseAt ?? conference.submissionCloseAt;
  if (!abstractCloseAt) {
    return false;
  }
  const closeAt = Date.parse(abstractCloseAt);
  return Number.isFinite(closeAt) && closeAt > Date.now();
}

onMounted(() => {
  void loadConferences();
});
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">作者</p>
        <h1>提交稿件</h1>
        <p class="body">先提交摘要获取取号编号，再上传 PDF 并提交完整论文。</p>
        <p class="body">{{ PDF_UPLOAD_LIMIT_HINT }}</p>
      </div>
    </div>

    <el-form ref="draftFormRef" class="workflow-form" :model="form" :rules="draftRules" label-position="top" @submit.prevent="createDraft">
      <el-form-item label="会议" prop="conferenceId">
        <el-select v-model="form.conferenceId" data-test="manuscript-conference" :loading="loadingConferences" placeholder="选择会议">
          <el-option
            v-for="conference in submissionTargetConferences"
            :key="conference.conferenceId"
            :label="`${conference.name} ${conference.year}`"
            :value="conference.conferenceId"
          />
        </el-select>
      </el-form-item>
      <el-alert
        v-if="!loadingConferences && !submissionTargetConferences.length"
        class="workflow-alert compact-alert"
        title="当前没有正在征稿且未过截止时间的会议。"
        type="info"
        :closable="false"
      />
      <el-alert v-if="selectedConference" class="workflow-alert compact-alert" type="info" :closable="false">
        <template #title>
          {{ selectedConference.name }} 使用 {{ workflowLabel(selectedConference.blindMode) }} 评审。
        </template>
        摘要提交截止 {{ formatDeadline(selectedConference.abstractSubmissionCloseAt) }}；
        论文提交截止 {{ formatDeadline(selectedConference.submissionCloseAt) }}。
      </el-alert>
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" placeholder="论文标题" />
      </el-form-item>
      <el-form-item label="摘要" prop="abstract">
        <el-input v-model="form.abstract" type="textarea" :rows="5" />
      </el-form-item>
      <el-form-item label="关键词" prop="keywords">
        <el-input v-model="form.keywords" placeholder="逗号分隔的关键词" />
      </el-form-item>
      <section class="subsection">
        <div class="subsection-heading">
          <h2>作者</h2>
          <el-button @click="addAuthor">添加作者</el-button>
        </div>
        <div v-for="(author, index) in form.authors" :key="index" class="author-row">
          <el-input v-model="author.authorName" placeholder="姓名" />
          <el-input v-model="author.email" placeholder="邮箱" />
          <el-input v-model="author.institution" placeholder="所属机构" />
          <el-checkbox v-model="author.isCorresponding">通讯作者</el-checkbox>
          <el-button :disabled="form.authors.length === 1" @click="removeAuthor(index)">删除</el-button>
        </div>
      </section>

      <el-button type="primary" native-type="submit" :loading="submitting" :disabled="!!created">
        提交摘要并取号
      </el-button>
    </el-form>

    <el-alert
      v-if="created"
      class="workflow-alert"
      type="success"
      :closable="false"
    >
      <template #title>
        摘要已提交，取号编号：{{ createdSubmissionNumber }}。
        <el-tag class="inline-status" :type="statusTagType(created.currentStatus)">
          {{ workflowLabel(created.currentStatus) }}
        </el-tag>
      </template>
      请在论文提交截止前上传 PDF，并提交完整论文。
    </el-alert>

    <section v-if="created && submissionChecklist" class="workflow-form review-form-panel">
      <div class="subsection-heading">
        <h2>{{ submissionChecklist.form.formName }}</h2>
        <el-tag :type="statusTagType(submissionChecklist.currentResponse?.responseStatus)">
          {{ workflowLabel(submissionChecklist.currentResponse?.responseStatus || "DRAFT") }}
        </el-tag>
      </div>
      <el-form label-position="top" @submit.prevent="submitChecklist">
        <el-form-item
          v-for="field in submissionChecklist.form.fields"
          :key="field.fieldId"
          :label="field.fieldLabel"
          :required="field.required"
          :data-test="`submission-form-${field.fieldKey}`"
        >
          <el-input
            v-if="field.fieldType === 'LONG_TEXT' || field.fieldType === 'TEXT'"
            v-model="checklistAnswers[field.fieldKey]"
            type="textarea"
            :rows="field.fieldType === 'LONG_TEXT' ? 4 : 2"
          />
          <el-input-number
            v-else-if="field.fieldType === 'NUMBER' || field.fieldType === 'SCORE'"
            v-model="checklistAnswers[field.fieldKey]"
            :min="field.fieldType === 'SCORE' ? 1 : undefined"
            :max="field.fieldType === 'SCORE' ? 5 : undefined"
          />
          <el-switch v-else-if="field.fieldType === 'BOOLEAN'" v-model="checklistAnswers[field.fieldKey]" />
          <el-input v-else v-model="checklistAnswers[field.fieldKey]" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="checklistSubmitting">提交核对清单</el-button>
      </el-form>
    </section>

    <div v-if="created" class="upload-actions">
      <p class="body">{{ PDF_UPLOAD_LIMIT_HINT }}</p>
      <el-upload :auto-upload="false" :limit="1" :on-change="selectPdf">
        <el-button>选择 PDF</el-button>
      </el-upload>
      <el-button :disabled="!selectedPdf" :loading="actions.isPending('upload-pdf')" @click="uploadSelectedPdf">上传 PDF</el-button>
      <el-button type="primary" :loading="actions.isPending('submit-version')" @click="submitCurrentVersion">提交版本</el-button>
    </div>
  </section>
</template>
