import { useQuery } from '@tanstack/react-query';
import { adaptersService } from '@lib/services/adapters.service';
import type { TargetDb } from '@routes/jobs/jobs.type';

export const useAdapterTypes = (targetDb: TargetDb, enabled = true) => {
  return useQuery({
    queryKey: ['adapter-types', targetDb],
    queryFn: () => adaptersService.getTypes(targetDb),
    enabled: enabled && !!targetDb,
  });
};
