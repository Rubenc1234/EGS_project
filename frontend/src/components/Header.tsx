import React, { useState, useEffect } from 'react'
import AppBar from '@mui/material/AppBar'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import IconButton from '@mui/material/IconButton'
import Badge from '@mui/material/Badge'
import NotificationsIcon from '@mui/icons-material/Notifications'
import Button from '@mui/material/Button'
import Box from '@mui/material/Box'
import useSSE from '../hooks/useSSE'
import { toast } from 'react-toastify'
import Brightness4Icon from '@mui/icons-material/Brightness4'
import Brightness7Icon from '@mui/icons-material/Brightness7'
import Avatar from '@mui/material/Avatar'
import { useColorMode } from '../colorMode'
import { useNavigate } from 'react-router-dom'

export default function Header() {
  const [unread, setUnread] = useState(0)
  const { toggleColorMode, mode } = useColorMode()
  const navigate = useNavigate()
  const [isLoggedIn, setIsLoggedIn] = useState(!!localStorage.getItem('egs_token'))

  useEffect(() => {
    // Listen for storage changes (for multiple tabs) or just re-check
    const checkAuth = () => {
      setIsLoggedIn(!!localStorage.getItem('egs_token'))
    }
    window.addEventListener('storage', checkAuth)
    
    // Interval check for local changes within the same tab if needed, 
    // but usually navigation/re-renders handle it
    const interval = setInterval(checkAuth, 1000)
    
    return () => {
      window.removeEventListener('storage', checkAuth)
      clearInterval(interval)
    }
  }, [])

  const handleLogout = () => {
    localStorage.removeItem('egs_token')
    localStorage.removeItem('egs_wallet_id')
    setIsLoggedIn(false)
    navigate('/')
  }

  return (
    <AppBar position="static">
      <Toolbar>
        <Typography variant="h6" component="div" sx={{ flexGrow: 1, display: 'flex', alignItems: 'center', gap: 2 }}>
          <Box component="span" sx={{ fontWeight: 700, cursor: 'pointer' }} onClick={() => navigate('/')}>EGS</Box>
          <Box component="span" sx={{ opacity: 0.85 }}>Composer</Box>
        </Typography>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
          <IconButton color="inherit" aria-label="toggle-theme" onClick={toggleColorMode}>
            {mode === 'dark' ? <Brightness7Icon /> : <Brightness4Icon />}
          </IconButton>
          <IconButton color="inherit" aria-label="notifications">
            <Badge badgeContent={unread} color="error">
              <NotificationsIcon />
            </Badge>
          </IconButton>
          {isLoggedIn ? (
            <>
              <Button color="inherit" onClick={() => navigate('/dashboard')}>Dashboard</Button>
              <Button color="inherit" onClick={handleLogout} sx={{ fontWeight: 'bold', color: '#ffcdd2' }}>Logout</Button>
            </>
          ) : (
            <>
              <Button color="inherit" onClick={() => navigate('/')}>Home</Button>
              <Button color="inherit" onClick={() => navigate('/login')}>Login</Button>
            </>
          )}
        </Box>
      </Toolbar>
    </AppBar>
  )
}
