"use client";

import Image from "next/image";
import { IconLink } from "@douyinfe/semi-icons";
import { useCallback, useEffect, useState } from "react";
import { Banner, Button, Card, Input, Select, Spin, Toast, Typography } from "@douyinfe/semi-ui";
import { ProfileShell } from "@/components/layout/ProfileShell";
import { apiClient } from "@/lib/api/client";

import { providers } from "./providers";

type Settings = { provider: string; baseUrl: string; model: string; keyConfigured: boolean };
const endpoint = "/management/system/ai";

const custom = "自定义模型";

function ProviderLabel({ item }: { item: (typeof providers)[number] }) {
  return <span className="inline-flex items-center gap-2 align-middle">
    {item.id === "custom"
      ? <IconLink aria-hidden="true" style={{ width: 20, color: "var(--semi-color-text-2)" }} />
      : <Image src={`/ai-providers/${item.id}.svg`} alt="" width={20} height={20} unoptimized
          className="h-5 w-5 shrink-0 object-contain" />}
    <span>{item.name}</span>
  </span>;
}

export default function AiSettingsPage() {
  const [saved, setSaved] = useState<Settings>();
  const [providerId, setProviderId] = useState("bailian");
  const [baseUrl, setBaseUrl] = useState(providers[0].url);
  const provider = providers.find(item => item.id === providerId) ?? providers[0];
  const models = provider.models;
  const [choice, setChoice] = useState("qwen-plus");
  const [customModel, setCustomModel] = useState("");
  const [key, setKey] = useState("");
  const [error, setError] = useState("");
  const [saving, setBusy] = useState(false);
  const [testing, setTesting] = useState(false);
  const busy = saving || testing;
  const load = useCallback(async () => {
    setError("");
    try {
      const settings = await apiClient.get<Settings>(endpoint);
      const models = providers.find(item => item.id === settings.provider)?.models ?? [];
      setProviderId(settings.provider);
      setBaseUrl(settings.baseUrl);
      setSaved(settings);
      setChoice(models.includes(settings.model) ? settings.model : custom);
      setCustomModel(models.includes(settings.model) ? "" : settings.model);
    } catch (e) {
      setError(e instanceof Error ? e.message : "读取配置失败");
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const model = choice === custom ? customModel.trim() : choice;
  const url = baseUrl.trim().replace(/\/+$/, "");
  const reuseKey = saved?.keyConfigured && saved.provider === providerId && saved.baseUrl === url;
  function changeProvider(id: string) {
    const next = providers.find(item => item.id === id);
    if (!next) return;
    const previous = saved?.provider === id ? saved : undefined;
    const model = previous?.model || next.models[0] || "";
    setProviderId(id);
    setBaseUrl(previous?.baseUrl ?? next.url);
    setChoice(next.models.includes(model) ? model : custom);
    setCustomModel(next.models.includes(model) ? "" : model);
    setKey("");
  }

  async function save() {
    if (!/^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$/.test(model)) {
      Toast.error("请填写有效的模型 ID");
      return;
    }
    if (!reuseKey && !key.trim()) {
      Toast.error("请填写此接口对应的 API Key");
      return;
    }
    setBusy(true);
    try {
      setSaved(await apiClient.put<Settings>(endpoint, { provider: providerId, baseUrl: url, model, apiKey: key.trim() || undefined }));
      setKey("");
      Toast.success("AI 配置已保存");
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setBusy(false);
    }
  }

  const canTest = reuseKey && model === saved?.model && !key;
  async function testConnection() {
    if (!canTest || busy) return;
    setTesting(true);
    try {
      const response = await apiClient.post<{ model: string; elapsedMs: number }>(
        endpoint + "/test-connection", undefined, { timeout: 80000 });
      Toast.success(`连接成功 · ${response.model} · ${(response.elapsedMs / 1000).toFixed(2)} 秒`);
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "测试连接失败");
    } finally {
      setTesting(false);
    }
  }

  return <ProfileShell><section className="space-y-5">
    <Typography.Title heading={2}>AI 配置</Typography.Title>
    <Card title="模型服务">
      {error ? <Banner type="danger" description={error} />
        : !saved ? <Spin /> : <form className="max-w-xl space-y-5"
          onSubmit={e => { e.preventDefault(); void save(); }}>
          <div className="space-y-2">
            <label htmlFor="ai-provider" className="block font-medium">服务商</label>
            <Select id="ai-provider" className="w-full" value={providerId} disabled={busy}
              renderSelectedItem={() => <ProviderLabel item={provider} />}
              onChange={value => changeProvider(String(value))}>
              {["国内", "海外", "其他"].map(group => <Select.OptGroup key={group} label={group}>
                {providers.filter(item => item.group === group).map(item =>
                  <Select.Option key={item.id} value={item.id}><ProviderLabel item={item} /></Select.Option>)}
              </Select.OptGroup>)}
            </Select>
          </div>
          <div className="space-y-2">
            <label htmlFor="ai-base-url" className="block font-medium">接口地址（Base URL）</label>
            <Input id="ai-base-url" value={baseUrl} maxLength={512} disabled={busy}
              placeholder="输入兼容 Chat Completions 的 HTTPS 接口前缀"
              onChange={value => { setBaseUrl(value); setKey(""); }} />
          </div>
          <div className="space-y-2">
            <label htmlFor="ai-model" className="block font-medium">模型</label>
            {models.length > 0 && <Select id="ai-model" className="w-full" value={choice} disabled={busy}
              optionList={[...models, custom].map(value => ({ value, label: value }))}
              onChange={value => setChoice(String(value))} />}
            {choice === custom && <Input id={models.length ? "ai-custom-model" : "ai-model"} aria-label="模型 ID" placeholder="输入服务商提供的模型 ID 或接入点 ID"
              value={customModel} maxLength={128} disabled={busy} onChange={setCustomModel} />}
          </div>
          <div className="space-y-2">
            <label htmlFor="ai-api-key" className="block font-medium">API Key</label>
            <Input id="ai-api-key" mode="password" autoComplete="new-password"
              placeholder={reuseKey ? "已配置，留空保留现有密钥" : "输入此接口的 API Key"}
              maxLength={768} value={key} disabled={busy} onChange={setKey} />
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Button htmlType="submit" theme="solid" loading={saving} disabled={testing}>保存配置</Button>
            <Button htmlType="button" onClick={testConnection} loading={testing} disabled={!canTest || saving}>测试连接</Button>
            {!canTest && <span className="text-sm text-slate-500">保存配置后可测试</span>}
          </div>
        </form>}
      {error && <Button onClick={load} className="mt-3">重新加载</Button>}
    </Card>
  </section></ProfileShell>;
}
