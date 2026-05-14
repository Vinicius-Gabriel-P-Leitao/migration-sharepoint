import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { connectionsService } from '@lib/services/connections.service';

export const useConnections = (enabled = true) => {
  return useQuery({
    queryKey: ['connections'],
    queryFn: connectionsService.getAll,
    enabled,
  });
};

export const useCreateConnection = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: connectionsService.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['connections'] });
    },
  });
};

export const useDeleteConnection = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: connectionsService.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['connections'] });
    },
  });
};
