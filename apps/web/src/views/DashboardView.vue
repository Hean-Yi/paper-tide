<script setup lang="ts">
import { computed } from "vue";

import { authState } from "../stores/auth";

const entries = computed(() => {
  const roles = new Set(authState.user?.roles ?? []);
  const items = [];
  if (roles.has("AUTHOR")) {
    items.push({ label: "My manuscripts", detail: "Drafts, submissions, and revisions.", target: "/author/manuscripts" });
    items.push({ label: "Submit manuscript", detail: "Create a paper record and upload the PDF.", target: "/author/submit" });
  }
  if (roles.has("REVIEWER")) {
    items.push({ label: "Review assignments", detail: "Assigned papers and review deadlines.", target: "/reviewer/assignments" });
  }
  if (roles.has("CHAIR") || roles.has("ADMIN")) {
    items.push({ label: "Screening", detail: "Submission checks and reviewer coordination.", target: "/chair/screening" });
    items.push({ label: "Decisions", detail: "Round decisions and conflict analysis.", target: "/chair/decisions" });
  }
  if (roles.has("ADMIN")) {
    items.push({ label: "Agent monitor", detail: "Analysis intent, projection, and execution checks.", target: "/admin/agents" });
  }
  return items;
});
</script>

<template>
  <section class="dashboard">
    <div class="dossier-header">
      <p class="eyebrow">Dashboard</p>
      <h1>Welcome, {{ authState.user?.username }}</h1>
      <p class="body">Choose an entry point to continue.</p>
    </div>

    <div class="entry-grid">
      <RouterLink v-for="entry in entries" :key="entry.label" class="entry-card" :to="entry.target">
        <h2>{{ entry.label }}</h2>
        <p>{{ entry.detail }}</p>
      </RouterLink>
    </div>
  </section>
</template>
