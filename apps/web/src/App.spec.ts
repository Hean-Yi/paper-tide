import { mount } from "@vue/test-utils";
import ElementPlus from "element-plus";
import { describe, expect, it } from "vitest";

import App from "./App.vue";
import { createAppRouter } from "./router";
import { resetAuthForTests } from "./stores/auth";

describe("App", () => {
  it("shows the login view for anonymous visitors", async () => {
    resetAuthForTests();
    const router = createAppRouter();
    router.push("/");
    await router.isReady();

    const wrapper = mount(App, {
      global: {
        plugins: [ElementPlus, router]
      }
    });

    expect(wrapper.text()).toContain("Sign in to Review System");
    expect(wrapper.text()).not.toContain("Monorepo scaffold is running.");
  });
});
