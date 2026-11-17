import { IconDownload, IconUpload } from "@douyinfe/semi-icons";
import { Button, Modal, Toast, Typography, Upload } from "@douyinfe/semi-ui";
import { useRef, useState } from "react";
import { downloadEmployeeTemplate, importEmployees } from "./api";

export function EmployeeImport({ onImported }: { onImported: () => Promise<void> }) {
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File>();
  const [saving, setSaving] = useState(false);
  const [imported, setImported] = useState<number>();
  const [uploadVersion, setUploadVersion] = useState(0);
  const busy = useRef(false);

  async function submit() {
    if (!file || busy.current) return;
    busy.current = true;
    setSaving(true);
    try {
      const count = await importEmployees(file);
      Toast.success(`已导入 ${count} 名学员`);
      setImported(count);
      setUploadVersion(version => version + 1);
      setFile(undefined);
      await onImported();
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "导入失败，请重试");
    } finally {
      busy.current = false;
      setSaving(false);
    }
  }

  return <>
    <Button onClick={() => { setFile(undefined); setImported(undefined); setOpen(true); }}>导入学员</Button>
    <Modal title="学员导入" visible={open} width="min(560px, calc(100vw - 24px))"
      bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }}
      okText="确定导入" cancelText={imported !== undefined ? "关闭" : "取消"}
      okButtonProps={{ disabled: !file || saving }} cancelButtonProps={{ disabled: saving }}
      closable={!saving} maskClosable={!saving} confirmLoading={saving}
      onOk={submit} onCancel={() => { if (!busy.current) setOpen(false); }}>
      <div className="flex flex-col gap-5 py-2">
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 p-4">
          <div className="min-w-0">
            <Typography.Text strong>1. 下载并填写模板</Typography.Text>
            <div className="mt-1 text-xs leading-5 text-slate-500">请替换或删除模板中的两行示例。姓名、手机号和部门必填。部门名称可手动填写，新部门会随导入自动创建。</div>
          </div>
          <Button icon={<IconDownload />} disabled={saving}
            onClick={() => downloadEmployeeTemplate().catch(e => Toast.error(e instanceof Error ? e.message : "模板下载失败"))}>下载模板</Button>
        </div>
        <div className="min-w-0">
          <div className="mb-3"><Typography.Text strong>2. 上传学员文件</Typography.Text></div>
          <Upload key={`${open}-${uploadVersion}`} style={{ width: "100%" }} disabled={saving}
            accept=".xlsx" limit={1} action="" uploadTrigger="custom" draggable
            dragIcon={<IconUpload size="extra-large" />} dragMainText="点击选择文件，或拖拽文件到此处"
            dragSubText="仅支持 .xlsx 格式，每次导入一个文件"
            onAcceptInvalid={() => Toast.error("请选择 .xlsx 格式的文件")}
            onExceed={() => Toast.warning("每次只能导入一个文件，请先移除已选文件")}
            onChange={({ fileList }) => { setFile(fileList[0]?.fileInstance); if (fileList.length) setImported(undefined); }}
            onRemove={() => setFile(undefined)} />
          <div className="mt-3 text-xs leading-5 text-slate-500">手机号重复或填写有误时，整批导入会取消。请按提示修正后重新上传。</div>
        </div>
      </div>
    </Modal>
  </>;
}
