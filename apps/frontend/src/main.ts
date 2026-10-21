import { api } from "@/shared/api";
import { useSessionStore } from "@/shared/auth";
import { createPinia } from "pinia";
import { createApp } from "vue";
import AppRoot from "./app/app-root.vue";
import { router } from "./app/router";
import "./app/styles.css";

const app = createApp(AppRoot);
const pinia = createPinia();
app.use(pinia);
const session = useSessionStore();
session.hydrate();
api.setClientId(session.clientId);
app.use(router);
app.mount("#app");
