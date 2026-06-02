import React from 'react'
import Container from '@mui/material/Container'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import Paper from '@mui/material/Paper'

const IAM_BASE = import.meta.env.VITE_IAM_BASE_URL || 'http://iam.pt'
// When using the Python Composer (app.py) we use a local login page that POSTs to /v1/composer/login
const LOGIN_REDIRECT = '/login'

const handleSignup = async () => {
  try {
    const redirectUri = `${window.location.origin}/callback`
    const res = await fetch(`${IAM_BASE}/v1/signup?redirect_uri=${encodeURIComponent(redirectUri)}`)
    const data = await res.json()
    if (data.signup_url) {
      window.location.href = data.signup_url
    }
  } catch (err) {
    console.error('Error fetching signup URL:', err)
  }
}

export default function Home() {
  return (
    <Container maxWidth="md">
      <Box sx={{ mt: 6 }}>
        <Paper elevation={2} sx={{ p: { xs: 3, md: 6 }, borderRadius: '16px' }}>
          <Typography variant="h3" component="h1" gutterBottom sx={{ fontWeight: 700, letterSpacing: '-0.5px' }}>
            PayNexus
          </Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
            Frontend do PayNexus — login via Keycloak (OIDC redirect). Usa o botão abaixo para entrar.
          </Typography>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
            <Button 
              variant="contained" 
              color="primary" 
              size="large" 
              href={LOGIN_REDIRECT}
              sx={{
                borderRadius: '12px',
                fontWeight: 600,
                textTransform: 'none',
                padding: '12px 32px',
                transition: 'all 0.2s ease-in-out',
                boxShadow: '0 4px 12px rgba(0, 0, 0, 0.1)',
                '&:hover': {
                  transform: 'translateY(-1px)',
                  boxShadow: '0 6px 16px rgba(0, 0, 0, 0.15)',
                }
              }}
            >
              Login
            </Button>
            <Button 
              variant="outlined" 
              color="primary" 
              size="large" 
              onClick={handleSignup}
              sx={{
                borderRadius: '12px',
                fontWeight: 600,
                textTransform: 'none',
                padding: '12px 32px',
                transition: 'all 0.2s ease-in-out',
                borderColor: 'rgba(0, 0, 0, 0.23)',
                color: 'text.primary',
                '&:hover': {
                  transform: 'translateY(-1px)',
                  borderColor: 'text.primary',
                  backgroundColor: 'rgba(0, 0, 0, 0.04)',
                }
              }}
            >
              Sign Up
            </Button>
          </Stack>

          <Typography variant="body2" color="text.secondary">
            Depois de autenticado, vai para <a href="/dashboard">Dashboard</a>.
          </Typography>
        </Paper>
      </Box>
    </Container>
  )
}
