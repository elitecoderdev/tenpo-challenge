export type Transaction = {
  id: number
  amountInPesos: number
  merchant: string
  customerName: string
  transactionDate: string
}

export type TransactionPayload = Omit<Transaction, 'id'>

export type ApiFieldError = {
  field: string
  message: string
}

export type ApiError = {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors: ApiFieldError[]
}
