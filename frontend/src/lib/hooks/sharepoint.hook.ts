import { useMutation } from '@tanstack/react-query';
import { sharepointService } from '@lib/services/sharepoint.service';

export const useResolveSharePoint = () => {
  return useMutation({
    mutationFn: sharepointService.resolve,
  });
};
