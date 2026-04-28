<template>
  <div class="flex flex-col h-full">
    <!-- 消息列表 -->
    <div ref="messagesContainer" class="flex-1 overflow-y-auto scrollbar-thin px-4 py-4 space-y-4">
      <div v-if="messages.length === 0" class="flex flex-col items-center justify-center h-full text-center">
        <div class="w-16 h-16 rounded-2xl bg-primary/10 flex items-center justify-center mb-4">
          <Bot :size="28" class="text-primary" />
        </div>
        <h2 class="text-lg font-medium text-[#D9D9D9] mb-2">OMS 运维智能助手</h2>
        <p class="text-sm text-[#8C8C8C] max-w-md">
          我可以帮助您解答配置疑问、分析错误日志。请描述您的问题，或粘贴错误日志/上传截图。
        </p>
        <div class="flex gap-3 mt-6">
          <button @click="quickAsk('OMS的告警相关配置有哪些？')" class="glass-panel-hover px-4 py-2 text-xs text-[#D9D9D9]">
            告警配置说明
          </button>
          <button @click="quickAsk('如何排查NullPointerException错误？')" class="glass-panel-hover px-4 py-2 text-xs text-[#D9D9D9]">
            日志排查帮助
          </button>
        </div>
      </div>

      <MessageItem v-for="msg in messages" :key="msg.id" :message="msg" />

      <!-- 加载动画（仅在等待首个chunk时显示） -->
      <div v-if="showTypingDots" class="flex justify-start">
        <div class="flex gap-3 max-w-[85%]">
          <div class="flex-shrink-0 w-8 h-8 rounded-lg bg-primary/20 flex items-center justify-center mt-1">
            <Bot :size="16" class="text-primary" />
          </div>
          <div class="glass-panel px-4 py-3">
            <div class="flex gap-1.5">
              <span class="typing-dot w-2 h-2 rounded-full bg-primary"></span>
              <span class="typing-dot w-2 h-2 rounded-full bg-primary"></span>
              <span class="typing-dot w-2 h-2 rounded-full bg-primary"></span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入栏 -->
    <InputBar
      @send="handleSend"
      @send-with-file="handleSendWithFile"
      @send-with-image="handleSendWithImage"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { Bot } from 'lucide-vue-next'
import MessageItem from './MessageItem.vue'
import InputBar from './InputBar.vue'
import { chatApi, fileApi, sessionApi } from '../api/chat'
import { readSSEStream } from '../utils/sse'

const props = defineProps({
  sessionId: { type: String, default: '' },
  messages: { type: Array, default: () => [] },
})

const emit = defineEmits(['message-sent'])

const messagesContainer = ref(null)
const isStreaming = ref(false)

// 是否有正在流式输出的assistant消息（已有内容在显示）
const hasStreamingMessage = computed(() => props.messages.some(m => m._streaming))

// 只在等待首个chunk时显示typing dots，流式消息出现后隐藏
const showTypingDots = computed(() => isStreaming.value && !hasStreamingMessage.value)

/**
 * 确保有sessionId，没有则先创建会话
 */
async function ensureSessionId() {
  if (props.sessionId) return props.sessionId
  try {
    const res = await sessionApi.create()
    const sessionId = res.data.id
    emit('message-sent', { type: 'session-created', sessionId, session: res.data })
    return sessionId
  } catch (e) {
    emit('message-sent', { type: 'error', content: '创建会话失败' })
    return null
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

watch(() => props.messages.length, () => {
  scrollToBottom()
})

function quickAsk(text) {
  handleSend(text)
}

async function handleSend(text) {
  if (!text.trim()) return

  // 确保有sessionId
  const sessionId = await ensureSessionId()
  if (!sessionId) return

  isStreaming.value = true

  emit('message-sent', {
    sessionId: sessionId,
    message: text,
  })

  try {
    const response = await chatApi.sendStream({
      sessionId: sessionId,
      message: text,
    })

    if (response.ok) {
      let fullContent = ''
      readSSEStream(
        response,
        (chunk) => {
          fullContent += chunk
          emit('message-sent', { type: 'stream-chunk', content: fullContent })
          scrollToBottom()
        },
        () => {
          isStreaming.value = false
          emit('message-sent', { type: 'stream-done', content: fullContent })
        },
        () => {
          isStreaming.value = false
        }
      )
    } else {
      const result = await chatApi.send({
        sessionId: props.sessionId,
        message: text,
      })
      isStreaming.value = false
      emit('message-sent', { type: 'sync-response', content: result.data.content })
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    isStreaming.value = false
    emit('message-sent', { type: 'error', content: '发送失败，请检查后端服务是否启动' })
  }
}

async function handleSendWithFile({ message, file }) {
  const sessionId = await ensureSessionId()
  if (!sessionId) return

  isStreaming.value = true
  try {
    const uploadResult = await fileApi.upload(file)
    const fileId = uploadResult.data.id

    const result = await chatApi.send({
      sessionId: sessionId,
      message: message || `请分析以下文件内容`,
      fileId,
    })

    isStreaming.value = false
    emit('message-sent', { type: 'sync-response', content: result.data.content })
  } catch (error) {
    console.error('文件发送失败:', error)
    isStreaming.value = false
    emit('message-sent', { type: 'error', content: '文件处理失败' })
  }
}

async function handleSendWithImage({ message, file, base64 }) {
  const sessionId = await ensureSessionId()
  if (!sessionId) return

  isStreaming.value = true
  try {
    const base64Data = base64.split(',')[1]
    const mimeType = file.type || 'image/png'

    const result = await chatApi.send({
      sessionId: sessionId,
      message: message || '请分析截图中的错误信息',
      imageBase64: base64Data,
      imageMimeType: mimeType,
    })

    isStreaming.value = false
    emit('message-sent', { type: 'sync-response', content: result.data.content })
  } catch (error) {
    console.error('截图发送失败:', error)
    isStreaming.value = false
    emit('message-sent', { type: 'error', content: '截图分析失败' })
  }
}
</script>
