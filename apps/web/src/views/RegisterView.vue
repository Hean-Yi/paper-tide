<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { RouterLink } from "vue-router";

import { ApiError, apiRequest } from "../lib/api";

const loading = ref(false);
const message = ref("");
const error = ref("");
const form = reactive({
  registrationType: "AUTHOR",
  username: "",
  password: "",
  realName: "",
  email: "",
  institution: "",
  homepageUrl: "",
  orcid: "",
  representativeWork: "",
  conflictDomains: "",
  areaCode: "",
  areaName: "",
  plannedConferenceTitle: ""
});

const requiresAcademicProfile = computed(() => form.registrationType !== "AUTHOR");

type RegistrationResponse = {
  registrationType: string;
  applicationStatus: string;
  emailVerificationRequired: boolean;
};

async function submit() {
  message.value = "";
  error.value = "";
  if (!form.username.trim() || !form.password || !form.realName.trim() || !form.email.trim()) {
    error.value = "用户名、密码、真实姓名和邮箱均为必填。";
    return;
  }
  if (requiresAcademicProfile.value && !form.homepageUrl.trim() && !form.orcid.trim()) {
    error.value = "审稿人和组织者注册需要提供学术主页或 ORCID。";
    return;
  }
  if (form.registrationType === "REVIEWER" && !form.representativeWork.trim()) {
    error.value = "审稿人注册需要至少填写一篇代表作。";
    return;
  }
  if (form.registrationType === "ORGANIZER" && !form.plannedConferenceTitle.trim()) {
    error.value = "组织者注册需要填写拟举办会议名称。";
    return;
  }
  loading.value = true;
  try {
    const response = await apiRequest<RegistrationResponse>("/auth/register", {
      method: "POST",
      json: registrationPayload()
    });
    message.value = successMessage(response);
  } catch (apiError) {
    error.value = apiError instanceof ApiError ? apiError.message : "注册失败，请稍后重试。";
  } finally {
    loading.value = false;
  }
}

function registrationPayload() {
  const representativeWorks = form.representativeWork.trim() ? [form.representativeWork.trim()] : [];
  const conflictDomains = form.conflictDomains
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean);
  const researchAreas = form.areaCode.trim()
    ? [{ areaCode: form.areaCode.trim(), areaName: form.areaName.trim() || form.areaCode.trim() }]
    : [];
  return {
    registrationType: form.registrationType,
    username: form.username.trim(),
    password: form.password,
    realName: form.realName.trim(),
    email: form.email.trim(),
    institution: form.institution.trim(),
    academicProfile: requiresAcademicProfile.value
      ? {
          homepageUrl: form.homepageUrl.trim() || null,
          orcid: form.orcid.trim() || null,
          dblpUrl: null,
          googleScholarUrl: null,
          representativeWorks,
          conflictDomains,
          defaultMaxLoad: 3,
          plannedConferenceTitle: form.plannedConferenceTitle.trim() || null
        }
      : null,
    researchAreas
  };
}

function successMessage(response: RegistrationResponse): string {
  if (response.emailVerificationRequired) {
    return "注册成功，请按系统提示完成后续验证。";
  }
  if (response.applicationStatus === "APPROVED") {
    return "注册成功，账号已激活，可直接登录。";
  }
  if (response.applicationStatus === "PENDING_ADMIN_APPROVAL") {
    return "注册成功，申请已进入管理员审核队列，审核通过后即可使用对应角色。";
  }
  return "注册成功。";
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel registration-panel" aria-labelledby="register-title">
      <p class="eyebrow">智能论文评审系统</p>
      <h1 id="register-title">注册账号</h1>

      <el-tabs v-model="form.registrationType" stretch>
        <el-tab-pane label="作者" name="AUTHOR" />
        <el-tab-pane label="审稿人" name="REVIEWER" />
        <el-tab-pane label="会议组织者" name="ORGANIZER" />
      </el-tabs>

      <form class="login-form" @submit.prevent="submit">
        <label class="field">
          <span>用户名</span>
          <el-input v-model="form.username" data-test="register-username" autocomplete="username" />
        </label>
        <label class="field">
          <span>密码</span>
          <el-input v-model="form.password" data-test="register-password" type="password" show-password autocomplete="new-password" />
        </label>
        <label class="field">
          <span>真实姓名</span>
          <el-input v-model="form.realName" data-test="register-real-name" autocomplete="name" />
        </label>
        <label class="field">
          <span>邮箱</span>
          <el-input v-model="form.email" data-test="register-email" autocomplete="email" />
        </label>
        <label class="field">
          <span>所属机构</span>
          <el-input v-model="form.institution" data-test="register-institution" autocomplete="organization" />
        </label>

        <template v-if="requiresAcademicProfile">
          <label class="field">
            <span>学术主页</span>
            <el-input v-model="form.homepageUrl" data-test="register-homepage" placeholder="https://example.edu/~you" />
          </label>
          <label class="field">
            <span>ORCID</span>
            <el-input v-model="form.orcid" data-test="register-orcid" />
          </label>
          <label v-if="form.registrationType === 'REVIEWER'" class="field">
            <span>代表作</span>
            <el-input v-model="form.representativeWork" data-test="register-work" placeholder="填写一篇近 5 年代表作标题" />
          </label>
          <label class="field">
            <span>利益冲突域</span>
            <el-input v-model="form.conflictDomains" placeholder="用逗号分隔，例如 example.edu, lab.org" />
          </label>
          <label class="field">
            <span>研究领域</span>
            <el-input v-model="form.areaCode" placeholder="例如 NLP" />
          </label>
          <label v-if="form.registrationType === 'ORGANIZER'" class="field">
            <span>拟举办会议名称</span>
            <el-input v-model="form.plannedConferenceTitle" data-test="register-conference-title" />
          </label>
        </template>

        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <p v-if="message" class="form-success" role="status">{{ message }}</p>

        <el-button data-test="register-submit" native-type="submit" type="primary" :loading="loading">
          注册
        </el-button>
        <RouterLink to="/login">返回登录</RouterLink>
      </form>
    </section>
  </main>
</template>
