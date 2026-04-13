<script setup lang="ts">
import { computed } from "vue";
import { RouterView, useRouter } from "vue-router";

import { authState, logout } from "../stores/auth";

const router = useRouter();

const navItems = computed(() => {
  const roles = new Set(authState.user?.roles ?? []);
  const items = [];
  if (roles.has("AUTHOR")) {
    items.push({ label: "My manuscripts", target: "/author/manuscripts" });
    items.push({ label: "Submit manuscript", target: "/author/submit" });
  }
  if (roles.has("REVIEWER")) {
    items.push({ label: "Review assignments", target: "/reviewer/assignments" });
  }
  if (roles.has("CHAIR") || roles.has("ADMIN")) {
    items.push({ label: "Screening", target: "/chair/screening" });
    items.push({ label: "Decisions", target: "/chair/decisions" });
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

    <main class="content">
      <RouterView />
    </main>
  </div>
</template>
