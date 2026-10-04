#!/usr/bin/env python3
"""Live smoke of the Avatar order path against running services (no mocks).

Needs the API (`make api`, direct profile, Postgres) and the geometry service (`make geometry`) running.
Uploads a generated photo and model, designs a Saathi keychain with a photo relief (Chhavi) and a Swaroop raw
print, checks hardware, minimum and setup price lines, orders three keychains with the mock OTP and payment,
and reads the studio print pack's packing list. Standard library only. Usage: make smoke-avatars
"""
import io, json, os, struct, sys, time, urllib.error, urllib.request, uuid, zipfile, zlib

API = os.environ.get("AAKAR_API_URL", "http://localhost:8080")
GUEST = str(uuid.uuid4())


def png_bytes(w=160, h=120):
    """A grey ramp with a bright disc: enough contrast for a relief."""
    rows = []
    for y in range(h):
        row = bytearray([0])
        for x in range(w):
            v = 255 if (x - w // 2) ** 2 + (y - h // 2) ** 2 < (h // 4) ** 2 else int(x / (w - 1) * 200)
            row.append(v)
        rows.append(bytes(row))
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 0, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(b"".join(rows))) + chunk(b"IEND", b"")


def box_stl(x=30.0, y=20.0, z=12.0):
    """An ASCII STL box with outward normals."""
    v = [(0, 0, 0), (x, 0, 0), (x, y, 0), (0, y, 0), (0, 0, z), (x, 0, z), (x, y, z), (0, y, z)]
    faces = [(0, 2, 1), (0, 3, 2), (4, 5, 6), (4, 6, 7), (0, 1, 5), (0, 5, 4), (1, 2, 6), (1, 6, 5), (2, 3, 7), (2, 7, 6), (3, 0, 4), (3, 4, 7)]
    out = ["solid box"]
    for a, b, c in faces:
        out += ["facet normal 0 0 0", " outer loop"] + [f"  vertex {v[i][0]} {v[i][1]} {v[i][2]}" for i in (a, b, c)] + [" endloop", "endfacet"]
    return ("\n".join(out + ["endsolid box"]) + "\n").encode()

def req(method, path, body=None, headers=None, raw=False):
    h = {"X-Aakar-Guest": GUEST, **(headers or {})}
    data = None
    if isinstance(body, (dict, list)):
        data = json.dumps(body).encode(); h["content-type"] = "application/json"
    elif isinstance(body, tuple):  # (content_type, bytes)
        h["content-type"], data = body
    r = urllib.request.Request(API + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=180) as resp:
            payload = resp.read()
            return resp.status, (payload if raw else (json.loads(payload) if payload else None))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"null")

def multipart(fields, file_field, filename, content, ctype):
    b = "----aakar" + uuid.uuid4().hex
    out = io.BytesIO()
    for k, v in fields.items():
        out.write(f'--{b}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
    out.write(f'--{b}\r\nContent-Disposition: form-data; name="{file_field}"; filename="{filename}"\r\nContent-Type: {ctype}\r\n\r\n'.encode())
    out.write(content); out.write(f"\r\n--{b}--\r\n".encode())
    return (f"multipart/form-data; boundary={b}", out.getvalue())

def check(cond, msg):
    print(("  ✓ " if cond else "  ✗ ") + msg)
    if not cond: sys.exit(1)

def wait_job(job_id):
    for _ in range(240):
        st, job = req("GET", f"/api/jobs/{job_id}")
        if job["status"] in ("succeeded", "failed"): return job
        time.sleep(0.5)
    raise SystemExit("job timed out")

def design(body, label):
    st, acc = req("POST", "/api/designs", body)
    check(st == 202, f"{label}: design accepted ({st} {acc if st != 202 else ''})")
    job = wait_job(acc["job_id"])
    check(job["status"] == "succeeded", f"{label}: geometry job {job['status']} {job.get('error_code') or ''}")
    st, d = req("GET", f"/api/designs/{acc['design_id']}")
    return d["latest_version"]

print("1 · Saathi keychain with a photo relief (Chhavi)")
st, up = req("POST", "/api/uploads", multipart({"kind": "image"}, "file", "photo.png", png_bytes(), "image/png"))
check(st == 201 and up["status"] == "ready", f"photo upload {st} {up.get('status')}")
v = design({"source": "create", "family_id": "keychain", "material": "indigo_matte",
            "features": [{"type": "relief_image", "anchor": "face", "source": {"upload_id": up["id"]}}]}, "keychain")
check(v["printability"]["passed"], f"printability passed; bounds {v['printability']['geometry']['bounds_mm']}")
check(any(h.get("name") == "Steel split ring 25 mm" for h in v.get("hardware", [])), f"hardware named: {v.get('hardware')}")
st, price = req("GET", f"/api/versions/{v['id']}/price?material=indigo_matte")
codes = [l["code"] for l in price["lines"]]
check("hardware" in codes, f"price lines {codes}, subtotal ₹{price['subtotal_paise']/100:.0f}, min {price.get('minimum_subtotal_paise')}")
check(price["subtotal_paise"] >= 24900, "keychain minimum ₹249 honoured")
keychain_version = v

print("2 · Swaroop: print my own model as it is")
st, mu = req("POST", "/api/uploads", multipart({"kind": "model"}, "file", "box.stl", box_stl(), "model/stl"))
check(st == 201 and mu["status"] == "ready", f"model upload {st} {mu.get('status')}")
v = design({"source": "upload", "family_id": "raw_print", "material": "basic_white", "params": {},
            "features": [{"type": "hero_mesh", "anchor": "body", "fit": "longest", "longest_mm": 60, "orientation": "as_uploaded", "source": {"upload_id": mu["id"]}}]}, "raw print")
b = v["printability"]["geometry"]["bounds_mm"]
check(abs(max(b) - 60) < 0.01, f"longest side 60 mm: {b}")
st, price = req("GET", f"/api/versions/{v['id']}/price?material=basic_white")
check("setup" in [l["code"] for l in price["lines"]], f"setup line present: {[(l['code'], l['amount_paise']) for l in price['lines']]}")
st, bad = req("POST", "/api/designs", {"source": "upload", "family_id": "raw_print", "material": "basic_white", "params": {},
            "features": [{"type": "hero_mesh", "anchor": "body", "fit": "longest", "longest_mm": 10, "source": {"upload_id": mu["id"]}}]})
check(st == 422, f"10 mm raw print refused ({st} {bad.get('code')}: {bad.get('detail')})")

print("3 · cart → sign in → checkout → mock pay")
st, cart = req("POST", "/api/cart/items", {"version_id": keychain_version["id"], "material": "indigo_matte", "qty": 3})
check(st in (200, 201), f"3 keychains in the cart ({st})")
st, otp = req("POST", "/api/auth/otp/request", {"phone": "+919876543210"})
st, sess = req("POST", "/api/auth/otp/verify", {"request_id": otp["request_id"], "code": otp.get("dev_code", "123456")})
check(st == 200, f"signed in ({st})")
auth = {"Authorization": f"Bearer {sess['access_token']}"}
st, addr = req("POST", "/api/me/addresses", {"name": "Asha", "phone": "+919876543210", "line1": "12 MG Road", "city": "Bengaluru", "state": "Karnataka", "pincode": "560001"}, auth)
check(st in (200, 201), f"address saved ({st})")
st, co = req("POST", "/api/checkout", {"address_id": addr["id"]}, auth)
check(st == 201, f"order {co.get('order_number') if isinstance(co, dict) else co} created ({st})")
st, paid = req("POST", f"/api/payments/{co['payment']['id']}/mock/complete", {"outcome": "success"}, auth)
check(st in (200, 201), f"mock payment success ({st})")

print("4 · studio print pack")
st, staff = req("POST", "/admin/api/auth/login", {"email": "studio@aakar.local", "password": "aakar-studio"})
check(st == 200, f"staff signed in ({st})")
st, zbytes = req("GET", f"/admin/api/orders/{co['order_id'] if 'order_id' in co else co['order']['id']}/print-pack", headers={"Authorization": f"Bearer {staff['access_token']}"}, raw=True)
check(st == 200, f"print pack downloaded ({st}, {len(zbytes)} bytes)")
z = zipfile.ZipFile(io.BytesIO(zbytes))
names = z.namelist()
check("packing-list.txt" in names, f"zip entries: {names}")
print("  packing-list.txt:\n    " + z.read("packing-list.txt").decode().strip().replace("\n", "\n    "))
sheet = next(n for n in names if n.endswith("print-sheet.txt"))
print("  " + sheet + " (excerpt):\n    " + "\n    ".join(l for l in z.read(sheet).decode().splitlines() if l.split(":")[0].strip() in ("Template", "Hardware", "Content", "Quantity", "Qty", "Filament")))
print("SMOKE OK")
