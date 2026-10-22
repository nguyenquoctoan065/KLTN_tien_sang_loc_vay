import { useState } from 'react';
import { Alert, App, Button, InputNumber, Space, Typography } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { appApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import type { Application } from '../../api/types';
import { moneyFormatter, moneyParser } from '../../utils/format';

/** Điều chỉnh phương án ngay trong hồ sơ khi thu nhập kiểm chứng làm phương án không còn đạt (bước 30) */
export default function PlanAdjust({ app }: { app: Application }) {
  const r = app.recalc!;
  const [amount, setAmount] = useState(Math.min(app.plan.amount, r.maxSafeAmount));
  const [term, setTerm] = useState(app.plan.termMonths);
  const qc = useQueryClient();
  const { message } = App.useApp();
  const m = useMutation({
    mutationFn: () => appApi.updatePlan(app.id, { amount, termMonths: term, crossSellIds: app.plan.crossSells.map(c => c.id) }),
    onSuccess: a => { qc.setQueryData(['application', app.id], a); message.success('Đã cập nhật phương án trong hồ sơ'); },
    onError: e => message.error(errorMessage(e)),
  });
  return (
    <Alert style={{ marginTop: 12 }} type="warning" showIcon message="Điều chỉnh phương án"
      description={
        <div>
          <Typography.Paragraph style={{ marginBottom: 8 }}>{r.issues.join(' ')}</Typography.Paragraph>
          <Space wrap>
            <InputNumber value={amount} min={10_000_000} step={1_000_000} formatter={moneyFormatter} parser={moneyParser} addonBefore="Số tiền" addonAfter="đ" onChange={v => v && setAmount(v)} style={{ width: 300 }} />
            {app.plan.productCode === 'TD' && <InputNumber value={term} min={6} max={60} addonBefore="Kỳ hạn" addonAfter="tháng" onChange={v => v && setTerm(v)} />}
            <Button type="primary" loading={m.isPending} onClick={() => m.mutate()}>Cập nhật phương án</Button>
          </Space>
        </div>
      } />
  );
}
