<script setup lang="ts">
</script>

<template>
  <section class="workflow-page">
    <div class="page-heading dossier-header">
      <div>
        <p class="eyebrow">Admin</p>
        <h1>Agent monitor</h1>
        <p class="body">Inspect task identifiers, status, and execution summaries.</p>
      </div>
      <el-button disabled>Intent governance pending</el-button>
    </div>

    <el-card>
      <p class="body">
        The first-generation mirrored task monitor has been retired. Agent execution now flows through analysis
        intents, outbox commands, and projections. Task 19 will add the replacement governance view for those
        runtime records.
      </p>
    </el-card>
  </section>
</template>
