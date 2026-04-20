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
      // Helper function to create/generate wallet after token is set
      const initializeWallet = async (token: string) => {
        try {
          console.log('🔐 Initializing wallet for user...')
          const res = await fetch(`${import.meta.env.VITE_TRANSACTIONS_BASE_URL || 'http://transactions.pt'}/v1/users/me/wallet`, {
            method: 'POST',
            headers: {
              'Authorization': `Bearer ${token}`,
              'Content-Type': 'application/json'
            }
          })
          
          if (res.ok) {
            const walletData = await res.json()
            const walletId = walletData.walletId || walletData.wallet || walletData.address || walletData.id
            if (walletId) {
              console.log('✅ Wallet initialized:', walletId)
              localStorage.setItem('egs_wallet_id', walletId)
            }
          } else {
            console.warn('⚠️ Failed to initialize wallet:', res.status, res.statusText)
          }
        } catch (err) {
          console.error('❌ Wallet initialization error:', err)
          // Don't block navigation if wallet init fails
        }
      }

      // First try: the server-side flow redirects back to the frontend with the token in the fragment
      // e.g. http://localhost:5175/callback#access_token=...&expires_in=...
      const hash = window.location.hash || ''
      if (hash && hash.includes('access_token')) {
        const params = new URLSearchParams(hash.replace('#', ''))
        const token = params.get('access_token')
        const expires = params.get('expires_in')
        if (token) {
          // Store token and set Authorization header
          localStorage.setItem('egs_token', token)
          api.defaults.headers.common['Authorization'] = `Bearer ${token}`

          // Clean URL (remove fragment) so token isn't in history
          try {
            const newUrl = window.location.pathname + window.location.search
            window.history.replaceState({}, document.title, newUrl)
          } catch (e) {
            // ignore
          }

          // Initialize wallet to generate private key
          await initializeWallet(token)

          // Navigate to dashboard
          navigate('/dashboard')
          return
        }
      }

      // Fallback: old flow where Keycloak returned a code as query param and frontend posts it to the server
      const code = searchParams.get('code')
      const state = searchParams.get('state')

      if (!code) {
        setError('No authorization code or access token received from Keycloak')
        return
      }

      try {
        // send code to transactions_service which now performs the token exchange
        const txBase = import.meta.env.VITE_TRANSACTIONS_BASE_URL || 'http://transactions.pt'
        console.log('Callback: exchanging code', code, 'state', state)
        // debug: show the full callback url
        console.log('Callback: POST', `${txBase}/v1/callback`)
        const res = await fetch(`${txBase}/v1/callback`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            code,
            redirect_uri: window.location.origin + '/callback',
            state: state || undefined,
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

          // Initialize wallet to generate private key
          try {
            console.log('🔐 Initializing wallet for user...')
            const walletRes = await fetch(`${txBase}/v1/users/me/wallet`, {
              method: 'POST',
              headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
              }
            })
            
            if (walletRes.ok) {
              const walletData = await walletRes.json()
              const walletId = walletData.walletId || walletData.wallet || walletData.address || walletData.id
              if (walletId) {
                console.log('✅ Wallet initialized:', walletId)
                localStorage.setItem('egs_wallet_id', walletId)
              }
            } else {
              console.warn('⚠️ Failed to initialize wallet:', walletRes.status, walletRes.statusText)
            }
          } catch (err) {
            console.error('❌ Wallet initialization error:', err)
            // Don't block navigation if wallet init fails
          }

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
