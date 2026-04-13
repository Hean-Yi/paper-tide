<script setup lang="ts">
import { computed } from "vue";

import { authState } from "../stores/auth";

const entries = computed(() => {
  const roles = new Set(authState.user?.roles ?? []);
  const items = [];
  if (roles.has("AUTHOR")) {
    items.push({ label: "My manuscripts", detail: "Drafts, submissions, and revisions." });
  }
  if (roles.has("REVIEWER")) {
    items.push({ label: "Review assignments", detail: "Assigned papers and review deadlines." });
  }
  if (roles.has("CHAIR")) {
    items.push({ label: "Screening", detail: "Submission checks and reviewer coordination." });
    items.push({ label: "Decisions", detail: "Round decisions and conflict analysis." });
  }
  if (roles.has("ADMIN")) {
    items.push({ label: "Audit logs", detail: "System activity and protected records." });
    items.push({ label: "Agent monitor", detail: "Agent task status and result checks." });
  }
  return items;
});
</script>

<template>
  <section class="dashboard">
    <p class="eyebrow">Dashboard</p>
    <h1>Welcome, {{ authState.user?.username }}</h1>
    <p class="body">Choose an entry point to continue.</p>

    <div class="entry-grid">
      <article v-for="entry in entries" :key="entry.label" class="entry-card">
        <h2>{{ entry.label }}</h2>
        <p>{{ entry.detail }}</p>
      </article>
    </div>
  </section>
</template>
