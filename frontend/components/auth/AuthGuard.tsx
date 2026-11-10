"use client";

import { useEffect, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Banner, Button, Spin, Typography } from "@douyinfe/semi-ui";
import { getCurrentUser, type AccountType } from "@/features/auth/api";
import { accountHome } from "@/features/auth/navigation";
import { useAuthStore } from "@/features/auth/store";
import { ApiError } from "@/lib/api/client";

/** Resolve the server identity before mounting protected pages or their data requests. */
export function AuthGuard({
  children,
  accountType,
}: {
  children: ReactNode;
  accountType?: AccountType;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const [checked, setChecked] = useState(false);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setChecked(false);
    setError("");
    const auth = useAuthStore.getState();
    const login = () =>
      router.replace(
        `/login?type=${accountType || "ADMIN"}&redirect=${encodeURIComponent(pathname)}`,
      );
    if (!auth.accessToken) {
      login();
      return;
    }
    getCurrentUser()
      .then((profile) => {
        if (!active) return;
        auth.setSession(profile, auth.accessToken!);
        if (accountType && profile.accountType !== accountType) {
          router.replace(accountHome(profile.accountType));
          return;
        }
        setChecked(true);
      })
      .catch((e) => {
        if (!active) return;
        if (e instanceof ApiError && e.status === 401) {
          auth.clearSession();
          login();
        } else setError(e instanceof Error ? e.message : "身份验证失败");
      });
    return () => {
      active = false;
    };
  }, [accountType, pathname, retry, router]);
  if (error)
    return (
      <div className="mx-auto max-w-lg p-8">
        <Banner type="danger" description={error} />
        <Button className="!mt-4" onClick={() => setRetry((v) => v + 1)}>
          重试
        </Button>
      </div>
    );
  if (!checked)
    return (
      <div className="flex min-h-[60dvh] w-full items-center justify-center px-6" role="status" aria-live="polite">
        <div className="flex flex-col items-center gap-4">
          <Spin size="large" />
          <Typography.Text type="secondary" className="whitespace-nowrap">
            正在验证身份，请稍候…
          </Typography.Text>
        </div>
      </div>
    );
  return children;
}
