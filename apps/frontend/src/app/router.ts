import { CampaignPage } from "@/pages/campaign";
import { ComposePage } from "@/pages/compose";
import { DashboardPage } from "@/pages/dashboard";
import { MeasurePage } from "@/pages/measure";
import { SenderNumbersPage } from "@/pages/sender-numbers";
import { StatsPage } from "@/pages/stats";
import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import AppShell from "./app-shell.vue";

export const routes: RouteRecordRaw[] = [
  { path: "/measure", component: MeasurePage },
  {
    path: "/",
    component: AppShell,
    children: [
      { path: "", component: DashboardPage },
      { path: "compose", component: ComposePage },
      { path: "campaigns/:id", component: CampaignPage },
      { path: "sender-numbers", component: SenderNumbersPage },
      { path: "stats", component: StatsPage },
    ],
  },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
});
