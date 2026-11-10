"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { AuthGuard } from "@/components/auth/AuthGuard";
import { accountHome } from "@/features/auth/navigation";
import { useAuthStore } from "@/features/auth/store";
function Home() {
  const user = useAuthStore(state => state.user);
  const router = useRouter();
  useEffect(() => { if (user) router.replace(accountHome(user.accountType)); }, [router, user]);
  return null;
}
export default function Workspace() { return <AuthGuard><Home /></AuthGuard>; }
