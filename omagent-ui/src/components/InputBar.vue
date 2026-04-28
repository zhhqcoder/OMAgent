<template>
  <div class="px-4 pb-4 pt-2">
    <div class="glass-panel flex items-end gap-2 p-3">
      <!-- 附件按钮 -->
      <button @click="triggerFileInput" class="flex-shrink-0 w-9 h-9 rounded-lg hover:bg-white/10 flex items-center justify-center transition-colors" title="上传TXT文件">
        <Paperclip :size="18" class="text-[#8C8C8C]" />
      </button>
      <input ref="fileInput" type="file" accept=".txt,.log,.csv" class="hidden" @change="handleFileSelect" />

      <!-- 截图按钮 -->
      <button @click="triggerImageInput" class="flex-shrink-0 w-9 h-9 rounded-lg hover:bg-white/10 flex items-center justify-center transition-colors" title="上传截图">
        <Camera :size="18" class="text-[#8C8C8C]" />
      </button>
      <input ref="imageInput" type="file" accept="image/jpeg,image/png,image/webp" class="hidden" @change="handleImageSelect" />

      <!-- 文本输入 -->
      <textarea
        ref="textareaRef"
        v-model="inputText"
        @keydown.enter.exact.prevent="handleSend"
        placeholder="描述您的问题，或粘贴错误日志..."
        rows="1"
        class="flex-1 bg-transparent text-sm text-[#D9D9D9] placeholder-[#8C8C8C]/50 outline-none resize-none max-h-32 py-1.5 scrollbar-thin"
        @input="autoResize"
      ></textarea>

      <!-- 发送按钮 -->
      <button
        @click="handleSend"
        :disabled="!canSend"
        class="flex-shrink-0 w-9 h-9 rounded-lg flex items-center justify-center transition-all duration-200"
        :class="canSend ? 'bg-primary hover:bg-primary-dark text-white cursor-pointer' : 'bg-white/5 text-[#8C8C8C] cursor-not-allowed'"
      >
        <Send :size="16" />
      </button>
    </div>

    <!-- 附件预览 -->
    <div v-if="attachedFile || attachedImage" class="mt-2 flex gap-2">
      <div v-if="attachedFile" class="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-xs text-[#D9D9D9]">
        <FileText :size="14" />
        <span>{{ attachedFile.name }}</span>
        <button @click="attachedFile = null" class="ml-1 hover:text-danger">
          <X :size="12" />
        </button>
      </div>
      <div v-if="attachedImage" class="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-xs text-[#D9D9D9]">
        <ImageIcon :size="14" />
        <span>{{ attachedImage.name }}</span>
        <img v-if="imagePreview" :src="imagePreview" class="w-8 h-6 object-cover rounded" />
        <button @click="clearImage" class="ml-1 hover:text-danger">
          <X :size="12" />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Paperclip, Camera, Send, X, FileText, Image as ImageIcon } from 'lucide-vue-next'

const emit = defineEmits(['send', 'send-with-file', 'send-with-image'])

const inputText = ref('')
const attachedFile = ref(null)
const attachedImage = ref(null)
const imagePreview = ref(null)
const textareaRef = ref(null)
const fileInput = ref(null)
const imageInput = ref(null)

const canSend = computed(() => {
  return inputText.value.trim() || attachedFile.value || attachedImage.value
})

function handleSend() {
  if (!canSend.value) return

  if (attachedImage.value) {
    emit('send-with-image', {
      message: inputText.value,
      file: attachedImage.value,
      base64: imagePreview.value,
    })
  } else if (attachedFile.value) {
    emit('send-with-file', {
      message: inputText.value,
      file: attachedFile.value,
    })
  } else {
    emit('send', inputText.value)
  }

  inputText.value = ''
  attachedFile.value = null
  attachedImage.value = null
  imagePreview.value = null
  autoResize()
}

function triggerFileInput() {
  fileInput.value?.click()
}

function triggerImageInput() {
  imageInput.value?.click()
}

function handleFileSelect(event) {
  const file = event.target.files?.[0]
  if (file) {
    attachedFile.value = file
  }
  event.target.value = ''
}

function handleImageSelect(event) {
  const file = event.target.files?.[0]
  if (file) {
    attachedImage.value = file
    const reader = new FileReader()
    reader.onload = (e) => {
      imagePreview.value = e.target.result
    }
    reader.readAsDataURL(file)
  }
  event.target.value = ''
}

function clearImage() {
  attachedImage.value = null
  imagePreview.value = null
}

function autoResize() {
  const textarea = textareaRef.value
  if (textarea) {
    textarea.style.height = 'auto'
    textarea.style.height = Math.min(textarea.scrollHeight, 128) + 'px'
  }
}
</script>
