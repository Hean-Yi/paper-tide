import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import { createApp } from "vue";

import App from "./App.vue";
import { createAppRouter } from "./router";
import { initializeAuth } from "./stores/auth";
import "./style.css";

initializeAuth();

const router = createAppRouter();

createApp(App).use(ElementPlus).use(router).mount("#app");
