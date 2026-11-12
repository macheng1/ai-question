"use client";
import { useEffect, useState, useRef } from "react";
import {
  Banner,
  Button,
  Card,
  Descriptions,
  Form,
  Toast,
  Typography,
} from "@douyinfe/semi-ui";
import {
  changePassword,
  getCurrentUser,
  updateProfile,
  type UserProfile,
} from "../api";
import { getLearnerProfile } from "../learner-session";
import { useAuthStore } from "../store";

import type { FormApi } from "@douyinfe/semi-ui/lib/es/form/interface";

export function AccountSettings({ employee = false }: { employee?: boolean }) {
  const passwordForm = useRef<FormApi | null>(null);
  const [profile, setProfile] = useState<UserProfile>();
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    (employee ? getLearnerProfile() : getCurrentUser())
      .then(setProfile)
      .catch((e) => setError(e.message));
  }, [employee]);
  return (
    <div className="flex flex-col gap-6">
      <Typography.Title heading={2} style={{ color: "#183f35", margin: 0 }}>{employee ? "员工资料" : "账号资料与密码"}</Typography.Title>
      {error && <Banner type="danger" description={error} />}
      {profile && (
        <Card title="账号资料">
          {profile.accountType === "EMPLOYEE" ? (
            <>
              <Descriptions
                data={[
                  { key: "姓名", value: profile.name },
                  { key: "手机号", value: profile.phone },
                  { key: "邮箱", value: profile.email || "未填写" },
                  { key: "企业", value: profile.unitName || "—" },
                  { key: "部门", value: profile.departmentName || "—" },
                ]}
              />
              <Typography.Text type="secondary">
                资料变更请联系企业管理员。
              </Typography.Text>
            </>
          ) : (
            <Form
              initValues={profile}
              onSubmit={async (values) => {
                setSaving(true);
                try {
                  const updated = await updateProfile({
                    name: String(values.name).trim(),
                    unitName: String(values.unitName).trim(),
                    address: String(values.address || "").trim(),
                  });
                  setProfile(updated);
                  const auth = useAuthStore.getState();
                  if (auth.accessToken)
                    auth.setSession(updated, auth.accessToken);
                  Toast.success("资料已保存");
                } catch (e) {
                  Toast.error(e instanceof Error ? e.message : "保存失败");
                } finally {
                  setSaving(false);
                }
              }}
            >
              <Form.Input
                field="name"
                label="姓名"
                maxLength={80}
                rules={[{ required: true, whitespace: true }]}
              />
              <Form.Input
                field="unitName"
                label="企业名称"
                maxLength={160}
                rules={[{ required: true, whitespace: true }]}
              />
              <Form.TextArea field="address" label="地址" maxLength={255} />
              <Button htmlType="submit" theme="solid" loading={saving}>
                保存资料
              </Button>
            </Form>
          )}
        </Card>
      )}
      {!employee && <Card title="修改密码">
        <Form
          getFormApi={(api) => {
            passwordForm.current = api;
          }}
          onSubmit={async (values) => {
            setSaving(true);
            try {
              await changePassword({
                currentPassword: String(values.currentPassword),
                newPassword: String(values.newPassword),
                confirmPassword: String(values.confirmPassword),
              });
              passwordForm.current?.reset();
              Toast.success("密码已修改");
            } catch (e) {
              Toast.error(e instanceof Error ? e.message : "修改失败");
            } finally {
              setSaving(false);
            }
          }}
        >
          <Form.Input
            field="currentPassword"
            label="当前密码"
            mode="password"
            rules={[{ required: true }]}
          />
          <Form.Input
            field="newPassword"
            label="新密码"
            mode="password"
            maxLength={64}
            rules={[{ required: true }, { min: 6, message: "密码至少6位" }]}
          />
          <Form.Input
            field="confirmPassword"
            label="确认新密码"
            mode="password"
            maxLength={64}
            rules={[{ required: true }]}
          />
          <Button htmlType="submit" theme="solid" loading={saving}>
            修改密码
          </Button>
        </Form>
      </Card>}
    </div>
  );
}
