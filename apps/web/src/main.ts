import ElementPlus from "element-plus";
import { createApp } from "vue";

import App from "./App.vue";
import router from "./router";
import { initializeAuth } from "./stores/auth";
import "./style.css";

initializeAuth();

createApp(App).use(ElementPlus).use(router).mount("#app");
