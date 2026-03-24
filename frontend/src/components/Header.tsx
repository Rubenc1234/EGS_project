import React, { useState, useEffect } from 'react'
import AppBar from '@mui/material/AppBar'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import IconButton from '@mui/material/IconButton'
import Badge from '@mui/material/Badge'
import NotificationsIcon from '@mui/icons-material/Notifications'
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive'
import Button from '@mui/material/Button'
import Box from '@mui/material/Box'
import useSSE from '../hooks/useSSE'
import { toast } from 'react-toastify'
import Brightness4Icon from '@mui/icons-material/Brightness4'
import Brightness7Icon from '@mui/icons-material/Brightness7'
import Avatar from '@mui/material/Avatar'
import { useColorMode } from '../colorMode'
import { useNavigate } from 'react-router-dom'
import { isTokenExpired } from '../App'
import Tooltip from '@mui/material/Tooltip'

export default function Header() {
  const [unread, setUnread] = useState(0)
  const { toggleColorMode, mode } = useColorMode()
  const navigate = useNavigate()
  
  const checkIsLoggedIn = () => {
    const token = localStorage.getItem('egs_token')
    if (!token) return false
    if (isTokenExpired(token)) {
      localStorage.removeItem('egs_token')
      return false
    }
    return true
  }

  const [isLoggedIn, setIsLoggedIn] = useState(checkIsLoggedIn())
  //const [isLoggedIn, setIsLoggedIn] = useState(!!localStorage.getItem('egs_token'))
  const [notificationEnabled, setNotificationEnabled] = useState(false)

  useEffect(() => {
    const checkAuth = () => {
      setIsLoggedIn(checkIsLoggedIn())
    }
    window.addEventListener('storage', checkAuth)
    
    const interval = setInterval(checkAuth, 1000)
    
    // Check notification permission status
    if ('Notification' in window) {
      setNotificationEnabled(Notification.permission === 'granted')
    }
    
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

  const handleEnableNotifications = async () => {
    try {
      console.log('[Header] User clicked enable notifications button');
      
      // Request permission (MUST be called from user event handler)
      const permission = await Notification.requestPermission();
      console.log('[Header] Notification permission result:', permission);
      
      if (permission === 'granted') {
        setNotificationEnabled(true);
        toast.success('✅ Notifications enabled! Now creating subscription...');
        // Give the app a moment to register the subscription
        setTimeout(() => {
          window.location.reload();
        }, 500);
      } else {
        toast.error('❌ You denied notification permission');
        setNotificationEnabled(false);
      }
    } catch (err) {
      console.error('[Header] Error requesting notification permission:', err);
      toast.error('Error requesting notification permission');
    }
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
          {isLoggedIn && !notificationEnabled && (
            <Tooltip title="Enable push notifications">
              <Button 
                color="inherit" 
                onClick={handleEnableNotifications}
                startIcon={<NotificationsIcon />}
                sx={{ fontWeight: 'bold', fontSize: '0.85rem' }}
              >
                Enable Notifications
              </Button>
            </Tooltip>
          )}
          {notificationEnabled && (
            <Tooltip title="Push notifications enabled">
              <IconButton color="inherit" aria-label="notifications">
                <Badge>
                  <NotificationsActiveIcon sx={{ color: '#4caf50' }} />
                </Badge>
              </IconButton>
            </Tooltip>
          )}
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
