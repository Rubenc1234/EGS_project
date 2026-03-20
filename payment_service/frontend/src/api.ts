import axios from 'axios'

const api = axios.create({ baseURL: '/' })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('payment_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export function getToken(): string | null {
  return localStorage.getItem('payment_token')
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

export async function concludePayment(paymentId: string): Promise<void> {
  await api.patch(`/v1/payments/${paymentId}`, { status: 'concluded' })
}
