import React, { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getWallet, listTransactions } from '../services/api'
import useSSE from '../hooks/useSSE'
import { Box, Button, Card, CardContent, Typography, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper, Grid } from '@mui/material'
import SendIcon from '@mui/icons-material/Send'

export default function Dashboard() {
  const { data: wallet, isLoading: walletLoading } = useQuery(['wallet'], getWallet)
  const { data: txs, isLoading: txsLoading } = useQuery(['transactions'], () => listTransactions({ limit: 20 }))
  const [userInfo, setUserInfo] = useState<any>(null)

  useSSE('/v1/events/me')

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

  const handleSendMoney = () => {
    // Future: Send money to another user
    alert('Send money feature coming soon!')
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
          </Grid>
        </Grid>
      </Grid>

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
                txs.items.map((t: any) => (
                  <TableRow key={t.id} hover>
                    <TableCell>{new Date(t.createdAt).toLocaleDateString()}</TableCell>
                    <TableCell>{t.description || t.type || 'Transaction'}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                      €{t.amount?.toFixed(2) ?? '0.00'}
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
                ))
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
