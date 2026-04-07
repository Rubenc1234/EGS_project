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
} from '@mui/material'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
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

  // 'new' means typing a new card; otherwise it's the SavedCard.id
  const [selectedCard, setSelectedCard] = useState<string>(savedCards.length > 0 ? savedCards[0].id : 'new')
  const [saveNewCard, setSaveNewCard] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // OTP dialog state
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
        throw new Error('No client secret returned from server.')
      }

      let stripeResult
      if (savedCard) {
        stripeResult = await stripe.confirmCardPayment(payment.stripe_client_secret, {
          payment_method: savedCard.stripe_payment_method_id,
        })
      } else {
        const card = elements.getElement(CardElement)
        if (!card) throw new Error('Card element not found.')
        stripeResult = await stripe.confirmCardPayment(payment.stripe_client_secret, {
          payment_method: { card },
        })
      }

      if (stripeResult.error) {
        setError(stripeResult.error.message ?? 'Payment failed.')
        setLoading(false)
        return
      }

      // Optionally save the new card (best-effort)
      if (!savedCard && saveNewCard && stripeResult.paymentIntent?.payment_method) {
        try {
          await addCard(stripeResult.paymentIntent.payment_method as string)
        } catch {
          // best-effort — don't block the flow
        }
      }

      // Send OTP
      setCurrentPaymentId(payment.id)
      setSuccessParams({ redirectUrl, amount })

      try {
        await sendOtp(payment.id)
        // OTP sent — open dialog
        setOtpDialogOpen(true)
        setLoading(false)
      } catch (err: unknown) {
        // If no phone number, skip OTP and go to success
        const status = (err as { response?: { status?: number; data?: { error?: string } } })?.response?.status
        const errorCode = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
        if (status === 422 && errorCode === 'no_phone_number') {
          navigate(`/success?redirect_url=${encodeURIComponent(redirectUrl)}&amount=${amount}`)
          return
        }
        throw err
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'An unexpected error occurred.'
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
        setOtpError('Invalid or expired code. Try again.')
        setOtpLoading(false)
      }
    } catch {
      setOtpError('Verification failed. Please try again.')
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
      setOtpError('Failed to resend code.')
    } finally {
      setOtpResending(false)
    }
  }

  return (
    <>
      <Box component="form" onSubmit={handleSubmit} sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
        {!hasPhone && (
          <Alert severity="warning" sx={{ borderRadius: 1.5 }}>
            No phone number set — OTP verification will be skipped.{' '}
            <a href="/profile" style={{ color: 'inherit', fontWeight: 600 }}>Add phone in Profile</a>
          </Alert>
        )}

        {/* Saved cards selection */}
        {savedCards.length > 0 && (
          <Box>
            <Typography variant="caption" color="text.secondary" fontWeight={600} sx={{ mb: 0.5, display: 'block' }}>
              PAYMENT METHOD
            </Typography>
            <FormControl fullWidth>
              <RadioGroup value={selectedCard} onChange={e => setSelectedCard(e.target.value)}>
                {savedCards.map(card => (
                  <FormControlLabel
                    key={card.id}
                    value={card.id}
                    control={<Radio size="small" />}
                    label={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Typography variant="body2" sx={{ textTransform: 'capitalize' }}>{card.brand}</Typography>
                        <Typography variant="body2" color="text.secondary">•••• {card.last4}</Typography>
                        <Typography variant="caption" color="text.disabled">
                          {card.exp_month.toString().padStart(2, '0')}/{card.exp_year}
                        </Typography>
                        {card.is_default && <Chip label="Default" size="small" color="primary" variant="outlined" sx={{ height: 18, fontSize: 10 }} />}
                      </Box>
                    }
                    sx={{ border: '1px solid', borderColor: selectedCard === card.id ? 'primary.main' : 'divider', borderRadius: 1.5, mx: 0, mb: 0.5, pr: 1 }}
                  />
                ))}
                <FormControlLabel
                  value="new"
                  control={<Radio size="small" />}
                  label={<Typography variant="body2">Use a new card</Typography>}
                  sx={{ border: '1px solid', borderColor: selectedCard === 'new' ? 'primary.main' : 'divider', borderRadius: 1.5, mx: 0, pr: 1 }}
                />
              </RadioGroup>
            </FormControl>
          </Box>
        )}

        {/* New card input — shown when no saved cards or "new" selected */}
        {(savedCards.length === 0 || selectedCard === 'new') && (
          <Box>
            <Typography variant="caption" color="text.secondary" fontWeight={600} sx={{ mb: 0.5, display: 'block' }}>
              {savedCards.length === 0 ? 'CARD DETAILS' : 'NEW CARD'}
            </Typography>
            <Box
              sx={{
                border: '1.5px solid',
                borderColor: 'divider',
                borderRadius: 1.5,
                p: 1.8,
                '&:focus-within': { borderColor: 'primary.main' },
                transition: 'border-color 0.2s',
              }}
            >
              <CardElement
                options={{
                  style: {
                    base: { fontSize: '15px', color: '#1a1a1a', '::placeholder': { color: '#aab7c4' } },
                    invalid: { color: '#e53935' },
                  },
                }}
              />
            </Box>
            <Typography variant="caption" color="text.disabled" sx={{ mt: 0.5, display: 'block' }}>
              Test card: 4242 4242 4242 4242 · any future date · any CVC
            </Typography>
            <FormControlLabel
              control={<Radio size="small" checked={saveNewCard} onClick={() => setSaveNewCard(v => !v)} />}
              label={<Typography variant="caption">Save this card for future payments</Typography>}
              sx={{ mt: 0.5, ml: -0.5 }}
            />
          </Box>
        )}

        {error && (
          <Box sx={{ bgcolor: '#fff3f3', border: '1px solid #ffcdd2', borderRadius: 1.5, p: 1.5 }}>
            <Typography color="error" variant="body2">{error}</Typography>
          </Box>
        )}

        <Button
          type="submit"
          variant="contained"
          size="large"
          fullWidth
          disabled={loading || !stripe}
          sx={{ py: 1.5, borderRadius: 2, fontWeight: 700, fontSize: '1rem' }}
        >
          {loading
            ? <CircularProgress size={22} color="inherit" />
            : <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <LockOutlinedIcon fontSize="small" />
                Pay €{amount.toFixed(2)}
              </Box>
          }
        </Button>
      </Box>

      {/* OTP Verification Dialog */}
      <Dialog open={otpDialogOpen} maxWidth="xs" fullWidth>
        <DialogTitle>Verify Payment</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Enter the 6-digit code sent to your WhatsApp number.
          </Typography>
          <TextField
            label="Verification code"
            value={otpCode}
            onChange={e => setOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            inputProps={{ inputMode: 'numeric', maxLength: 6 }}
            autoFocus
            fullWidth
          />
          {otpError && <Alert severity="error">{otpError}</Alert>}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2, gap: 1 }}>
          <Button
            onClick={handleResendOtp}
            disabled={otpResending}
            size="small"
          >
            {otpResending ? <CircularProgress size={16} /> : 'Resend'}
          </Button>
          <Button
            variant="contained"
            onClick={handleVerifyOtp}
            disabled={otpLoading || otpCode.length !== 6}
          >
            {otpLoading ? <CircularProgress size={18} color="inherit" /> : 'Verify'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}

function TestParamsForm({ onSubmit }: { onSubmit: (w: string, a: number, r: string) => void }) {
  const [walletId, setWalletId] = useState('test-wallet-123')
  const [amount, setAmount] = useState('10')
  const [redirectUrl, setRedirectUrl] = useState('http://localhost:5174/payments')

  return (
    <Box
      sx={{
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #1a237e 0%, #283593 50%, #3949ab 100%)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Container maxWidth="xs">
        <Paper elevation={12} sx={{ borderRadius: 3, p: 4, display: 'flex', flexDirection: 'column', gap: 2.5 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <AccountBalanceWalletOutlinedIcon color="primary" />
            <Typography variant="h6" fontWeight={700}>Test Mode</Typography>
            <Chip label="Dev only" size="small" color="warning" sx={{ ml: 'auto' }} />
          </Box>
          <Typography variant="body2" color="text.secondary">
            In production the composer passes these automatically via URL params.
          </Typography>
          <Divider />
          <TextField label="Wallet ID" value={walletId} onChange={e => setWalletId(e.target.value)} size="small" fullWidth />
          <TextField label="Amount (€)" type="number" value={amount} onChange={e => setAmount(e.target.value)} size="small" fullWidth slotProps={{ htmlInput: { min: 0.5, step: 0.01 } }} />
          <TextField label="Redirect URL after payment" value={redirectUrl} onChange={e => setRedirectUrl(e.target.value)} size="small" fullWidth />
          <Button
            variant="contained"
            size="large"
            fullWidth
            disabled={!walletId || !amount || !redirectUrl}
            onClick={() => onSubmit(walletId, parseFloat(amount), redirectUrl)}
            sx={{ py: 1.5, borderRadius: 2, fontWeight: 600 }}
          >
            Continue to Payment
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
      getProfile().then(setProfile).catch(() => {/* non-fatal */})
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
        background: 'linear-gradient(135deg, #1a237e 0%, #283593 50%, #3949ab 100%)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Container maxWidth="xs">
        <Paper elevation={12} sx={{ borderRadius: 3, overflow: 'hidden' }}>
          {/* Header */}
          <Box
            sx={{
              background: 'linear-gradient(135deg, #1565c0, #1976d2)',
              py: 3,
              px: 4,
              display: 'flex',
              alignItems: 'center',
              gap: 2,
            }}
          >
            <AccountBalanceWalletOutlinedIcon sx={{ color: 'white', fontSize: 32 }} />
            <Box>
              <Typography variant="h6" fontWeight={700} color="white">
                Wallet Top-up
              </Typography>
              <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.75)' }}>
                {walletId}
              </Typography>
            </Box>
          </Box>

          <Box sx={{ p: 4, display: 'flex', flexDirection: 'column', gap: 3 }}>
            {/* Amount summary */}
            <Box
              sx={{
                bgcolor: '#f0f4ff',
                border: '1px solid #c5cae9',
                borderRadius: 2,
                p: 2,
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <Box>
                <Typography variant="body2" color="text.secondary">Amount to add</Typography>
                <Typography variant="h4" fontWeight={800} color="primary">
                  €{amount.toFixed(2)}
                </Typography>
              </Box>
              <Chip label="EUR" variant="outlined" color="primary" />
            </Box>

            <Divider />

            <Elements stripe={stripePromise}>
              <CheckoutForm walletId={walletId} amount={amount} redirectUrl={redirectUrl} profile={profile} />
            </Elements>

            <Typography variant="caption" color="text.disabled" textAlign="center">
              Secured by Stripe · 256-bit TLS encryption
            </Typography>
          </Box>
        </Paper>
      </Container>
    </Box>
  )
}
