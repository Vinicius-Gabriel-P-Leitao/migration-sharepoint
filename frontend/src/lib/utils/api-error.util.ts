import axios from 'axios';

export function getErrorMessage(error: unknown, defaultMessage: string = 'Ocorreu um erro inesperado.'): string {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message || error.response?.data?.error || error.message || defaultMessage;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return defaultMessage;
}
