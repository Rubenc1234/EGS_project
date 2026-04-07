import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
  Tab,
  Tabs,
} from '@mui/material'
import { cancelPayment, clearToken, downloadReceipt, getUserIdFromToken, getUserPayments, PaymentResponse } from '../api'

const STATUS_COLOR: Record<string, 'warning' | 'success' | 'error'> = {
  pending: 'warning',
  concluded: 'success',
  cancelled: 'error',
}

const STATUS_LABEL: Record<string, string> = {
  pending: 'Pendente',
  concluded: 'Concluído',
  cancelled: 'Cancelado',
}

const TABS = ['all', 'pending', 'concluded', 'cancelled'] as const
const TAB_LABEL: Record<string, string> = {
  all: 'Todos',
  pending: 'Pendentes',
  concluded: 'Concluídos',
  cancelled: 'Cancelados',
}

function formatDate(iso: string | null) {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleDateString('pt-PT', { day: '2-digit', month: '2-digit', year: 'numeric' })
    + ' ' + d.toLocaleTimeString('pt-PT', { hour: '2-digit', minute: '2-digit' })
}

export default function PaymentsPage() {
  const navigate = useNavigate()
  const [payments, setPayments] = useState<PaymentResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [tab, setTab] = useState<typeof TABS[number]>('all')
  const [cancelling, setCancelling] = useState<string | null>(null)
  const [downloading, setDownloading] = useState<string | null>(null)

  useEffect(() => {
    const userId = getUserIdFromToken()
    if (!userId) {
      navigate('/', { replace: true })
      return
    }
    getUserPayments(userId)
      .then(data => setPayments(data.sort((a, b) =>
        (b.created_at ?? '').localeCompare(a.created_at ?? '')
      )))
      .catch(() => setError('Não foi possível carregar os pagamentos.'))
      .finally(() => setLoading(false))
  }, [navigate])

  async function handleDownloadReceipt(id: string) {
    setDownloading(id)
    try {
      await downloadReceipt(id)
    } finally {
      setDownloading(null)
    }
  }

  async function handleCancel(id: string) {
    setCancelling(id)
    try {
      const updated = await cancelPayment(id)
      setPayments(prev => prev.map(p => p.id === id ? updated : p))
    } finally {
      setCancelling(null)
    }
  }

  function handleLogout() {
    clearToken()
    navigate('/', { replace: true })
  }

  const concluded = payments.filter(p => p.status === 'concluded')
  const pending = payments.filter(p => p.status === 'pending')
  const totalSpent = concluded.reduce((sum, p) => sum + p.amount, 0)

  const filtered = tab === 'all' ? payments : payments.filter(p => p.status === tab)

  return (
    <Box
      sx={{
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'center',
        pt: 6,
        pb: 6,
      }}
    >
      <Container maxWidth="lg">
        {/* Header */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
          <Typography variant="h5" fontWeight={700} color="white">
            Os meus pagamentos
          </Typography>
          <Button variant="outlined" size="small" onClick={() => navigate('/profile')}
            sx={{ color: 'white', borderColor: 'rgba(255,255,255,0.5)', '&:hover': { borderColor: 'white' } }}>
            My Profile
          </Button>
          <Button variant="outlined" size="small" onClick={handleLogout}
            sx={{ color: 'white', borderColor: 'rgba(255,255,255,0.5)', '&:hover': { borderColor: 'white' } }}>
            Logout
          </Button>
        </Box>

        {/* Summary cards */}
        {!loading && !error && (
          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 2, mb: 3 }}>
            <Paper elevation={4} sx={{ borderRadius: 2, p: 2.5 }}>
              <Typography variant="caption" color="text.secondary" fontWeight={600}>TOTAL GASTO</Typography>
              <Typography variant="h5" fontWeight={800} color="primary">€{totalSpent.toFixed(2)}</Typography>
              <Typography variant="caption" color="text.secondary">{concluded.length} pagamento{concluded.length !== 1 ? 's' : ''} concluído{concluded.length !== 1 ? 's' : ''}</Typography>
            </Paper>
            <Paper elevation={4} sx={{ borderRadius: 2, p: 2.5 }}>
              <Typography variant="caption" color="text.secondary" fontWeight={600}>TOTAL PAGAMENTOS</Typography>
              <Typography variant="h5" fontWeight={800}>{payments.length}</Typography>
              <Typography variant="caption" color="text.secondary">desde o início</Typography>
            </Paper>
            <Paper elevation={4} sx={{ borderRadius: 2, p: 2.5 }}>
              <Typography variant="caption" color="text.secondary" fontWeight={600}>PENDENTES</Typography>
              <Typography variant="h5" fontWeight={800} color={pending.length > 0 ? 'warning.main' : 'text.primary'}>
                {pending.length}
              </Typography>
              <Typography variant="caption" color="text.secondary">a aguardar confirmação</Typography>
            </Paper>
          </Box>
        )}

        {/* Table card */}
        <Paper elevation={8} sx={{ borderRadius: 3 }}>
          {loading && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
              <CircularProgress />
            </Box>
          )}

          {error && (
            <Typography color="error" sx={{ py: 6, textAlign: 'center' }}>{error}</Typography>
          )}

          {!loading && !error && (
            <>
              <Tabs
                value={tab}
                onChange={(_, v) => setTab(v)}
                sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}
              >
                {TABS.map(t => (
                  <Tab
                    key={t}
                    value={t}
                    label={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                        {TAB_LABEL[t]}
                        <Chip
                          label={t === 'all' ? payments.length : payments.filter(p => p.status === t).length}
                          size="small"
                          sx={{ height: 18, fontSize: '0.65rem' }}
                        />
                      </Box>
                    }
                  />
                ))}
              </Tabs>

              {filtered.length === 0 ? (
                <Typography color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>
                  Nenhum pagamento {tab !== 'all' ? `com estado "${STATUS_LABEL[tab]}"` : ''}.
                </Typography>
              ) : (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell><strong>ID</strong></TableCell>
                      <TableCell><strong>Wallet destino</strong></TableCell>
                      <TableCell align="right"><strong>Valor (€)</strong></TableCell>
                      <TableCell align="center"><strong>Estado</strong></TableCell>
                      <TableCell><strong>Data</strong></TableCell>
                      <TableCell />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filtered.map((p) => (
                      <TableRow key={p.id} hover>
                        <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                          {p.id.slice(0, 8)}…
                        </TableCell>
                        <TableCell>{p.wallet_id ?? '—'}</TableCell>
                        <TableCell align="right">{p.amount.toFixed(2)}</TableCell>
                        <TableCell align="center">
                          <Chip
                            label={STATUS_LABEL[p.status] ?? p.status}
                            color={STATUS_COLOR[p.status] ?? 'default'}
                            size="small"
                          />
                        </TableCell>
                        <TableCell sx={{ fontSize: '0.8rem', color: 'text.secondary' }}>
                          {formatDate(p.created_at)}
                        </TableCell>
                        <TableCell align="right">
                          <Box sx={{ display: 'flex', gap: 0.75, justifyContent: 'flex-end' }}>
                            {p.status === 'pending' && (
                              <Button
                                size="small"
                                color="error"
                                variant="outlined"
                                disabled={cancelling === p.id}
                                onClick={() => handleCancel(p.id)}
                                sx={{ fontSize: '0.7rem', py: 0.25 }}
                              >
                                {cancelling === p.id ? <CircularProgress size={12} /> : 'Cancelar'}
                              </Button>
                            )}
                            {p.status === 'concluded' && (
                              <Button
                                size="small"
                                color="primary"
                                variant="outlined"
                                disabled={downloading === p.id}
                                onClick={() => handleDownloadReceipt(p.id)}
                                sx={{ fontSize: '0.7rem', py: 0.25 }}
                              >
                                {downloading === p.id ? <CircularProgress size={12} /> : 'Recibo'}
                              </Button>
                            )}
                          </Box>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </>
          )}
        </Paper>
      </Container>
    </Box>
  )
}
