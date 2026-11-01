#!/usr/bin/env python3
"""Live smoke of the second catalogue wave against running services (no mocks).

Needs the API (`make api`, direct profile, Postgres) and the geometry service (`make geometry`, ideally with
AAKAR_SLICER_BIN pointing at PrusaSlicer) running. For every family of the wave it designs the default piece with a
name or a motif on its first anchor (a photo for the cookie cutter), and checks that it builds, passes the printability
checks, carries a print estimate and prices at or above its minimum. It then orders two of them and a Shop piece with
the mock OTP and payment, downloads the studio print pack and, when AAKAR_SLICER_BIN is set, slices the packed model to
G-code: a file a printer can run. Standard library only. Usage: make smoke-catalogue
"""
import io, json, os, subprocess, sys, tempfile, time, urllib.error, urllib.request, uuid, zipfile
from pathlib import Path

API = os.environ.get("AAKAR_API_URL", "http://localhost:8080")
GUEST = str(uuid.uuid4())
SLICER = os.environ.get("AAKAR_SLICER_BIN", "").strip()

WAVE = ["pendant", "cake_topper", "cookie_cutter", "coaster", "pin", "bookmark", "planter", "bookend", "table_lamp",
        "diya_holder", "pooja_accessory", "wall_hook", "desk_organizer", "car_accessory"]


def png_bytes(w=160, h=120):
    import struct, zlib
    rows = []
    for y in range(h):
        row = bytearray([0])
        for x in range(w):
            row.append(255 if (x - w // 2) ** 2 + (y - h // 2) ** 2 < (h // 4) ** 2 else int(x / (w - 1) * 200))
        rows.append(bytes(row))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 0, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(b"".join(rows))) + chunk(b"IEND", b"")


def req(method, path, body=None, headers=None, raw=False):
    h = {"X-Aakar-Guest": GUEST, **(headers or {})}
    data = None
    if isinstance(body, (dict, list)):
        data = json.dumps(body).encode(); h["content-type"] = "application/json"
    elif isinstance(body, tuple):
        h["content-type"], data = body
    r = urllib.request.Request(API + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=300) as resp:
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
    if not cond:
        sys.exit(1)


def wait_job(job_id):
    for _ in range(600):
        st, job = req("GET", f"/api/jobs/{job_id}")
        if job["status"] in ("succeeded", "failed"):
            return job
        time.sleep(0.5)
    raise SystemExit("job timed out")


def design(body, label):
    st, acc = req("POST", "/api/designs", body)
    check(st == 202, f"{label}: design accepted ({st} {acc if st != 202 else ''})")
    job = wait_job(acc["job_id"])
    check(job["status"] == "succeeded", f"{label}: geometry job {job['status']} {job.get('error_code') or ''} {job.get('error_message') or ''}")
    st, d = req("GET", f"/api/designs/{acc['design_id']}")
    return d["latest_version"]


st, materials = req("GET", "/api/catalog/materials")
heat_safe = [m["id"] for m in materials if m.get("heat_safe") and m.get("available", True)]
st, photo = req("POST", "/api/uploads", multipart({"kind": "image"}, "file", "photo.png", png_bytes(), "image/png"))
check(st == 201 and photo["status"] == "ready", f"photo upload {st}")

print("1 · every family of the wave builds, passes printability and prices")
versions = {}
for fid in WAVE:
    st, fam = req("GET", f"/api/families/{fid}")
    check(st == 200 and fam["available"] and fam.get("ready", True), f"{fid}: listed and ready ({st})")
    tpl = fam["templates"][0]
    anchor = tpl["anchors"][0]
    accepts = anchor.get("accepts") or tpl["features_supported"]
    depth = min(1.0, anchor.get("max_relief_mm") or 1.0)  # the storefront keeps depths within the spot's cap too
    if "emboss_text" in accepts:
        feature = {"type": "emboss_text", "anchor": anchor["id"], "text": "Asha"[: (fam["content_slot"].get("max_text_chars") or 4)], "depth_mm": depth}
    elif "motif" in accepts:
        feature = {"type": "motif", "anchor": anchor["id"], "motif_id": "lotus", "mode": "emboss", "depth_mm": min(0.8, depth)}
    else:
        feature = {"type": "relief_image", "anchor": anchor["id"], "mode": "deboss", "relief_mm": 0.8, "source": {"upload_id": photo["id"]}}
    material = heat_safe[0] if fam["material_rules"].get("heat_safe_only") else "basic_white"
    t0 = time.time()
    v = design({"source": "create", "family_id": fid, "material": material, "features": [feature]}, fid)
    est = v.get("print_estimate") or {}
    check(v["printability"]["passed"], f"{fid}: printable, bounds {v['printability']['geometry']['bounds_mm']}, "
          f"{est.get('method')} {round(est.get('print_seconds', 0) / 60)} min, built in {time.time() - t0:.0f}s")
    st, price = req("GET", f"/api/versions/{v['id']}/price?material={material}")
    floor = fam.get("price_from_paise") or 0
    check(st == 200 and price["subtotal_paise"] >= floor, f"{fid}: ₹{price['subtotal_paise'] / 100:.0f} (minimum ₹{floor / 100:.0f})")
    versions[fid] = (v, material)
if SLICER:
    methods = {fid: (v.get("print_estimate") or {}).get("method") for fid, (v, _) in versions.items()}
    check(all(m == "prusaslicer_cli" for m in methods.values()), f"estimates came from the real slicer: {set(methods.values())}")

print("2 · a curated Shop piece starts and builds")
st, item = req("GET", "/api/catalog/items/ajrakh-coasters")
check(st == 200 and item["available"], f"ajrakh-coasters on sale ({st})")
shop_v = design({"source": "shop", "catalog_item_slug": "ajrakh-coasters", "material": item["default_material"]}, "ajrakh-coasters")
check(shop_v["printability"]["passed"], "shop coaster printable")

print("3 · cart → sign in → checkout → mock pay")
for fid in ("planter", "pin"):
    v, material = versions[fid]
    st, _ = req("POST", "/api/cart/items", {"version_id": v["id"], "material": material, "qty": 1})
    check(st in (200, 201), f"{fid} in the cart ({st})")
st, _ = req("POST", "/api/cart/items", {"version_id": shop_v["id"], "material": item["default_material"], "qty": 4})
check(st in (200, 201), f"four coasters in the cart ({st})")
st, otp = req("POST", "/api/auth/otp/request", {"phone": "+919876543211"})
st, sess = req("POST", "/api/auth/otp/verify", {"request_id": otp["request_id"], "code": otp.get("dev_code", "123456")})
check(st == 200, f"signed in ({st})")
auth = {"Authorization": f"Bearer {sess['access_token']}"}
st, addr = req("POST", "/api/me/addresses", {"name": "Asha", "phone": "+919876543211", "line1": "12 MG Road", "city": "Bengaluru", "state": "Karnataka", "pincode": "560001"}, auth)
check(st in (200, 201), f"address saved ({st})")
st, co = req("POST", "/api/checkout", {"address_id": addr["id"]}, auth)
check(st == 201, f"order {co.get('order_number')} created ({st})")
st, _ = req("POST", f"/api/payments/{co['payment']['id']}/mock/complete", {"outcome": "success"}, auth)
check(st in (200, 201), f"mock payment success ({st})")

print("4 · studio print pack → G-code")
st, staff = req("POST", "/admin/api/auth/login", {"email": "studio@aakar.local", "password": "aakar-studio"})
check(st == 200, f"staff signed in ({st})")
order_id = co["order_id"] if "order_id" in co else co["order"]["id"]
st, zbytes = req("GET", f"/admin/api/orders/{order_id}/print-pack", headers={"Authorization": f"Bearer {staff['access_token']}"}, raw=True)
check(st == 200, f"print pack downloaded ({len(zbytes)} bytes)")
z = zipfile.ZipFile(io.BytesIO(zbytes))
models = [n for n in z.namelist() if n.endswith("model.stl")]
check(len(models) == 3 and all(len(z.read(n)) > 1000 for n in models), f"three model files packed: {models}")
if SLICER:
    with tempfile.TemporaryDirectory() as tmp:
        for n in models:
            stl = Path(tmp) / (n.replace("/", "_"))
            stl.write_bytes(z.read(n))
            gcode = stl.with_suffix(".gcode")
            proc = subprocess.run([SLICER, "--export-gcode", "--output", str(gcode), str(stl)], capture_output=True, text=True, timeout=600)
            ok = proc.returncode == 0 and gcode.exists() and gcode.stat().st_size > 10000
            check(ok, f"{n} slices to G-code ({gcode.stat().st_size // 1024 if gcode.exists() else 0} KiB) {proc.stderr[-300:] if not ok else ''}")
else:
    print("  · AAKAR_SLICER_BIN not set: skipped slicing the pack to G-code")
print("SMOKE OK")
