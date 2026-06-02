import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Box,
  Button,
  CircularProgress,
  Container,
  Divider,
  Paper,
  Step,
  StepLabel,
  Stepper,
  Typography,
} from '@mui/material'
import { downloadReceipt, getPayment, getUserIdFromToken, PaymentResponse } from '../api'
import StatusChip from '../components/StatusChip'

function formatDate(iso: string | null) {
  if (!iso) return '—'
  const d = new Date(iso)
  return (
    d.toLocaleDateString('pt-PT', { day: '2-digit', month: '2-digit', year: 'numeric' }) +
    ' ' +
    d.toLocaleTimeString('pt-PT', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  )
}

function Field({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" fontWeight={600} display="block">
        {label}
      </Typography>
      <Typography variant="body2" fontFamily={mono ? 'monospace' : undefined} sx={{ wordBreak: 'break-all' }}>
        {value}
      </Typography>
    </Box>
  )
}

function PaymentTimeline({ status }: { status: string }) {
  const isCancelled = status === 'cancelled'
  const isConcluded = status === 'concluded'

  const step3Label = isCancelled ? 'Cancelado' : 'Concluído'
  const activeStep = isCancelled || isConcluded ? 3 : 1

  return (
    <Stepper activeStep={activeStep} alternativeLabel>
      <Step completed>
        <StepLabel>Criado</StepLabel>
      </Step>
      <Step completed={isConcluded || isCancelled}>
        <StepLabel>Pendente</StepLabel>
      </Step>
      <Step completed={isConcluded || isCancelled}>
        <StepLabel
          StepIconProps={
            isCancelled
              ? { style: { color: '#d32f2f' } }
              : undefined
          }
        >
          <Typography
            variant="caption"
            color={isCancelled ? 'error' : isConcluded ? 'success.main' : 'text.disabled'}
            fontWeight={isConcluded || isCancelled ? 600 : 400}
          >
            {step3Label}
          </Typography>
        </StepLabel>
      </Step>
    </Stepper>
  )
}

export default function PaymentDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [payment, setPayment] = useState<PaymentResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [downloading, setDownloading] = useState(false)

  useEffect(() => {
    if (!getUserIdFromToken()) {
      navigate('/', { replace: true })
      return
    }
    if (!id) {
      navigate('/payments', { replace: true })
      return
    }
    getPayment(id)
      .then(setPayment)
      .catch(() => setError('Não foi possível carregar o pagamento.'))
      .finally(() => setLoading(false))
  }, [id, navigate])

  async function handleDownload() {
    if (!id) return
    setDownloading(true)
    try {
      await downloadReceipt(id)
    } finally {
      setDownloading(false)
    }
  }

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
      <Container maxWidth="md">
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 4 }}>
          <Button
            size="small"
            onClick={() => navigate('/payments')}
            sx={{ color: 'rgba(255,255,255,0.7)', minWidth: 'auto', px: 0.5, '&:hover': { color: 'white' } }}
          >
            ← Pagamentos
          </Button>
          <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.4)' }}>/</Typography>
          <Typography variant="h5" fontWeight={700} color="white">Detalhe</Typography>
        </Box>

        {loading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
            <CircularProgress sx={{ color: 'white' }} />
          </Box>
        )}

        {error && (
          <Paper elevation={4} sx={{ borderRadius: 2, p: 3 }}>
            <Typography color="error" textAlign="center">{error}</Typography>
          </Paper>
        )}

        {!loading && !error && payment && (
          <>
            {/* Info card */}
            <Paper elevation={8} sx={{ borderRadius: 3, p: 3, mb: 3 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                <Typography variant="h6" fontWeight={700}>
                  Informação do Pagamento
                </Typography>
                <StatusChip status={payment.status} size="medium" />
              </Box>

              <Divider sx={{ mb: 2.5 }} />

              <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2.5 }}>
                <Field label="ID" value={payment.id} mono />
                <Field label="Valor" value={`€${payment.amount.toFixed(2)}`} />
                <Field label="Wallet destino" value={payment.wallet_id ?? '—'} mono />
                <Field label="Data de criação" value={formatDate(payment.created_at)} />
                {payment.stripe_payment_intent_id && (
                  <Field label="Stripe Payment Intent" value={payment.stripe_payment_intent_id} mono />
                )}
                {payment.redirect_url && (
                  <Field label="URL de redirecionamento" value={payment.redirect_url} />
                )}
              </Box>

              {payment.status === 'concluded' && (
                <Box sx={{ mt: 3, display: 'flex', justifyContent: 'flex-end' }}>
                  <Button
                    variant="contained"
                    color="success"
                    disabled={downloading}
                    onClick={handleDownload}
                    startIcon={downloading ? <CircularProgress size={16} color="inherit" /> : undefined}
                  >
                    {downloading ? 'A descarregar…' : 'Download Recibo PDF'}
                  </Button>
                </Box>
              )}
            </Paper>

            {/* Timeline card */}
            <Paper elevation={8} sx={{ borderRadius: 3, p: 3 }}>
              <Typography variant="subtitle1" fontWeight={700} mb={3}>
                Histórico de Estado
              </Typography>
              <PaymentTimeline status={payment.status} />
            </Paper>
          </>
        )}
      </Container>
    </Box>
  )
}
