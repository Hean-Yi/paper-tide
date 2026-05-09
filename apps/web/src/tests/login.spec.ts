import { flushPromises, mount } from "@vue/test-utils";
import ElementPlus, { ElMessageBox } from "element-plus";
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
import DashboardView from "../views/DashboardView.vue";
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
    document.body.innerHTML = "";
    resetAuthForTests();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    document.body.innerHTML = "";
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

  it("lazy-loads workflow route views", () => {
    const router = createAppRouter();
    const routeNames = [
      "author-manuscripts",
      "author-submit",
      "reviewer-assignments",
      "reviewer-review-editor",
      "chair-screening",
      "chair-decisions",
      "admin-agent-monitor"
    ];

    for (const routeName of routeNames) {
      const route = router.getRoutes().find((entry) => entry.name === routeName);
      expect(typeof route?.components?.default).toBe("function");
    }
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
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    await flushPromises();
    await flushPromises();
    await new Promise((resolve) => setTimeout(resolve, 0));

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
    await wrapper.get("form").trigger("submit");
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

  it("clears the local session when an authenticated API request returns 401", async () => {
    const authToken = futureToken(["AUTHOR"]);
    localStorage.setItem("review.auth.token", authToken);
    initializeAuth();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: () => Promise.resolve({
        status: 401,
        code: "UNAUTHORIZED",
        message: "Token expired",
        traceId: "trace-expired"
      })
    }));

    await expect(apiRequest("/manuscripts")).rejects.toMatchObject({
      status: 401,
      code: "UNAUTHORIZED"
    });

    expect(localStorage.getItem("review.auth.token")).toBeNull();
    expect(isAuthenticated.value).toBe(false);
  });

  it("treats successful empty response bodies as undefined", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ "Content-Length": "0" }),
      json: vi.fn()
    }));

    await expect(apiRequest("/manuscripts/1/versions/2/pdf", { method: "POST" })).resolves.toBeUndefined();
  });

  it("parses structured API errors with code and trace id instead of status text", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      statusText: "Conflict from browser",
      json: () => Promise.resolve({
        status: 409,
        code: "CONFLICT",
        message: "Review report already submitted",
        traceId: "trace-123"
      })
    }));

    await expect(apiRequest("/review-assignments/7/review-report", { method: "POST" }))
      .rejects.toMatchObject({
        status: 409,
        code: "CONFLICT",
        traceId: "trace-123",
        message: "Review report already submitted"
      });
  });

  it("keeps admin-only users inside admin governance routes", async () => {
    const router = createAppRouter();
    localStorage.setItem("review.auth.token", futureToken(["ADMIN"]));
    initializeAuth();

    router.push("/chair/conferences");
    await router.isReady();
    expect(router.currentRoute.value.path).toBe("/chair/conferences");

    await router.push("/chair/screening");
    expect(router.currentRoute.value.path).toBe("/dashboard");

    await router.push("/chair/decisions");
    expect(router.currentRoute.value.path).toBe("/dashboard");

    await router.push("/chair/conferences/501/papers/11");
    expect(router.currentRoute.value.path).toBe("/dashboard");
  });

  it("routes admin-only users to dashboard after login", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ token: futureToken(["ADMIN"]) })
    });
    vi.stubGlobal("fetch", fetchMock);
    const router = createAppRouter();
    const pushSpy = vi.spyOn(router, "push");
    router.push("/login");
    await router.isReady();

    const wrapper = mount(LoginView, {
      global: {
        plugins: [ElementPlus, router]
      }
    });
    await wrapper.get('input[autocomplete="username"]').setValue("admin_demo");
    await wrapper.get('input[autocomplete="current-password"]').setValue("demo123");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    await flushPromises();

    expect(fetchMock).toHaveBeenCalled();
    expect(authState.user?.roles).toEqual(["ADMIN"]);
    expect(pushSpy).toHaveBeenCalledWith("/dashboard");
  });

  it("does not keep a duplicate admin console route", () => {
    const router = createAppRouter();

    expect(router.getRoutes().some((route) => route.name === "admin-dashboard")).toBe(false);
    expect(router.getRoutes().some((route) => route.path === "/admin")).toBe(false);
    expect(router.getRoutes().some((route) => route.name === "chair-publication-operations")).toBe(false);
    expect(router.getRoutes().some((route) => route.path === "/chair/publication-operations")).toBe(false);
  });

  it("renders role-aware shell links and logs out", async () => {
    const router = createAppRouter();
    localStorage.setItem("review.auth.token", futureToken(["AUTHOR", "CHAIR"]));
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

    expect(wrapper.text()).toContain("我的稿件");
    expect(wrapper.text()).toContain("初筛队列");
    expect(wrapper.text()).not.toContain("出版操作");
    expect(wrapper.text()).not.toContain("Agent 监控");
    expect(wrapper.text()).not.toContain("管理后台");
    expect(wrapper.text()).not.toContain("Review assignments");

    await wrapper.get('[data-test="logout"]').trigger("click");
    await flushPromises();

    expect(localStorage.getItem("review.auth.token")).toBeNull();
    expect(router.currentRoute.value.path).toBe("/login");
    expect(logout).toBeDefined();
  });

  it("renders a focused admin shell without chair operation clutter", async () => {
    const router = createAppRouter();
    localStorage.setItem("review.auth.token", futureToken(["ADMIN"]));
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

    expect(wrapper.text()).toContain("角色申请");
    expect(wrapper.text()).toContain("会议审批");
    expect(wrapper.text()).toContain("Agent 监控");
    expect(wrapper.text()).toContain("会议管理");
    const links = wrapper.findAll("a");
    expect(links.some((link) => link.text().includes("管理后台"))).toBe(false);
    expect(links.some((link) => link.attributes("href") === "/admin")).toBe(false);
    expect(wrapper.text()).not.toContain("初筛队列");
    expect(wrapper.text()).not.toContain("决策工作台");
    expect(wrapper.text()).not.toContain("审稿人分配");
    expect(wrapper.text()).not.toContain("出版操作");
  });

  it("uses the admin console layout for admin dashboard", async () => {
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

    expect(wrapper.text()).toContain("平台管理员控制台");
    expect(wrapper.text()).toContain("平台管理");
    expect(wrapper.text()).toContain("会议运营辅助");
    expect(wrapper.text()).toContain("会议管理");
    expect(wrapper.text()).not.toContain("初筛队列");
    expect(wrapper.text()).not.toContain("决策工作台");
    expect(wrapper.text()).not.toContain("审稿人分配");
  });

  it("prompts an author-reviewer with active review work to choose an interface", async () => {
    const confirm = vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
    const router = createAppRouter();
    localStorage.setItem("review.auth.token", futureToken(["AUTHOR", "REVIEWER"]));
    initializeAuth();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/reviewer/interface-choice") {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({
            shouldPrompt: true,
            activeAssignmentCount: 1
          })
        });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({})
      });
    });
    vi.stubGlobal("fetch", fetchMock);
    router.push("/dashboard");
    await router.isReady();
    const pushSpy = vi.spyOn(router, "push");

    mount(AppShell, {
      global: {
        plugins: [ElementPlus, router],
        stubs: {
          RouterView: true
        }
      }
    });
    await flushPromises();
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledWith("/api/reviewer/interface-choice", expect.anything());
    expect(confirm).toHaveBeenCalledWith(
      expect.stringContaining("您当前有 1 个会议中的审稿任务"),
      "选择本次进入的工作界面",
      expect.objectContaining({
        confirmButtonText: "进入审稿人界面",
        cancelButtonText: "进入作者界面"
      })
    );
    await flushPromises();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(pushSpy).toHaveBeenCalledWith("/reviewer/assignments");
  });

  it("does not prompt an author-reviewer after all active review work is closed", async () => {
    const router = createAppRouter();
    localStorage.setItem("review.auth.token", futureToken(["AUTHOR", "REVIEWER"]));
    initializeAuth();
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      const path = String(input).replace(/^\/api/, "");
      if (path === "/reviewer/interface-choice") {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({
            shouldPrompt: false,
            activeAssignmentCount: 0
          })
        });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({})
      });
    }));
    router.push("/dashboard");
    await router.isReady();

    mount(AppShell, {
      global: {
        plugins: [ElementPlus, router],
        stubs: {
          RouterView: true
        }
      }
    });
    await flushPromises();
    await flushPromises();

    expect(document.body.textContent).not.toContain("选择本次进入的工作界面");
  });
});
