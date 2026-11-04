import { useQuery } from '@tanstack/react-query';
import { api, contracts } from '@/lib/api';

export const useClassroomStatus = () => useQuery({ queryKey: ['classroom', 'status'], queryFn: () => api(contracts.classroom.status), refetchInterval: (q) => (q.state.data?.status === 'syncing' ? 3000 : false) });
