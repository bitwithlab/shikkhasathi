import { useQuery } from '@tanstack/react-query';
import { api, contracts } from './api';

export function usePublicConfig() {
  return useQuery({ queryKey: ['config'], queryFn: () => api(contracts.settings.publicConfig), staleTime: 10 * 60_000 });
}
