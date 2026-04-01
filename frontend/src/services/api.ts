import axios from 'axios'

const api = axios.create({ baseURL: 'http://localhost:8081', withCredentials: true })

// set Authorization header from stored token if present using interceptor
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('egs_token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
}, (error) => {
  return Promise.reject(error)
})

export async function getWallet() {
  // Try to create or get wallet via POST /v1/users/me/wallet (auto-generates if needed)
  try {
    const res = await api.post('/v1/users/me/wallet')
    // Cache the wallet id for manual fallback
    const dto = res.data || {}
    const walletId = dto.walletId || dto.wallet_id || dto.wallet || dto.address
    if (walletId) {
      localStorage.setItem('egs_wallet_id', walletId)
    }
    // Normalize to frontend shape: { id, balance: number, nativeBalance }
    const normalized = {
      id: walletId,
      balance: dto.balance ? parseFloat(String(dto.balance)) : (dto.balanceInFiat ? Number(dto.balanceInFiat) : 0),
      nativeBalance: dto.nativeBalance ? parseFloat(String(dto.nativeBalance)) : (dto.native_balance ? parseFloat(String(dto.native_balance)) : 0),
      raw: dto,
    }
    return normalized
  } catch (err: any) {
    console.error('Failed to get wallet via POST /v1/users/me/wallet:', err.message)
    // Fallback: try to use a manually saved wallet id
    const savedWalletId = typeof window !== 'undefined' ? localStorage.getItem('egs_wallet_id') : null
    if (savedWalletId) {
      console.log('Trying fallback: using saved wallet id from localStorage:', savedWalletId)
      const res = await api.get(`/v1/wallets/${savedWalletId}/balance`)
      const dto = res.data || {}
      const walletId = dto.walletId || dto.wallet_id || dto.wallet || dto.address || savedWalletId
      const normalized = {
        id: walletId,
        balance: dto.balance ? parseFloat(String(dto.balance)) : (dto.balanceInFiat ? Number(dto.balanceInFiat) : 0),
        nativeBalance: dto.nativeBalance ? parseFloat(String(dto.nativeBalance)) : (dto.native_balance ? parseFloat(String(dto.native_balance)) : 0),
        raw: dto,
      }
      return normalized
    }
    // No saved wallet and POST failed
    throw err
  }
}

export async function listTransactions(params: any = {}) {
  const res = await api.get('/v1/transactions', { params })
  console.log('listTransactions response:', res.data)
  return res.data
}

export async function sendTransaction(payload: {
  from_wallet: string
  to_wallet: string
  amount: string
  asset: string
  idempotency_key: string
}) {
  const res = await api.post('/v1/transactions', payload)
  return res.data
}

/**
 * Get the REAL balance from MockBlockchain (not calculated from transaction history)
 * This shows the actual funds available for transactions.
 */
export async function getRealBlockchainBalance(walletAddress: string) {
  try {
    const res = await api.get(`/v1/blockchain/${walletAddress}/balance`)
    return res.data?.balance || 0
  } catch (err: any) {
    console.warn('Failed to get real blockchain balance:', err.message)
    return null
  }
}

export default api
