import { createRouter, createWebHistory, type RouteLocationNormalized } from "vue-router";

import AppShell from "../layouts/AppShell.vue";
import { hasRole, initializeAuth, isAuthenticated } from "../stores/auth";
import DashboardView from "../views/DashboardView.vue";
import LoginView from "../views/LoginView.vue";
import PublicCfpView from "../views/PublicCfpView.vue";
import ConferenceConsoleView from "../views/chair/ConferenceConsoleView.vue";
import BiddingView from "../views/reviewer/BiddingView.vue";

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
        path: "/register",
        name: "register",
        component: () => import("../views/RegisterView.vue")
      },
      {
        path: "/cfp",
        name: "public-cfp",
        component: PublicCfpView
      },
      {
        path: "/verify-email",
        name: "verify-email",
        component: () => import("../views/VerifyEmailView.vue")
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
            component: () => import("../views/author/ManuscriptListView.vue"),
            meta: { requiresAuth: true, roles: ["AUTHOR"] }
          },
          {
            path: "author/submit",
            name: "author-submit",
            component: () => import("../views/author/SubmitManuscriptView.vue"),
            meta: { requiresAuth: true, roles: ["AUTHOR"] }
          },
          {
            path: "reviewer/assignments",
            name: "reviewer-assignments",
            component: () => import("../views/reviewer/AssignmentListView.vue"),
            meta: { requiresAuth: true, roles: ["REVIEWER"] }
          },
          {
            path: "reviewer/bidding",
            name: "reviewer-bidding",
            component: BiddingView,
            meta: { requiresAuth: true, roles: ["REVIEWER"] }
          },
          {
            path: "reviewer/reviews/:assignmentId",
            name: "reviewer-review-editor",
            component: () => import("../views/reviewer/ReviewEditorView.vue"),
            meta: { requiresAuth: true, roles: ["REVIEWER"] }
          },
          {
            path: "chair/screening",
            name: "chair-screening",
            component: () => import("../views/chair/ScreeningQueueView.vue"),
            meta: { requiresAuth: true, roles: ["CHAIR", "ADMIN"] }
          },
          {
            path: "chair/conferences",
            name: "chair-conferences",
            component: ConferenceConsoleView,
            meta: { requiresAuth: true, roles: ["CHAIR", "ADMIN"] }
          },
          {
            path: "chair/decisions",
            name: "chair-decisions",
            component: () => import("../views/chair/DecisionWorkbenchView.vue"),
            meta: { requiresAuth: true, roles: ["CHAIR", "ADMIN"] }
          },
          {
            path: "admin/agents",
            name: "admin-agent-monitor",
            component: () => import("../views/admin/AgentMonitorView.vue"),
            meta: { requiresAuth: true, roles: ["ADMIN"] }
          },
          {
            path: "admin/role-applications",
            name: "admin-role-applications",
            component: () => import("../views/admin/RoleApplicationsView.vue"),
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
    if ((to.path === "/login" || to.path === "/register") && isAuthenticated.value) {
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
