import { useEffect, useState } from 'react';
import { Alert, App, Button, Card, Checkbox, Form, Input, InputNumber, Modal, Select, Space, Table, Tabs, Tag, Typography } from 'antd';
import { PlusOutlined, SaveOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import type { CrossSellProduct, DocumentType, LoanProduct, ProductCode, ProductDocRule } from '../../api/types';
import { money, moneyFormatter, moneyParser } from '../../utils/format';

const ST = (v: string) => (v === 'ACTIVE' ? <Tag color="success">Đang áp dụng</Tag> : <Tag>Ngừng</Tag>);
const STATUS_OPTS = [{ value: 'ACTIVE', label: 'Đang áp dụng' }, { value: 'INACTIVE', label: 'Ngừng áp dụng' }];

/** 7.3 Cập nhật danh mục gói vay, giấy tờ và sản phẩm bán chéo (MB13) */
export default function Catalog() {
  const qc = useQueryClient();
  const { message } = App.useApp();
  const q = useQuery({ queryKey: ['admin-catalog'], queryFn: adminApi.catalog });
  const refresh = () => { qc.invalidateQueries({ queryKey: ['admin-catalog'] }); qc.invalidateQueries({ queryKey: ['products'] }); qc.invalidateQueries({ queryKey: ['cross-sells'] }); };
  const [form] = Form.useForm();
  const [edit, setEdit] = useState<{ kind: 'product' | 'doc' | 'cs'; row: LoanProduct | DocumentType | CrossSellProduct | null } | null>(null);
  const [rules, setRules] = useState<ProductDocRule[]>([]);
  useEffect(() => { if (q.data) setRules(q.data.productDocuments); }, [q.data]);

  const save = useMutation({
    mutationFn: async (v: Record<string, unknown>) => {
      if (!edit) return;
      if (edit.kind === 'product') return adminApi.updateProduct((edit.row as LoanProduct).id, v as Partial<LoanProduct>);
      if (edit.kind === 'doc') return edit.row ? adminApi.updateDocType((edit.row as DocumentType).id, v as Omit<DocumentType, 'id'>) : adminApi.createDocType(v as Omit<DocumentType, 'id'>);
      return edit.row ? adminApi.updateCrossSell((edit.row as CrossSellProduct).id, v as Omit<CrossSellProduct, 'id'>) : adminApi.createCrossSell(v as Omit<CrossSellProduct, 'id'>);
    },
    onSuccess: () => { refresh(); setEdit(null); message.success('Đã lưu danh mục'); },
  });
  const saveRules = useMutation({ mutationFn: () => adminApi.updateProductDocs(rules), onSuccess: () => { refresh(); message.success('Đã lưu giấy tờ theo gói vay'); }, onError: e => message.error(errorMessage(e)) });
  const open = (kind: 'product' | 'doc' | 'cs', row: LoanProduct | DocumentType | CrossSellProduct | null, dflt: Record<string, unknown> = {}) => {
    setEdit({ kind, row }); save.reset();
    form.resetFields();
    form.setFieldsValue(row ?? dflt);
  };
  const rule = (pc: ProductCode, docId: number) => rules.find(r => r.productCode === pc && r.documentTypeId === docId);
  const toggle = (pc: ProductCode, docId: number, on: boolean) => setRules(rs => on ? [...rs, { productCode: pc, documentTypeId: docId, required: true }] : rs.filter(r => !(r.productCode === pc && r.documentTypeId === docId)));
  const setReq = (pc: ProductCode, docId: number, req: boolean) => setRules(rs => rs.map(r => (r.productCode === pc && r.documentTypeId === docId ? { ...r, required: req } : r)));
  const dirtyRules = JSON.stringify(rules) !== JSON.stringify(q.data?.productDocuments ?? []);

  return (
    <div>
      <Typography.Title level={3} style={{ marginTop: 0 }}>Gói vay, giấy tờ và sản phẩm bán chéo (MB13)</Typography.Title>
      <Card>
        <Tabs items={[
          {
            key: 'p', label: 'Gói vay', children: (
              <Table rowKey="id" loading={q.isLoading} dataSource={q.data?.products} pagination={false} scroll={{ x: 800 }} columns={[
                { title: 'Mã', dataIndex: 'code' }, { title: 'Tên gói', dataIndex: 'name' },
                { title: 'Phương thức trả nợ', dataIndex: 'repaymentMethod' }, { title: 'Trạng thái', dataIndex: 'status', render: ST },
                { title: '', key: 'x', render: (_, r) => <Button size="small" onClick={() => open('product', r)}>Sửa</Button> },
              ]} footer={() => <Typography.Text type="secondary">Ngưỡng, trần hạn mức, kỳ hạn, lãi suất của từng gói sửa ở “Phiên bản tham số” (MB11).</Typography.Text>} />
            ),
          },
          {
            key: 'd', label: 'Loại giấy tờ', children: (
              <Table rowKey="id" loading={q.isLoading} dataSource={q.data?.documentTypes} pagination={false} scroll={{ x: 800 }}
                title={() => <Button icon={<PlusOutlined />} onClick={() => open('doc', null, { group: 'Pháp lý', allowedFormats: 'JPG,PNG,PDF', validityDays: 30, status: 'ACTIVE' })}>Thêm loại giấy tờ</Button>}
                columns={[
                  { title: 'Tên giấy tờ', dataIndex: 'name' }, { title: 'Nhóm', dataIndex: 'group' },
                  { title: 'Định dạng', dataIndex: 'allowedFormats' }, { title: 'Hiệu lực', dataIndex: 'validityDays', render: v => (v ? `${v} ngày` : 'Không giới hạn') },
                  { title: 'Trạng thái', dataIndex: 'status', render: ST },
                  { title: '', key: 'x', render: (_, r) => <Button size="small" onClick={() => open('doc', r)}>Sửa</Button> },
                ]} />
            ),
          },
          {
            key: 'r', label: 'Giấy tờ theo gói vay', children: (
              <div>
                <Table rowKey="id" size="small" pagination={false} dataSource={q.data?.documentTypes.filter(d => d.status === 'ACTIVE')} columns={[
                  { title: 'Giấy tờ', dataIndex: 'name' },
                  ...(['TD', 'TC'] as ProductCode[]).map(pc => ({
                    title: pc === 'TD' ? 'Vay tiêu dùng' : 'Vay thấu chi', key: pc, render: (_: unknown, d: DocumentType) => {
                      const r = rule(pc, d.id);
                      return (
                        <Space>
                          <Checkbox checked={!!r} disabled={d.id === 1} onChange={e => toggle(pc, d.id, e.target.checked)}>Áp dụng</Checkbox>
                          {r && <Checkbox checked={r.required} disabled={d.id === 1} onChange={e => setReq(pc, d.id, e.target.checked)}>Bắt buộc</Checkbox>}
                        </Space>
                      );
                    },
                  })),
                ]} />
                <Space style={{ marginTop: 12 }}>
                  <Button type="primary" icon={<SaveOutlined />} disabled={!dirtyRules} loading={saveRules.isPending} onClick={() => saveRules.mutate()}>Lưu</Button>
                  <Typography.Text type="secondary">Áp dụng cho hồ sơ tạo mới; hồ sơ đã tạo giữ danh mục cũ.</Typography.Text>
                </Space>
              </div>
            ),
          },
          {
            key: 'c', label: 'Sản phẩm bán chéo', children: (
              <Table rowKey="id" loading={q.isLoading} dataSource={q.data?.crossSells} pagination={false} scroll={{ x: 800 }}
                title={() => <Button icon={<PlusOutlined />} onClick={() => open('cs', null, { productType: 'INSURANCE', estCost: 0, scoreBonus: 2, rateDiscount: 0.3, status: 'ACTIVE' })}>Thêm sản phẩm</Button>}
                columns={[
                  { title: 'Tên sản phẩm', dataIndex: 'name' }, { title: 'Chi phí ước tính/năm', dataIndex: 'estCost', align: 'right', render: money },
                  { title: 'Điểm cộng', dataIndex: 'scoreBonus' }, { title: 'Giảm lãi suất', dataIndex: 'rateDiscount', render: v => `${v}%/năm` },
                  { title: 'Trạng thái', dataIndex: 'status', render: ST },
                  { title: '', key: 'x', render: (_, r) => <Button size="small" onClick={() => open('cs', r)}>Sửa</Button> },
                ]} />
            ),
          },
        ]} />
      </Card>
      <Modal open={!!edit} title={{ product: 'Sửa gói vay', doc: edit?.row ? 'Sửa loại giấy tờ' : 'Thêm loại giấy tờ', cs: edit?.row ? 'Sửa sản phẩm bán chéo' : 'Thêm sản phẩm bán chéo' }[edit?.kind ?? 'product']}
        okText="Lưu" cancelText="Hủy" onCancel={() => setEdit(null)} onOk={() => form.submit()} confirmLoading={save.isPending} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={save.mutate}>
          {edit?.kind === 'product' && (<>
            <Form.Item name="name" label="Tên gói" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="repaymentMethod" label="Phương thức trả nợ" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="description" label="Mô tả"><Input.TextArea rows={3} /></Form.Item>
            <Form.Item name="status" label="Trạng thái"><Select options={STATUS_OPTS} /></Form.Item>
          </>)}
          {edit?.kind === 'doc' && (<>
            <Form.Item name="name" label="Tên giấy tờ" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="group" label="Nhóm" rules={[{ required: true }]}><Select options={['Pháp lý', 'Thu nhập', 'Mục đích', 'Khác'].map(v => ({ value: v }))} /></Form.Item>
            <Form.Item name="allowedFormats" label="Định dạng cho phép (phân cách bằng dấu phẩy)" rules={[{ required: true }]}><Input placeholder="JPG,PNG,PDF" /></Form.Item>
            <Form.Item name="validityDays" label="Số ngày hiệu lực (để trống nếu không giới hạn)"><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="status" label="Trạng thái"><Select options={STATUS_OPTS} /></Form.Item>
          </>)}
          {edit?.kind === 'cs' && (<>
            <Form.Item name="name" label="Tên sản phẩm" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="productType" label="Loại" rules={[{ required: true }]}>
              <Select options={[['INSURANCE', 'Bảo hiểm'], ['CARD', 'Thẻ'], ['DEPOSIT', 'Tiền gửi'], ['ACCOUNT', 'Tài khoản'], ['FUND', 'Chứng chỉ quỹ']].map(([v, l]) => ({ value: v, label: l }))} />
            </Form.Item>
            <Form.Item name="estCost" label="Chi phí ước tính/năm"><InputNumber min={0} style={{ width: '100%' }} formatter={moneyFormatter} parser={moneyParser} addonAfter="đ" /></Form.Item>
            <Form.Item name="scoreBonus" label="Điểm cộng ưu tiên"><InputNumber min={0} max={20} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="rateDiscount" label="Giảm lãi suất tham chiếu"><InputNumber min={0} max={5} step={0.1} style={{ width: '100%' }} addonAfter="%/năm" /></Form.Item>
            <Form.Item name="status" label="Trạng thái"><Select options={STATUS_OPTS} /></Form.Item>
          </>)}
          {save.isError && <Alert type="error" message={errorMessage(save.error)} />}
        </Form>
      </Modal>
    </div>
  );
}
