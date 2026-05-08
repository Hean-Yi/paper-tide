import { flushPromises, mount } from "@vue/test-utils";
import ElementPlus from "element-plus";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { createAppRouter } from "../router";
import { initializeAuth, resetAuthForTests } from "../stores/auth";
import DashboardView from "../views/DashboardView.vue";
import RegisterView from "../views/RegisterView.vue";
import RoleApplicationsView from "../views/admin/RoleApplicationsView.vue";

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
        applicationStatus: "APPROVED",
        emailVerificationRequired: false
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
    expect(wrapper.text()).toContain("账号已激活");
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

  it("shows role application payload summary for admin decisions", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve([
        {
          applicationId: 77,
          userId: 42,
          registrationType: "REVIEWER",
          status: "PENDING_ADMIN_APPROVAL",
          username: "new_reviewer",
          realName: "New Reviewer",
          email: "new_reviewer@example.com",
          institution: "Nanjing University",
          homepageUrl: "https://example.edu/new-reviewer",
          orcid: "0000-0002-1825-0097",
          representativeWorks: ["Representative Systems Paper"],
          conflictDomains: ["example.edu"],
          plannedConferenceTitle: null,
          researchAreas: [{ areaCode: "NLP", areaName: "Natural Language Processing" }]
        }
      ])
    }));

    const wrapper = mount(RoleApplicationsView, {
      global: {
        plugins: [ElementPlus]
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain("New Reviewer");
    expect(wrapper.text()).toContain("https://example.edu/new-reviewer");
    expect(wrapper.text()).toContain("Representative Systems Paper");
    expect(wrapper.text()).toContain("Natural Language Processing");
  });
});
