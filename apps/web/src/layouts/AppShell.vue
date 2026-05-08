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
    items.push({ label: "我的稿件", target: "/author/manuscripts" });
    items.push({ label: "提交稿件", target: "/author/submit" });
  }
  if (roles.has("REVIEWER")) {
    items.push({ label: "评审任务", target: "/reviewer/assignments" });
    items.push({ label: "竞标投票", target: "/reviewer/bidding" });
  }
  if (roles.has("CHAIR") || roles.has("ADMIN")) {
    items.push({ label: "会议管理", target: "/chair/conferences" });
    items.push({ label: "初筛队列", target: "/chair/screening" });
    items.push({ label: "决策工作台", target: "/chair/decisions" });
    items.push({ label: "分配操作", target: "/chair/assignment-operations" });
    items.push({ label: "出版操作", target: "/chair/publication-operations" });
  }
  if (roles.has("ADMIN")) {
    items.push({ label: "Agent 监控", target: "/admin/agents" });
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
      <RouterLink class="brand" to="/dashboard">论文评审系统</RouterLink>
      <nav aria-label="Main navigation">
        <RouterLink v-for="item in navItems" :key="item.label" :to="item.target">
          {{ item.label }}
        </RouterLink>
      </nav>
      <div class="user-menu">
        <span>{{ authState.user?.username }}</span>
        <el-button data-test="logout" size="small" @click="signOut">退出登录</el-button>
      </div>
    </header>

    <main class="content" :class="contentClass">
      <RouterView />
    </main>
  </div>
</template>
