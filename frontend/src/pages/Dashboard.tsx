import React, { useState, useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getWallet, listTransactions } from '../services/api'
import useSSE from '../hooks/useSSE'
import { Box, Button, Card, CardContent, Typography, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper, Grid, Dialog, DialogTitle, DialogContent, DialogActions, TextField, FormControl, InputLabel, Select, MenuItem, Snackbar, Alert } from '@mui/material'
import SendIcon from '@mui/icons-material/Send'
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet'
import api from '../services/api'

export default function Dashboard() {
  const { data: wallet, isLoading: walletLoading, refetch: refetchWallet } = useQuery(['wallet'], getWallet)
  const queryClient = useQueryClient()
  const { data: txs, isLoading: txsLoading } = useQuery(['transactions', wallet?.id], () => 
    listTransactions({ wallet_id: wallet?.id, limit: 20 }), 
    { enabled: !!wallet?.id }  // Only fetch transactions when wallet is loaded
  )
  const [userInfo, setUserInfo] = useState<any>(null)
  const [openWalletPrompt, setOpenWalletPrompt] = useState(false)
  const [walletIdInput, setWalletIdInput] = useState('')
  const [savingWallet, setSavingWallet] = useState(false)

  // TODO: Implement SSE endpoint POST /v1/events/subscribe or similar in backend for live updates
  // useSSE('/v1/events/me')

  // Extract user info from token (JWT decode)
  useEffect(() => {
    const token = localStorage.getItem('egs_token')
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]))
        setUserInfo({
          name: payload.given_name || payload.preferred_username || 'User',
          email: payload.email || '—',
        })
      } catch (e) {
        console.error('Failed to decode token:', e)
      }
    }
  }, [])

  // If wallet not loaded and no saved wallet, show error state (should not happen now)
  useEffect(() => {
    if (!walletLoading && !wallet) {
      console.warn('Wallet failed to load - this should not happen if POST /v1/users/me/wallet is working.')
    }
  }, [wallet, walletLoading])

  const handleSendMoney = () => {
    // Open modal to send internally (composer wallets)
    if (!wallet?.id) {
      alert('Wallet not loaded yet. Please try again.')
      return
    }
    setOpenSend(true)
  }

  // Modal state and form
  const [openSend, setOpenSend] = useState(false)
  const [toWallet, setToWallet] = useState('')
  const [amount, setAmount] = useState('')
  const [asset, setAsset] = useState('EUR')
  const [loadingSend, setLoadingSend] = useState(false)
  const [snack, setSnack] = useState<{ open: boolean; message: string; severity?: 'success'|'error' }>({ open: false, message: '', severity: 'success' })

  const handleCloseSend = () => {
    setOpenSend(false)
  }

  const handleSubmitSend = async () => {
    if (!toWallet || !amount) {
      setSnack({ open: true, message: 'Please fill recipient and amount', severity: 'error' })
      return
    }

    try {
      setLoadingSend(true)
      const payload = {
        from_wallet: wallet?.id,
        to_wallet: toWallet,
        amount: parseFloat(amount).toString(),
        asset: asset,
        idempotency_key: `${wallet?.id}-${toWallet}-${Date.now()}-${Math.random()}`,
      }
      console.log('Sending transaction:', payload)
      const response = await api.post('/v1/transactions', payload)
      console.log('Transaction response:', response.data)
      setSnack({ open: true, message: `Transaction submitted (ID: ${response.data?.tx_id || 'pending'})`, severity: 'success' })
      // reset
      setToWallet('')
      setAmount('')
      setAsset('EUR')
      setOpenSend(false)
    } catch (e: any) {
      console.error('Send failed:', e?.response?.data || e.message, e)
      setSnack({ open: true, message: e?.response?.data?.error || e?.response?.data?.message || 'Send failed', severity: 'error' })
    } finally {
      setLoadingSend(false)
    }
  }

  const handleSaveWalletId = async () => {
    if (!walletIdInput || !walletIdInput.startsWith('0x') || walletIdInput.length !== 42) {
      setSnack({ open: true, message: 'Invalid wallet address. Must start with 0x and be 42 chars long.', severity: 'error' })
      return
    }
    try {
      setSavingWallet(true)
      // Save to localStorage
      localStorage.setItem('egs_wallet_id', walletIdInput)
      console.log('Saved wallet id to localStorage:', walletIdInput)
      
      // Try to load its balance
      const res = await api.get(`/v1/wallets/${walletIdInput}/balance`)
      queryClient.setQueryData(['wallet'], res.data)
      
      setSnack({ open: true, message: 'Wallet loaded successfully!', severity: 'success' })
      setOpenWalletPrompt(false)
      setWalletIdInput('')
    } catch (e: any) {
      console.error('Failed to load wallet:', e)
      setSnack({ open: true, message: e?.response?.data?.error || 'Failed to load wallet. Check the address and try again.', severity: 'error' })
    } finally {
      setSavingWallet(false)
    }
  }

  return (
    <Box sx={{ padding: 3, maxWidth: 1200, margin: '0 auto' }}>
      {/* Header */}
      <Box sx={{ marginBottom: 4 }}>
        <Typography variant="h4" sx={{ fontWeight: 'bold' }}>
          Welcome back, {userInfo?.name || 'User'}! 👋
        </Typography>
        <Typography variant="body2" color="textSecondary">
          {userInfo?.email}
        </Typography>
      </Box>

      <Grid container spacing={3}>
        {/* Wallet Card */}
        <Grid item xs={12} md={6}>
          <Card sx={{ 
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            color: 'white',
            minHeight: 200,
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between'
          }}>
            <CardContent>
              <Typography variant="h6" sx={{ opacity: 0.9 }}>
                Current Balance
              </Typography>
              <Typography variant="h3" sx={{ fontWeight: 'bold', marginTop: 2, marginBottom: 3 }}>
                €{wallet?.balance?.toFixed(2) ?? '0.00'}
              </Typography>
              <Typography variant="caption" sx={{ opacity: 0.8 }}>
                Wallet ID: {wallet?.id ?? '—'}
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        {/* Action Buttons */}
        <Grid item xs={12} md={6}>
          <Grid container spacing={2} sx={{ height: '100%', flexDirection: 'column' }}>
            <Grid item xs={12}>
              <Button
                fullWidth
                variant="contained"
                startIcon={<SendIcon />}
                size="large"
                onClick={handleSendMoney}
                sx={{ 
                  background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                  padding: '12px 24px'
                }}
              >
                Send Money to User
              </Button>
            </Grid>
            <Grid item xs={12}>
              <Button 
                fullWidth 
                variant="contained" 
                startIcon={<AccountBalanceWalletIcon />}
                size="large"
                onClick={() => {
                  window.location.href = 'http://localhost:5174/';
                }}
                sx={{ 
                  background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                  padding: '12px 24px'
                }}
              >
                Add funds to wallet
              </Button>
            </Grid>
          </Grid>
        </Grid>
      </Grid>

      {/* Send Modal */}
      <Dialog open={openSend} onClose={handleCloseSend} fullWidth maxWidth="sm">
        <DialogTitle>Send funds to another wallet</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <TextField label="From wallet" value={wallet?.id ?? ''} disabled fullWidth />
            <TextField label="To wallet (0x...) or user id" value={toWallet} onChange={(e) => setToWallet(e.target.value)} fullWidth />
            <TextField label="Amount" value={amount} onChange={(e) => setAmount(e.target.value)} fullWidth />
            <FormControl fullWidth>
              <InputLabel id="asset-label">Asset</InputLabel>
              <Select labelId="asset-label" value={asset} label="Asset" onChange={(e) => setAsset(e.target.value)}>
                <MenuItem value="EUR">EUR (token)</MenuItem>
                <MenuItem value="MATIC">MATIC (native)</MenuItem>
              </Select>
            </FormControl>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseSend} disabled={loadingSend}>Cancel</Button>
          <Button onClick={handleSubmitSend} variant="contained" disabled={loadingSend}>Send</Button>
        </DialogActions>
      </Dialog>

      {/* Wallet ID Prompt Modal (no longer needed - wallet auto-created on login) 
      <Dialog open={openWalletPrompt} onClose={() => {}} fullWidth maxWidth="sm">
        <DialogTitle>Wallet Not Found</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <Typography>
              Your Keycloak token doesn't include a wallet address. Please paste your wallet address (0x...) to proceed.
            </Typography>
            <TextField 
              label="Wallet Address (0x...)" 
              value={walletIdInput} 
              onChange={(e) => setWalletIdInput(e.target.value)} 
              fullWidth 
              placeholder="0x1234567890abcdef..."
            />
            <Typography variant="caption" color="textSecondary">
              Tip: Check your wallet in Keycloak or use a known address for testing.
            </Typography>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleSaveWalletId} variant="contained" disabled={savingWallet}>
            Load Wallet
          </Button>
        </DialogActions>
      </Dialog>
      */}

      <Snackbar open={snack.open} autoHideDuration={4000} onClose={() => setSnack({ ...snack, open: false })}>
        <Alert severity={snack.severity} onClose={() => setSnack({ ...snack, open: false })}>{snack.message}</Alert>
      </Snackbar>

      {/* Transaction History */}
      <Box sx={{ marginTop: 4 }}>
        <Typography variant="h6" sx={{ fontWeight: 'bold', marginBottom: 2 }}>
          📊 Recent Transactions
        </Typography>
        <TableContainer component={Paper}>
          <Table>
            <TableHead sx={{ backgroundColor: '#f5f5f5' }}>
              <TableRow>
                <TableCell><strong>Date</strong></TableCell>
                <TableCell><strong>Description</strong></TableCell>
                <TableCell align="right"><strong>Amount</strong></TableCell>
                <TableCell><strong>Status</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {txs?.items?.length ? (
                txs.items.map((t: any) => {
                  // Parse confirmed_at safely - backend sends ISO format like "2026-03-21T19:11:20Z"
                  let dateStr = 'N/A'
                  try {
                    const dateField = t.confirmed_at || t.createdAt // Try both field names
                    if (dateField) {
                      const date = new Date(dateField)
                      if (!isNaN(date.getTime())) {
                        dateStr = date.toLocaleDateString()
                      }
                    }
                  } catch (e) {
                    console.warn('Failed to parse date:', t.confirmed_at, t.createdAt)
                  }
                  
                  // Use tx_id from JSON response (not txId from Java)
                  const key = t.tx_id || t.txId || t.id || Math.random()
                  
                  return (
                  <TableRow key={key} hover>
                    <TableCell>{dateStr}</TableCell>
                    <TableCell>{t.description || t.type || 'Transaction'}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                      €{parseFloat(t.amount || '0').toFixed(2)}
                    </TableCell>
                    <TableCell>
                      <Box
                        sx={{
                          display: 'inline-block',
                          padding: '4px 12px',
                          borderRadius: '12px',
                          fontSize: '12px',
                          fontWeight: 'bold',
                          backgroundColor: t.status === 'completed' ? '#e8f5e9' : '#fff3e0',
                          color: t.status === 'completed' ? '#2e7d32' : '#e65100',
                        }}
                      >
                        {t.status}
                      </Box>
                    </TableCell>
                  </TableRow>
                  )
                })
              ) : (
                <TableRow>
                  <TableCell colSpan={4} align="center" sx={{ padding: '24px' }}>
                    <Typography color="textSecondary">No transactions yet</Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
    </Box>
  )
}
