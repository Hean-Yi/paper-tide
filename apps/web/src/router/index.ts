import { createRouter, createWebHistory, type RouteLocationNormalized } from "vue-router";

import AppShell from "../layouts/AppShell.vue";
import { hasRole, initializeAuth, isAuthenticated } from "../stores/auth";
import AgentMonitorView from "../views/admin/AgentMonitorView.vue";
import ManuscriptListView from "../views/author/ManuscriptListView.vue";
import SubmitManuscriptView from "../views/author/SubmitManuscriptView.vue";
import DecisionWorkbenchView from "../views/chair/DecisionWorkbenchView.vue";
import ScreeningQueueView from "../views/chair/ScreeningQueueView.vue";
import DashboardView from "../views/DashboardView.vue";
import LoginView from "../views/LoginView.vue";
import AssignmentListView from "../views/reviewer/AssignmentListView.vue";
import ReviewEditorView from "../views/reviewer/ReviewEditorView.vue";

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
          },
          {
            path: "author/manuscripts",
            name: "author-manuscripts",
            component: ManuscriptListView,
            meta: { requiresAuth: true, roles: ["AUTHOR"] }
          },
          {
            path: "author/submit",
            name: "author-submit",
            component: SubmitManuscriptView,
            meta: { requiresAuth: true, roles: ["AUTHOR"] }
          },
          {
            path: "reviewer/assignments",
            name: "reviewer-assignments",
            component: AssignmentListView,
            meta: { requiresAuth: true, roles: ["REVIEWER"] }
          },
          {
            path: "reviewer/reviews/:assignmentId",
            name: "reviewer-review-editor",
            component: ReviewEditorView,
            meta: { requiresAuth: true, roles: ["REVIEWER"] }
          },
          {
            path: "chair/screening",
            name: "chair-screening",
            component: ScreeningQueueView,
            meta: { requiresAuth: true, roles: ["CHAIR", "ADMIN"] }
          },
          {
            path: "chair/decisions",
            name: "chair-decisions",
            component: DecisionWorkbenchView,
            meta: { requiresAuth: true, roles: ["CHAIR", "ADMIN"] }
          },
          {
            path: "admin/agents",
            name: "admin-agent-monitor",
            component: AgentMonitorView,
            meta: { requiresAuth: true, roles: ["ADMIN"] }
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
