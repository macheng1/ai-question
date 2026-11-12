"use client";
import { Typography } from "@douyinfe/semi-ui";
import { Card, Layout } from "@douyinfe/semi-ui";
import { useCallback, useEffect, useRef, useState } from "react";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Form, Input, Modal, Table, Tag, Toast } from "@douyinfe/semi-ui";
import type { FormApi } from "@douyinfe/semi-ui/lib/es/form/interface";
import { IconPlus, IconSearch } from "@douyinfe/semi-icons";
import { ProfileShell } from "@/components/layout/ProfileShell";
import { getDepartments, getEmployees, saveDepartment, saveEmployee, type Department, type Employee, type SaveDepartment, type SaveEmployee } from "@/features/organization/api";

export default function OrganizationPage() {
  const [departments, setDepartments] = useState<Department[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const busy = useRef(false);
  const departmentForm = useRef<FormApi | null>(null);
  const employeeForm = useRef<FormApi | null>(null);
  const [departmentEdit, setDepartmentEdit] = useState<Department | "new" | null>(null);
  const [employeeEdit, setEmployeeEdit] = useState<Employee | "new" | null>(null);
  const load = useCallback(async () => {
    setLoading(true); setError("");
    try { const [d, e] = await Promise.all([getDepartments(), getEmployees()]); setDepartments(d); setEmployees(e); }
    catch (e) { setError(e instanceof Error ? e.message : "加载失败，请重试"); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const mutate = async (action: () => Promise<unknown>) => {
    if (busy.current) return;
    busy.current = true; setSaving(true);
    try { await action(); setDepartmentEdit(null); setEmployeeEdit(null); Toast.success("已保存"); await load(); }
    catch (e) { Toast.error(e instanceof Error ? e.message : "保存失败，请重试"); }
    finally { busy.current = false; setSaving(false); }
  };
  const toggleDepartment = (d: Department) => Modal.confirm({ title: d.status === "ACTIVE" ? "停用部门" : "恢复部门", content: d.status === "ACTIVE" ? `停用“${d.name}”后，该部门不可用于新考试。历史考试名单保持不变。` : `恢复“${d.name}”？`, onOk: () => mutate(() => saveDepartment(d.id, { name: d.name, status: d.status === "ACTIVE" ? "INACTIVE" : "ACTIVE" })) });
  const toggleEmployee = (e: Employee) => Modal.confirm({ title: e.status === "ACTIVE" ? "停用学员" : "恢复学员", content: `确认${e.status === "ACTIVE" ? "停用" : "恢复"}“${e.name}”？历史考试名单保持不变。`, onOk: () => mutate(() => saveEmployee(e.id, { name: e.name, phone: e.phone, email: e.email, jobTitle: e.jobTitle, learnerType: e.learnerType, gender: e.gender, employeeNo: e.employeeNo, remark: e.remark, departmentId: e.departmentId, status: e.status === "ACTIVE" ? "INACTIVE" : "ACTIVE" })) });
  const visible = employees.filter(e => (selected === null || e.departmentId === selected) && [e.name, e.phone, e.email, e.employeeNo, e.jobTitle, e.departmentName].some(v => (v || "").toLowerCase().includes(keyword.trim().toLowerCase())));
  const activeDepartments = departments.filter(d => d.status === "ACTIVE");
  const currentDepartment = departments.find(d => d.id === selected);
  return <ProfileShell>
    <Typography.Title heading={1} className="!m-0 !text-[28px] !font-semibold !text-[#183f35]">学员管理</Typography.Title>
    <p className="mt-2 mb-6 text-sm text-slate-500">维护部门和学员名册，用于按部门或人员安排考试。人员调整不会改变历史考试名单。</p>
    {error ? <div role="alert" className="rounded-xl bg-white p-6 text-red-600">{error}<Button onClick={() => void load()}>重新加载</Button></div> : <div className="grid gap-4 lg:grid-cols-[220px_minmax(0,1fr)]">
      <Card bodyStyle={{ padding: 0 }} className="rounded-2xl border border-slate-200 bg-white"><div className="p-4">
        <div className="mb-4 flex items-center justify-between"><Typography.Title heading={2} className="!text-base !font-semibold">部门</Typography.Title><Button icon={<IconPlus />} disabled={saving || loading} onClick={() => setDepartmentEdit("new")}>新增</Button></div>
        <Button block theme={selected === null ? "light" : "borderless"} onClick={() => setSelected(null)}>全部学员（{employees.length}）</Button>
        {loading && <p className="p-4 text-sm text-slate-500">加载中…</p>}
        {!loading && !departments.length && <p className="py-6 text-sm text-slate-500">暂无部门，请先新增部门。</p>}
        {departments.map(d => <div key={d.id} className="mt-3 border-t border-slate-100 pt-3"><Button block theme={selected === d.id ? "light" : "borderless"} style={{ height: "auto", minHeight: 44, padding: "10px 8px", textAlign: "left", justifyContent: "flex-start" }} onClick={() => setSelected(d.id)}><span className="whitespace-normal break-words text-left leading-6">{d.name}（{d.employeeCount}）{d.status === "INACTIVE" ? " · 已停用" : ""}</span></Button><div className="flex justify-end"><Button size="small" theme="borderless" disabled={saving} onClick={() => setDepartmentEdit(d)}>重命名</Button><Button size="small" theme="borderless" disabled={saving} onClick={() => toggleDepartment(d)}>{d.status === "ACTIVE" ? "停用" : "恢复"}</Button></div></div>)}
      </div></Card>
      <Layout.Content className="min-w-0 rounded-2xl border border-slate-200 bg-white p-4">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3"><Typography.Title heading={2} className="break-all !text-base !font-semibold">{currentDepartment?.name || "全部学员"}</Typography.Title><Button theme="solid" type="primary" icon={<IconPlus />} disabled={saving || loading || !activeDepartments.length} onClick={() => setEmployeeEdit("new")}>新增学员</Button></div>
        {!activeDepartments.length && !loading && <p className="mb-3 text-sm text-slate-500">请先新增或恢复一个部门，再添加学员。</p>}
        <Input prefix={<IconSearch />} showClear placeholder="搜索姓名、手机号、邮箱、工号、岗位或部门" value={keyword} onChange={setKeyword} />
        <div className="mt-4 overflow-x-auto"><Table<Employee> rowKey="id" loading={loading} dataSource={visible} empty={keyword ? "没有匹配的学员" : "暂无学员"} pagination={{ pageSize: 10 }} scroll={{ x: 1500 }} columns={[
          { title: "姓名", dataIndex: "name", width: 100 }, { title: "学员类型", width: 100, render: (_, e) => <Tag color={e.learnerType === "EXTERNAL" ? "orange" : "blue"}>{e.learnerType === "EXTERNAL" ? "外部" : "内部"}</Tag> }, { title: "性别", width: 80, render: (_, e) => e.gender === "MALE" ? "男" : e.gender === "FEMALE" ? "女" : "未填写" }, { title: "工号", dataIndex: "employeeNo", width: 130, render: value => value || "—" }, { title: "手机号", dataIndex: "phone", width: 135 }, { title: "邮箱", dataIndex: "email", width: 220, render: value => <span className="break-all">{value || "—"}</span> }, { title: "部门", dataIndex: "departmentName", width: 120 }, { title: "岗位", dataIndex: "jobTitle", width: 100 },
          { title: "状态", width: 80, render: (_, e) => <Tag color={e.status === "ACTIVE" ? "green" : "grey"}>{e.status === "ACTIVE" ? "在用" : "停用"}</Tag> },
          { title: "备注", dataIndex: "remark", width: 180, render: value => <Typography.Text ellipsis={{ showTooltip: true }} style={{ maxWidth: 150 }}>{value || "—"}</Typography.Text> },
          { title: "操作", fixed: "right", width: 200, render: (_, e) => <><Button theme="borderless" disabled={saving} onClick={() => setEmployeeEdit(e)}>编辑 / 调部门</Button><Button theme="borderless" disabled={saving} onClick={() => toggleEmployee(e)}>{e.status === "ACTIVE" ? "停用" : "恢复"}</Button></> }
        ]} /></div>
      </Layout.Content>
    </div>}
    <Modal bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }} title={departmentEdit === "new" ? "新增部门" : "部门重命名"} visible={departmentEdit !== null} okText="保存" cancelText="取消" confirmLoading={saving} cancelButtonProps={{ disabled: saving }} onOk={() => { if (!saving) departmentForm.current?.submitForm(); }} onCancel={() => { if (!saving) setDepartmentEdit(null); }} width="min(480px, 94vw)">
      {departmentEdit && <Form getFormApi={api => { departmentForm.current = api; }} key={typeof departmentEdit === "string" ? "new" : departmentEdit.id} initValues={{ name: departmentEdit === "new" ? "" : departmentEdit.name }} onSubmit={values => void mutate(() => saveDepartment(departmentEdit === "new" ? undefined : departmentEdit.id, { name: String(values.name).trim(), status: departmentEdit === "new" ? "ACTIVE" : departmentEdit.status } as SaveDepartment))}>
        <Form.Input field="name" label="部门名称" maxLength={100} rules={[{ required: true, whitespace: true, message: "请输入部门名称" }]} />
      </Form>}
    </Modal>
    <Modal bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }} title={employeeEdit === "new" ? "新增学员" : "编辑学员 / 调整部门"} visible={employeeEdit !== null} okText="保存" cancelText="取消" confirmLoading={saving} cancelButtonProps={{ disabled: saving }} onOk={() => { if (!saving) employeeForm.current?.submitForm(); }} onCancel={() => { if (!saving) setEmployeeEdit(null); }} width="min(520px, 94vw)">
      {employeeEdit && <Form getFormApi={api => { employeeForm.current = api; }} key={typeof employeeEdit === "string" ? "new" : employeeEdit.id} initValues={employeeEdit === "new" ? { name: "", phone: "", email: "", jobTitle: "", learnerType: "EXTERNAL", gender: "UNKNOWN", employeeNo: "", remark: "", departmentId: currentDepartment?.status === "ACTIVE" ? selected : activeDepartments[0]?.id } : employeeEdit} onSubmit={values => void mutate(() => saveEmployee(employeeEdit === "new" ? undefined : employeeEdit.id, { name: String(values.name).trim(), phone: String(values.phone).trim(), email: String(values.email || "").trim(), jobTitle: String(values.jobTitle || "").trim(), learnerType: values.learnerType, gender: values.gender, employeeNo: String(values.employeeNo || "").trim(), remark: String(values.remark || "").trim(), departmentId: Number(values.departmentId), status: employeeEdit === "new" ? "ACTIVE" : employeeEdit.status } as SaveEmployee))}>
        <Form.Input field="name" label="姓名" maxLength={100} rules={[{ required: true, whitespace: true, message: "请输入姓名" }]} />
        <div className="grid grid-cols-1 gap-x-4 sm:grid-cols-2">
          <Form.Select field="learnerType" label="学员类型" style={{ width: "100%" }} rules={[{ required: true, message: "请选择学员类型" }]} optionList={[{ label: "内部", value: "INTERNAL" }, { label: "外部", value: "EXTERNAL" }]} />
          <Form.Select field="gender" label="性别" style={{ width: "100%" }} optionList={[{ label: "未填写", value: "UNKNOWN" }, { label: "男", value: "MALE" }, { label: "女", value: "FEMALE" }]} />
        </div>
        <Form.Input field="employeeNo" label="工号（选填）" maxLength={100} />
        <Form.Input field="phone" label="手机号" maxLength={11} rules={[{ required: true, whitespace: true, message: "请输入手机号" }, { pattern: /^1[0-9]{10}$/, message: "请输入正确的11位手机号" }]} />
        <Form.Input field="email" label="邮箱（选填）" maxLength={254} placeholder="请输入邮箱" rules={[{ validator: (_rule, value) => !value || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value).trim()), message: "请输入有效邮箱" }]} />
        <Form.Select field="departmentId" label="所属部门" style={{ width: "100%" }} rules={[{ required: true, message: "请选择部门" }]} optionList={departments.filter(d => d.status === "ACTIVE" || (employeeEdit !== "new" && d.id === employeeEdit.departmentId)).map(d => ({ label: d.name + (d.status === "INACTIVE" ? "（已停用）" : ""), value: d.id, disabled: d.status === "INACTIVE" }))} />
        <Form.Input field="jobTitle" label="岗位（选填）" maxLength={100} />
        <Form.TextArea field="remark" label="备注（选填）" maxLength={1000} showClear rows={3} />
      </Form>}
    </Modal>
  </ProfileShell>;
}
