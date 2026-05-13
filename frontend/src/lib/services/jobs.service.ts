import apiClient from '@lib/utils/axios.util';
import type { JobResponse, JobRequest, LogResponse } from '@routes/jobs/jobs.type';

export const jobsService = {
  getAll: async (): Promise<JobResponse[]> => {
    const { data } = await apiClient.get<JobResponse[]>('/jobs');
    return data;
  },

  getById: async (id: number): Promise<JobResponse> => {
    const { data } = await apiClient.get<JobResponse>(`/jobs/${id}`);
    return data;
  },

  create: async (job: JobRequest): Promise<JobResponse> => {
    const { data } = await apiClient.post<JobResponse>('/jobs', job);
    return data;
  },

  update: async (id: number, job: JobRequest): Promise<JobResponse> => {
    const { data } = await apiClient.put<JobResponse>(`/jobs/${id}`, job);
    return data;
  },

  delete: async (id: number): Promise<void> => {
    await apiClient.delete(`/jobs/${id}`);
  },

  run: async (id: number): Promise<void> => {
    await apiClient.post(`/jobs/${id}/run`);
  },

  getLogs: async (id: number): Promise<LogResponse[]> => {
    const { data } = await apiClient.get<LogResponse[]>(`/jobs/${id}/logs`);
    return data;
  },
};
