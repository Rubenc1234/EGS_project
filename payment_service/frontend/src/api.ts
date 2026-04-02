import axios from 'axios'

// Use Vite dev proxy in local development to avoid browser CORS issues.
const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '' })

export function isTokenExpired(token: string) {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
    }).join(''))

    const payload = JSON.parse(jsonPayload)
    const now = Math.floor(Date.now() / 1000)
    
    if (payload.exp && payload.exp < now) {
      return true
    }
    return false
  } catch (e) {
    return true
  }
}

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('payment_token')
  if (token) {
    if (isTokenExpired(token)) {
      localStorage.removeItem('payment_token')
    } else {
      config.headers.Authorization = `Bearer ${token}`
    }
  }
  return config
})

// Handle 401 Unauthorized globally
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('payment_token')
      // Only redirect if we are not already on the login page
      if (window.location.pathname !== '/') {
        window.location.href = '/'
      }
    }
    return Promise.reject(error)
  }
)

export function getToken(): string | null {
  const token = localStorage.getItem('payment_token')
  if (!token) return null
  if (isTokenExpired(token)) {
    localStorage.removeItem('payment_token')
    return null
  }
  return token
}

export function setToken(token: string): void {
  localStorage.setItem('payment_token', token)
}

export function clearToken(): void {
  localStorage.removeItem('payment_token')
}

export async function fetchLoginUrl(redirectUri: string): Promise<string> {
  const res = await api.get('/v1/pay/login', { params: { redirect_uri: redirectUri } })
  return res.data.login_url
}

export async function exchangeCode(code: string, redirectUri: string): Promise<string> {
  const res = await api.post('/v1/pay/callback', { code, redirect_uri: redirectUri })
  return res.data.access_token
}

export interface PaymentResponse {
  id: string
  user_id: string
  amount: number
  status: string
  wallet_id: string | null
  redirect_url: string | null
  stripe_client_secret: string | null
  stripe_payment_intent_id: string | null
  created_at: string | null
}

export function getUserIdFromToken(): string | null {
  const token = getToken()
  if (!token) return null
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return payload.sub ?? null
  } catch {
    return null
  }
}

export async function getUserPayments(userId: string): Promise<PaymentResponse[]> {
  const res = await api.get('/v1/payments', { params: { user_id: userId } })
  return res.data
}

export async function cancelPayment(paymentId: string): Promise<PaymentResponse> {
  const res = await api.patch(`/v1/payments/${paymentId}`, { status: 'cancelled' })
  return res.data
}

export async function downloadReceipt(paymentId: string): Promise<void> {
  const res = await api.get(`/v1/payments/${paymentId}/receipt`, { responseType: 'blob' })
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url
  a.download = `receipt_${paymentId}.pdf`
  a.click()
  URL.revokeObjectURL(url)
}

export async function createPayment(params: {
  user_id: string
  amount: number
  wallet_id?: string
  redirect_url?: string
}): Promise<PaymentResponse> {
  const res = await api.post('/v1/payments', params)
  return res.data
}

