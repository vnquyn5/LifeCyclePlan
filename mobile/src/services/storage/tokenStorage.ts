import * as SecureStore from "expo-secure-store";

const LEGACY_ACCESS_TOKEN_KEY = "auth.accessToken";
const REFRESH_TOKEN_KEY = "auth.refreshToken";
let storageMutationQueue: Promise<void> = Promise.resolve();

export async function saveRefreshToken(refreshToken: string): Promise<void> {
  return enqueueStorageMutation(async () => {
    try {
      await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, refreshToken);
      await SecureStore.deleteItemAsync(LEGACY_ACCESS_TOKEN_KEY);
    } catch (error) {
      await Promise.allSettled([
        SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY),
        SecureStore.deleteItemAsync(LEGACY_ACCESS_TOKEN_KEY)
      ]);
      throw error;
    }
  });
}

export async function readRefreshToken(): Promise<string | null> {
  return enqueueStorageMutation(async () => {
    await SecureStore.deleteItemAsync(LEGACY_ACCESS_TOKEN_KEY);
    return SecureStore.getItemAsync(REFRESH_TOKEN_KEY);
  });
}

export async function clearStoredTokens(): Promise<void> {
  return enqueueStorageMutation(async () => {
    await Promise.all([
      SecureStore.deleteItemAsync(LEGACY_ACCESS_TOKEN_KEY),
      SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY)
    ]);
  });
}

export async function clearRefreshTokenIfMatches(refreshToken: string): Promise<void> {
  return enqueueStorageMutation(async () => {
    if (await SecureStore.getItemAsync(REFRESH_TOKEN_KEY) === refreshToken) {
      await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
    }
  });
}

function enqueueStorageMutation<T>(operation: () => Promise<T>): Promise<T> {
  const result = storageMutationQueue.then(operation);
  storageMutationQueue = result.then(() => undefined, () => undefined);
  return result;
}
