import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Paper,
  TextField,
  Typography,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import CreditCardIcon from '@mui/icons-material/CreditCard'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import PhoneIcon from '@mui/icons-material/Phone'
import { loadStripe } from '@stripe/stripe-js'
import { Elements, CardElement, useStripe, useElements } from '@stripe/react-stripe-js'
import { getProfile, updateProfile, addCard, deleteCard, getToken, type SavedCard } from '../api'

const STRIPE_PK = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY as string
const stripePromise = loadStripe(STRIPE_PK)

function AddCardForm({ onSaved, onCancel }: { onSaved: () => void; onCancel: () => void }) {
  const stripe = useStripe()
  const elements = useElements()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSave = async () => {
    if (!stripe || !elements) return
    setLoading(true)
    setError(null)
    try {
      const card = elements.getElement(CardElement)
      if (!card) throw new Error('Card element not found.')
      const result = await stripe.createPaymentMethod({ type: 'card', card })
      if (result.error) {
        setError(result.error.message ?? 'Failed to process card.')
        setLoading(false)
        return
      }
      await addCard(result.paymentMethod.id)
      onSaved()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to save card.')
      setLoading(false)
    }
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
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
      <Typography variant="caption" color="text.disabled">
        Test card: 4242 4242 4242 4242 · any future date · any CVC
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
        <Button onClick={onCancel} disabled={loading}>Cancel</Button>
        <Button variant="contained" onClick={handleSave} disabled={loading || !stripe}>
          {loading ? <CircularProgress size={18} color="inherit" /> : 'Save Card'}
        </Button>
      </Box>
    </Box>
  )
}

export default function ProfilePage() {
  const navigate = useNavigate()
  const [phone, setPhone] = useState<string | null>(null)
  const [cards, setCards] = useState<SavedCard[]>([])
  const [loading, setLoading] = useState(true)
  const [phoneEdit, setPhoneEdit] = useState(false)
  const [phoneValue, setPhoneValue] = useState('')
  const [phoneSaving, setPhoneSaving] = useState(false)
  const [phoneError, setPhoneError] = useState<string | null>(null)
  const [addCardOpen, setAddCardOpen] = useState(false)

  useEffect(() => {
    if (!getToken()) { navigate('/'); return }
    getProfile()
      .then(p => { setPhone(p.phone_number); setCards(p.cards) })
      .catch(() => navigate('/'))
      .finally(() => setLoading(false))
  }, [navigate])

  const handlePhoneSave = async () => {
    setPhoneSaving(true)
    setPhoneError(null)
    try {
      const updated = await updateProfile(phoneValue.trim() || null)
      setPhone(updated.phone_number)
      setPhoneEdit(false)
    } catch {
      setPhoneError('Failed to save phone number.')
    } finally {
      setPhoneSaving(false)
    }
  }

  const handleDeleteCard = async (card: SavedCard) => {
    if (!confirm(`Remove •••• ${card.last4}?`)) return
    try {
      await deleteCard(card.id)
      setCards(prev => prev.filter(c => c.id !== card.id))
    } catch {
      alert('Failed to remove card.')
    }
  }

  const handleCardSaved = () => {
    setAddCardOpen(false)
    getProfile().then(p => setCards(p.cards))
  }

  if (loading) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <CircularProgress />
      </Box>
    )
  }

  return (
    <Box sx={{ minHeight: '100vh', background: 'linear-gradient(135deg, #1a237e 0%, #283593 50%, #3949ab 100%)' }}>
      {/* Header */}
      <Box sx={{ background: 'rgba(0,0,0,0.2)', py: 2, px: 3, display: 'flex', alignItems: 'center', gap: 2 }}>
        <IconButton onClick={() => navigate('/payments')} sx={{ color: 'white' }}>
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h6" fontWeight={700} color="white">My Profile</Typography>
      </Box>

      <Container maxWidth="sm" sx={{ py: 4, display: 'flex', flexDirection: 'column', gap: 3 }}>
        {/* Phone Section */}
        <Paper elevation={6} sx={{ borderRadius: 3, p: 3 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
            <PhoneIcon color="primary" />
            <Typography variant="subtitle1" fontWeight={700}>Phone Number</Typography>
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
            Used for WhatsApp OTP verification after each payment.
          </Typography>

          {phoneEdit ? (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
              <TextField
                label="Phone number (E.164 format, e.g. +351912345678)"
                value={phoneValue}
                onChange={e => setPhoneValue(e.target.value)}
                size="small"
                fullWidth
                autoFocus
              />
              {phoneError && <Alert severity="error">{phoneError}</Alert>}
              <Box sx={{ display: 'flex', gap: 1 }}>
                <Button onClick={() => setPhoneEdit(false)} disabled={phoneSaving}>Cancel</Button>
                <Button variant="contained" onClick={handlePhoneSave} disabled={phoneSaving}>
                  {phoneSaving ? <CircularProgress size={18} color="inherit" /> : 'Save'}
                </Button>
              </Box>
            </Box>
          ) : (
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <Typography variant="body1" color={phone ? 'text.primary' : 'text.disabled'}>
                {phone ?? 'Not set'}
              </Typography>
              <Button
                size="small"
                onClick={() => { setPhoneValue(phone ?? ''); setPhoneEdit(true) }}
              >
                {phone ? 'Edit' : 'Add'}
              </Button>
            </Box>
          )}
        </Paper>

        {/* Saved Cards Section */}
        <Paper elevation={6} sx={{ borderRadius: 3, p: 3 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
            <CreditCardIcon color="primary" />
            <Typography variant="subtitle1" fontWeight={700}>Saved Cards</Typography>
          </Box>

          {cards.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              No saved cards yet.
            </Typography>
          ) : (
            <List disablePadding sx={{ mb: 1 }}>
              {cards.map((card, idx) => (
                <Box key={card.id}>
                  {idx > 0 && <Divider />}
                  <ListItem
                    disablePadding
                    sx={{ py: 1 }}
                    secondaryAction={
                      <IconButton edge="end" onClick={() => handleDeleteCard(card)} color="error" size="small">
                        <DeleteOutlineIcon fontSize="small" />
                      </IconButton>
                    }
                  >
                    <ListItemText
                      primary={
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <Typography variant="body2" fontWeight={600} sx={{ textTransform: 'capitalize' }}>
                            {card.brand}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            •••• {card.last4}
                          </Typography>
                          {card.is_default && (
                            <Typography variant="caption" color="primary" fontWeight={600}>Default</Typography>
                          )}
                        </Box>
                      }
                      secondary={`Expires ${card.exp_month.toString().padStart(2, '0')}/${card.exp_year}`}
                    />
                  </ListItem>
                </Box>
              ))}
            </List>
          )}

          <Button variant="outlined" size="small" startIcon={<CreditCardIcon />} onClick={() => setAddCardOpen(true)}>
            Add Card
          </Button>
        </Paper>
      </Container>

      {/* Add Card Dialog */}
      <Dialog open={addCardOpen} onClose={() => setAddCardOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Add New Card</DialogTitle>
        <DialogContent sx={{ pt: 2 }}>
          <Elements stripe={stripePromise}>
            <AddCardForm onSaved={handleCardSaved} onCancel={() => setAddCardOpen(false)} />
          </Elements>
        </DialogContent>
        <DialogActions />
      </Dialog>
    </Box>
  )
}
