import { ProfileShell } from "@/components/layout/ProfileShell";
import { AccountSettings } from "@/features/auth/components/AccountSettings";
export default function Settings() {
  return (
    <ProfileShell>
      <AccountSettings />
    </ProfileShell>
  );
}
