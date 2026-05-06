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

async function submit() {
  message.value = "";
  error.value = "";
  if (!form.username.trim() || !form.password || !form.realName.trim() || !form.email.trim()) {
    error.value = "Username, password, name, and email are required.";
    return;
  }
  if (requiresAcademicProfile.value && !form.homepageUrl.trim() && !form.orcid.trim()) {
    error.value = "Reviewer and organizer registration requires an academic profile URL or ORCID.";
    return;
  }
  loading.value = true;
  try {
    await apiRequest("/auth/register", {
      method: "POST",
      json: registrationPayload()
    });
    message.value = "注册成功，请前往邮箱点击验证链接完成邮箱验证。";
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
</script>

<template>
  <main class="login-page">
    <section class="login-panel registration-panel" aria-labelledby="register-title">
      <p class="eyebrow">Review System</p>
      <h1 id="register-title">Create an account</h1>

      <el-tabs v-model="form.registrationType" stretch>
        <el-tab-pane label="Author" name="AUTHOR" />
        <el-tab-pane label="Reviewer" name="REVIEWER" />
        <el-tab-pane label="Organizer" name="ORGANIZER" />
      </el-tabs>

      <form class="login-form" @submit.prevent="submit">
        <label class="field">
          <span>Username</span>
          <el-input v-model="form.username" data-test="register-username" autocomplete="username" />
        </label>
        <label class="field">
          <span>Password</span>
          <el-input v-model="form.password" data-test="register-password" type="password" show-password autocomplete="new-password" />
        </label>
        <label class="field">
          <span>Real name</span>
          <el-input v-model="form.realName" data-test="register-real-name" autocomplete="name" />
        </label>
        <label class="field">
          <span>Email</span>
          <el-input v-model="form.email" data-test="register-email" autocomplete="email" />
        </label>
        <label class="field">
          <span>Institution</span>
          <el-input v-model="form.institution" data-test="register-institution" autocomplete="organization" />
        </label>

        <template v-if="requiresAcademicProfile">
          <label class="field">
            <span>Academic profile URL</span>
            <el-input v-model="form.homepageUrl" data-test="register-homepage" />
          </label>
          <label class="field">
            <span>ORCID</span>
            <el-input v-model="form.orcid" data-test="register-orcid" />
          </label>
          <label class="field">
            <span>Representative work</span>
            <el-input v-model="form.representativeWork" data-test="register-work" />
          </label>
          <label class="field">
            <span>Conflict domains</span>
            <el-input v-model="form.conflictDomains" placeholder="example.edu, lab.org" />
          </label>
          <label class="field">
            <span>Research area</span>
            <el-input v-model="form.areaCode" placeholder="NLP" />
          </label>
          <label v-if="form.registrationType === 'ORGANIZER'" class="field">
            <span>Planned conference</span>
            <el-input v-model="form.plannedConferenceTitle" data-test="register-conference-title" />
          </label>
        </template>

        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <p v-if="message" class="form-success" role="status">{{ message }}</p>

        <el-button data-test="register-submit" native-type="submit" type="primary" :loading="loading">
          Register
        </el-button>
        <RouterLink to="/login">Back to sign in</RouterLink>
      </form>
    </section>
  </main>
</template>
