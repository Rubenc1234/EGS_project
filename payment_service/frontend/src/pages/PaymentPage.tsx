import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Divider,
  Paper,
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
import { createPayment, concludePayment, getToken } from '../api'

const STRIPE_PK = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY as string
const stripePromise = loadStripe(STRIPE_PK)

function CheckoutForm({
  walletId,
  amount,
  redirectUrl,
}: {
  walletId: string
  amount: number
  redirectUrl: string
}) {
  const stripe = useStripe()
  const elements = useElements()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!stripe || !elements) return
    setLoading(true)
    setError(null)

    try {
      const payment = await createPayment({
        user_id: 'me',
        amount,
        wallet_id: walletId,
        redirect_url: redirectUrl,
      })

      if (!payment.stripe_client_secret) {
        throw new Error('No client secret returned from server.')
      }

      const card = elements.getElement(CardElement)
      if (!card) throw new Error('Card element not found.')

      const result = await stripe.confirmCardPayment(payment.stripe_client_secret, {
        payment_method: { card },
      })

      if (result.error) {
        setError(result.error.message ?? 'Payment failed.')
        setLoading(false)
        return
      }

      await concludePayment(payment.id)
      navigate(`/success?redirect_url=${encodeURIComponent(redirectUrl)}&amount=${amount}`)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'An unexpected error occurred.'
      setError(msg)
      setLoading(false)
    }
  }

  return (
    <Box component="form" onSubmit={handleSubmit} sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
      {/* Card input */}
      <Box>
        <Typography variant="caption" color="text.secondary" fontWeight={600} sx={{ mb: 0.5, display: 'block' }}>
          CARD DETAILS
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
      </Box>

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
  )
}

function TestParamsForm({ onSubmit }: { onSubmit: (w: string, a: number, r: string) => void }) {
  const [walletId, setWalletId] = useState('test-wallet-123')
  const [amount, setAmount] = useState('10')
  const [redirectUrl, setRedirectUrl] = useState('http://localhost:5001/')

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

  const walletId = searchParams.get('wallet_id') ?? testParams?.walletId ?? ''
  const amount = parseFloat(searchParams.get('amount') ?? String(testParams?.amount ?? '0'))
  const redirectUrl = searchParams.get('redirect_url') ?? testParams?.redirectUrl ?? ''

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
              <CheckoutForm walletId={walletId} amount={amount} redirectUrl={redirectUrl} />
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
