<template>
  <div class="flex h-screen overflow-hidden bg-[#141414]">
    <!-- 左侧导航栏 -->
    <aside class="w-[60px] flex flex-col items-center py-4 bg-[#0F0F0F] border-r border-white/5">
      <div class="w-9 h-9 rounded-lg bg-primary flex items-center justify-center mb-6 cursor-pointer" @click="$router.push('/')">
        <span class="text-white font-bold text-sm">OM</span>
      </div>
      <nav class="flex-1 flex flex-col items-center gap-2">
        <router-link to="/" class="nav-icon-btn" :class="{ active: $route.path === '/' }">
          <MessageSquare :size="20" />
        </router-link>
        <router-link to="/knowledge" class="nav-icon-btn" :class="{ active: $route.path === '/knowledge' }">
          <Database :size="20" />
        </router-link>
        <router-link to="/settings" class="nav-icon-btn" :class="{ active: $route.path === '/settings' }">
          <Settings :size="20" />
        </router-link>
      </nav>
      <div class="mt-auto">
        <div class="w-8 h-8 rounded-full bg-surface-light flex items-center justify-center">
          <User :size="16" class="text-[#8C8C8C]" />
        </div>
      </div>
    </aside>

    <!-- 主内容区域 -->
    <main class="flex-1 overflow-hidden">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { MessageSquare, Database, Settings, User } from 'lucide-vue-next'
</script>

<style scoped>
.nav-icon-btn {
  @apply w-10 h-10 rounded-lg flex items-center justify-center text-[#8C8C8C]
         hover:bg-white/5 hover:text-[#D9D9D9] transition-all duration-200;
}
.nav-icon-btn.active {
  @apply bg-primary/15 text-primary;
}
</style>
