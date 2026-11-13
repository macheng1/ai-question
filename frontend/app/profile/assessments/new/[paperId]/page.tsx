import { redirect } from "next/navigation";
export default async function LegacyArrangePage({ params }: { params: Promise<{ paperId: string }> }) {
  const { paperId } = await params;
  redirect(`/profile/trainings/new?paperId=${encodeURIComponent(paperId)}`);
}
