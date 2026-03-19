import React, { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import Container from '@mui/material/Container'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Typography from '@mui/material/Typography'
import Alert from '@mui/material/Alert'
import api from '../services/api'

export default function Callback() {
  const [searchParams] = useSearchParams()
  const [error, setError] = useState<string>('')
  const navigate = useNavigate()

  useEffect(() => {
    const exchangeCode = async () => {
      const code = searchParams.get('code')
      const state = searchParams.get('state')

      if (!code) {
        setError('No authorization code received from Keycloak')
        return
      }

      try {
        // send code to transactions_service which now performs the token exchange
        const txBase = 'http://localhost:8081'
        console.log('Callback: exchanging code', code, 'state', state)
        // debug: show the full callback url
        console.log('Callback: POST', `${txBase}/v1/callback`)
        const res = await fetch(`${txBase}/v1/callback`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            code,
            redirect_uri: window.location.origin + '/callback'
          })
        })

        console.log('Callback: fetch completed, response status', res.status)
        if (!res.ok) {
          const body = await res.json().catch(() => ({}))
          setError(`Token exchange failed: ${body.error || res.statusText}`)
          return
        }

        const data = await res.json()
        const token = data.access_token || data.token

        if (token) {
          // Store token
          localStorage.setItem('egs_token', token)
          // Set authorization header
          api.defaults.headers.common['Authorization'] = `Bearer ${token}`
          
          // Redirect to dashboard
          navigate('/dashboard')
        } else {
          setError('No token received from server')
        }
      } catch (err) {
        setError('Callback error: ' + (err as any).toString())
      }
    }

    exchangeCode()
  }, [searchParams, navigate])

  return (
    <Container maxWidth="sm">
      <Box sx={{ mt: 6, textAlign: 'center' }}>
        {error ? (
          <Alert severity="error">
            <Typography>{error}</Typography>
          </Alert>
        ) : (
          <>
            <CircularProgress />
            <Typography variant="h6" sx={{ mt: 2 }}>Authenticating...</Typography>
          </>
        )}
      </Box>
    </Container>
  )
}
