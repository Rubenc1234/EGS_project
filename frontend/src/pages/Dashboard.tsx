import React, { useState, useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getWallet, listTransactions, getRealBlockchainBalance, requestRefund, acceptRefund, denyRefund } from '../services/api'
import useSSE from '../hooks/useSSE'
import { Box, Button, Card, CardContent, Typography, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper, Grid, Dialog, DialogTitle, DialogContent, DialogActions, TextField, FormControl, InputLabel, Select, MenuItem, Snackbar, Alert, IconButton, Tooltip } from '@mui/material'
import SendIcon from '@mui/icons-material/Send'
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet'
import RefreshIcon from '@mui/icons-material/Refresh'
import CheckIcon from '@mui/icons-material/Check'
import CloseIcon from '@mui/icons-material/Close'
import api from '../services/api'

export default function Dashboard() {
  const paymentBaseUrl = import.meta.env.VITE_PAYMENT_BASE_URL || 'http://payment.pt'
  const { data: wallet, isLoading: walletLoading, refetch: refetchWallet } = useQuery(['wallet'], getWallet)
  const queryClient = useQueryClient()
  const { data: txs, isLoading: txsLoading } = useQuery(['transactions', wallet?.id], () => 
    listTransactions({ wallet_id: wallet?.id, limit: 20 }), 
    { 
      enabled: !!wallet?.id,
      staleTime: 1000,
      refetchInterval: 3000  // Auto-refetch transactions every 3 seconds
    }
  )
  const { data: realBalance, isLoading: loadingRealBalance, refetch: refetchRealBalance } = useQuery(
    ['realBalance', wallet?.id],
    () => getRealBlockchainBalance(wallet?.id!),
    { 
      enabled: !!wallet?.id, 
      staleTime: 1000,  // Consider stale after 1 second
      refetchInterval: 3000  // Auto-refetch every 3 seconds for live updates
    }
  )
  const [userInfo, setUserInfo] = useState<any>(null)
  const [openWalletPrompt, setOpenWalletPrompt] = useState(false)
  const [walletIdInput, setWalletIdInput] = useState('')
  const [savingWallet, setSavingWallet] = useState(false)

  // Refund dialog state
  const [openRefund, setOpenRefund] = useState(false)
  const [refundTxId, setRefundTxId] = useState('')
  const [refundReason, setRefundReason] = useState('')
  const [loadingRefund, setLoadingRefund] = useState(false)

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

  // Add Funds Modal state
  const [openAddFunds, setOpenAddFunds] = useState(false)
  const [addFundsAmount, setAddFundsAmount] = useState('10')

  const handleOpenAddFunds = () => {
    if (!wallet?.id) {
      alert('Wallet not loaded yet. Please try again.')
      return
    }
    setOpenAddFunds(true)
  }

  const handleCloseAddFunds = () => {
    setOpenAddFunds(false)
  }

  const handleSubmitAddFunds = () => {
    const amount = parseFloat(addFundsAmount)
    if (isNaN(amount) || amount <= 0) {
      setSnack({ open: true, message: 'Please enter a valid amount', severity: 'error' })
      return
    }

    const walletId = wallet?.id
    const redirectUrl = window.location.origin + import.meta.env.BASE_URL + 'dashboard'
    // Redirect to payment service with pre-filled parameters to skip the manual entry page
    const paymentUrl = `${paymentBaseUrl}/?wallet_id=${walletId}&amount=${amount}&redirect_url=${encodeURIComponent(redirectUrl)}`
    
    window.location.href = paymentUrl
  }

  // Modal state and form
  const [openSend, setOpenSend] = useState(false)
  const [toWallet, setToWallet] = useState('')
  const [amount, setAmount] = useState('')
  const [asset, setAsset] = useState('ETH')
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
      setAsset('ETH')
      setOpenSend(false)
      
      // 🔄 Refetch REAL blockchain balance to show updated saldo
      refetchRealBalance()
      
      // Wait a bit then refetch transactions to show new transaction
      setTimeout(() => {
        queryClient.invalidateQueries(['transactions', wallet?.id])
      }, 2000)
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

  const handleRequestRefund = (txId: string) => {
    setRefundTxId(txId)
    setRefundReason('')
    setOpenRefund(true)
  }

  const handleSubmitRefund = async () => {
    try {
      setLoadingRefund(true)
      await requestRefund(refundTxId, refundReason)
      setSnack({ open: true, message: 'Refund request submitted successfully!', severity: 'success' })
      setOpenRefund(false)
      // Invalidate queries to refresh transaction list
      queryClient.invalidateQueries(['transactions', wallet?.id])
    } catch (e: any) {
      console.error('Refund request failed:', e)
      setSnack({ open: true, message: e?.response?.data?.error || 'Failed to request refund', severity: 'error' })
    } finally {
      setLoadingRefund(false)
    }
  }

  const handleAcceptRefund = async (txId: string) => {
    if (!window.confirm('Are you sure you want to accept this refund request? The funds will be returned to the sender.')) return
    try {
      const response = await acceptRefund(txId)
      setSnack({
        open: true,
        message: response?.message || 'Refund accepted. Blockchain submission started.',
        severity: 'success'
      })
      queryClient.invalidateQueries(['transactions', wallet?.id])
      refetchRealBalance()
    } catch (e: any) {
      console.error('Accept refund failed:', e)
      setSnack({ open: true, message: e?.response?.data?.error || 'Failed to accept refund', severity: 'error' })
    }
  }

  const handleDenyRefund = async (txId: string) => {
    if (!window.confirm('Are you sure you want to deny this refund request?')) return
    try {
      await denyRefund(txId)
      setSnack({ open: true, message: 'Refund denied.', severity: 'success' })
      queryClient.invalidateQueries(['transactions', wallet?.id])
    } catch (e: any) {
      console.error('Deny refund failed:', e)
      setSnack({ open: true, message: e?.response?.data?.error || 'Failed to deny refund', severity: 'error' })
    }
  }

  return (
    <Box sx={{ padding: 3, maxWidth: 1200, margin: '0 auto' }}>
      {/* Header */}
      <Box sx={{ marginBottom: 4 }}>
        <Typography variant="h4" sx={{ fontWeight: 'bold' }}>
          Welcome back, {userInfo?.name || 'User'}!
        </Typography>
        <Typography variant="body2" color="textSecondary">
          {userInfo?.email}
        </Typography>
      </Box>

      <Grid container spacing={3}>
        {/* Wallet Card */}
        <Grid item xs={12} md={6}>
          <Card sx={{ 
            backgroundColor: '#1976d2',
            color: 'white',
            minHeight: 200,
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
            borderRadius: '12px',
            boxShadow: '0 4px 15px rgba(25, 118, 210, 0.2)'
          }}>
            <CardContent>
              <Typography variant="h6" sx={{ opacity: 0.9 }}>
                Current Balance
              </Typography>
              <Typography variant="h3" sx={{ fontWeight: 'bold', marginTop: 2, marginBottom: 3 }}>
                {loadingRealBalance ? (
                  <span style={{ fontSize: '1.5rem', opacity: 0.7 }}>Loading...</span>
                ) : realBalance !== null ? (
                  <span>€{realBalance.toFixed(2)}</span>
                ) : (
                  <span style={{ fontSize: '1.5rem', opacity: 0.7 }}>Error loading</span>
                )}
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
                  backgroundColor: '#1976d2',
                  padding: '14px 28px',
                  borderRadius: '12px',
                  fontWeight: 600,
                  textTransform: 'none',
                  boxShadow: '0 4px 15px rgba(25, 118, 210, 0.2)',
                  transition: 'all 0.3s ease-in-out',
                  '&:hover': {
                    transform: 'translateY(-2px)',
                    boxShadow: '0 6px 20px rgba(25, 118, 210, 0.4)',
                    backgroundColor: '#1565c0',
                  }
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
                onClick={handleOpenAddFunds}
                sx={{ 
                  backgroundColor: '#1976d2',
                  padding: '14px 28px',
                  borderRadius: '12px',
                  fontWeight: 600,
                  textTransform: 'none',
                  boxShadow: '0 4px 15px rgba(25, 118, 210, 0.2)',
                  transition: 'all 0.3s ease-in-out',
                  '&:hover': {
                    transform: 'translateY(-2px)',
                    boxShadow: '0 6px 20px rgba(25, 118, 210, 0.4)',
                    backgroundColor: '#1565c0',
                  }
                }}
              >
                Add funds to wallet
              </Button>
            </Grid>
          </Grid>
        </Grid>
      </Grid>

      {/* Add Funds Modal */}
      <Dialog open={openAddFunds} onClose={handleCloseAddFunds} fullWidth maxWidth="sm">
        <DialogTitle>Add funds to your wallet</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <Typography variant="body2" color="textSecondary">
              Enter the amount you wish to add to your wallet. You will be redirected to our secure payment provider.
            </Typography>
            <TextField 
              label="Amount (€)" 
              type="number"
              value={addFundsAmount} 
              onChange={(e) => setAddFundsAmount(e.target.value)} 
              fullWidth 
              slotProps={{ htmlInput: { min: 0.5, step: 0.01 } }}
            />
          </Box>
        </DialogContent>
        <DialogActions sx={{ p: 2.5, gap: 1.5 }}>
          <Button 
            onClick={handleCloseAddFunds}
            sx={{
              borderRadius: '8px',
              textTransform: 'none',
              fontWeight: 600,
              color: 'text.secondary'
            }}
          >
            Cancel
          </Button>
          <Button 
            onClick={handleSubmitAddFunds} 
            variant="contained"
            sx={{
              borderRadius: '8px',
              textTransform: 'none',
              fontWeight: 600,
              padding: '8px 20px',
              boxShadow: 'none',
              '&:hover': {
                boxShadow: 'none'
              }
            }}
          >
            Continue to Payment
          </Button>
        </DialogActions>
      </Dialog>

      {/* Send Modal */}
      <Dialog open={openSend} onClose={handleCloseSend} fullWidth maxWidth="sm">
        <DialogTitle>Send funds to another wallet</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <TextField label="From wallet" value={wallet?.id ?? ''} disabled fullWidth />
            <TextField label="To wallet (0x...) or user id" value={toWallet} onChange={(e) => setToWallet(e.target.value)} fullWidth />
            <TextField label="Amount (EUR)" value={amount} onChange={(e) => setAmount(e.target.value)} fullWidth />
            {(() => {
              const numAmount = parseFloat(amount) || 0;
              const fee = numAmount * 0.02;
              const netAmount = numAmount - fee;
              if (numAmount <= 0) return null;
              return (
                <Box sx={{ mt: 1, padding: 1.5, borderRadius: 1, backgroundColor: '#f9f9f9', border: '1px solid #e0e0e0' }}>
                  <Typography variant="body2" color="textSecondary">
                    Tax (2%): <strong>€{fee.toFixed(2)}</strong>
                  </Typography>
                  <Typography variant="body2" color="textSecondary">
                    Recipient receives: <strong>€{netAmount.toFixed(2)}</strong>
                  </Typography>
                </Box>
              );
            })()}
          </Box>
        </DialogContent>
        <DialogActions sx={{ p: 2.5, gap: 1.5 }}>
          <Button 
            onClick={handleCloseSend} 
            disabled={loadingSend}
            sx={{
              borderRadius: '8px',
              textTransform: 'none',
              fontWeight: 600,
              color: 'text.secondary'
            }}
          >
            Cancel
          </Button>
          <Button 
            onClick={handleSubmitSend} 
            variant="contained" 
            disabled={loadingSend || (parseFloat(amount) || 0) <= 0}
            sx={{
              borderRadius: '8px',
              textTransform: 'none',
              fontWeight: 600,
              padding: '8px 20px',
              boxShadow: 'none',
              '&:hover': {
                boxShadow: 'none'
              }
            }}
          >
            {loadingSend ? 'Sending...' : `Send`}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Refund Request Reason Modal */}
      <Dialog open={openRefund} onClose={() => setOpenRefund(false)} fullWidth maxWidth="sm">
        <DialogTitle>Request Refund</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <Typography>
              Please provide a reason for the refund request.
            </Typography>
            <TextField 
              label="Reason" 
              value={refundReason} 
              onChange={(e) => setRefundReason(e.target.value)} 
              fullWidth 
              multiline
              rows={3}
            />
          </Box>
        </DialogContent>
        <DialogActions sx={{ p: 2.5, gap: 1.5 }}>
          <Button 
            onClick={() => setOpenRefund(false)} 
            disabled={loadingRefund}
            sx={{
              borderRadius: '8px',
              textTransform: 'none',
              fontWeight: 600,
              color: 'text.secondary'
            }}
          >
            Cancel
          </Button>
          <Button 
            onClick={handleSubmitRefund} 
            variant="contained" 
            disabled={loadingRefund}
            sx={{
              borderRadius: '8px',
              textTransform: 'none',
              fontWeight: 600,
              padding: '8px 20px',
              boxShadow: 'none',
              '&:hover': {
                boxShadow: 'none'
              }
            }}
          >
            Submit Request
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={snack.open} autoHideDuration={4000} onClose={() => setSnack({ ...snack, open: false })}>
        <Alert severity={snack.severity} onClose={() => setSnack({ ...snack, open: false })}>{snack.message}</Alert>
      </Snackbar>

      {/* Transaction History */}
      <Box sx={{ marginTop: 4 }}>
        <Typography variant="h6" sx={{ fontWeight: 'bold', marginBottom: 2 }}>
          Recent Transactions
        </Typography>
        <TableContainer component={Paper}>
          <Table>
            <TableHead sx={{ backgroundColor: '#f5f5f5' }}>
              <TableRow>
                <TableCell><strong>Date</strong></TableCell>
                <TableCell><strong>Description</strong></TableCell>
                <TableCell align="right"><strong>Amount</strong></TableCell>
                <TableCell><strong>Status</strong></TableCell>
                <TableCell><strong>Actions</strong></TableCell>
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
                  
                  const isSender = wallet?.id && t.from && wallet.id.toLowerCase() === t.from.toLowerCase()
                  const isReceiver = wallet?.id && t.to && wallet.id.toLowerCase() === t.to.toLowerCase()
                  const isFromBank = t.from && t.from.toLowerCase() === '0xcc1d685797fc94209814476c97baf4be5a22f6de'

                  return (
                  <TableRow key={key} hover>
                    <TableCell>{dateStr}</TableCell>
                    <TableCell>
                      {(isReceiver && isFromBank) ? (t.type === 'REFUND' ? 'REFUND' : 'FUND WALLET') : (t.description || t.type || 'Transaction')}
                      {isSender && <Typography variant="caption" display="block" color="textSecondary">Outbound to {t.to?.substring(0, 10)}...</Typography>}
                      {isReceiver && <Typography variant="caption" display="block" color="textSecondary">Inbound from {t.from?.substring(0, 10)}...</Typography>}
                    </TableCell>
                    <TableCell align="right" sx={{ 
                      fontWeight: 'bold',
                      color: isSender ? 'error.main' : (isReceiver ? 'success.main' : 'inherit')
                    }}>
                      {isSender ? '-' : ''}€{parseFloat(t.amount || '0').toFixed(2)}
                    </TableCell>
                    <TableCell>
                      {(() => {
                        let displayStatus = t.status;
                        let bgColor = '#fff3e0'; // Default warning/pending
                        let color = '#e65100';

                        const status = (t.status || '').toUpperCase();

                        if (status === 'CONFIRMED' || status === 'COMPLETED') {
                          displayStatus = 'CONFIRMED';
                          bgColor = '#e8f5e9'; // Light green
                          color = '#2e7d32';
                        } else if (status === 'FAILED' || status === 'REJECTED') {
                          displayStatus = 'FAILED';
                          bgColor = '#ffebee'; // Light red
                          color = '#c62828';
                        } else if (status === 'AWAITING_APPROVAL') {
                          displayStatus = 'AWAITING APPROVAL';
                          bgColor = '#e3f2fd'; // Light blue
                          color = '#1976d2';
                        } else if (status === 'BROADCASTED') {
                          displayStatus = 'BROADCASTED';
                          bgColor = '#ede7f6'; // Light purple
                          color = '#5e35b1';
                        } else if (status === 'PENDING') {
                          displayStatus = 'PENDING';
                          bgColor = '#fff8e1'; // Light yellow
                          color = '#ff8f00';
                        }

                        return (
                          <Box
                            sx={{
                              display: 'inline-block',
                              padding: '4px 12px',
                              borderRadius: '12px',
                              fontSize: '12px',
                              fontWeight: 'bold',
                              backgroundColor: bgColor,
                              color: color,
                            }}
                          >
                            {displayStatus}
                          </Box>
                        );
                      })()}
                    </TableCell>
                    <TableCell>
                      {/* Refund Button for Sender (Original Sender requests refund) */}
                      {isSender && (t.status === 'CONFIRMED' || t.status === 'completed') && !t.refunded && t.type !== 'REFUND' && (
                        <Tooltip title="Request Refund">
                          <Button 
                            size="small" 
                            variant="outlined" 
                            startIcon={<RefreshIcon />}
                            onClick={() => handleRequestRefund(t.tx_id || t.txId || t.id)}
                            sx={{
                              borderRadius: '8px',
                              textTransform: 'none',
                              fontWeight: 600,
                              borderColor: 'rgba(0, 0, 0, 0.23)',
                              color: 'text.primary',
                              '&:hover': {
                                borderColor: 'text.primary',
                                backgroundColor: 'rgba(0, 0, 0, 0.04)',
                              }
                            }}
                          >
                            Refund
                          </Button>
                        </Tooltip>
                      )}

                      {/* Accept/Deny Buttons for current user when they are the ones who received the funds originally (now they are the 'from' in the refund request) */}
                      {isSender && t.status === 'AWAITING_APPROVAL' && t.type === 'REFUND' && (
                        <Box sx={{ display: 'flex', gap: 1 }}>
                          <Tooltip title="Accept Refund">
                            <IconButton 
                              size="small" 
                              onClick={() => handleAcceptRefund(t.tx_id || t.txId || t.id)}
                            >
                              <CheckIcon />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Deny Refund">
                            <IconButton 
                              size="small" 
                              onClick={() => handleDenyRefund(t.tx_id || t.txId || t.id)}
                            >
                              <CloseIcon />
                            </IconButton>
                          </Tooltip>
                        </Box>
                      )}
                      
                      {t.refunded && (
                        <Typography variant="caption" color="textSecondary">
                          Refund Processed
                        </Typography>
                      )}
                    </TableCell>
                  </TableRow>
                  )
                })
              ) : (
                <TableRow>
                  <TableCell colSpan={5} align="center" sx={{ padding: '24px' }}>
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
