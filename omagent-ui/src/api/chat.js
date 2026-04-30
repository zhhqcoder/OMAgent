import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 会话管理
export const sessionApi = {
  create: (userId = 'default') => api.post('/chat/sessions', null, { params: { userId } }),
  list: (userId = 'default') => api.get('/chat/sessions', { params: { userId } }),
  delete: (sessionId, userId = 'default') => api.delete(`/chat/sessions/${sessionId}`, { params: { userId } }),
  getMessages: (sessionId) => api.get(`/chat/sessions/${sessionId}/messages`),
}

// 聊天消息
export const chatApi = {
  send: (data, userId = 'default') => api.post('/chat/messages', data, { params: { userId } }),
  sendStream: (data, userId = 'default') => {
    return fetch(`/api/chat/messages/stream?userId=${userId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    })
  },
}

// 文件上传
export const fileApi = {
  upload: (file, userId = 'default', knowledgeType = null) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/files/upload', formData, {
      params: { userId, knowledgeType },
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  list: (userId = 'default') => api.get('/files', { params: { userId } }),
  getContent: (fileId) => api.get(`/files/${fileId}/content`),
}

// 知识库管理
export const knowledgeApi = {
  import: (data) => api.post('/knowledge/import', data),
  stats: () => api.get('/knowledge/stats'),
  clear: (type) => api.delete(`/knowledge/clear/${type}`),
  deleteFile: (fileId) => api.delete(`/knowledge/file/${fileId}`),
  reimport: (fileId, params = {}) => api.post(`/knowledge/reimport/${fileId}`, null, { params }),
}

export default api
