<script setup lang="ts">
import { reactive } from "vue";
import { useRouter } from "vue-router";

import { authState, login } from "../stores/auth";

const router = useRouter();
const form = reactive({
  username: "",
  password: ""
});
const validation = reactive({
  username: "",
  password: ""
});

async function submit() {
  validation.username = form.username.trim() ? "" : "请输入用户名";
  validation.password = form.password ? "" : "请输入密码";
  if (validation.username || validation.password) {
    return;
  }
  try {
    await login(form.username.trim(), form.password);
  } catch {
    // The auth store owns the user-facing message.
    return;
  }
  const target = postLoginTarget();
  await router.push(target);
}

function postLoginTarget() {
  return "/dashboard";
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel" aria-labelledby="login-title">
      <p class="eyebrow">论文评审系统</p>
      <h1 id="login-title">登录论文评审系统</h1>
      <p class="body">使用您的论文评审账号继续。</p>

      <form class="login-form" @submit.prevent="submit">
        <label class="field">
          <span>用户名</span>
          <el-input
            v-model="form.username"
            data-test="username"
            autocomplete="username"
            :disabled="authState.loading"
          />
          <small v-if="validation.username">{{ validation.username }}</small>
        </label>

        <label class="field">
          <span>密码</span>
          <el-input
            v-model="form.password"
            data-test="password"
            type="password"
            autocomplete="current-password"
            show-password
            :disabled="authState.loading"
          />
          <small v-if="validation.password">{{ validation.password }}</small>
        </label>

        <p v-if="authState.error" class="form-error" role="alert">{{ authState.error }}</p>

        <el-button
          data-test="login-submit"
          native-type="submit"
          type="primary"
          :loading="authState.loading"
        >
          登录
        </el-button>
        <RouterLink to="/register">注册账号</RouterLink>
      </form>
    </section>
  </main>
</template>
