<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";

import { ApiError, apiRequest } from "../lib/api";

type VerifyResponse = {
  userId: number;
  applicationId: number;
  registrationType: string;
  userStatus: string;
  applicationStatus: string;
};

const route = useRoute();
const status = ref<"pending" | "success" | "error">("pending");
const message = ref("");
const result = ref<VerifyResponse | null>(null);

onMounted(async () => {
  const token = typeof route.query.token === "string" ? route.query.token : "";
  if (!token) {
    status.value = "error";
    message.value = "链接中缺少验证令牌，请重新从邮件中打开。";
    return;
  }
  try {
    result.value = await apiRequest<VerifyResponse>("/auth/verify-email", {
      method: "POST",
      json: { token }
    });
    status.value = "success";
    message.value = nextStepMessage(result.value);
  } catch (apiError) {
    status.value = "error";
    message.value = apiError instanceof ApiError ? apiError.message : "邮箱验证失败，请稍后重试。";
  }
});

function nextStepMessage(response: VerifyResponse): string {
  if (response.applicationStatus === "APPROVED") {
    return "邮箱验证成功，账号已激活，可直接登录。";
  }
  if (response.applicationStatus === "PENDING_ADMIN_APPROVAL") {
    return "邮箱验证成功。你的申请已进入管理员审核队列，审核通过后会开放登录权限。";
  }
  return "邮箱验证成功。";
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel" aria-labelledby="verify-title">
      <p class="eyebrow">论文评审系统</p>
      <h1 id="verify-title">邮箱验证</h1>

      <p v-if="status === 'pending'" role="status">正在验证你的邮箱，请稍候…</p>
      <p v-else-if="status === 'success'" class="form-success" role="status" data-test="verify-success">
        {{ message }}
      </p>
      <p v-else class="form-error" role="alert" data-test="verify-error">{{ message }}</p>

      <div v-if="status !== 'pending'" class="field">
        <RouterLink to="/login">返回登录</RouterLink>
      </div>
    </section>
  </main>
</template>
