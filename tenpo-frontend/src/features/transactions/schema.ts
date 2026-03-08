import dayjs from 'dayjs'
import { z } from 'zod'
import type { Transaction, TransactionPayload } from './types'

export const MAX_AMOUNT_IN_PESOS = 2_147_483_647

const normalizeText = (value: string) => value.trim().replace(/\s+/g, ' ')

export const transactionFormSchema = z.object({
  amountInPesos: z
    .number()
    .int('Amount must be an integer.')
    .min(0, 'Amount cannot be negative.')
    .max(
      MAX_AMOUNT_IN_PESOS,
      `Amount must stay below ${MAX_AMOUNT_IN_PESOS.toLocaleString('en-US')}.`,
    ),
  merchant: z
    .string()
    .trim()
    .min(1, 'Merchant is required.')
    .max(160, 'Merchant must stay under 160 characters.'),
  customerName: z
    .string()
    .trim()
    .min(1, 'Tenpista name is required.')
    .max(120, 'Tenpista name must stay under 120 characters.'),
  transactionDate: z
    .string()
    .min(1, 'Transaction date is required.')
    .refine((value) => dayjs(value).isValid(), 'Use a valid date and time.')
    .refine(
      (value) => !dayjs(value).isAfter(dayjs()),
      'Transaction date cannot be in the future.',
    ),
})

export type TransactionFormValues = z.infer<typeof transactionFormSchema>

export const getInitialFormValues = (
  transaction: Transaction | null,
): TransactionFormValues => ({
  amountInPesos: transaction?.amountInPesos ?? 0,
  merchant: transaction?.merchant ?? '',
  customerName: transaction?.customerName ?? '',
  transactionDate: transaction
    ? dayjs(transaction.transactionDate).format('YYYY-MM-DDTHH:mm')
    : dayjs().subtract(5, 'minute').format('YYYY-MM-DDTHH:mm'),
})

export const toPayload = (
  values: TransactionFormValues,
): TransactionPayload => ({
  amountInPesos: values.amountInPesos,
  merchant: normalizeText(values.merchant),
  customerName: normalizeText(values.customerName),
  transactionDate: dayjs(values.transactionDate).format('YYYY-MM-DDTHH:mm:ss'),
})
