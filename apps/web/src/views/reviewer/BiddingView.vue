<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";

import { apiErrorMessage } from "../../composables/useApiError";
import {
  listPublicCfps,
  listReviewerBiddingItems,
  submitReviewerBid,
  type ConferenceCfpSummary,
  type ReviewerBiddingItem
} from "../../lib/workflow-api";
import { statusTagType, workflowLabel } from "../../lib/workflow-format";

const conferences = ref<ConferenceCfpSummary[]>([]);
const selectedConferenceId = ref<number | null>(null);
const papers = ref<ReviewerBiddingItem[]>([]);
const loading = ref(false);
const error = ref("");

const selectedConference = computed(() => conferences.value.find((item) => item.conferenceId === selectedConferenceId.value) ?? null);

onMounted(loadConferences);

async function loadConferences() {
  conferences.value = await listPublicCfps();
  selectedConferenceId.value = conferences.value.find((item) => item.status === "BIDDING_OPEN")?.conferenceId
    ?? conferences.value[0]?.conferenceId
    ?? null;
}

async function loadPapers() {
  if (!selectedConferenceId.value) {
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    papers.value = await listReviewerBiddingItems(selectedConferenceId.value);
  } catch (err) {
    error.value = apiErrorMessage(err, "Bidding papers are unavailable.");
  } finally {
    loading.value = false;
  }
}

async function submitBid(row: ReviewerBiddingItem, bidValue: string) {
  if (!selectedConferenceId.value) {
    return;
  }
  await submitReviewerBid(selectedConferenceId.value, {
    manuscriptId: row.manuscriptId,
    bidValue,
    conflictDeclared: bidValue === "CONFLICT",
    conflictType: bidValue === "CONFLICT" ? "DECLARED" : null,
    conflictDescription: bidValue === "CONFLICT" ? "Reviewer declared conflict during bidding." : null
  });
  row.bidValue = bidValue;
  row.conflictDeclared = bidValue === "CONFLICT";
  ElMessage.success("Bid saved.");
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Reviewer bidding</p>
        <h1>Reviewer bidding</h1>
        <p class="body">Choose a bidding-open conference and submit preference or conflict signals.</p>
      </div>
      <div class="header-actions">
        <el-select v-model="selectedConferenceId" placeholder="Conference" style="width: 260px">
          <el-option
            v-for="conference in conferences"
            :key="conference.conferenceId"
            :label="`${conference.acronym} ${conference.year}`"
            :value="conference.conferenceId"
          />
        </el-select>
        <el-button type="primary" :disabled="!selectedConferenceId" @click="loadPapers">Load papers</el-button>
      </div>
    </div>

    <el-alert v-if="selectedConference" type="info" :closable="false">
      <template #title>
        {{ selectedConference.name }} ·
        <el-tag :type="statusTagType(selectedConference.status)">{{ workflowLabel(selectedConference.status) }}</el-tag>
      </template>
    </el-alert>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />

    <el-table v-loading="loading" :data="papers" empty-text="No papers open for bidding.">
      <el-table-column label="Paper" min-width="260">
        <template #default="{ row }">
          <strong>{{ row.title }}</strong>
          <p class="muted-line">{{ row.keywords || "No keywords" }}</p>
        </template>
      </el-table-column>
      <el-table-column prop="abstractText" label="Abstract" min-width="320" />
      <el-table-column label="Current bid" width="160">
        <template #default="{ row }">
          <el-tag :type="row.conflictDeclared ? 'danger' : 'info'">{{ workflowLabel(row.bidValue || "NONE") }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Actions" width="360">
        <template #default="{ row }">
          <div class="action-row">
            <el-button size="small" @click="submitBid(row, 'WANT_TO_REVIEW')">Want to review</el-button>
            <el-button size="small" @click="submitBid(row, 'CAN_REVIEW')">Can review</el-button>
            <el-button size="small" type="danger" @click="submitBid(row, 'CONFLICT')">Declare conflict</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
