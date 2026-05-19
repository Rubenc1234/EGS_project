import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  Paper,
  Radio,
  RadioGroup,
  TextField,
  Typography,
  Fade,
} from '@mui/material'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import CreditCardOutlinedIcon from '@mui/icons-material/CreditCardOutlined'
import VerifiedUserOutlinedIcon from '@mui/icons-material/VerifiedUserOutlined'
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined'
import { loadStripe } from '@stripe/stripe-js'
import {
  Elements,
  CardElement,
  useStripe,
  useElements,
} from '@stripe/react-stripe-js'
import {
  createPayment,
  getToken,
  getUserIdFromToken,
  getProfile,
  addCard,
  sendOtp,
  verifyOtp,
  type SavedCard,
  type UserProfile,
} from '../api'

const STRIPE_PK = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY as string
const stripePromise = loadStripe(STRIPE_PK)
const MIN_PAYMENT_AMOUNT_EUR = 0.5
const DEFAULT_REDIRECT_URL = import.meta.env.VITE_PAYMENT_DEFAULT_REDIRECT_URL || 'http://app.pt/dashboard'

function CheckoutForm({
  walletId,
  amount,
  redirectUrl,
  profile,
}: {
  walletId: string
  amount: number
  redirectUrl: string
  profile: UserProfile | null
}) {
  const stripe = useStripe()
  const elements = useElements()
  const navigate = useNavigate()

  const savedCards: SavedCard[] = profile?.cards ?? []
  const hasPhone = !!profile?.phone_number

  const [selectedCard, setSelectedCard] = useState<string>(savedCards.length > 0 ? savedCards[0].id : 'new')
  const [saveNewCard, setSaveNewCard] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [otpDialogOpen, setOtpDialogOpen] = useState(false)
  const [otpCode, setOtpCode] = useState('')
  const [otpError, setOtpError] = useState<string | null>(null)
  const [otpLoading, setOtpLoading] = useState(false)
  const [otpResending, setOtpResending] = useState(false)
  const [currentPaymentId, setCurrentPaymentId] = useState<string | null>(null)
  const [successParams, setSuccessParams] = useState<{ redirectUrl: string; amount: number } | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!stripe || !elements) return
    if (amount < MIN_PAYMENT_AMOUNT_EUR) {
      setError('O montante mínimo de pagamento é €0.50.')
      return
    }
    setLoading(true)
    setError(null)

    try {
      const savedCard = savedCards.find(c => c.id === selectedCard)
      const payment = await createPayment({
        user_id: getUserIdFromToken() ?? 'anonymous',
        amount,
        wallet_id: walletId,
        redirect_url: redirectUrl,
        payment_method_id: savedCard?.stripe_payment_method_id,
      })

      if (!payment.stripe_client_secret) {
        throw new Error('Erro na comunicação com o servidor de pagamentos.')
      }

      let stripeResult
      if (savedCard) {
        stripeResult = await stripe.confirmCardPayment(payment.stripe_client_secret, {
          payment_method: savedCard.stripe_payment_method_id,
        })
      } else {
        const card = elements.getElement(CardElement)
        if (!card) throw new Error('Elemento do cartão não encontrado.')
        stripeResult = await stripe.confirmCardPayment(payment.stripe_client_secret, {
          payment_method: { card },
        })
      }

      if (stripeResult.error) {
        setError(stripeResult.error.message ?? 'O pagamento falhou.')
        setLoading(false)
        return
      }

      if (!savedCard && saveNewCard && stripeResult.paymentIntent?.payment_method) {
        try {
          await addCard(stripeResult.paymentIntent.payment_method as string)
        } catch {
          // best-effort
        }
      }

      setCurrentPaymentId(payment.id)
      setSuccessParams({ redirectUrl, amount })

      try {
        await sendOtp(payment.id)
        setOtpDialogOpen(true)
        setLoading(false)
      } catch (err: unknown) {
        const status = (err as { response?: { status?: number; data?: { error?: string } } })?.response?.status
        const errorCode = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
        if (status === 422 && errorCode === 'no_phone_number') {
          navigate(`/success?redirect_url=${encodeURIComponent(redirectUrl)}&amount=${amount}`)
          return
        }
        throw err
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Ocorreu um erro inesperado.'
      setError(msg)
      setLoading(false)
    }
  }

  const handleVerifyOtp = async () => {
    if (!currentPaymentId || !successParams) return
    setOtpLoading(true)
    setOtpError(null)
    try {
      const result = await verifyOtp(currentPaymentId, otpCode)
      if (result.verified) {
        navigate(`/success?redirect_url=${encodeURIComponent(successParams.redirectUrl)}&amount=${successParams.amount}`)
      } else {
        setOtpError('Código inválido ou expirado. Tente novamente.')
        setOtpLoading(false)
      }
    } catch {
      setOtpError('Falha na verificação. Por favor, tente novamente.')
      setOtpLoading(false)
    }
  }

  const handleResendOtp = async () => {
    if (!currentPaymentId) return
    setOtpResending(true)
    setOtpError(null)
    try {
      await sendOtp(currentPaymentId)
      setOtpCode('')
    } catch {
      setOtpError('Não foi possível reenviar o código.')
    } finally {
      setOtpResending(false)
    }
  }

  return (
    <>
      <Box component="form" onSubmit={handleSubmit} sx={{ display: 'flex', flexDirection: 'column', gap: 3.5 }}>
        {!hasPhone && (
          <Alert 
            severity="info" 
            variant="outlined"
            sx={{ 
              borderRadius: '12px', 
              borderColor: '#e0e0e0',
              bgcolor: '#fcfcfd',
              color: '#555',
              fontSize: '0.85rem',
              '& .MuiAlert-icon': { color: '#757575' }
            }}
          >
            Sem contacto telefónico associado. A verificação OTP será ignorada.{' '}
            <a href="/profile" style={{ color: '#0891b2', fontWeight: 600, textDecoration: 'none', borderBottom: '1px solid' }}>
              Adicionar telefone no perfil
            </a>
          </Alert>
        )}

        {/* Saved cards selection */}
        {savedCards.length > 0 && (
          <Box>
            <Typography variant="caption" color="text.secondary" fontWeight={700} sx={{ mb: 1.5, display: 'block', letterSpacing: '0.5px' }}>
              MÉTODO DE PAGAMENTO
            </Typography>
            <FormControl fullWidth>
              <RadioGroup value={selectedCard} onChange={e => setSelectedCard(e.target.value)}>
                {savedCards.map(card => {
                  const isSelected = selectedCard === card.id
                  return (
                    <FormControlLabel
                      key={card.id}
                      value={card.id}
                      control={<Radio size="small" sx={{ display: 'none' }} />}
                      label={
                        <Box sx={{ display: 'flex', alignItems: 'center', width: '100%', gap: 2 }}>
                          <CreditCardOutlinedIcon sx={{ color: isSelected ? 'primary.main' : 'text.secondary', fontSize: 20 }} />
                          <Box sx={{ display: 'flex', flexDirection: 'column' }}>
                            <Typography variant="body2" sx={{ textTransform: 'capitalize', fontWeight: 600, color: isSelected ? 'text.primary' : 'text.secondary' }}>
                              {card.brand} •••• {card.last4}
                            </Typography>
                            <Typography variant="caption" color="text.disabled">
                              Expira em {card.exp_month.toString().padStart(2, '0')}/{card.exp_year}
                            </Typography>
                          </Box>
                          {card.is_default && (
                            <Chip 
                              label="Predefinido" 
                              size="small" 
                              sx={{ ml: 'auto', height: 20, fontSize: '0.65rem', fontWeight: 600, bgcolor: '#cffafe', color: '#0e7490' }} 
                            />
                          )}
                        </Box>
                      }
                      sx={{ 
                        border: '1px solid', 
                        borderColor: isSelected ? 'primary.main' : '#e0e0e0', 
                        bgcolor: isSelected ? '#ecfeff' : '#ffffff',
                        borderRadius: '12px', 
                        mx: 0, 
                        mb: 1.2, 
                        p: 2,
                        cursor: 'pointer',
                        transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
                        '&:hover': { borderColor: isSelected ? 'primary.main' : '#b0b0b0', bgcolor: isSelected ? '#ecfeff' : '#fafafa' }
                      }}
                    />
                  )
                })}
                
                <FormControlLabel
                  value="new"
                  control={<Radio size="small" sx={{ display: 'none' }} />}
                  label={
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                      <CreditCardOutlinedIcon sx={{ color: selectedCard === 'new' ? 'primary.main' : 'text.secondary', fontSize: 20 }} />
                      <Typography variant="body2" fontWeight={600} color={selectedCard === 'new' ? 'text.primary' : 'text.secondary'}>
                        Utilizar um novo cartão de crédito
                      </Typography>
                    </Box>
                  }
                  sx={{ 
                    border: '1px solid', 
                    borderColor: selectedCard === 'new' ? 'primary.main' : '#e0e0e0', 
                    bgcolor: selectedCard === 'new' ? '#ecfeff' : '#ffffff',
                    borderRadius: '12px', 
                    mx: 0, 
                    p: 2,
                    cursor: 'pointer',
                    transition: 'all 0.2s ease',
                    '&:hover': { borderColor: selectedCard === 'new' ? 'primary.main' : '#b0b0b0', bgcolor: selectedCard === 'new' ? '#ecfeff' : '#fafafa' }
                  }}
                />
              </RadioGroup>
            </FormControl>
          </Box>
        )}

        {/* New card input */}
        {(savedCards.length === 0 || selectedCard === 'new') && (
          <Fade in timeout={300}>
            <Box>
              <Typography variant="caption" color="text.secondary" fontWeight={700} sx={{ mb: 1.5, display: 'block', letterSpacing: '0.5px' }}>
                DADOS DO CARTÃO
              </Typography>
              <Box
                sx={{
                  border: '1px solid',
                  borderColor: '#e0e0e0',
                  bgcolor: '#ffffff',
                  borderRadius: '12px',
                  p: 2.2,
                  boxShadow: '0 1px 2px rgba(0,0,0,0.02)',
                  '&:focus-within': { borderColor: 'primary.main', boxShadow: '0 0 0 3px rgba(8, 145, 178, 0.12)' },
                  transition: 'all 0.2s ease',
                }}
              >
                <CardElement
                  options={{
                    style: {
                      base: { 
                        fontSize: '15px', 
                        color: '#212121', 
                        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
                        '::placeholder': { color: '#9e9e9e' } 
                      },
                      invalid: { color: '#d32f2f' },
                    },
                  }}
                />
              </Box>
              
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 1.5, px: 0.5 }}>
                <Typography variant="caption" color="text.disabled">
                  Modo teste: 4242 4242... · Qualquer data/CVC futura.
                </Typography>
                <FormControlLabel
                  control={<Radio size="small" checked={saveNewCard} onClick={() => setSaveNewCard(v => !v)} sx={{ p: 0.5 }} />}
                  label={<Typography variant="caption" color="text.secondary" fontWeight={500}>Guardar cartão</Typography>}
                  sx={{ mr: 0 }}
                />
              </Box>
            </Box>
          </Fade>
        )}

        {error && (
          <Fade in>
            <Box sx={{ bgcolor: '#fff5f5', border: '1px solid #ffdbdb', borderRadius: '12px', p: 2 }}>
              <Typography color="#c62828" variant="body2" fontWeight={500}>{error}</Typography>
            </Box>
          </Fade>
        )}

        <Button
          type="submit"
          variant="contained"
          size="large"
          fullWidth
          disabled={loading || !stripe}
          disableElevation
          sx={{ 
            py: 2, 
            borderRadius: '12px', 
            fontWeight: 600, 
            fontSize: '0.95rem',
            textTransform: 'none',
            background: '#111111', 
            '&:hover': {
              background: '#292929',
              boxShadow: '0 8px 20px rgba(0,0,0,0.15)',
            },
            '&:disabled': {
              background: '#f5f5f5',
              color: '#9e9e9e'
            },
            transition: 'all 0.2s ease'
          }}
        >
          {loading
            ? <CircularProgress size={22} color="inherit" thickness={4} />
            : <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <LockOutlinedIcon sx={{ fontSize: 18 }} />
                Pagar €{amount.toFixed(2)}
              </Box>
          }
        </Button>
      </Box>

      {/* OTP Verification Dialog */}
      <Dialog open={otpDialogOpen} maxWidth="xs" fullWidth PaperProps={{ sx: { borderRadius: '20px', p: 1 } }}>
        <DialogTitle sx={{ fontWeight: 700, pb: 1 }}>Verificação de Segurança</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 3, pt: 1 }}>
          <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.5 }}>
            Introduza o código de 6 dígitos que enviámos para o seu número associado ao WhatsApp.
          </Typography>
          <TextField
            autoFocus
            fullWidth
            placeholder="000000"
            value={otpCode}
            onChange={e => setOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            inputProps={{ inputMode: 'numeric', maxLength: 6, style: { letterSpacing: '12px', textAlign: 'center', fontSize: '1.6rem', fontWeight: 700, paddingLeft: '12px' } }}
            variant="outlined"
            sx={{ '& .MuiOutlinedInput-root': { borderRadius: '12px' } }}
          />
          {otpError && <Alert severity="error" sx={{ borderRadius: '10px' }}>{otpError}</Alert>}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2, gap: 1 }}>
          <Button onClick={handleResendOtp} disabled={otpResending} sx={{ textTransform: 'none', color: 'text.secondary', fontWeight: 600 }}>
            {otpResending ? <CircularProgress size={16} /> : 'Reenviar código'}
          </Button>
          <Button
            variant="contained"
            onClick={handleVerifyOtp}
            disabled={otpLoading || otpCode.length !== 6}
            disableElevation
            sx={{ borderRadius: '10px', textTransform: 'none', fontWeight: 600, px: 3, bgcolor: '#111' }}
          >
            {otpLoading ? <CircularProgress size={18} color="inherit" /> : 'Confirmar'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}

function TestParamsForm({ onSubmit }: { onSubmit: (w: string, a: number, r: string) => void }) {
  const [walletId, setWalletId] = useState('test-wallet-123')
  const [amount, setAmount] = useState('10')
  const [redirectUrl, setRedirectUrl] = useState(DEFAULT_REDIRECT_URL)

  return (
    <Box 
      sx={{ 
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #0891b2 0%, #06b6d4 100%)',
        display: 'flex', 
        alignItems: 'center', 
        justifyContent: 'center', 
        p: 2 
      }}
    >
      <Container maxWidth="xs">
        <Paper elevation={0} sx={{ borderRadius: '16px', border: '1px solid #e0e0e0', p: 4, display: 'flex', flexDirection: 'column', gap: 3, boxShadow: '0 20px 40px rgba(0, 0, 0, 0.04)' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <AccountBalanceWalletOutlinedIcon sx={{ color: 'text.primary' }} />
            <Typography variant="subtitle1" fontWeight={700}>Ambiente de Testes</Typography>
            <Chip label="Dev" size="small" sx={{ ml: 'auto', fontWeight: 600, bgcolor: '#fff3e0', color: '#b78103' }} />
          </Box>
          <Divider />
          <TextField label="Wallet ID" value={walletId} onChange={e => setWalletId(e.target.value)} fullWidth slotProps={{ input: { style: { borderRadius: '10px' } } }} />
          <TextField label="Montante (€)" type="number" value={amount} onChange={e => setAmount(e.target.value)} fullWidth />
          <TextField label="URL de Redirecionamento" value={redirectUrl} onChange={e => setRedirectUrl(e.target.value)} fullWidth />
          <Button
            variant="contained"
            size="large"
            fullWidth
            disabled={!walletId || !amount || !redirectUrl}
            onClick={() => onSubmit(walletId, parseFloat(amount), redirectUrl)}
            disableElevation
            sx={{ py: 1.5, borderRadius: '10px', fontWeight: 600, textTransform: 'none', bgcolor: '#111' }}
          >
            Aceder ao Checkout
          </Button>
        </Paper>
      </Container>
    </Box>
  )
}

export default function PaymentPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [testParams, setTestParams] = useState<{ walletId: string; amount: number; redirectUrl: string } | null>(null)
  const [profile, setProfile] = useState<UserProfile | null>(null)

  const walletId = searchParams.get('wallet_id') ?? testParams?.walletId ?? ''
  const amount = parseFloat(searchParams.get('amount') ?? String(testParams?.amount ?? '0'))
  const redirectUrl = searchParams.get('redirect_url') ?? testParams?.redirectUrl ?? ''

  useEffect(() => {
    if (walletId && amount && redirectUrl) {
      getProfile().then(setProfile).catch(() => {})
    }
  }, [walletId, amount, redirectUrl])

  if (!getToken()) {
    navigate(`/?${searchParams.toString()}`, { replace: true })
    return null
  }

  if (!walletId || !amount || !redirectUrl) {
    return <TestParamsForm onSubmit={(w, a, r) => setTestParams({ walletId: w, amount: a, redirectUrl: r })} />
  }

  return (
    <Box
      sx={{
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #0891b2 0%, #06b6d4 100%)',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        p: 2
      }}
    >
      <Container maxWidth="xs" disableGutters>
        <Paper 
          elevation={0} 
          sx={{ 
            borderRadius: '24px', 
            overflow: 'hidden',
            border: '1px solid rgba(255, 255, 255, 0.4)',
            boxShadow: '0 25px 50px rgba(15, 23, 42, 0.08), 0 0 0 1px rgba(15, 23, 42, 0.02)', 
            bgcolor: '#ffffff',
            backdropFilter: 'blur(10px)' // Efeito ligeiro de vidro nas bordas onde a luz interage
          }}
        >
          {/* Header Minimalista */}
          <Box sx={{ pt: 4, px: 4, pb: 1, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
              <Box sx={{ width: 10, height: 10, bgcolor: '#10b981', borderRadius: '50%', boxShadow: '0 0 8px rgba(16,185,129,0.4)' }} /> 
              <Typography variant="subtitle1" fontWeight={700} color="text.primary">
                Carregamento
              </Typography>
            </Box>
            <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.disabled', bgcolor: '#f4f5f6', px: 1, py: 0.5, borderRadius: '6px' }}>
              ID: {walletId.slice(-8)}
            </Typography>
          </Box>

          <Box sx={{ p: 4, display: 'flex', flexDirection: 'column', gap: 4 }}>
            {/* Secção de Montante Re-desenhada */}
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
              <Typography variant="caption" color="text.disabled" fontWeight={700} sx={{ letterSpacing: '0.8px' }}>
                VALOR A DEPOSITAR
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1 }}>
                <Typography variant="h3" fontWeight={800} color="text.primary" sx={{ letterSpacing: '-1.5px' }}>
                  €{amount.toFixed(2)}
                </Typography>
                <Typography variant="subtitle2" color="text.secondary" fontWeight={600}>
                  EUR
                </Typography>
              </Box>
            </Box>

            <Divider sx={{ borderColor: '#f1f3f5' }} />

            <Elements stripe={stripePromise}>
              <CheckoutForm walletId={walletId} amount={amount} redirectUrl={redirectUrl} profile={profile} />
            </Elements>

            {/* Rodapé de Segurança Premium */}
            <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1, mt: 1 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.disabled' }}>
                <VerifiedUserOutlinedIcon sx={{ fontSize: 14, color: '#10b981' }} />
                <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500 }}>
                  Pagamento Seguro via Stripe
                </Typography>
              </Box>
              <Typography variant="caption" color="text.disabled" sx={{ fontSize: '0.68rem', textAlign: 'center', px: 2 }}>
                Encriptação SSL de 256 bits · Compatível com normas PCI-DSS
              </Typography>
            </Box>
          </Box>
        </Paper>
      </Container>
    </Box>
  )
}