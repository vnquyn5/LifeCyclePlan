import { useEffect, useRef, useState } from "react";

export function useAuthRetryDelay() {
  const [isWaiting, setIsWaiting] = useState(false);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const blockRetry = (retryAfterSeconds?: number) => {
    if (!retryAfterSeconds || !Number.isFinite(retryAfterSeconds) || retryAfterSeconds <= 0) return;
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    setIsWaiting(true);
    timeoutRef.current = setTimeout(() => {
      setIsWaiting(false);
      timeoutRef.current = undefined;
    }, retryAfterSeconds * 1000);
  };

  useEffect(
    () => () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    },
    []
  );

  return { blockRetry, isWaiting };
}
