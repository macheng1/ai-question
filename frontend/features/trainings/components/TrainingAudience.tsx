"use client";

import { useEffect, useState } from "react";
import { Banner, Button, Cascader, Select, Tooltip } from "@douyinfe/semi-ui";
import { IconHelpCircleStroked } from "@douyinfe/semi-icons";
import {
  getDepartments,
  getEmployees,
  type Department,
  type Employee,
} from "@/features/organization/api";
import { summarizeAudience } from "../audience-rules";
import { getTrainingExcludedEmployees, type TrainingAudienceScope } from "../api";

export function TrainingAudience({
  value,
  onChange,
  disabled = false,
  assessmentId,
  trainingId,
  locked = false,
  onValidationChange,
}: {
  value: TrainingAudienceScope;
  onChange: (scope: TrainingAudienceScope) => void;
  disabled?: boolean;
  assessmentId?: number | null;
  trainingId?: number;
  locked?: boolean;
  onValidationChange?: (error: string) => void;
}) {
  const [departments, setDepartments] = useState<Department[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    Promise.all([getDepartments(), getEmployees()])
      .then(([d, e]) => {
        if (active) {
          setDepartments(d);
          setEmployees(e);
        }
      })
      .catch((e) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);
  const [eligibility, setEligibility] = useState<{
    assessmentId: number | null;
    trainingId?: number;
    excluded: number[];
    error: string;
  }>({ assessmentId: null, excluded: [], error: "" });
  useEffect(() => {
    let active = true;
    if (!assessmentId || locked) return;
    getTrainingExcludedEmployees(assessmentId, trainingId)
      .then((excluded) => {
        if (active) setEligibility({ assessmentId, trainingId, excluded, error: "" });
      })
      .catch((e) => {
        if (active) setEligibility({ assessmentId, trainingId, excluded: [], error: e.message || "人员资格加载失败，请刷新后重试" });
      });
    return () => { active = false; };
  }, [assessmentId, trainingId, locked]);
  const matchingEligibility = eligibility.assessmentId === assessmentId && eligibility.trainingId === trainingId;
  const eligibilityLoading = !!assessmentId && !locked && !matchingEligibility;
  const eligibilityError = assessmentId && !locked && matchingEligibility ? eligibility.error : "";
  const excluded = new Set(!locked && assessmentId && matchingEligibility ? eligibility.excluded : []);
  const inactive = disabled || loading || !!error || eligibilityLoading || !!eligibilityError;
  const summary = summarizeAudience(value, employees,
    departments.filter(department => department.status === "ACTIVE").map(department => department.id), [...excluded]);
  const validationError = locked ? "" : loading || eligibilityLoading ? "正在检查学员资格，请稍候"
    : error || eligibilityError || (summary.invalidSelection ? "所选学员中有已绑定该试卷或不可用的人员，请重新选择"
      : summary.eligible === 0 ? "当前范围内没有可分配学员，请调整范围" : "");
  useEffect(() => { onValidationChange?.(validationError); }, [onValidationChange, validationError]);
  const tree = departments
    .filter((d) => d.status === "ACTIVE")
    .map((d) => ({
      label: d.name,
      value: `d${d.id}`,
      children: employees
        .filter((e) => e.departmentId === d.id && e.status === "ACTIVE")
        .map((e) => ({
          label: `${e.name} · ${e.phone}${excluded.has(e.id) ? "（已绑定该试卷或已交卷）" : ""}`,
          value: `e${e.id}`,
          disabled: excluded.has(e.id),
        })),
    }))
    .filter((d) => d.children.length > 0);
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-1">
        <strong>学习范围</strong>
        <Tooltip content="范围随培训一起保存，独立培训和关联考试共用此名单。按保存时的在用员工生成名单；已在其他培训绑定同一试卷的学员不可重复分配，无论是否考试；本培训原学员保留。必修培训会自动补入范围内的新增学员；已有学习或考试记录后范围锁定。">
          <Button
            theme="borderless"
            type="tertiary"
            size="small"
            icon={<IconHelpCircleStroked />}
            aria-label="学习范围说明"
          />
        </Tooltip>
      </div>
      {(error || eligibilityError) && <Banner type="danger" description={error || eligibilityError} />}
      <div className={`grid grid-cols-1 items-start gap-3 ${value.type !== "ALL" ? "sm:grid-cols-2" : ""}`}>
        <Select
          style={{ width: "100%", minWidth: 0 }}
          aria-label="学习范围"
          disabled={inactive}
          value={value.type}
          optionList={[
            { value: "ALL", label: "本企业全部在用员工" },
            { value: "DEPARTMENT", label: "指定部门" },
            { value: "USERS", label: "指定人员" },
          ]}
          onChange={(type) =>
            onChange({ type: type as TrainingAudienceScope["type"], ids: [] })
          }
        />
        {value.type === "DEPARTMENT" && (
          <Select
          style={{ width: "100%", minWidth: 0 }}
            multiple
            filter
            aria-label="培训部门"
            placeholder="选择部门"
            value={value.ids}
            disabled={inactive}
            optionList={departments
              .filter((d) => d.status === "ACTIVE")
              .map((d) => ({ label: d.name, value: d.id }))}
            onChange={(ids) => onChange({ ...value, ids: ids as number[] })}
          />
        )}
        {value.type === "USERS" && (
          <Cascader
            style={{ width: "100%", minWidth: 0 }}
            multiple
            leafOnly
            filterTreeNode
            aria-label="培训人员"
            placeholder="选择部门下的人员"
            disabled={inactive}
            treeData={tree}
            value={employees
              .filter((e) => value.ids.includes(e.id))
              .map((e) => [`d${e.departmentId}`, `e${e.id}`])}
            onChange={(paths) =>
              onChange({
                ...value,
                ids: (Array.isArray(paths) ? paths : []).flatMap((path) => {
                  const leaf = Array.isArray(path) ? path[path.length - 1] : null;
                  return typeof leaf === "string" && leaf.startsWith("e")
                    ? [Number(leaf.slice(1))]
                    : [];
                }),
              })
            }
          />
        )}
      </div>
      {!locked && !inactive && <p className="m-0 text-sm text-slate-500">
        当前范围可分配 {summary.eligible} 人，因试卷已绑定或已交卷排除 {summary.excluded} 人。
      </p>}
      {!locked && !inactive && validationError && <Banner type="warning" description={validationError} />}
      {!locked && eligibilityLoading && <p className="m-0 text-sm text-slate-500">正在检查人员资格…</p>}

    </div>
  );
}
