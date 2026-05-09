<script setup lang="ts">
import { computed, onMounted, watch } from "vue";
import { RouterView, useRoute, useRouter } from "vue-router";
import { ElMessageBox } from "element-plus";

import { getReviewerInterfaceChoice } from "../lib/workflow-api";
import { authState, logout } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const promptedInterfaceUsers = new Set<number>();

const contentClass = computed(() => ({
  "content--review-editor": route.name === "reviewer-review-editor"
}));

const navItems = computed(() => {
  const roles = new Set(authState.user?.roles ?? []);
  const items = [];
  if (roles.has("ADMIN")) {
    items.push({ label: "角色申请", target: "/admin/role-applications" });
    items.push({ label: "会议审批", target: "/admin/conference-approvals" });
    items.push({ label: "Agent 监控", target: "/admin/agents" });
    items.push({ label: "会议管理", target: "/chair/conferences" });
    return items;
  }
  if (roles.has("AUTHOR")) {
    items.push({ label: "我的稿件", target: "/author/manuscripts" });
    items.push({ label: "提交稿件", target: "/author/submit" });
  }
  if (roles.has("REVIEWER")) {
    items.push({ label: "评审任务", target: "/reviewer/assignments" });
    items.push({ label: "竞标投票", target: "/reviewer/bidding" });
  }
  if (roles.has("CHAIR")) {
    items.push({ label: "会议管理", target: "/chair/conferences" });
    items.push({ label: "初筛队列", target: "/chair/screening" });
    items.push({ label: "决策工作台", target: "/chair/decisions" });
    items.push({ label: "审稿人分配", target: "/chair/assignment-operations" });
  }
  return items;
});

onMounted(checkReviewerInterfaceChoice);

watch(
  () => authState.user?.userId,
  () => {
    void checkReviewerInterfaceChoice();
  }
);

async function signOut() {
  logout();
  await router.push("/login");
}

async function checkReviewerInterfaceChoice() {
  const roles = new Set(authState.user?.roles ?? []);
  if (!roles.has("AUTHOR") || !roles.has("REVIEWER") || route.path !== "/dashboard") {
    return;
  }
  if (promptedInterfaceUsers.has(authState.user!.userId)) {
    return;
  }
  let choice: Awaited<ReturnType<typeof getReviewerInterfaceChoice>>;
  try {
    choice = await getReviewerInterfaceChoice();
  } catch {
    return;
  }
  if (!choice.shouldPrompt) {
    return;
  }
  promptedInterfaceUsers.add(authState.user!.userId);
  try {
    await ElMessageBox.confirm(
      `您当前有 ${choice.activeAssignmentCount} 个会议中的审稿任务。请选择本次进入的工作界面。`,
      "选择本次进入的工作界面",
      {
        confirmButtonText: "进入审稿人界面",
        cancelButtonText: "进入作者界面",
        distinguishCancelAndClose: true,
        closeOnClickModal: false,
        closeOnPressEscape: false
      }
    );
    await router.push("/reviewer/assignments");
  } catch {
    await router.push("/author/manuscripts");
  }
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
