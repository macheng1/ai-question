import { apiClient } from "@/lib/api/client";
export type OrganizationStatus = "ACTIVE" | "INACTIVE";
export type Department = { id: number; name: string; status: OrganizationStatus; employeeCount: number };
export type Employee = { id: number; name: string; phone: string; email?: string | null; jobTitle: string; departmentId: number; departmentName: string; status: OrganizationStatus };
export type SaveDepartment = Pick<Department, "name" | "status">;
export type SaveEmployee = Omit<Employee, "id" | "departmentName">;
export const getDepartments = () => apiClient.get<Department[]>("/management/departments");
export const getEmployees = () => apiClient.get<Employee[]>("/management/employees");
export const saveDepartment = (id: number | undefined, data: SaveDepartment) => id ? apiClient.put<Department>(`/management/departments/${id}`, data) : apiClient.post<Department>("/management/departments", data);
export const saveEmployee = (id: number | undefined, data: SaveEmployee) => id ? apiClient.put<Employee>(`/management/employees/${id}`, data) : apiClient.post<Employee>("/management/employees", data);

export const resetEmployeePassword = (id: number) => apiClient.post<void>(`/management/employees/${id}/reset-password`);
