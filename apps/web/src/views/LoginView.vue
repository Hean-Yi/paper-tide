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
  validation.username = form.username.trim() ? "" : "Username is required.";
  validation.password = form.password ? "" : "Password is required.";
  if (validation.username || validation.password) {
    return;
  }
  try {
    await login(form.username.trim(), form.password);
    await router.push("/dashboard");
  } catch {
    // The auth store owns the user-facing message.
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel" aria-labelledby="login-title">
      <p class="eyebrow">Review System</p>
      <h1 id="login-title">Sign in to Review System</h1>
      <p class="body">Use your paper review account to continue.</p>

      <form class="login-form" @submit.prevent="submit">
        <label class="field">
          <span>Username</span>
          <el-input
            v-model="form.username"
            data-test="username"
            autocomplete="username"
            :disabled="authState.loading"
          />
          <small v-if="validation.username">{{ validation.username }}</small>
        </label>

        <label class="field">
          <span>Password</span>
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
          Sign in
        </el-button>
      </form>
    </section>
  </main>
</template>
