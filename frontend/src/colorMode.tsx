import React, { createContext, useContext, useMemo, useState } from 'react'
import { ThemeProvider, createTheme, CssBaseline } from '@mui/material'

type Mode = 'light' | 'dark'

type ColorModeContextType = {
  toggleColorMode: () => void
  mode: Mode
}

const ColorModeContext = createContext<ColorModeContextType>({
  toggleColorMode: () => {},
  mode: 'light',
})

export function useColorMode() {
  return useContext(ColorModeContext)
}

export default function ColorModeProvider({ children }: { children: React.ReactNode }) {
  const [mode, setMode] = useState<Mode>('light')

  const colorMode = useMemo(
    () => ({
      toggleColorMode: () => setMode((prev) => (prev === 'light' ? 'dark' : 'light')),
      mode,
    }),
    [mode]
  )

  const theme = useMemo(
    () =>
      createTheme({
        palette: {
          mode,
          primary: { main: '#1976d2' },
          secondary: { main: '#9c27b0' },
        },
        typography: {
          fontFamily: 'Inter, Roboto, Helvetica, Arial, sans-serif',
        },
      }),
    [mode]
  )

  return (
    <ColorModeContext.Provider value={colorMode}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ColorModeContext.Provider>
  )
}
