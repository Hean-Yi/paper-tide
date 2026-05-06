import { flushPromises, mount } from "@vue/test-utils";
import ElementPlus from "element-plus";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { createAppRouter } from "../router";
import { initializeAuth, resetAuthForTests } from "../stores/auth";
import DashboardView from "../views/DashboardView.vue";
import RegisterView from "../views/RegisterView.vue";

function token(claims: Record<string, unknown>): string {
  const encode = (value: object) => btoa(JSON.stringify(value)).replaceAll("=", "");
  return `${encode({ alg: "none" })}.${encode(claims)}.signature`;
}

function futureToken(roles: string[]): string {
  return token({
    sub: "admin_demo",
    uid: 1004,
    roles,
    exp: Math.floor(Date.now() / 1000) + 3600
  });
}

describe("registration workflow", () => {
  beforeEach(() => {
    resetAuthForTests();
    vi.restoreAllMocks();
  });

  it("registers an author through the public registration page", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({
        userId: 42,
        applicationId: 77,
        registrationType: "AUTHOR",
        applicationStatus: "PENDING_EMAIL_VERIFICATION",
        emailVerificationRequired: true
      })
    });
    vi.stubGlobal("fetch", fetchMock);
    const router = createAppRouter();
    router.push("/register");
    await router.isReady();

    const wrapper = mount(RegisterView, {
      global: {
        plugins: [ElementPlus, router]
      }
    });

    await wrapper.get('[data-test="register-username"]').setValue("new_author");
    await wrapper.get('[data-test="register-password"]').setValue("demo123");
    await wrapper.get('[data-test="register-real-name"]').setValue("New Author");
    await wrapper.get('[data-test="register-email"]').setValue("new_author@example.com");
    await wrapper.get('[data-test="register-institution"]').setValue("Southeast University");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/auth/register",
      expect.objectContaining({ method: "POST" })
    );
    const [, requestOptions] = fetchMock.mock.calls[0];
    expect(JSON.parse(requestOptions.body as string)).toEqual(expect.objectContaining({
      registrationType: "AUTHOR",
      username: "new_author",
      email: "new_author@example.com"
    }));
    expect(wrapper.text()).toContain("注册成功");
  });

  it("keeps registration and admin approval routes in the router", () => {
    const router = createAppRouter();

    expect(router.getRoutes().some((route) => route.path === "/register")).toBe(true);
    expect(router.getRoutes().some((route) => route.name === "admin-role-applications")).toBe(true);
  });

  it("shows admin users the role application workbench link", async () => {
    const router = createAppRouter();
    localStorage.setItem("review.auth.token", futureToken(["ADMIN"]));
    initializeAuth();
    router.push("/dashboard");
    await router.isReady();

    const wrapper = mount(DashboardView, {
      global: {
        plugins: [ElementPlus, router]
      }
    });

    expect(wrapper.text()).toContain("Role applications");
  });
});
