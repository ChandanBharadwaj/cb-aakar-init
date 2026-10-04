#!/usr/bin/env python3
"""Live smoke of Katha (Comics & heroes) and the keepsakes against running services (no mocks).

Needs the API (`make api`, direct profile, Postgres) and the geometry service (`make geometry`) running.
Checks the content-term guardrail (a hero's name refused whatever its spelling, a look-alike word allowed, an upload
named after a hero held for review), a Katha keychain in the comic_pop style with its comic defaults, the Roshni photo
night light (photo required, lithophane mode only, LED base and minimum price), the Pratima plinth with a form and a
name (minimum price), and the photo frame still switched off. Standard library only. Usage: make smoke-katha
"""
import io, json, os, struct, sys, time, urllib.error, urllib.request, uuid, zlib

API = os.environ.get("AAKAR_API_URL", "http://localhost:8080")
GUEST = str(uuid.uuid4())


def png_bytes(w=160, h=120):
    """A grey ramp with a bright disc: enough contrast for a relief or a lithophane."""
    rows = []
    for y in range(h):
        row = bytearray([0])
        for x in range(w):
            v = 255 if (x - w // 2) ** 2 + (y - h // 2) ** 2 < (h // 4) ** 2 else int(x / (w - 1) * 200)
            row.append(v)
        rows.append(bytes(row))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 0, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"".join(rows))) + chunk(b"IEND", b""))


def box_stl(x=30.0, y=20.0, z=40.0):
    """An ASCII STL box with outward normals."""
    v = [(0, 0, 0), (x, 0, 0), (x, y, 0), (0, y, 0), (0, 0, z), (x, 0, z), (x, y, z), (0, y, z)]
    faces = [(0, 2, 1), (0, 3, 2), (4, 5, 6), (4, 6, 7), (0, 1, 5), (0, 5, 4), (1, 2, 6), (1, 6, 5), (2, 3, 7), (2, 7, 6),
             (3, 0, 4), (3, 4, 7)]
    out = ["solid box"]
    for a, b, c in faces:
        out += ["facet normal 0 0 0", " outer loop"] + [f"  vertex {v[i][0]} {v[i][1]} {v[i][2]}" for i in (a, b, c)]
        out += [" endloop", "endfacet"]
    return ("\n".join(out + ["endsolid box"]) + "\n").encode()


def req(method, path, body=None, headers=None):
    h = {"X-Aakar-Guest": GUEST, **(headers or {})}
    data = None
    if isinstance(body, (dict, list)):
        data = json.dumps(body).encode()
        h["content-type"] = "application/json"
    elif isinstance(body, tuple):  # (content_type, bytes)
        h["content-type"], data = body
    r = urllib.request.Request(API + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=180) as resp:
            payload = resp.read()
            return resp.status, (json.loads(payload) if payload else None)
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"null")


def multipart(fields, file_field, filename, content, ctype):
    b = "----aakar" + uuid.uuid4().hex
    out = io.BytesIO()
    for k, v in fields.items():
        out.write(f'--{b}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
    out.write(f'--{b}\r\nContent-Disposition: form-data; name="{file_field}"; filename="{filename}"\r\n'
              f"Content-Type: {ctype}\r\n\r\n".encode())
    out.write(content)
    out.write(f"\r\n--{b}--\r\n".encode())
    return (f"multipart/form-data; boundary={b}", out.getvalue())


def check(cond, msg):
    print(("  ✓ " if cond else "  ✗ ") + msg)
    if not cond:
        sys.exit(1)


def wait_job(job_id):
    for _ in range(240):
        _, job = req("GET", f"/api/jobs/{job_id}")
        if job["status"] in ("succeeded", "failed"):
            return job
        time.sleep(0.5)
    raise SystemExit("job timed out")


def design(body, label):
    st, acc = req("POST", "/api/designs", body)
    check(st == 202, f"{label}: design accepted ({st} {acc if st != 202 else ''})")
    job = wait_job(acc["job_id"])
    check(job["status"] == "succeeded", f"{label}: geometry job {job['status']} {job.get('error_code') or ''}")
    _, d = req("GET", f"/api/designs/{acc['design_id']}")
    return d["latest_version"]


def refused(body, status, code, label):
    st, problem = req("POST", "/api/designs", body)
    check(st == status and problem.get("code") == code, f"{label}: {st} {problem.get('code')} · {problem.get('detail')}")
    return problem


def naam(text, anchor="back", **extra):
    return {"type": "emboss_text", "text": text, "anchor": anchor, **extra}


def price_of(version, material):
    _, price = req("GET", f"/api/versions/{version['id']}/price?material={material}")
    return price


print("1 · Katha guardrail: protected heroes stay out")
for text in ("Batman", "Iron-Man", "SPIDER MAN"):
    problem = refused({"source": "create", "family_id": "keychain", "features": [naam(text)]}, 422, "protected_term",
                      f"name {text!r} refused")
    check(text.lower().replace("-", "").replace(" ", "") not in json.dumps(problem).lower().replace("-", "").replace(" ", ""),
          "the refusal never repeats the protected term")
st, ok = req("POST", "/api/designs", {"source": "create", "family_id": "keychain", "features": [naam("Adcock")]})
check(st == 202, f"a look-alike surname ('Adcock') is fine ({st})")
st, up = req("POST", "/api/uploads", multipart({"kind": "model"}, "file", "IronMan_final.stl", box_stl(), "model/stl"))
check(st == 201 and up["status"] == "pending_review", f"upload 'IronMan_final.stl' held for review ({st} {up.get('status')})")
_, staff = req("POST", "/admin/api/auth/login", {"email": "studio@aakar.local", "password": "aakar-studio"})
staff_auth = {"Authorization": f"Bearer {staff['access_token']}"}
st, pending = req("GET", "/admin/api/uploads?status=pending_review", headers=staff_auth)
held = next((u for u in (pending if isinstance(pending, list) else pending.get("items", [])) if u.get("id") == up["id"]), None)
check(held is not None and "iron man" in json.dumps(held).lower(), f"the review queue names the term: {held and held.get('review_reason', held)}")
st, terms = req("GET", "/admin/api/content-terms", headers=staff_auth)
check(st == 200 and len(terms) >= 24, f"portal content rules listed ({st}, {len(terms)} terms)")

print("2 · Katha keychain in the comic style")
v = design({"source": "create", "family_id": "keychain", "experience_id": "comics", "material": "basic_white",
            "features": [naam("Asha")]}, "Katha keychain")
spec = v["spec"]
check(spec.get("style") == "comic_pop", f"style from the experience: {spec.get('style')}")
text = spec["features"][0]
check(text.get("mode") == "emboss" and text.get("depth_mm", 0) > 1.2, f"comic defaults: {text.get('mode')} {text.get('depth_mm')} mm")
note = json.dumps(v).lower()
check("comic pop" in note, "the karigar's note names the comic style")

print("3 · Roshni photo night light")
st, photo = req("POST", "/api/uploads", multipart({"kind": "image"}, "file", "family.png", png_bytes(), "image/png"))
check(st == 201 and photo["status"] == "ready", f"photo upload ({st} {photo.get('status')})")
lit = {"type": "relief_image", "anchor": "plate", "source": {"upload_id": photo["id"]}}
refused({"source": "create", "family_id": "lithophane", "material": "basic_white", "features": []}, 422, "validation_failed",
        "no photo")
refused({"source": "create", "family_id": "lithophane", "material": "basic_white", "features": [{**lit, "mode": "emboss"}]},
        422, "unsupported_feature", "a raised photo")
v = design({"source": "create", "family_id": "lithophane", "material": "basic_white", "features": [{**lit, "mode": "lithophane"}]},
           "night light")
check(any(h.get("sku") == "led_base_usb" for h in v.get("hardware", [])), f"LED base included: {v.get('hardware')}")
price = price_of(v, "basic_white")
check(price["subtotal_paise"] >= 59900, f"minimum ₹599 honoured: subtotal ₹{price['subtotal_paise'] / 100:.0f}")

print("4 · Pratima plinth with your form and a name")
st, form = req("POST", "/api/uploads", multipart({"kind": "model"}, "file", "form.stl", box_stl(), "model/stl"))
check(st == 201 and form["status"] == "ready", f"form upload ({st} {form.get('status')})")
v = design({"source": "create", "family_id": "figurine_base", "material": "basic_white",
            "features": [{"type": "hero_mesh", "anchor": "top", "source": {"upload_id": form["id"]}},
                         naam("Asha", anchor="base_front")]}, "plinth")
check(v["printability"]["passed"], f"printability passed; bounds {v['printability']['geometry']['bounds_mm']}")
price = price_of(v, "basic_white")
check(price["subtotal_paise"] >= 69900, f"minimum ₹699 honoured: subtotal ₹{price['subtotal_paise'] / 100:.0f}")

print("5 · second wave stays switched off")
refused({"source": "create", "family_id": "photo_frame", "features": [naam("Asha", anchor="base_front", mode="deboss")]},
        422, "family_not_available", "photo frame")
print("KATHA AND KEEPSAKES SMOKE OK")
