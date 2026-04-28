import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import ChatView from './views/ChatView.vue'
import KnowledgeView from './views/KnowledgeView.vue'
import SettingsView from './views/SettingsView.vue'
import './index.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'chat', component: ChatView },
    { path: '/knowledge', name: 'knowledge', component: KnowledgeView },
    { path: '/settings', name: 'settings', component: SettingsView },
  ],
})

const app = createApp(App)
app.use(router)
app.mount('#app')
