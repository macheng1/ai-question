/** Preview only; the backend rechecks and atomically reserves eligibility on save. */
export function summarizeAudience(
  scope: { type: string; ids: number[] },
  employees: { id: number; departmentId: number; status: string }[],
  activeDepartments: number[],
  excludedIds: number[],
) {
  const selectedIds = new Set(scope.ids);
  const departments = new Set(activeDepartments);
  const exclusions = new Set(excludedIds);
  const selected = employees.filter(employee => employee.status === "ACTIVE"
    && departments.has(employee.departmentId)
    && (scope.type === "ALL" || (scope.type === "DEPARTMENT"
      ? selectedIds.has(employee.departmentId) : selectedIds.has(employee.id))));
  const excluded = selected.filter(employee => exclusions.has(employee.id)).length;
  return {
    eligible: selected.length - excluded,
    excluded,
    invalidSelection: scope.type === "USERS" && (excluded > 0 || selected.length !== selectedIds.size),
  };
}
