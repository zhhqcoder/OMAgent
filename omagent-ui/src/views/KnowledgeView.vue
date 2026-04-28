<template>
  <div class="flex flex-col h-full p-6 overflow-y-auto scrollbar-thin">
    <div class="max-w-4xl mx-auto w-full">
      <!-- 标题 -->
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-xl font-semibold text-[#F0F0F0]">知识库管理</h1>
          <p class="text-sm text-[#8C8C8C] mt-1">管理OMS运维知识库，导入配置说明和源码文档</p>
        </div>
      </div>

      <!-- 知识库卡片 -->
      <div class="grid grid-cols-2 gap-4 mb-8">
        <!-- 配置知识库 -->
        <div class="glass-panel p-5">
          <div class="flex items-center gap-3 mb-4">
            <div class="w-10 h-10 rounded-xl bg-primary/15 flex items-center justify-center">
              <Settings :size="20" class="text-primary" />
            </div>
            <div>
              <h3 class="text-sm font-medium text-[#F0F0F0]">配置知识库</h3>
              <p class="text-xs text-[#8C8C8C]">配置说明书、配置模板、参数范围</p>
            </div>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-2xl font-bold text-primary">{{ knowledgeStats.configTotal || 0 }}</span>
            <span class="text-xs text-[#8C8C8C]">文档数</span>
          </div>
        </div>

        <!-- 源码/日志库 -->
        <div class="glass-panel p-5">
          <div class="flex items-center gap-3 mb-4">
            <div class="w-10 h-10 rounded-xl bg-success/15 flex items-center justify-center">
              <Code :size="20" class="text-success" />
            </div>
            <div>
              <h3 class="text-sm font-medium text-[#F0F0F0]">源码/日志知识库</h3>
              <p class="text-xs text-[#8C8C8C]">源码片段、FAQ、日志模式</p>
            </div>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-2xl font-bold text-success">{{ knowledgeStats.sourceTotal || 0 }}</span>
            <span class="text-xs text-[#8C8C8C]">文档数</span>
          </div>
        </div>
      </div>

      <!-- 导入面板 -->
      <div class="glass-panel p-5 mb-6">
        <h3 class="text-sm font-medium text-[#F0F0F0] mb-4">导入文档</h3>
        <div class="flex gap-4 mb-4">
          <select v-model="importTarget" class="bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-[#D9D9D9] outline-none">
            <option value="config">配置知识库</option>
            <option value="source">源码/日志知识库</option>
          </select>
          <input v-model="importDocType" placeholder="文档类型（如：config-guide）"
            class="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-[#D9D9D9] outline-none placeholder-[#8C8C8C]/50" />
          <input v-model="importModule" placeholder="模块名（选填）"
            class="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-[#D9D9D9] outline-none placeholder-[#8C8C8C]/50" />
        </div>

        <!-- 拖拽上传区域 -->
        <div
          @dragover.prevent="isDragging = true"
          @dragleave="isDragging = false"
          @drop.prevent="handleDrop"
          @click="triggerUpload"
          class="border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-all duration-200"
          :class="isDragging ? 'border-primary bg-primary/5' : 'border-white/10 hover:border-white/20'"
        >
          <UploadCloud :size="32" class="mx-auto mb-3 text-[#8C8C8C]" />
          <p class="text-sm text-[#8C8C8C]">拖拽文件到此处，或点击选择文件</p>
          <p class="text-xs text-[#8C8C8C]/50 mt-1">支持 .txt .log .csv .md .doc .docx .java .xml .yml .properties .sql .json .sh .pom .zip 等格式</p>
        </div>
        <input ref="uploadInput" type="file" accept=".txt,.log,.csv,.md,.doc,.docx,.java,.xml,.yml,.yaml,.properties,.sql,.json,.js,.ts,.html,.css,.sh,.pom,.zip" class="hidden" @change="handleFileSelect" />

        <!-- 上传进度 -->
        <div v-if="uploading" class="mt-4">
          <div class="flex items-center justify-between text-xs text-[#8C8C8C] mb-1">
            <span>正在处理...</span>
            <span>{{ uploadProgress }}%</span>
          </div>
          <div class="w-full h-1.5 bg-white/5 rounded-full overflow-hidden">
            <div class="h-full bg-primary rounded-full transition-all duration-300" :style="{ width: uploadProgress + '%' }"></div>
          </div>
        </div>

        <!-- 上传结果 -->
        <div v-if="uploadResult" class="mt-4 p-3 rounded-lg text-sm" :class="uploadResult.success ? 'bg-success/10 text-success' : 'bg-danger/10 text-danger'">
          {{ uploadResult.message }}
        </div>
      </div>

      <!-- 已上传文件列表 -->
      <div class="glass-panel p-5">
        <h3 class="text-sm font-medium text-[#F0F0F0] mb-4">已上传文件</h3>
        <div v-if="uploadedFiles.length === 0" class="text-center py-8 text-[#8C8C8C] text-xs">
          暂无上传文件
        </div>
        <div v-else class="space-y-2">
          <div v-for="file in uploadedFiles" :key="file.id"
            class="flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-white/5 transition-colors">
            <FileText :size="16" class="text-[#8C8C8C]" />
            <div class="flex-1 min-w-0">
              <div class="text-sm text-[#D9D9D9] truncate">{{ file.originalName }}</div>
              <div class="text-[10px] text-[#8C8C8C]">
                {{ file.knowledgeType || '未分类' }} · {{ formatFileSize(file.fileSize) }}
              </div>
            </div>
            <div class="flex items-center gap-2">
              <span v-if="file.vectorized" class="text-[10px] px-2 py-0.5 rounded-full bg-success/10 text-success">已向量化</span>
              <span v-else class="text-[10px] px-2 py-0.5 rounded-full bg-warning/10 text-warning">未处理</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Settings, Code, UploadCloud, FileText } from 'lucide-vue-next'
import { fileApi, knowledgeApi } from '../api/chat'

const knowledgeStats = ref({})
const uploadedFiles = ref([])
const importTarget = ref('config')
const importDocType = ref('')
const importModule = ref('')
const isDragging = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadResult = ref(null)
const uploadInput = ref(null)

onMounted(async () => {
  await loadData()
})

async function loadData() {
  try {
    const [statsRes, filesRes] = await Promise.all([
      knowledgeApi.stats(),
      fileApi.list(),
    ])
    knowledgeStats.value = statsRes.data
    uploadedFiles.value = filesRes.data || []
  } catch (e) {
    console.error('加载数据失败:', e)
  }
}

function triggerUpload() {
  uploadInput.value?.click()
}

async function handleFileSelect(event) {
  const file = event.target.files?.[0]
  if (file) {
    await uploadAndImport(file)
  }
  event.target.value = ''
}

async function handleDrop(event) {
  isDragging.value = false
  const file = event.dataTransfer.files?.[0]
  if (file) {
    await uploadAndImport(file)
  }
}

async function uploadAndImport(file) {
  uploading.value = true
  uploadProgress.value = 10
  uploadResult.value = null

  try {
    // 上传文件
    const uploadRes = await fileApi.upload(file, 'default', importTarget.value)
    uploadProgress.value = 50

    // 导入知识库
    const importRes = await knowledgeApi.import({
      fileId: uploadRes.data.id,
      knowledgeType: importTarget.value,
      docType: importDocType.value || null,
      module: importModule.value || null,
    })
    uploadProgress.value = 100
    uploadResult.value = { success: true, message: importRes.data.message }

    await loadData()
  } catch (e) {
    uploadResult.value = { success: false, message: '导入失败: ' + (e.response?.data?.message || e.message) }
  } finally {
    uploading.value = false
  }
}

function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return size.toFixed(1) + ' ' + units[i]
}
</script>
