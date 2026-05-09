<script setup lang="ts">
const adminEntries = [
  {
    label: "角色申请",
    detail: "审核审稿人、会议组织者等注册申请。",
    target: "/admin/role-applications"
  },
  {
    label: "Agent 监控",
    detail: "查看分析意图、投影状态和执行链路。",
    target: "/admin/agents"
  },
  {
    label: "会议审批",
    detail: "审批主席提交的会议征稿启事。",
    target: "/admin/conference-approvals"
  }
];

const operationsEntries = [
  {
    label: "会议管理",
    detail: "查看全部会议状态和详情；论文内容由会议主席处理。",
    target: "/chair/conferences"
  }
];
</script>

<template>
  <section class="dashboard">
    <div class="dossier-header">
      <p class="eyebrow">管理后台</p>
      <h1>平台管理员控制台</h1>
      <p class="body">优先处理平台级审核、监控和治理任务；会议运营入口保留为管理员辅助能力。</p>
    </div>

    <section class="workflow-section">
      <h2>平台管理</h2>
      <div class="entry-grid">
        <RouterLink v-for="entry in adminEntries" :key="entry.label" class="entry-card" :to="entry.target">
          <h2>{{ entry.label }}</h2>
          <p>{{ entry.detail }}</p>
        </RouterLink>
      </div>
    </section>

    <section class="workflow-section">
      <h2>会议运营辅助</h2>
      <div class="entry-grid">
        <RouterLink v-for="entry in operationsEntries" :key="entry.label" class="entry-card" :to="entry.target">
          <h2>{{ entry.label }}</h2>
          <p>{{ entry.detail }}</p>
        </RouterLink>
      </div>
    </section>
  </section>
</template>
