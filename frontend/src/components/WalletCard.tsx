import React from 'react'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import CardActions from '@mui/material/CardActions'

export default function WalletCard({ wallet }: { wallet?: any }) {
  return (
    <Card sx={{ minWidth: 275 }}>
      <CardContent>
        <Typography variant="h6">Wallet</Typography>
        <Typography sx={{ fontSize: 14 }} color="text.secondary" gutterBottom>
          ID: {wallet?.id ?? '—'}
        </Typography>
        <Typography variant="h4">{wallet?.balance ?? '0.00'}</Typography>
      </CardContent>
      <CardActions>
        <Button size="small" href="/dashboard">View</Button>
        <Button size="small" href="#">Send</Button>
      </CardActions>
    </Card>
  )
}
