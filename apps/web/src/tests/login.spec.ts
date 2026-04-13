import { flushPromises, mount } from "@vue/test-utils";
import ElementPlus from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import AppShell from "../layouts/AppShell.vue";
import { apiRequest } from "../lib/api";
import { createAppRouter } from "../router";
import {
  authState,
  initializeAuth,
  isAuthenticated,
  logout,
  resetAuthForTests
} from "../stores/auth";
import LoginView from "../views/LoginView.vue";

function token(claims: Record<string, unknown>): string {
  const encode = (value: object) => btoa(JSON.stringify(value)).replaceAll("=", "");
  return `${encode({ alg: "none" })}.${encode(claims)}.signature`;
}

function futureToken(roles: string[] = ["AUTHOR"]): string {
  return token({
    sub: "author_demo",
    uid: 1001,
    roles,
    exp: Math.floor(Date.now() / 1000) + 3600
  });
}

describe("frontend authentication", () => {
  beforeEach(() => {
    resetAuthForTests();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    resetAuthForTests();
    vi.restoreAllMocks();
  });

  it("redirects anonymous users to login for protected routes", async () => {
    const router = createAppRouter();
    router.push("/dashboard");
    await router.isReady();

    expect(router.currentRoute.value.path).toBe("/login");
  });

  it("does not create a router instance as an import side effect", async () => {
    vi.resetModules();
    const createRouter = vi.fn(() => ({
      beforeEach: vi.fn()
    }));
    vi.doMock("vue-router", async (importOriginal) => ({
      ...await importOriginal<typeof import("vue-router")>(),
      createRouter,
      createWebHistory: vi.fn(() => ({}))
    }));

    await import("../router");

    expect(createRouter).not.toHaveBeenCalled();
    vi.doUnmock("vue-router");
  });

  it("stores the token and routes to dashboard after successful login", async () => {
    const authToken = futureToken(["CHAIR"]);
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ token: authToken })
    });
    vi.stubGlobal("fetch", fetchMock);
    const router = createAppRouter();
    router.push("/login");
    await router.isReady();

    const wrapper = mount(LoginView, {
      global: {
        plugins: [ElementPlus, router]
      }
    });
    await wrapper.get('input[autocomplete="username"]').setValue("chair_demo");
    await wrapper.get('input[autocomplete="current-password"]').setValue("demo123");
    await wrapper.get('[data-test="login-submit"]').trigger("submit");
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/auth/login",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ username: "chair_demo", password: "demo123" })
      })
    );
    expect(localStorage.getItem("review.auth.token")).toBe(authToken);
    expect(isAuthenticated.value).toBe(true);
    expect(authState.user?.roles).toEqual(["CHAIR"]);
    expect(router.currentRoute.value.path).toBe("/dashboard");
  });

  it("shows an error and does not store a token for invalid login", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: () => Promise.resolve({ message: "Invalid credentials" })
    }));
    const router = createAppRouter();
    router.push("/login");
    await router.isReady();

    const wrapper = mount(LoginView, {
      global: {
        plugins: [ElementPlus, router]
      }
    });
    await wrapper.get('input[autocomplete="username"]').setValue("author_demo");
    await wrapper.get('input[autocomplete="current-password"]').setValue("wrong");
    await wrapper.get('[data-test="login-submit"]').trigger("submit");
    await flushPromises();

    expect(wrapper.text()).toContain("Invalid username or password.");
    expect(localStorage.getItem("review.auth.token")).toBeNull();
    expect(router.currentRoute.value.path).toBe("/login");
  });

  it("restores a valid stored token and clears expired or malformed tokens", () => {
    localStorage.setItem("review.auth.token", futureToken(["REVIEWER"]));
    initializeAuth();

    expect(authState.user?.username).toBe("author_demo");
    expect(authState.user?.roles).toEqual(["REVIEWER"]);

    localStorage.setItem("review.auth.token", token({ sub: "old", uid: 99, roles: ["AUTHOR"], exp: 1 }));
    initializeAuth();
    expect(localStorage.getItem("review.auth.token")).toBeNull();
    expect(isAuthenticated.value).toBe(false);

    localStorage.setItem("review.auth.token", "not-a-jwt");
    initializeAuth();
    expect(localStorage.getItem("review.auth.token")).toBeNull();
  });

  it("adds the bearer token to authenticated API requests", async () => {
    const authToken = futureToken(["ADMIN"]);
    localStorage.setItem("review.auth.token", authToken);
    initializeAuth();
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ placeholder: true })
    });
    vi.stubGlobal("fetch", fetchMock);

    await apiRequest("/audit-logs");

    const [, options] = fetchMock.mock.calls[0];
    expect(new Headers(options.headers).get("Authorization")).toBe(`Bearer ${authToken}`);
  });

  it("renders role-aware shell links and logs out", async () => {
    const router = createAppRouter();
    localStorage.setItem("review.auth.token", futureToken(["AUTHOR", "CHAIR", "ADMIN"]));
    initializeAuth();
    router.push("/dashboard");
    await router.isReady();

    const wrapper = mount(AppShell, {
      global: {
        plugins: [ElementPlus, router],
        stubs: {
          RouterView: true
        }
      }
    });

    expect(wrapper.text()).toContain("My manuscripts");
    expect(wrapper.text()).toContain("Screening");
    expect(wrapper.text()).toContain("Audit logs");
    expect(wrapper.text()).not.toContain("Review assignments");

    await wrapper.get('[data-test="logout"]').trigger("click");
    await flushPromises();

    expect(localStorage.getItem("review.auth.token")).toBeNull();
    expect(router.currentRoute.value.path).toBe("/login");
    expect(logout).toBeDefined();
  });
});
