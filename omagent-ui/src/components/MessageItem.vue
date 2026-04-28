<template>
  <div class="message-fade-in" :class="message.role === 'user' ? 'flex justify-end' : 'flex justify-start'">
    <!-- AI消息 -->
    <div v-if="message.role === 'assistant'" class="flex gap-3 max-w-[85%]">
      <div class="flex-shrink-0 w-8 h-8 rounded-lg bg-primary/20 flex items-center justify-center mt-1">
        <Bot :size="16" class="text-primary" />
      </div>
      <div class="flex-1 min-w-0">
        <div class="glass-panel px-4 py-3 text-sm leading-relaxed text-[#D9D9D9]"
          v-html="renderedContent"></div>
        <div v-if="message.sources && message.sources.length" class="flex flex-wrap gap-1.5 mt-2">
          <span v-for="source in message.sources" :key="source"
            class="text-[10px] px-2 py-0.5 rounded-full bg-white/5 text-[#8C8C8C] border border-white/5">
            {{ source }}
          </span>
        </div>
      </div>
    </div>

    <!-- 用户消息 -->
    <div v-else class="flex gap-3 max-w-[75%] flex-row-reverse">
      <div class="flex-shrink-0 w-8 h-8 rounded-lg bg-surface-light flex items-center justify-center mt-1">
        <User :size="16" class="text-[#8C8C8C]" />
      </div>
      <div class="flex-1 min-w-0">
        <div class="bg-primary/90 text-white px-4 py-3 rounded-xl text-sm leading-relaxed">
          {{ message.content }}
        </div>
        <div v-if="message.imageUrl" class="mt-2">
          <div class="w-20 h-14 rounded-md bg-surface-light flex items-center justify-center border border-white/5">
            <ImageIcon :size="16" class="text-[#8C8C8C]" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Bot, User, Image as ImageIcon } from 'lucide-vue-next'
import { renderMarkdown } from '../utils/markdown'

const props = defineProps({
  message: { type: Object, required: true },
})

const renderedContent = computed(() => {
  return renderMarkdown(props.message.content || '')
})
</script>

<style>
.hljs-pre {
  @apply relative rounded-lg overflow-hidden my-2;
}
.hljs-pre .copy-btn {
  @apply absolute top-2 right-2 text-xs px-2 py-1 rounded bg-white/10 text-[#8C8C8C]
         opacity-0 transition-opacity hover:bg-white/20;
}
.hljs-pre:hover .copy-btn {
  @apply opacity-100;
}
</style>
