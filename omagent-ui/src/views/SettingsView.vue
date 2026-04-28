<template>
  <div class="flex flex-col h-full p-6 overflow-y-auto scrollbar-thin">
    <div class="max-w-2xl mx-auto w-full">
      <h1 class="text-xl font-semibold text-[#F0F0F0] mb-6">系统设置</h1>

      <!-- 模型配置 -->
      <div class="glass-panel p-5 mb-4">
        <h3 class="text-sm font-medium text-[#F0F0F0] mb-4">模型配置</h3>
        <div class="space-y-4">
          <div>
            <label class="text-xs text-[#8C8C8C] mb-1 block">对话模型</label>
            <div class="flex items-center gap-2 px-3 py-2 bg-white/5 rounded-lg">
              <span class="text-sm text-[#D9D9D9]">Qwen-Plus (通义千问)</span>
              <span class="text-[10px] px-2 py-0.5 rounded-full bg-primary/15 text-primary ml-auto">当前</span>
            </div>
          </div>
          <div>
            <label class="text-xs text-[#8C8C8C] mb-1 block">多模态模型</label>
            <div class="flex items-center gap-2 px-3 py-2 bg-white/5 rounded-lg">
              <span class="text-sm text-[#D9D9D9]">Qwen-VL-Max (截图分析)</span>
            </div>
          </div>
          <div>
            <label class="text-xs text-[#8C8C8C] mb-2 block">Temperature: {{ temperature }}</label>
            <input type="range" v-model="temperature" min="0" max="1" step="0.1"
              class="w-full h-1 bg-white/10 rounded-full appearance-none cursor-pointer accent-primary" />
          </div>
        </div>
      </div>

      <!-- 连接状态 -->
      <div class="glass-panel p-5 mb-4">
        <h3 class="text-sm font-medium text-[#F0F0F0] mb-4">连接状态</h3>
        <div class="space-y-3">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <div class="w-2 h-2 rounded-full" :class="status.dashscope ? 'bg-success' : 'bg-danger'"></div>
              <span class="text-sm text-[#D9D9D9]">DashScope API</span>
            </div>
            <span class="text-xs" :class="status.dashscope ? 'text-success' : 'text-danger'">
              {{ status.dashscope ? '已连接' : '未连接' }}
            </span>
          </div>
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <div class="w-2 h-2 rounded-full" :class="status.redis ? 'bg-success' : 'bg-danger'"></div>
              <span class="text-sm text-[#D9D9D9]">Redis 向量存储</span>
            </div>
            <span class="text-xs" :class="status.redis ? 'text-success' : 'text-danger'">
              {{ status.redis ? '已连接' : '未连接' }}
            </span>
          </div>
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <div class="w-2 h-2 rounded-full" :class="status.mysql ? 'bg-success' : 'bg-danger'"></div>
              <span class="text-sm text-[#D9D9D9]">MySQL 数据库</span>
            </div>
            <span class="text-xs" :class="status.mysql ? 'text-success' : 'text-danger'">
              {{ status.mysql ? '已连接' : '未连接' }}
            </span>
          </div>
        </div>
      </div>

      <!-- 关于 -->
      <div class="glass-panel p-5">
        <h3 class="text-sm font-medium text-[#F0F0F0] mb-3">关于</h3>
        <div class="text-xs text-[#8C8C8C] space-y-1">
          <p>OMS 运维智能助手 v1.0.0</p>
          <p>基于 Spring AI Alibaba 构建</p>
          <p>支持配置问答、日志排查、截图分析</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const temperature = ref(0.7)

const status = ref({
  dashscope: false,
  redis: false,
  mysql: false,
})

onMounted(async () => {
  try {
    const res = await fetch('/api/knowledge/stats')
    if (res.ok) {
      status.value.dashscope = true
      status.value.redis = true
      status.value.mysql = true
    }
  } catch (e) {
    console.error('状态检测失败:', e)
  }
})
</script>
