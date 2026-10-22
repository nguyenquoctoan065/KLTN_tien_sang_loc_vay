import { useEffect, useState } from 'react';
import { Alert, App, Button, Card, DatePicker, Image, Input, Space, Table, Tag, Typography, Upload } from 'antd';
import { UploadOutlined, CheckOutlined, FileTextOutlined, FilePdfOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import { appApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import type { Application, AppDocument } from '../../api/types';
import { dt } from '../../utils/format';

export const DOC_STATUS = {
  MISSING: { label: 'Chưa tải', color: 'default' },
  UPLOADED: { label: 'Chờ xác nhận thông tin', color: 'processing' },
  CONFIRMED: { label: 'Đã xác nhận', color: 'success' },
  REJECTED_QUALITY: { label: 'Không đạt chất lượng', color: 'error' },
} as const;

export const VERIFY_TAG = {
  MATCH: { label: 'Khớp bản gốc', color: 'success' },
  MISMATCH: { label: 'Không khớp', color: 'error' },
  MISSING: { label: 'Thiếu bản gốc', color: 'error' },
} as const;

/** Một giấy tờ của MB05: tải lên, kiểm tra chất lượng, xác nhận thông tin trích xuất (MB06) */
export default function DocumentCard({ app, doc, editable }: { app: Application; doc: AppDocument; editable: boolean }) {
  const qc = useQueryClient();
  const { message } = App.useApp();
  const [issued, setIssued] = useState<Dayjs | null>(null);
  const [vals, setVals] = useState<Record<number, string>>({});
  useEffect(() => { setVals(Object.fromEntries(doc.fields.map(f => [f.id, f.confirmedValue ?? f.ocrValue]))); }, [doc.fields]);
  const set = (a: Application) => qc.setQueryData(['application', app.id], a);
  const up = useMutation({
    mutationFn: (f: File) => appApi.upload(app.id, doc.id, f, issued?.format('YYYY-MM-DD')),
    onSuccess: a => {
      set(a);
      const d = a.documents.find(x => x.id === doc.id);
      if (d?.status === 'REJECTED_QUALITY') message.warning(d.qualityMessage ?? 'Giấy tờ không đạt chất lượng');
      else message.success('Đã tải lên và trích xuất thông tin, vui lòng kiểm tra và xác nhận');
    },
    onError: e => message.error(errorMessage(e)),
  });
  const confirm = useMutation({
    mutationFn: () => appApi.confirmFields(app.id, doc.id, doc.fields.map(f => ({ id: f.id, confirmedValue: vals[f.id] ?? f.ocrValue }))),
    onSuccess: a => { set(a); message.success('Đã xác nhận thông tin giấy tờ'); },
    onError: e => message.error(errorMessage(e)),
  });
  const system = doc.documentTypeId === 1;
  const st = DOC_STATUS[doc.status];
  const accept = doc.allowedFormats.split(',').map(x => (x === 'JPG' ? '.jpg,.jpeg' : `.${x.toLowerCase()}`)).join(',');
  const canUpload = editable && !system;
  const needReupload = doc.verifyResult === 'MISMATCH' || doc.verifyResult === 'MISSING';

  return (
    <Card size="small" className={needReupload ? 'doc-card doc-bad' : 'doc-card'}
      title={<Space wrap><FileTextOutlined />{doc.typeName}{doc.required ? <Tag color="red">Bắt buộc</Tag> : <Tag>Nếu có</Tag>}<Tag>{doc.group}</Tag></Space>}
      extra={system ? <Tag color={doc.status === 'CONFIRMED' ? 'success' : 'default'}>{doc.status === 'CONFIRMED' ? 'Đã lập' : 'Hệ thống tự lập khi nộp'}</Tag>
        : <Space>{doc.verifyResult && <Tag color={VERIFY_TAG[doc.verifyResult].color}>{VERIFY_TAG[doc.verifyResult].label}</Tag>}<Tag color={st.color}>{st.label}</Tag></Space>}>
      {system ? (
        <Typography.Text type="secondary">Giấy đề nghị vay vốn được hệ thống điền sẵn từ thông tin hồ sơ ở bước “Giấy đề nghị”. Bạn ký tươi bản in tại quầy.</Typography.Text>
      ) : (
        <>
          {needReupload && <Alert style={{ marginBottom: 8 }} type="error" showIcon message={`Chi nhánh ghi nhận: ${doc.verifyReason}`} description="Tải lại bản đúng của giấy tờ này." />}
          <Space wrap align="start" style={{ width: '100%', justifyContent: 'space-between' }}>
            <Space align="start">
              {doc.previewUrl ? <Image src={doc.previewUrl} width={84} height={64} style={{ objectFit: 'cover', borderRadius: 6 }} />
                : doc.fileName ? <div className="file-thumb"><FilePdfOutlined /></div> : null}
              <div>
                {doc.fileName ? (
                  <>
                    <div>{doc.fileName}</div>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>{doc.fileFormat} · {doc.fileSize} KB · phiên bản {doc.versionNo} · {dt(doc.uploadedAt)}</Typography.Text>
                    {doc.issuedDate && <div><Typography.Text type="secondary" style={{ fontSize: 12 }}>Ngày phát hành: {doc.issuedDate.split('-').reverse().join('/')}</Typography.Text></div>}
                  </>
                ) : <Typography.Text type="secondary">Định dạng cho phép: {doc.allowedFormats}; tối đa 10 MB; ảnh chụp rõ nét, đủ bốn góc.</Typography.Text>}
              </div>
            </Space>
            {canUpload && (
              <Space wrap>
                <DatePicker size="middle" placeholder="Ngày phát hành" format="DD/MM/YYYY" value={issued} onChange={setIssued} />
                <Upload accept={accept} showUploadList={false} beforeUpload={f => { up.mutate(f); return false; }}>
                  <Button icon={<UploadOutlined />} loading={up.isPending}>{doc.fileName ? 'Tải lại' : 'Tải lên'}</Button>
                </Upload>
              </Space>
            )}
          </Space>
          {doc.status === 'REJECTED_QUALITY' && <Alert style={{ marginTop: 8 }} type="error" showIcon message={doc.qualityMessage} />}
          {doc.fields.length > 0 && (
            <div style={{ marginTop: 12 }}>
              <Typography.Text strong>Thông tin đọc từ giấy tờ</Typography.Text>
              <Table size="small" rowKey="id" pagination={false} dataSource={doc.fields} style={{ marginTop: 6 }}
                columns={[
                  { title: 'Trường', dataIndex: 'label', width: '28%' },
                  { title: 'Máy đọc', dataIndex: 'ocrValue', render: (v: string, f) => <span>{v || '—'} <Tag color={f.confidence >= 90 ? 'green' : f.confidence >= 85 ? 'gold' : 'orange'}>{f.confidence}%</Tag></span> },
                  {
                    title: 'Giá trị xác nhận', key: 'c', width: '38%', render: (_, f) => editable && doc.status !== 'MISSING'
                      ? <Input.TextArea autoSize={{ minRows: 1, maxRows: 8 }} value={vals[f.id] ?? ''} onChange={e => setVals(v => ({ ...v, [f.id]: e.target.value }))} />
                      : (f.confirmedValue ?? '—'),
                  },
                ]} />
              {editable && doc.status === 'UPLOADED' && (
                <Button type="primary" icon={<CheckOutlined />} style={{ marginTop: 8 }} loading={confirm.isPending} onClick={() => confirm.mutate()}>
                  Xác nhận thông tin đúng
                </Button>
              )}
              {editable && doc.status === 'CONFIRMED' && (
                <Button size="small" style={{ marginTop: 8 }} loading={confirm.isPending} onClick={() => confirm.mutate()}>Lưu chỉnh sửa</Button>
              )}
            </div>
          )}
        </>
      )}
    </Card>
  );
}
