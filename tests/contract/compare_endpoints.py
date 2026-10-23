"""So danh sách path + method giữa backend (springdoc /v3/api-docs) và hợp đồng docs/openapi.yaml.

Cài đặt: pip install pyyaml
Chạy (backend đang chạy ở cổng 8080):
  python tests\\contract\\compare_endpoints.py
  python tests\\contract\\compare_endpoints.py --url http://localhost:8080/api/v3/api-docs --spec docs/openapi.yaml
Tên biến đường dẫn không so ({id} và {appId} coi như nhau). Endpoint hạ tầng (actuator, api-docs, swagger) bỏ qua.
/files/local và /files/cloud chỉ có một cái tùy app.storage.provider (local | cloudinary): thiếu cái còn lại không tính lỗi.
Thoát mã 1 nếu thiếu hoặc thừa."""
import argparse, json, os, re, sys, urllib.request
import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
METHODS = {'get', 'post', 'put', 'patch', 'delete'}
IGNORE = re.compile(r'^/(actuator|v3/api-docs|swagger-ui)(/|$)')
# Controller có @ConditionalOnProperty(app.storage.provider): mỗi lần chạy backend chỉ có đúng một trong hai
STORAGE_FILES = {('GET', '/files/local'), ('GET', '/files/cloud')}

ap = argparse.ArgumentParser(description='So endpoint springdoc với docs/openapi.yaml')
ap.add_argument('--url', default=os.environ.get('API_DOCS_URL', 'http://localhost:8080/api/v3/api-docs'))
ap.add_argument('--spec', default=os.path.join(ROOT, 'docs', 'openapi.yaml'))
args = ap.parse_args()


def endpoints(doc):
    """Tập (METHOD, path) với biến đường dẫn chuẩn hóa thành {}."""
    out = {}
    for path, item in (doc.get('paths') or {}).items():
        if IGNORE.match(path):
            continue
        norm = re.sub(r'\{[^}]+\}', '{}', path.rstrip('/') or '/')
        for m in item:
            if m.lower() in METHODS:
                out[(m.upper(), norm)] = path
    return out


try:
    with urllib.request.urlopen(args.url, timeout=10) as r:
        actual = endpoints(json.loads(r.read().decode('utf-8')))
except Exception as e:
    sys.exit(f'Không tải được {args.url} ({e}). Backend đã chạy chưa?')
with open(args.spec, encoding='utf-8') as f:
    expected = endpoints(yaml.safe_load(f))

missing = expected.keys() - actual.keys()
if len(STORAGE_FILES & actual.keys()) == 1:
    skipped = STORAGE_FILES & missing
    missing -= skipped
    for m, p in skipped:
        print(f'(bỏ qua {m} {p}: backend đang chạy kho lưu trữ khác)')
missing = sorted(missing, key=lambda k: (k[1], k[0]))
extra = sorted(actual.keys() - expected.keys(), key=lambda k: (k[1], k[0]))
print(f'openapi.yaml: {len(expected)} endpoint · backend: {len(actual)} endpoint')
if missing:
    print('\nThiếu trong backend (có trong openapi.yaml):')
    for m, p in missing:
        print(f'  {m:6} {expected[(m, p)]}')
if extra:
    print('\nThừa ở backend (không có trong openapi.yaml):')
    for m, p in extra:
        print(f'  {m:6} {actual[(m, p)]}')
if not missing and not extra:
    print('Khớp: không thiếu, không thừa.')
sys.exit(1 if missing or extra else 0)
