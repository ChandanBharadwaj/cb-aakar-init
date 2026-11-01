#!/usr/bin/env python3
"""Photograph the Shop: build every Shop item's default piece and attach its render to the item.

Each build now carries a `thumb` (a PNG of the real model in the item's finish, rendered by the geometry service). For
every Shop item this builds the item as a customer would start it (`source: shop`), then replaces the item's `media` with
that thumbnail and the GLB, through the owner's catalogue endpoint, so the Shop cards and the item pages show the piece
rather than a stand-in. Re-run it after changing an item's template, parameters or finish. Needs `make api` and
`make geometry`. Standard library only. Usage: make shoot-catalogue  (AAKAR_ONLY=slug,slug to limit it)
"""
import json, os, sys, time, urllib.error, urllib.request, uuid

API = os.environ.get("AAKAR_API_URL", "http://localhost:8080")
GUEST = str(uuid.uuid4())
ONLY = {s.strip() for s in os.environ.get("AAKAR_ONLY", "").split(",") if s.strip()}
INPUT_FIELDS = ("slug", "name", "category", "family_id", "description", "template_id", "default_params", "default_material",
                "base_price_paise", "specs_line", "environment", "available")


def req(method, path, body=None, headers=None):
    h = {"X-Aakar-Guest": GUEST, **(headers or {})}
    data = None
    if body is not None:
        data = json.dumps(body).encode(); h["content-type"] = "application/json"
    r = urllib.request.Request(API + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=300) as resp:
            payload = resp.read()
            return resp.status, (json.loads(payload) if payload else None)
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"null")


def build(item):
    st, acc = req("POST", "/api/designs", {"source": "shop", "catalog_item_slug": item["slug"], "material": item["default_material"]})
    if st != 202:
        raise RuntimeError(f"design refused ({st}): {acc}")
    for _ in range(600):
        st, job = req("GET", f"/api/jobs/{acc['job_id']}")
        if job["status"] in ("succeeded", "failed"):
            break
        time.sleep(0.5)
    if job["status"] != "succeeded":
        raise RuntimeError(f"build {job['status']}: {job.get('error_code')} {job.get('error_message')}")
    st, d = req("GET", f"/api/designs/{acc['design_id']}")
    return d["latest_version"]["assets"]


st, staff = req("POST", "/admin/api/auth/login", {"email": os.environ.get("AAKAR_OWNER_EMAIL", "studio@aakar.local"),
                                                   "password": os.environ.get("AAKAR_OWNER_PASSWORD", "aakar-studio")})
if st != 200:
    sys.exit(f"owner sign-in failed ({st}): {staff}")
auth = {"Authorization": f"Bearer {staff['access_token']}"}
st, items = req("GET", "/admin/api/catalog/items", headers=auth)
failed = []
for item in items:
    if ONLY and item["slug"] not in ONLY:
        continue
    try:
        assets = build(item)
        thumb, glb = assets.get("thumb"), assets.get("glb")
        if not thumb:
            raise RuntimeError("the build carried no thumbnail: is the geometry service up to date?")
        media = [{"kind": "thumbnail", "url": thumb["url"]}]
        if glb:
            media.append({"kind": "glb", "url": glb["url"]})
        body = {k: item.get(k) for k in INPUT_FIELDS} | {"media": media}
        st, out = req("PUT", f"/admin/api/catalog/items/{item['slug']}", body, auth)
        if st != 200:
            raise RuntimeError(f"update refused ({st}): {out}")
        print(f"  ✓ {item['slug']}: {thumb['url']}")
    except Exception as exc:  # one item failing should not hide the rest
        failed.append(item["slug"])
        print(f"  ✗ {item['slug']}: {exc}")
if failed:
    sys.exit(f"not photographed: {', '.join(failed)}")
print("SHOP PHOTOGRAPHED")
