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
import type { TrainingAudienceScope } from "../api";

export function TrainingAudience({
  value,
  onChange,
  disabled = false,
}: {
  value: TrainingAudienceScope;
  onChange: (scope: TrainingAudienceScope) => void;
  disabled?: boolean;
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
  const inactive = disabled || loading || !!error;
  const tree = departments
    .filter((d) => d.status === "ACTIVE")
    .map((d) => ({
      label: d.name,
      value: `d${d.id}`,
      children: employees
        .filter((e) => e.departmentId === d.id && e.status === "ACTIVE")
        .map((e) => ({ label: `${e.name} · ${e.phone}`, value: `e${e.id}` })),
    }))
    .filter((d) => d.children.length > 0);
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-1">
        <strong>学习范围</strong>
        <Tooltip content="范围随培训一起保存，独立培训和关联考试共用此名单。按保存时的在用员工生成名单；已有学习或考试记录后锁定。">
          <Button
            theme="borderless"
            type="tertiary"
            size="small"
            icon={<IconHelpCircleStroked />}
            aria-label="学习范围说明"
          />
        </Tooltip>
      </div>
      {error && <Banner type="danger" description={error} />}
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
    </div>
  );
}
