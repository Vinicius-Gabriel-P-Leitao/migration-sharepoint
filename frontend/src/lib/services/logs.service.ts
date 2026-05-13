import apiClient from '@lib/utils/axios.util';
import type { LogResponse } from '@routes/jobs/jobs.type';

export const logsService = {
  getByJobId: async (jobId: number): Promise<LogResponse[]> => {
    const { data } = await apiClient.get<LogResponse[]>(`/jobs/${jobId}/logs`);
    return data;
  },
};
