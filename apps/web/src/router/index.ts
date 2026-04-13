import { createRouter, createWebHistory, type RouteLocationNormalized } from "vue-router";

import AppShell from "../layouts/AppShell.vue";
import { hasRole, initializeAuth, isAuthenticated } from "../stores/auth";
import DashboardView from "../views/DashboardView.vue";
import LoginView from "../views/LoginView.vue";

declare module "vue-router" {
  interface RouteMeta {
    requiresAuth?: boolean;
    roles?: string[];
  }
}

export function createAppRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      {
        path: "/login",
        name: "login",
        component: LoginView
      },
      {
        path: "/",
        component: AppShell,
        meta: { requiresAuth: true },
        children: [
          {
            path: "",
            redirect: "/dashboard"
          },
          {
            path: "dashboard",
            name: "dashboard",
            component: DashboardView,
            meta: { requiresAuth: true }
          }
        ]
      }
    ]
  });

  router.beforeEach((to) => {
    initializeAuth();
    if (to.meta.requiresAuth && !isAuthenticated.value) {
      return "/login";
    }
    if (to.path === "/login" && isAuthenticated.value) {
      return "/dashboard";
    }
    if (!canUseRoute(to)) {
      return "/dashboard";
    }
    return true;
  });

  return router;
}

function canUseRoute(route: RouteLocationNormalized): boolean {
  const roles = route.meta.roles;
  return !roles || roles.some((role) => hasRole(role));
}
