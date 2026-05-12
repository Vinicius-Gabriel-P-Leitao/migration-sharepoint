import apiClient from '@/lib/utils/axios.util';

export interface SharePointResolveRequest {
  url: string;
}

export interface SharePointResolveResponse {
  siteId: string;
  listId: string;
  columns: string[];
}

export const sharepointService = {
  resolve: async (req: SharePointResolveRequest): Promise<SharePointResolveResponse> => {
    const { data } = await apiClient.post<SharePointResolveResponse>('/sharepoint/resolve', req);
    return data;
  },
};
