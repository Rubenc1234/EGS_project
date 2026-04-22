import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Button,
  CircularProgress,
  Container,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { getStats, StatsResponse } from '../api'

function MetricCard({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
  return (
    <Paper elevation={4} sx={{ borderRadius: 2, p: 2.5 }}>
      <Typography variant="caption" color="text.secondary" fontWeight={600} display="block">
        {label}
      </Typography>
      <Typography variant="h5" fontWeight={800} color="primary">
        {value}
      </Typography>
      {sub && (
        <Typography variant="caption" color="text.secondary">
          {sub}
        </Typography>
      )}
    </Paper>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Box sx={{ mb: 4 }}>
      <Typography variant="h6" fontWeight={700} color="white" sx={{ mb: 1.5 }}>
        {title}
      </Typography>
      {children}
    </Box>
  )
}

export default function StatsPage() {
  const navigate = useNavigate()
  const [stats, setStats] = useState<StatsResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getStats()
      .then(setStats)
      .catch((err) => {
        if (err.response?.status === 403) {
          setError('Acesso negado. É necessário o role "operator" para ver estas estatísticas.')
        } else {
          setError('Não foi possível carregar as estatísticas.')
        }
      })
      .finally(() => setLoading(false))
  }, [])

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
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
          <Typography variant="h5" fontWeight={700} color="white">
            Estatísticas do Serviço
          </Typography>
          <Button
            variant="outlined"
            size="small"
            onClick={() => navigate('/payments')}
            sx={{ color: 'white', borderColor: 'rgba(255,255,255,0.5)', '&:hover': { borderColor: 'white' } }}
          >
            ← Voltar
          </Button>
        </Box>

        {loading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 10 }}>
            <CircularProgress sx={{ color: 'white' }} />
          </Box>
        )}

        {error && (
          <Paper elevation={4} sx={{ borderRadius: 2, p: 4, textAlign: 'center' }}>
            <Typography color="error">{error}</Typography>
          </Paper>
        )}

        {!loading && !error && stats && (
          <>
            <Section title="Visão Geral">
              <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 2 }}>
                <MetricCard label="TOTAL UTILIZADORES" value={stats.overview.total_users} />
                <MetricCard label="TOTAL PAGAMENTOS" value={stats.overview.total_payments} />
                <MetricCard
                  label="RECEITA TOTAL"
                  value={`€${stats.overview.total_revenue_eur.toFixed(2)}`}
                />
                <MetricCard
                  label="VALOR MÉDIO"
                  value={`€${stats.overview.avg_transaction_amount.toFixed(2)}`}
                />
                <MetricCard
                  label="VALOR MÍNIMO"
                  value={`€${stats.overview.min_transaction_amount.toFixed(2)}`}
                />
                <MetricCard
                  label="VALOR MÁXIMO"
                  value={`€${stats.overview.max_transaction_amount.toFixed(2)}`}
                />
              </Box>
            </Section>

            <Section title="Por Estado">
              <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 2 }}>
                <MetricCard
                  label="PENDENTES"
                  value={stats.payments_by_status.pending}
                />
                <MetricCard
                  label="CONCLUÍDOS"
                  value={stats.payments_by_status.concluded}
                />
                <MetricCard
                  label="CANCELADOS"
                  value={stats.payments_by_status.cancelled}
                />
                <MetricCard
                  label="TAXA DE SUCESSO"
                  value={`${stats.payments_by_status.success_rate_pct.toFixed(1)}%`}
                />
              </Box>
            </Section>

            <Section title="Tendências — últimos 30 dias">
              <Paper elevation={4} sx={{ borderRadius: 2, overflow: 'hidden', maxHeight: 340, overflowY: 'auto' }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell><strong>Data</strong></TableCell>
                      <TableCell align="right"><strong>Nº Pagamentos</strong></TableCell>
                      <TableCell align="right"><strong>Receita (€)</strong></TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {stats.daily_trends_last_30_days.map((row) => (
                      <TableRow key={row.date} hover>
                        <TableCell>{row.date}</TableCell>
                        <TableCell align="right">{row.count}</TableCell>
                        <TableCell align="right">{row.revenue.toFixed(2)}</TableCell>
                      </TableRow>
                    ))}
                    {stats.daily_trends_last_30_days.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={3} align="center" sx={{ color: 'text.secondary', py: 3 }}>
                          Sem dados
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </Paper>
            </Section>

            <Section title="Cartões Guardados">
              <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 2 }}>
                <MetricCard label="CARTÕES TOTAIS" value={stats.cards.total_saved_cards} />
                <MetricCard
                  label="UTILIZADORES COM CARTÃO"
                  value={stats.cards.users_with_saved_cards}
                />
                <MetricCard
                  label="MÉDIA CARTÕES/UTILIZADOR"
                  value={stats.cards.avg_cards_per_user.toFixed(2)}
                  sub={`Mais popular: ${stats.cards.most_popular_brand || '—'}`}
                />
              </Box>
            </Section>

            <Section title="Perfis & OTPs">
              <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 2 }}>
                <MetricCard
                  label="PERFIS CRIADOS"
                  value={stats.user_profiles.total_profiles}
                  sub={`${stats.user_profiles.users_with_phone} com telefone`}
                />
                <MetricCard label="OTPs ENVIADOS" value={stats.otps.total_otps_sent} />
                <MetricCard
                  label="TAXA VERIFICAÇÃO OTP"
                  value={`${stats.otps.otp_success_rate_pct.toFixed(1)}%`}
                  sub={`${stats.otps.total_otps_verified} verificados`}
                />
              </Box>
            </Section>

            <Section title="Actividade por Dia da Semana">
              <Paper elevation={4} sx={{ borderRadius: 2, overflow: 'hidden' }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell><strong>Dia</strong></TableCell>
                      <TableCell align="right"><strong>Pagamentos</strong></TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {Object.entries(stats.activity_patterns.payments_by_weekday).map(([day, count]) => (
                      <TableRow key={day} hover>
                        <TableCell>{day}</TableCell>
                        <TableCell align="right">{count}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Paper>
            </Section>
          </>
        )}
      </Container>
    </Box>
  )
}
