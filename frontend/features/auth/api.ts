import { apiClient } from "@/lib/api/client";
export type AuthResult = { userId:number; name:string; phone:string; token:{accessToken:string;refreshToken:string;tokenType:string;expiresInSeconds:number} };
export type UserProfile = { userId:number; name:string; phone:string; unitName?:string; province?:string; city?:string; district?:string; address?:string; roleCode?:string };
export function login(data:{phone:string;password:string}) { return apiClient.post<AuthResult>("/auth/login", data); }
export function register(data:{phone:string;password:string;confirmPassword:string;captcha:string;name?:string;unitName?:string;province?:string;city?:string;district?:string;address?:string}) { return apiClient.post<AuthResult>("/auth/register", data); }
export function sendCaptcha(phone:string) { return apiClient.post<{message:string;testCode?:string}>(`/auth/captcha?phone=${encodeURIComponent(phone)}`); }
export function getCurrentUser() { return apiClient.get<UserProfile>("/auth/me"); }
export function updateProfile(data:{name:string;unitName:string;address?:string}) { return apiClient.put<UserProfile>("/auth/me/profile", data); }
export function changePassword(data:{currentPassword:string;newPassword:string;confirmPassword:string}) { return apiClient.put<void>("/auth/me/password", data); }
