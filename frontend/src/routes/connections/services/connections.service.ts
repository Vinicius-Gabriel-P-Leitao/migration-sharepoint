import apiClient from '@/lib/utils/axios.util';

export interface ConnectionSummary {
  key: string;
  name: string;
}

export interface ConnectionRequest {
  key: string;
  name: string;
  url: string;
}

export const connectionsService = {
  getAll: async (): Promise<ConnectionSummary[]> => {
    const { data } = await apiClient.get<ConnectionSummary[]>('/connections');
    return data;
  },
  create: async (connection: ConnectionRequest): Promise<void> => {
    await apiClient.post('/connections', connection);
  },
  delete: async (key: string): Promise<void> => {
    await apiClient.delete(`/connections/${key}`);
  }
};
