import { useEffect } from 'react'

export default function useSSE(path: string, onMessage?: (data: string) => void) {
  useEffect(() => {
    const url = path.startsWith('/') ? path : `/${path}`
    let es: EventSource | null = null
    try {
      es = new EventSource(url)
    } catch (err) {
      console.warn('SSE connection failed', err)
      return
    }

    es.onmessage = (e) => {
      // allow consumer to handle message
      try {
        if (onMessage) onMessage(e.data)
      } catch (err) {
        console.error('SSE onMessage handler error', err)
      }
    }
    es.onerror = (e) => {
      console.warn('SSE error', e)
      if (es) es.close()
    }
    return () => { if (es) es.close() }
  }, [path, onMessage])
}
