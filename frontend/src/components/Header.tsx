import React, { useState } from 'react'
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

export default function Header() {
  const [unread, setUnread] = useState(0)
  const { toggleColorMode, mode } = useColorMode()

  // TODO: Implement SSE endpoint POST /v1/events/subscribe or similar in backend for live updates
  // useSSE('/v1/events/me', (data) => {
  //   // show toast and increment badge
  //   toast.info(`Event: ${data}`)
  //   setUnread((u) => u + 1)
  // })

  return (
    <AppBar position="static">
      <Toolbar>
        <Typography variant="h6" component="div" sx={{ flexGrow: 1, display: 'flex', alignItems: 'center', gap: 2 }}>
          <Box component="span" sx={{ fontWeight: 700 }}>EGS</Box>
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
          <Avatar alt="User" sx={{ width: 32, height: 32 }}>R</Avatar>
          <Button color="inherit" href="/">Home</Button>
          <Button color="inherit" href="/dashboard">Dashboard</Button>
        </Box>
      </Toolbar>
    </AppBar>
  )
}
