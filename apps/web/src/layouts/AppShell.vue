<script setup lang="ts">
import { computed } from "vue";
import { RouterView, useRoute, useRouter } from "vue-router";

import { authState, logout } from "../stores/auth";

const route = useRoute();
const router = useRouter();

const contentClass = computed(() => ({
  "content--review-editor": route.name === "reviewer-review-editor"
}));

const navItems = computed(() => {
  const roles = new Set(authState.user?.roles ?? []);
  const items = [];
  if (roles.has("AUTHOR")) {
    items.push({ label: "My manuscripts", target: "/author/manuscripts" });
    items.push({ label: "Submit manuscript", target: "/author/submit" });
  }
  if (roles.has("REVIEWER")) {
    items.push({ label: "Review assignments", target: "/reviewer/assignments" });
    items.push({ label: "Reviewer bidding", target: "/reviewer/bidding" });
  }
  if (roles.has("CHAIR") || roles.has("ADMIN")) {
    items.push({ label: "Conferences", target: "/chair/conferences" });
    items.push({ label: "Screening", target: "/chair/screening" });
    items.push({ label: "Decisions", target: "/chair/decisions" });
    items.push({ label: "Assignment ops", target: "/chair/assignment-operations" });
    items.push({ label: "Publication ops", target: "/chair/publication-operations" });
  }
  if (roles.has("ADMIN")) {
    items.push({ label: "Agent monitor", target: "/admin/agents" });
  }
  return items;
});

async function signOut() {
  logout();
  await router.push("/login");
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/dashboard">Review System</RouterLink>
      <nav aria-label="Main navigation">
        <RouterLink v-for="item in navItems" :key="item.label" :to="item.target">
          {{ item.label }}
        </RouterLink>
      </nav>
      <div class="user-menu">
        <span>{{ authState.user?.username }}</span>
        <el-button data-test="logout" size="small" @click="signOut">Logout</el-button>
      </div>
    </header>

    <main class="content" :class="contentClass">
      <RouterView />
    </main>
  </div>
</template>
