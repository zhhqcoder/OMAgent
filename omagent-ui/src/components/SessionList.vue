<template>
  <div class="flex flex-col h-full">
    <div class="p-3 flex items-center justify-between border-b border-white/5">
      <span class="text-xs text-[#8C8C8C] font-medium">对话列表</span>
      <button @click="$emit('new-session')" class="w-6 h-6 rounded-md hover:bg-white/10 flex items-center justify-center transition-colors">
        <Plus :size="14" class="text-[#8C8C8C]" />
      </button>
    </div>
    <div class="flex-1 overflow-y-auto scrollbar-thin p-2 space-y-1">
      <div
        v-for="session in sessions"
        :key="session.id"
        @click="$emit('select-session', session.id)"
        class="group px-3 py-2.5 rounded-lg cursor-pointer transition-all duration-200"
        :class="activeSessionId === session.id
          ? 'bg-primary/15 text-primary border border-primary/20'
          : 'hover:bg-white/5 text-[#D9D9D9] border border-transparent'"
      >
        <div class="flex items-center justify-between">
          <span class="text-sm truncate flex-1">{{ session.title }}</span>
          <button
            @click.stop="$emit('delete-session', session.id)"
            class="opacity-0 group-hover:opacity-100 w-5 h-5 rounded hover:bg-white/10 flex items-center justify-center transition-all"
          >
            <Trash2 :size="12" class="text-[#8C8C8C]" />
          </button>
        </div>
        <div class="text-[10px] mt-1" :class="activeSessionId === session.id ? 'text-primary/50' : 'text-[#8C8C8C]/50'">
          {{ formatTime(session.updatedAt) }}
        </div>
      </div>
      <div v-if="sessions.length === 0" class="text-center py-8 text-[#8C8C8C] text-xs">
        暂无对话，点击上方 + 新建
      </div>
    </div>
  </div>
</template>

<script setup>
import { Plus, Trash2 } from 'lucide-vue-next'

defineProps({
  sessions: { type: Array, default: () => [] },
  activeSessionId: { type: String, default: '' },
})

defineEmits(['select-session', 'delete-session', 'new-session'])

function formatTime(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now - date
  const diffMins = Math.floor(diffMs / 60000)
  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours}小时前`
  return `${date.getMonth() + 1}/${date.getDate()}`
}
</script>
