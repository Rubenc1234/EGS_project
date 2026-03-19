import axios from 'axios'

const api = axios.create({ baseURL: '/', withCredentials: true })

// set Authorization header from stored token if present
const token = typeof window !== 'undefined' ? localStorage.getItem('egs_token') : null
if (token) {
  api.defaults.headers.common['Authorization'] = `Bearer ${token}`
}

export async function getWallet() {
  // composer endpoints were previously /v1/composer/...; transactions_service will act as composer
  const res = await api.get('/v1/users/me/wallet')
  return res.data
}

export async function listTransactions(params: any = {}) {
  const res = await api.get('/v1/transactions', { params })
  return res.data
}

export default api
