import apiClient from '@lib/utils/axios.util';
import type { TargetDb } from '@routes/jobs/jobs.type';

export interface AdapterTypesResponse {
  canonical: Record<string, string>;
  nativeTypes: string[];
}

export const adaptersService = {
  getTypes: async (targetDb: TargetDb): Promise<AdapterTypesResponse> => {
    const { data } = await apiClient.get<AdapterTypesResponse>(`/adapters/${targetDb}/types`);
    return data;
  },
};
