import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { getWallet, listTransactions } from '../services/api'
import useSSE from '../hooks/useSSE'

export default function Dashboard() {
  const { data: wallet } = useQuery(['wallet'], getWallet)
  const { data: txs } = useQuery(['transactions'], () => listTransactions({ limit: 20 }))

  useSSE('/v1/composer/events/me')

  return (
    <div>
      <h2>Dashboard</h2>
      <div>
        <strong>Wallet</strong>
        <div>Id: {wallet?.id ?? '—'}</div>
        <div>Balance: {wallet?.balance ?? '—'}</div>
      </div>

      <div style={{ marginTop: 12 }}>
        <strong>Transactions</strong>
        <ul>
          {txs?.items?.map((t: any) => (
            <li key={t.id}>{t.id} — {t.status} — {t.amount}</li>
          ))}
        </ul>
      </div>
    </div>
  )
}
