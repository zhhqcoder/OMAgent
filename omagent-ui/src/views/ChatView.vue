<template>
  <div class="flex h-full">
    <!-- 左侧会话面板 -->
    <div class="w-[260px] border-r border-white/5 flex flex-col bg-[#0F0F0F]/50">
      <SessionList
        :sessions="sessions"
        :active-session-id="activeSessionId"
        @select-session="switchSession"
        @delete-session="deleteSession"
        @new-session="createSession"
      />
      <!-- 知识库状态 -->
      <div class="p-3 border-t border-white/5">
        <div class="text-[10px] text-[#8C8C8C] space-y-1">
          <div class="flex justify-between">
            <span>配置知识库</span>
            <span class="text-primary">{{ knowledgeStats.configTotal || 0 }} 文档</span>
          </div>
          <div class="flex justify-between">
            <span>源码/日志库</span>
            <span class="text-primary">{{ knowledgeStats.sourceTotal || 0 }} 文档</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧聊天区域 -->
    <div class="flex-1 flex flex-col min-w-0">
      <!-- 顶部栏 -->
      <div class="h-12 flex items-center px-4 border-b border-white/5 bg-[#0F0F0F]/30 backdrop-blur-sm">
        <div class="flex items-center gap-2">
          <div class="w-2 h-2 rounded-full" :class="connected ? 'bg-success' : 'bg-danger'"></div>
          <span class="text-sm text-[#8C8C8C]">{{ currentTitle }}</span>
        </div>
      </div>

      <!-- 聊天窗口 -->
      <ChatWindow
        :session-id="activeSessionId"
        :messages="currentMessages"
        @message-sent="handleMessageSent"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import SessionList from '../components/SessionList.vue'
import ChatWindow from '../components/ChatWindow.vue'
import { sessionApi, knowledgeApi } from '../api/chat'

const sessions = ref([])
const activeSessionId = ref('')
const currentMessages = ref([])
const knowledgeStats = ref({})
const connected = ref(false)
let healthTimer = null

const currentTitle = computed(() => {
  const session = sessions.value.find(s => s.id === activeSessionId.value)
  return session?.title || 'OMS 运维助手'
})

onMounted(async () => {
  checkHealth()
  healthTimer = setInterval(checkHealth, 15000) // 每15秒检测一次
  await loadSessions()
  await loadKnowledgeStats()
  if (sessions.value.length === 0) {
    await createSession()
  } else if (!activeSessionId.value) {
    // 有历史会话时，自动选中最近的一个
    await switchSession(sessions.value[0].id)
  }
})

onUnmounted(() => {
  if (healthTimer) {
    clearInterval(healthTimer)
    healthTimer = null
  }
})

async function checkHealth() {
  try {
    const res = await fetch('/actuator/health')
    connected.value = res.ok
  } catch {
    connected.value = false
  }
}

async function loadSessions() {
  try {
    const res = await sessionApi.list()
    sessions.value = res.data
  } catch (e) {
    console.error('加载会话列表失败:', e)
  }
}

async function loadKnowledgeStats() {
  try {
    const res = await knowledgeApi.stats()
    knowledgeStats.value = res.data
  } catch (e) {
    console.error('加载知识库统计失败:', e)
  }
}

async function createSession() {
  try {
    const res = await sessionApi.create()
    sessions.value.unshift(res.data)
    await switchSession(res.data.id)
  } catch (e) {
    console.error('创建会话失败:', e)
  }
}

async function switchSession(sessionId) {
  activeSessionId.value = sessionId
  try {
    const res = await sessionApi.getMessages(sessionId)
    currentMessages.value = res.data || []
  } catch (e) {
    console.error('加载消息失败:', e)
    currentMessages.value = []
  }
}

async function deleteSession(sessionId) {
  try {
    await sessionApi.delete(sessionId)
    sessions.value = sessions.value.filter(s => s.id !== sessionId)
    if (activeSessionId.value === sessionId) {
      if (sessions.value.length > 0) {
        await switchSession(sessions.value[0].id)
      } else {
        await createSession()
      }
    }
  } catch (e) {
    console.error('删除会话失败:', e)
  }
}

async function handleMessageSent(event) {
  // 确保有活跃会话再处理消息
  if (!activeSessionId.value) {
    await createSession()
  }

  if (event.type === 'session-created') {
    // ChatWindow创建了新会话，更新本地状态
    activeSessionId.value = event.sessionId
    if (event.session && !sessions.value.find(s => s.id === event.sessionId)) {
      sessions.value.unshift(event.session)
    }
    return
  }

  if (event.type === 'stream-chunk') {
    const lastMsg = currentMessages.value[currentMessages.value.length - 1]
    if (lastMsg && lastMsg.role === 'assistant' && lastMsg._streaming) {
      lastMsg.content = event.content
    } else {
      currentMessages.value.push({
        id: 'streaming',
        role: 'assistant',
        content: event.content,
        _streaming: true,
      })
    }
  } else if (event.type === 'stream-done' || event.type === 'sync-response') {
    const lastMsg = currentMessages.value[currentMessages.value.length - 1]
    if (lastMsg && lastMsg._streaming) {
      lastMsg.content = event.content
      lastMsg._streaming = false
    } else {
      currentMessages.value.push({
        id: Date.now().toString(),
        role: 'assistant',
        content: event.content,
      })
    }
    // 刷新会话列表（更新标题）和消息
    loadSessions()
    if (activeSessionId.value) {
      sessionApi.getMessages(activeSessionId.value).then(res => {
        currentMessages.value = res.data || []
      })
    }
  } else if (event.type === 'error') {
    currentMessages.value.push({
      id: Date.now().toString(),
      role: 'assistant',
      content: event.content,
    })
  } else {
    // 用户消息直接添加
    currentMessages.value.push({
      id: Date.now().toString(),
      role: 'user',
      content: event.message,
    })
  }
}
</script>
