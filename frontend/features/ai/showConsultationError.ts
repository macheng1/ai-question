import { Toast } from "@douyinfe/semi-ui";
import { ApiError } from "@/lib/api/client";

export function showConsultationError(error: unknown, fallback: string) {
  if (error instanceof ApiError && error.status === 401) {
    Toast.error("登录已失效，请重新登录");
    return;
  }
  Toast.error(error instanceof Error && error.message ? error.message : fallback);
}
