import { keepPreviousData } from '@tanstack/react-query'
import { apiClient } from '../../app/api'
import type { Transaction, TransactionPayload } from './types'

export const transactionKeys = {
  all: ['transactions'] as const,
}

export const transactionsQueryOptions = {
  queryKey: transactionKeys.all,
  queryFn: async (): Promise<Transaction[]> => {
    const response = await apiClient.get<Transaction[]>('/transactions')
    return response.data
  },
  placeholderData: keepPreviousData,
}

export const createTransaction = async (
  payload: TransactionPayload,
): Promise<Transaction> => {
  const response = await apiClient.post<Transaction>('/transactions', payload)
  return response.data
}

export const updateTransaction = async (
  transactionId: number,
  payload: TransactionPayload,
): Promise<Transaction> => {
  const response = await apiClient.put<Transaction>(
    `/transactions/${transactionId}`,
    payload,
  )
  return response.data
}

export const deleteTransaction = async (transactionId: number): Promise<void> => {
  await apiClient.delete(`/transactions/${transactionId}`)
}
