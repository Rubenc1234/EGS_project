import { useNavigate, useLocation, Outlet } from 'react-router-dom'
import { AppBar, Box, Button, Toolbar, Typography } from '@mui/material'
import { clearToken, getUsername, isOperator } from '../api'

export default function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const username = getUsername()

  function handleLogout() {
    clearToken()
    navigate('/', { replace: true })
  }

  function isActive(path: string) {
    return location.pathname === path || location.pathname.startsWith(path + '/')
  }

  const navBtn = (active: boolean) => ({
    color: 'white',
    fontWeight: active ? 700 : 500,
    borderBottom: active ? '2px solid rgba(255,255,255,0.9)' : '2px solid transparent',
    borderRadius: 0,
    px: 1.5,
    textTransform: 'none' as const,
    '&:hover': { background: 'rgba(255,255,255,0.1)', borderBottomColor: 'rgba(255,255,255,0.6)' },
  })

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <AppBar
        position="fixed"
        elevation={0}
        sx={{
          background: 'linear-gradient(135deg, #0891b2 0%, #06b6d4 100%)',
          borderBottom: '1px solid rgba(255,255,255,0.15)',
        }}
      >
        <Toolbar sx={{ gap: 1 }}>
          <Typography
            variant="subtitle1"
            fontWeight={800}
            color="white"
            sx={{ cursor: 'pointer', mr: 3 }}
            onClick={() => navigate('/home')}
          >
            Payments
          </Typography>
          <Button sx={navBtn(isActive('/home'))} onClick={() => navigate('/home')}>Home</Button>
          <Button sx={navBtn(isActive('/payments'))} onClick={() => navigate('/payments')}>Pagamentos</Button>
          <Button sx={navBtn(isActive('/profile'))} onClick={() => navigate('/profile')}>Perfil</Button>
          {isOperator() && (
            <Button sx={navBtn(isActive('/stats'))} onClick={() => navigate('/stats')}>Stats</Button>
          )}
          <Box sx={{ flexGrow: 1 }} />
          {username && (
            <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.8)', mr: 1, fontSize: '0.8rem' }}>
              {username}
            </Typography>
          )}
          <Button
            variant="outlined"
            size="small"
            onClick={handleLogout}
            sx={{
              color: 'white',
              borderColor: 'rgba(255,255,255,0.5)',
              textTransform: 'none',
              '&:hover': { borderColor: 'white', background: 'rgba(255,255,255,0.1)' },
            }}
          >
            Logout
          </Button>
        </Toolbar>
      </AppBar>
      <Outlet />
    </Box>
  )
}
