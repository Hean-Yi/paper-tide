<script setup lang="ts">
import { computed } from "vue";

import { authState } from "../stores/auth";

const entries = computed(() => {
  const roles = new Set(authState.user?.roles ?? []);
  const items = [];
  if (roles.has("AUTHOR")) {
    items.push({ label: "我的稿件", detail: "草稿、投稿与修改版本。", target: "/author/manuscripts" });
    items.push({ label: "提交稿件", detail: "创建论文记录并上传 PDF。", target: "/author/submit" });
  }
  if (roles.has("REVIEWER")) {
    items.push({ label: "评审任务", detail: "已分配论文及评审截止日期。", target: "/reviewer/assignments" });
    items.push({ label: "竞标投票", detail: "对开放中的会议投稿进行竞标。", target: "/reviewer/bidding" });
  }
  if (roles.has("CHAIR") || roles.has("ADMIN")) {
    items.push({ label: "会议管理", detail: "发布征稿启事并管理审稿人。", target: "/chair/conferences" });
    items.push({ label: "初筛队列", detail: "投稿检查与审稿人协调。", target: "/chair/screening" });
    items.push({ label: "决策工作台", detail: "轮次决策与冲突分析。", target: "/chair/decisions" });
  }
  if (roles.has("ADMIN")) {
    items.push({ label: "会议审批", detail: "审批提交的会议征稿启事。", target: "/chair/conferences" });
    items.push({ label: "角色申请", detail: "审核待处理的审稿人和组织者申请。", target: "/admin/role-applications" });
    items.push({ label: "Agent 监控", detail: "分析意图、结果投影与执行检查。", target: "/admin/agents" });
  }
  return items;
});
</script>

<template>
  <section class="dashboard">
    <div class="dossier-header">
      <p class="eyebrow">工作台</p>
      <h1>欢迎，{{ authState.user?.username }}</h1>
      <p class="body">请选择以下功能继续操作。</p>
    </div>

    <div class="entry-grid">
      <RouterLink v-for="entry in entries" :key="entry.label" class="entry-card" :to="entry.target">
        <h2>{{ entry.label }}</h2>
        <p>{{ entry.detail }}</p>
      </RouterLink>
    </div>
  </section>
</template>
