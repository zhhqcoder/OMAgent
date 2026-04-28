/**
 * SSE流式接收工具
 */
export function readSSEStream(response, onChunk, onDone, onError) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  function read() {
    reader.read().then(({ done, value }) => {
      if (done) {
        if (buffer.trim()) {
          onChunk(buffer)
        }
        onDone && onDone()
        return
      }

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed || trimmed.startsWith(':')) continue

        if (trimmed.startsWith('data:')) {
          const data = trimmed.slice(5).trim()
          if (data === '[DONE]') {
            onDone && onDone()
            return
          }
          onChunk(data)
        } else {
          onChunk(trimmed)
        }
      }

      read()
    }).catch(err => {
      onError && onError(err)
    })
  }

  read()
}
