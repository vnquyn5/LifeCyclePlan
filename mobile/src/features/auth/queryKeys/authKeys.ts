export const authKeys = {
  privateRoot: ["auth-private"] as const,
  privateUser: (userId: string) => ["auth-private", userId] as const
};
