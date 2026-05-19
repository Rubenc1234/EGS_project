import { Chip } from '@mui/material'
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import CancelIcon from '@mui/icons-material/Cancel'

type PaymentStatus = 'pending' | 'concluded' | 'cancelled'

const STATUS_CONFIG: Record<PaymentStatus, {
  label: string
  color: 'warning' | 'success' | 'error'
  icon: React.ReactElement
}> = {
  pending:   { label: 'Pendente',  color: 'warning', icon: <HourglassEmptyIcon /> },
  concluded: { label: 'Concluído', color: 'success', icon: <CheckCircleIcon />    },
  cancelled: { label: 'Cancelado', color: 'error',   icon: <CancelIcon />         },
}

export function getStatusLabel(status: string): string {
  return STATUS_CONFIG[status as PaymentStatus]?.label ?? status
}

interface StatusChipProps {
  status: string
  size?: 'small' | 'medium'
}

export default function StatusChip({ status, size = 'small' }: StatusChipProps) {
  const config = STATUS_CONFIG[status as PaymentStatus]
  if (!config) return <Chip label={status} size={size} />
  return (
    <Chip label={config.label} color={config.color} size={size} icon={config.icon} />
  )
}
