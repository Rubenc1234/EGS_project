import axios from 'axios'

const api = axios.create({ baseURL: '/', withCredentials: true })

export async function getWallet() {
  const res = await api.get('/v1/composer/users/me/wallet')
  return res.data
}

export async function listTransactions(params: any = {}) {
  const res = await api.get('/v1/composer/transactions', { params })
  return res.data
}

export default api
