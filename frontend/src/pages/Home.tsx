import React from 'react'
import Container from '@mui/material/Container'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import Paper from '@mui/material/Paper'

const COMPOSER_BASE = import.meta.env.VITE_COMPOSER_BASE || 'http://localhost:5001'
const LOGIN_REDIRECT = import.meta.env.VITE_LOGIN_URL || `${COMPOSER_BASE}/v1/composer/auth/redirect`
const SIGNUP_REDIRECT = import.meta.env.VITE_SIGNUP_URL || `${COMPOSER_BASE}/v1/composer/auth/redirect?signup=true`

export default function Home() {
  return (
    <Container maxWidth="md">
      <Box sx={{ mt: 6 }}>
        <Paper elevation={2} sx={{ p: { xs: 3, md: 6 } }}>
          <Typography variant="h3" component="h1" gutterBottom sx={{ fontWeight: 700 }}>
            EGS Composer
          </Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
            Frontend do Composer — login via Keycloak (OIDC redirect). Usa o botão abaixo para entrar.
          </Typography>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
            <Button variant="contained" color="primary" size="large" href={LOGIN_REDIRECT}>
              Login
            </Button>
            <Button variant="outlined" color="primary" size="large" href={SIGNUP_REDIRECT}>
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
