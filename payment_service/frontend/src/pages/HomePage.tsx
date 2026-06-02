import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Button,
  Chip,
  Container,
  Paper,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong'
import {
  getProfile,
  getUserIdFromToken,
  getUserPayments,
  PaymentResponse,
  UserProfile,
} from '../api'
import StatusChip from '../components/StatusChip'

function formatDate(iso: string | null) {
  if (!iso) return '—'
  const d = new Date(iso)
  return (
    d.toLocaleDateString('pt-PT', { day: '2-digit', month: '2-digit', year: 'numeric' }) +
    ' ' +
    d.toLocaleTimeString('pt-PT', { hour: '2-digit', minute: '2-digit' })
  )
}

export default function HomePage() {
  const navigate = useNavigate()
  const [payments, setPayments] = useState<PaymentResponse[]>([])
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const userId = getUserIdFromToken()
    if (!userId) {
      navigate('/', { replace: true })
      return
    }
    Promise.all([getUserPayments(userId), getProfile()])
      .then(([pmts, prof]) => {
        setPayments(pmts.sort((a, b) => (b.created_at ?? '').localeCompare(a.created_at ?? '')))
        setProfile(prof)
      })
      .catch(() => setError('Não foi possível carregar o dashboard.'))
      .finally(() => setLoading(false))
  }, [navigate])

  const concluded = payments.filter(p => p.status === 'concluded')
  const pending = payments.filter(p => p.status === 'pending')
  const totalSpent = concluded.reduce((sum, p) => sum + p.amount, 0)
  const recent5 = payments.slice(0, 5)

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
        <Typography variant="h5" fontWeight={700} color="white" sx={{ mb: 4 }}>
          Dashboard
        </Typography>

        {loading && (
          <>
            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 2, mb: 4 }}>
              {[0, 1, 2].map(i => (
                <Skeleton key={i} variant="rectangular" height={80} sx={{ borderRadius: 2 }} />
              ))}
            </Box>
            <Skeleton variant="rectangular" height={240} sx={{ borderRadius: 3, mb: 3 }} />
            <Skeleton variant="rectangular" height={100} sx={{ borderRadius: 3, mb: 4 }} />
          </>
        )}

        {error && (
          <Paper elevation={4} sx={{ borderRadius: 2, p: 3 }}>
            <Typography color="error" textAlign="center">{error}</Typography>
          </Paper>
        )}

        {!loading && !error && (
          <>
            {/* Summary cards */}
            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 2, mb: 4 }}>
              <Paper elevation={4} sx={{ borderRadius: 2, p: 2.5 }}>
                <Typography variant="caption" color="text.secondary" fontWeight={600}>
                  TOTAL GASTO
                </Typography>
                <Typography variant="h5" fontWeight={800} color="primary">
                  €{totalSpent.toFixed(2)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {concluded.length} pagamento{concluded.length !== 1 ? 's' : ''} concluído{concluded.length !== 1 ? 's' : ''}
                </Typography>
              </Paper>

              <Paper elevation={4} sx={{ borderRadius: 2, p: 2.5 }}>
                <Typography variant="caption" color="text.secondary" fontWeight={600}>
                  TOTAL PAGAMENTOS
                </Typography>
                <Typography variant="h5" fontWeight={800}>
                  {payments.length}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  desde o início
                </Typography>
              </Paper>

              <Paper elevation={4} sx={{ borderRadius: 2, p: 2.5 }}>
                <Typography variant="caption" color="text.secondary" fontWeight={600}>
                  PENDENTES
                </Typography>
                <Typography
                  variant="h5"
                  fontWeight={800}
                  color={pending.length > 0 ? 'warning.main' : 'text.primary'}
                >
                  {pending.length}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  a aguardar confirmação
                </Typography>
              </Paper>
            </Box>

            {/* Recent payments */}
            <Paper elevation={8} sx={{ borderRadius: 3, mb: 3 }}>
              <Box sx={{ px: 3, pt: 2.5, pb: 1, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Typography variant="subtitle1" fontWeight={700}>
                  Pagamentos Recentes
                </Typography>
                <Button size="small" onClick={() => navigate('/payments')} sx={{ fontSize: '0.75rem' }}>
                  Ver todos
                </Button>
              </Box>

              {recent5.length === 0 ? (
                <Box sx={{ py: 5, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5 }}>
                  <ReceiptLongIcon sx={{ fontSize: 40, color: 'text.disabled' }} />
                  <Typography color="text.secondary" variant="body2">Ainda não há pagamentos.</Typography>
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
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {recent5.map(p => (
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
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </Paper>

            {/* Saved cards */}
            <Paper elevation={8} sx={{ borderRadius: 3, mb: 4, p: 2.5 }}>
              <Typography variant="subtitle1" fontWeight={700} mb={1.5}>
                Cartões Guardados
              </Typography>

              {!profile || profile.cards.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  Nenhum cartão guardado.{' '}
                  <span
                    style={{ cursor: 'pointer', textDecoration: 'underline', color: '#667eea' }}
                    onClick={() => navigate('/profile')}
                  >
                    Adicionar cartão
                  </span>
                </Typography>
              ) : (
                <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                  {profile.cards.map(card => (
                    <Paper
                      key={card.id}
                      variant="outlined"
                      sx={{ borderRadius: 2, p: 2, minWidth: 180, display: 'flex', flexDirection: 'column', gap: 0.5 }}
                    >
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <Typography variant="body2" fontWeight={700} textTransform="capitalize">
                          {card.brand}
                        </Typography>
                        {card.is_default && (
                          <Chip label="Default" size="small" color="primary" sx={{ height: 18, fontSize: '0.65rem' }} />
                        )}
                      </Box>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', letterSpacing: 2 }}>
                        •••• {card.last4}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {String(card.exp_month).padStart(2, '0')}/{card.exp_year}
                      </Typography>
                    </Paper>
                  ))}
                </Box>
              )}
            </Paper>

            {/* Quick actions */}
            <Box sx={{ display: 'flex', gap: 2, justifyContent: 'center', flexWrap: 'wrap' }}>
              <Button
                variant="contained"
                size="large"
                onClick={() => navigate('/pay')}
                sx={{
                  background: 'white',
                  color: '#764ba2',
                  fontWeight: 700,
                  px: 4,
                  '&:hover': { background: 'rgba(255,255,255,0.9)' },
                }}
              >
                Novo Pagamento
              </Button>
              <Button
                variant="outlined"
                size="large"
                onClick={() => navigate('/payments')}
                sx={{
                  color: 'white',
                  borderColor: 'rgba(255,255,255,0.6)',
                  fontWeight: 600,
                  px: 4,
                  '&:hover': { borderColor: 'white', background: 'rgba(255,255,255,0.1)' },
                }}
              >
                Ver Todos
              </Button>
              <Button
                variant="outlined"
                size="large"
                onClick={() => navigate('/profile')}
                sx={{
                  color: 'white',
                  borderColor: 'rgba(255,255,255,0.6)',
                  fontWeight: 600,
                  px: 4,
                  '&:hover': { borderColor: 'white', background: 'rgba(255,255,255,0.1)' },
                }}
              >
                O meu Perfil
              </Button>
            </Box>
          </>
        )}
      </Container>
    </Box>
  )
}
