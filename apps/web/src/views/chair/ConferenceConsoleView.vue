<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";

import { apiErrorMessage } from "../../composables/useApiError";
import { authState } from "../../stores/auth";
import {
  addConferenceReviewer,
  approveConference,
  createConferenceDraft,
  listPendingConferenceApprovals,
  submitConferenceForApproval,
  type ConferenceCfpSummary,
  type ConferenceDetail
} from "../../lib/workflow-api";
import { formatDateTime, statusTagType, workflowLabel } from "../../lib/workflow-format";

const isAdmin = computed(() => authState.user?.roles.includes("ADMIN") ?? false);
const loading = ref(false);
const pendingLoading = ref(false);
const error = ref("");
const createdConference = ref<ConferenceDetail | null>(null);
const pendingConferences = ref<ConferenceCfpSummary[]>([]);

const form = reactive({
  name: "",
  acronym: "",
  year: new Date().getFullYear(),
  publicSlug: "",
  blindMode: "DOUBLE_BLIND",
  cfpText: "",
  topicAreas: "",
  targetReviewsPerPaper: 3,
  defaultReviewerMaxLoad: 4,
  submissionOpenAt: "2026-05-01T00:00:00Z",
  submissionCloseAt: "2026-06-01T00:00:00Z",
  biddingOpenAt: "2026-06-02T00:00:00Z",
  biddingCloseAt: "2026-06-10T00:00:00Z",
  reviewDeadlineAt: "2026-07-01T00:00:00Z",
  decisionReleaseAt: "2026-07-15T00:00:00Z"
});

const reviewerForm = reactive({
  conferenceId: 0,
  reviewerId: 0,
  maxLoad: 3
});

onMounted(() => {
  if (isAdmin.value) {
    void loadPending();
  }
});

async function createDraft() {
  loading.value = true;
  error.value = "";
  try {
    createdConference.value = await createConferenceDraft({
      name: form.name.trim(),
      acronym: form.acronym.trim(),
      year: Number(form.year),
      blindMode: form.blindMode,
      cfpText: form.cfpText.trim() || `${form.acronym} ${form.year} call for papers`,
      topicAreas: form.topicAreas.split(",").map((entry) => entry.trim()).filter(Boolean),
      targetReviewsPerPaper: Number(form.targetReviewsPerPaper),
      defaultReviewerMaxLoad: Number(form.defaultReviewerMaxLoad),
      publicSlug: form.publicSlug.trim(),
      phase: {
        submissionOpenAt: form.submissionOpenAt,
        submissionCloseAt: form.submissionCloseAt,
        biddingOpenAt: form.biddingOpenAt,
        biddingCloseAt: form.biddingCloseAt,
        reviewDeadlineAt: form.reviewDeadlineAt,
        decisionReleaseAt: form.decisionReleaseAt
      }
    });
    reviewerForm.conferenceId = createdConference.value.conferenceId;
    ElMessage.success("Conference draft created.");
  } catch (err) {
    error.value = apiErrorMessage(err, "Conference draft could not be created.");
  } finally {
    loading.value = false;
  }
}

async function submitCreatedConference() {
  if (!createdConference.value) {
    return;
  }
  createdConference.value = await submitConferenceForApproval(createdConference.value.conferenceId);
  ElMessage.success("Conference submitted for approval.");
}

async function addReviewerToConference() {
  await addConferenceReviewer(
    Number(reviewerForm.conferenceId),
    Number(reviewerForm.reviewerId),
    Number(reviewerForm.maxLoad)
  );
  ElMessage.success("Reviewer added to conference pool.");
}

async function loadPending() {
  pendingLoading.value = true;
  try {
    pendingConferences.value = await listPendingConferenceApprovals();
  } finally {
    pendingLoading.value = false;
  }
}

async function approve(row: ConferenceCfpSummary) {
  await approveConference(row.conferenceId);
  await loadPending();
  ElMessage.success("Conference approved.");
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Chair console</p>
        <h1>Conferences</h1>
        <p class="body">Create CFPs, submit conferences for approval, and seed the reviewer pool.</p>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" />

    <section class="workflow-section">
      <h2>Create CFP draft</h2>
      <el-form class="workflow-form" label-position="top" @submit.prevent="createDraft">
        <div class="score-grid">
          <el-form-item label="Name">
            <el-input v-model="form.name" data-test="conference-name" />
          </el-form-item>
          <el-form-item label="Acronym">
            <el-input v-model="form.acronym" data-test="conference-acronym" />
          </el-form-item>
          <el-form-item label="Year">
            <el-input-number v-model="form.year" data-test="conference-year" :min="2026" />
          </el-form-item>
          <el-form-item label="Public slug">
            <el-input v-model="form.publicSlug" data-test="conference-slug" />
          </el-form-item>
          <el-form-item label="Topics">
            <el-input v-model="form.topicAreas" data-test="conference-topics" placeholder="agents,systems" />
          </el-form-item>
          <el-form-item label="Blind mode">
            <el-select v-model="form.blindMode">
              <el-option label="Double blind" value="DOUBLE_BLIND" />
              <el-option label="Single blind" value="SINGLE_BLIND" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="CFP text">
          <el-input v-model="form.cfpText" type="textarea" :rows="3" />
        </el-form-item>
        <div class="action-row">
          <el-button type="primary" native-type="submit" :loading="loading">Create draft</el-button>
          <el-button :disabled="!createdConference" @click="submitCreatedConference">Submit for approval</el-button>
        </div>
      </el-form>
      <el-alert
        v-if="createdConference"
        :title="`${createdConference.name} is ${workflowLabel(createdConference.status)}`"
        type="success"
        :closable="false"
      />
    </section>

    <section class="workflow-section">
      <h2>Reviewer pool</h2>
      <el-form class="workflow-form" label-position="top" @submit.prevent="addReviewerToConference">
        <div class="score-grid">
          <el-form-item label="Conference id">
            <el-input-number v-model="reviewerForm.conferenceId" :min="1" />
          </el-form-item>
          <el-form-item label="Reviewer id">
            <el-input-number v-model="reviewerForm.reviewerId" :min="1" />
          </el-form-item>
          <el-form-item label="Max load">
            <el-input-number v-model="reviewerForm.maxLoad" :min="1" :max="20" />
          </el-form-item>
        </div>
        <el-button type="primary" native-type="submit">Add reviewer</el-button>
      </el-form>
    </section>

    <section v-if="isAdmin" class="workflow-section">
      <div class="subsection-heading">
        <h2>Conference approvals</h2>
        <el-button @click="loadPending">Refresh</el-button>
      </div>
      <el-table v-loading="pendingLoading" :data="pendingConferences" empty-text="No pending conferences.">
        <el-table-column prop="acronym" label="Acronym" width="120" />
        <el-table-column label="Conference">
          <template #default="{ row }">
            <strong>{{ row.name }}</strong>
            <p class="muted-line">{{ formatDateTime(row.submissionCloseAt) }}</p>
          </template>
        </el-table-column>
        <el-table-column label="Status" width="180">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ workflowLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="160">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="approve(row)">Approve</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>
