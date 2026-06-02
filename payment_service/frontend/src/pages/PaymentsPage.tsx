import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Paper,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
  Tab,
  Tabs,
} from '@mui/material'
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong'
import { cancelPayment, downloadReceipt, getUserIdFromToken, getUserPayments, PaymentResponse } from '../api'
import StatusChip, { getStatusLabel } from '../components/StatusChip'

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

  const concluded = payments.filter(p => p.status === 'concluded')
  const pending = payments.filter(p => p.status === 'pending')
  const totalSpent = concluded.reduce((sum, p) => sum + p.amount, 0)

  const filtered = tab === 'all' ? payments : payments.filter(p => p.status === tab)

  return (
    <Box
      sx={{
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #0891b2 0%, #06b6d4 100%)',
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'center',
        pt: 10,
        pb: 6,
      }}
    >
      <Container maxWidth="lg">
        <Typography variant="h5" fontWeight={700} color="white" sx={{ mb: 3 }}>
          Os meus pagamentos
        </Typography>

        {/* Summary cards */}
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 2, mb: 3 }}>
          {loading
            ? [0, 1, 2].map(i => (
                <Skeleton key={i} variant="rectangular" height={80} sx={{ borderRadius: 2 }} />
              ))
            : !error && (
                <>
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
                </>
              )
          }
        </Box>

        {/* Table card */}
        <Paper elevation={8} sx={{ borderRadius: 3 }}>
          {loading && (
            <Box sx={{ p: 2 }}>
              {[...Array(5)].map((_, i) => (
                <Skeleton key={i} variant="rectangular" height={40} sx={{ mb: 1, borderRadius: 1 }} />
              ))}
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
                <Box sx={{ py: 8, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
                  <ReceiptLongIcon sx={{ fontSize: 56, color: 'text.disabled' }} />
                  <Typography variant="subtitle1" color="text.secondary" fontWeight={600}>
                    {tab !== 'all'
                      ? `Nenhum pagamento com estado "${getStatusLabel(tab)}"`
                      : 'Ainda não há pagamentos'}
                  </Typography>
                  <Typography variant="body2" color="text.disabled" textAlign="center">
                    {tab !== 'all'
                      ? 'Tente selecionar um filtro diferente.'
                      : 'Os seus pagamentos aparecerão aqui assim que fizer o primeiro.'}
                  </Typography>
                  {tab === 'all' && (
                    <Button variant="contained" size="small" onClick={() => navigate('/pay')} sx={{ mt: 1 }}>
                      Fazer primeiro pagamento
                    </Button>
                  )}
                </Box>
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
                      <TableRow
                        key={p.id}
                        hover
                        onClick={() => navigate(`/payments/${p.id}`)}
                        sx={{ cursor: 'pointer' }}
                      >
                        <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                          {p.id.slice(0, 8)}…
                        </TableCell>
                        <TableCell>{p.wallet_id ?? '—'}</TableCell>
                        <TableCell align="right">{p.amount.toFixed(2)}</TableCell>
                        <TableCell align="center">
                          <StatusChip status={p.status} />
                        </TableCell>
                        <TableCell sx={{ fontSize: '0.8rem', color: 'text.secondary' }}>
                          {formatDate(p.created_at)}
                        </TableCell>
                        <TableCell align="right" onClick={(e) => e.stopPropagation()}>
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
