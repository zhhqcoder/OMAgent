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

/* 来源区块样式 */
.source-section-kb {
  @apply border-l-2 border-primary/60 pl-3 py-1 mb-3;
  background: rgba(59, 130, 246, 0.04);
  border-radius: 0 6px 6px 0;
}
.source-section-kb h3 {
  @apply text-primary text-sm font-semibold mb-2;
}
/* 去掉h3左侧border，避免与section border重叠 */
.source-section-kb h3,
.source-section-ai h3 {
  border-left: none !important;
  padding-left: 0 !important;
}
/* 隐藏合并后多余的h3标题（连续同类型section合并时，第二个及之后的h3不显示） */
.source-section-kb h3 ~ h3,
.source-section-ai h3 ~ h3 {
  display: none;
}

.source-section-ai {
  @apply border-l-2 border-amber-500/60 pl-3 py-1 mb-3 mt-4;
  background: rgba(245, 158, 11, 0.06);
  border-radius: 0 6px 6px 0;
}
.source-section-ai h3 {
  @apply text-amber-400 text-sm font-semibold mb-2;
}
</style>
