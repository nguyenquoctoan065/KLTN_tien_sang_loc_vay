import { useState } from 'react';
import { Card, Input, Select, Space, Table, Tag, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { adminApi } from '../../api/endpoints';
import { dt } from '../../utils/format';

/** Nhật ký hệ thống (kho Nhật ký D10) */
export default function Logs() {
  const [keyword, setKeyword] = useState('');
  const [actorType, setType] = useState<string>();
  const q = useQuery({ queryKey: ['admin-logs', keyword, actorType], queryFn: () => adminApi.logs({ keyword, actorType }) });
  return (
    <div>
      <Typography.Title level={3} style={{ marginTop: 0 }}>Nhật ký hệ thống</Typography.Title>
      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Input.Search allowClear placeholder="Người thực hiện, hành động, mã hồ sơ" style={{ width: 320 }} onSearch={setKeyword} />
          <Select allowClear placeholder="Loại người thực hiện" style={{ width: 200 }} value={actorType} onChange={setType}
            options={[{ value: 'CUSTOMER', label: 'Khách hàng' }, { value: 'STAFF', label: 'Nhân viên' }, { value: 'SYSTEM', label: 'Hệ thống' }]} />
        </Space>
        <Table size="small" rowKey="id" loading={q.isLoading} dataSource={q.data} pagination={{ pageSize: 20 }} scroll={{ x: 900 }} columns={[
          { title: 'Thời điểm', dataIndex: 'createdAt', render: dt, width: 150 },
          { title: 'Người thực hiện', dataIndex: 'actorName', render: (v, r) => <span>{v} <Tag>{{ CUSTOMER: 'Khách hàng', STAFF: 'Nhân viên', SYSTEM: 'Hệ thống' }[r.actorType]}</Tag></span> },
          { title: 'Hành động', dataIndex: 'action' },
          { title: 'Hồ sơ', dataIndex: 'applicationCode', render: v => v ?? '—' },
          { title: 'Chi tiết', dataIndex: 'detail' },
        ]} />
      </Card>
    </div>
  );
}
