import ElementPlus from "element-plus";
import { createApp } from "vue";

import App from "./App.vue";
import { createAppRouter } from "./router";
import { initializeAuth } from "./stores/auth";
import "./style.css";

initializeAuth();

const router = createAppRouter();

createApp(App).use(ElementPlus).use(router).mount("#app");
