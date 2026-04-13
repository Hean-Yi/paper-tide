<script setup lang="ts">
import { onMounted, ref } from "vue";

import { listAgentTasks, type AgentTask } from "../../lib/workflow-api";

const loading = ref(false);
const tasks = ref<AgentTask[]>([]);
const filters = ref({ status: "", taskType: "" });

onMounted(loadTasks);

async function loadTasks() {
  loading.value = true;
  try {
    tasks.value = await listAgentTasks({
      status: filters.value.status || undefined,
      taskType: filters.value.taskType || undefined
    });
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Admin</p>
        <h1>Agent monitor</h1>
        <p class="body">Inspect task identifiers, status, and execution summaries.</p>
      </div>
      <el-button @click="loadTasks">Refresh</el-button>
    </div>

    <el-form class="filter-bar" inline>
      <el-form-item label="Status">
        <el-select v-model="filters.status" clearable placeholder="Any status">
          <el-option label="Pending" value="PENDING" />
          <el-option label="Processing" value="PROCESSING" />
          <el-option label="Success" value="SUCCESS" />
          <el-option label="Failed" value="FAILED" />
        </el-select>
      </el-form-item>
      <el-form-item label="Type">
        <el-select v-model="filters.taskType" clearable placeholder="Any type">
          <el-option label="Screening analysis" value="SCREENING_ANALYSIS" />
          <el-option label="Review assist analysis" value="REVIEW_ASSIST_ANALYSIS" />
          <el-option label="Decision conflict analysis" value="DECISION_CONFLICT_ANALYSIS" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadTasks">Apply</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tasks" empty-text="No agent tasks.">
      <el-table-column prop="taskId" label="Task" width="100" />
      <el-table-column prop="externalTaskId" label="External task" min-width="180" />
      <el-table-column prop="taskType" label="Type" min-width="210" />
      <el-table-column prop="taskStatus" label="Status" width="130">
        <template #default="{ row }">
          <el-tag>{{ row.taskStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Scope" min-width="170">
        <template #default="{ row }">
          M{{ row.manuscriptId }} / V{{ row.versionId }}<span v-if="row.roundId"> / R{{ row.roundId }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="Created" min-width="190" />
      <el-table-column prop="finishedAt" label="Finished" min-width="190" />
      <el-table-column prop="resultSummary" label="Summary" min-width="220" />
    </el-table>
  </section>
</template>
