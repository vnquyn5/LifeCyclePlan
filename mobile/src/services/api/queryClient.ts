import { QueryClient } from "@tanstack/react-query";
import { ApiRequestError } from "@/types/api.types";
import { authKeys } from "@/features/auth/queryKeys/authKeys";

export const queryClient = new QueryClient({
  defaultOptions: {
    mutations: { retry: false },
    queries: {
      retry: (failureCount, error) => shouldRetryQuery(failureCount, error)
    }
  }
});

export async function clearPrivateQueryCache(client: QueryClient = queryClient): Promise<void> {
  const isPrivate = (query: { queryKey: readonly unknown[] }) =>
    query.queryKey[0] === authKeys.privateRoot[0];
  await client.cancelQueries({ predicate: isPrivate });
  client.removeQueries({ predicate: isPrivate });
}

export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (
    error instanceof ApiRequestError &&
    (error.status === 401 || error.status === 403 || error.status === 429 || error.code === "REQUEST_CANCELLED")
  ) {
    return false;
  }
  return failureCount < 1;
}
