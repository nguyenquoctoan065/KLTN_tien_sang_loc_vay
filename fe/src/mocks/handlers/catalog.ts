import { http, HttpResponse } from 'msw';
import type { ProductCode } from '../../api/types';
import { API, db } from '../ctx';
import { SYSTEM_DOC_TYPE } from '../db';

export function checklist(code: ProductCode) {
  return db.productDocs.filter(d => d.productCode === code).map(d => {
    const t = db.docTypes.find(x => x.id === d.documentTypeId)!;
    return { name: t.name + (t.id === SYSTEM_DOC_TYPE ? ' – hệ thống tự lập' : ''), group: t.group, required: d.required };
  });
}

export const catalogHandlers = [
  http.get(`${API}/products`, () => HttpResponse.json(db.products.filter(p => p.status === 'ACTIVE'))),
  http.get(`${API}/cross-sell-products`, () => HttpResponse.json(db.crossSells.filter(c => c.status === 'ACTIVE'))),
  http.get(`${API}/products/:code/checklist`, ({ params }) => HttpResponse.json(checklist(params.code as ProductCode))),
  http.get(`${API}/branches`, () => HttpResponse.json(db.branches.filter(b => b.status === 'ACTIVE'))),
];
