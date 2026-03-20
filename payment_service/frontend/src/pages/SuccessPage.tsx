import { useEffect, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Box, Button, Container, Typography } from '@mui/material'
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline'

const REDIRECT_DELAY_MS = 4000

export default function SuccessPage() {
  const [searchParams] = useSearchParams()
  const redirectUrl = searchParams.get('redirect_url') ?? ''
  const amount = searchParams.get('amount') ?? ''
  const redirected = useRef(false)

  useEffect(() => {
    if (!redirectUrl || redirected.current) return
    const timer = setTimeout(() => {
      redirected.current = true
      window.location.href = redirectUrl
    }, REDIRECT_DELAY_MS)
    return () => clearTimeout(timer)
  }, [redirectUrl])

  return (
    <Container maxWidth="sm">
      <Box
        sx={{
          mt: 12,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 3,
          textAlign: 'center',
        }}
      >
        <CheckCircleOutlineIcon sx={{ fontSize: 80, color: 'success.main' }} />
        <Typography variant="h4" fontWeight={700}>
          Payment successful!
        </Typography>
        {amount && (
          <Typography variant="body1" color="text.secondary">
            €{parseFloat(amount).toFixed(2)} has been added to your wallet.
          </Typography>
        )}
        {redirectUrl && (
          <Typography variant="body2" color="text.secondary">
            Redirecting you back in {REDIRECT_DELAY_MS / 1000} seconds…
          </Typography>
        )}
        {redirectUrl && (
          <Button variant="outlined" onClick={() => { window.location.href = redirectUrl }}>
            Return now
          </Button>
        )}
      </Box>
    </Container>
  )
}
