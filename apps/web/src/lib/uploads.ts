// Client-side rules for customer uploads (POST /api/uploads): accepted formats and size caps from the
// contract, so a file that the API would refuse never leaves the browser. The noun in copy is always
// "photo" or "model file", never mesh.
import type { Upload, UploadKind } from "@/lib/api/types";
import type { ContentFormat } from "@/lib/features";

/** A file the content scanner sent to a reviewer is polled every 4 s, for at most two minutes. */
export const REVIEW_POLL_MS = 4_000;
export const REVIEW_POLL_LIMIT_MS = 120_000;

/** What a customer reads when a file is turned down and the API gives no reason of its own. */
export const REJECTED_COPY = "We can't print this one; try a different file.";

/**
 * Why a file was turned down, in the API's words when it sends a `message` (not in the Upload contract yet,
 * so it is read defensively), else the default copy.
 */
export function rejectionMessage(upload: Upload): string {
  const message = (upload as Upload & { message?: unknown }).message;
  return typeof message === "string" && message.trim() ? message.trim() : REJECTED_COPY;
}

/** "dragon_v2.stl" → "dragon v2": a working title from a file name. */
export function fileTitle(name: string): string {
  return name
    .replace(/\.[^.]+$/, "")
    .replace(/[_-]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 80);
}

export const IMAGE_FORMATS: readonly ContentFormat[] = ["png", "jpg", "webp", "heic"];
export const MODEL_FORMATS: readonly ContentFormat[] = ["stl", "glb", "3mf", "obj", "ply", "off", "gltf"];

export const IMAGE_MAX_BYTES = 15 * 1024 * 1024;
export const MODEL_MAX_BYTES = 50 * 1024 * 1024;

/** `accept` attribute for a file input of this kind. */
export const IMAGE_ACCEPT = ".png,.jpg,.jpeg,.webp,.heic,image/png,image/jpeg,image/webp,image/heic";
export const MODEL_ACCEPT = ".stl,.obj,.3mf,.glb,.gltf,.ply,.off";

export function acceptFor(kind: UploadKind): string {
  return kind === "image" ? IMAGE_ACCEPT : MODEL_ACCEPT;
}

export function maxBytesFor(kind: UploadKind): number {
  return kind === "image" ? IMAGE_MAX_BYTES : MODEL_MAX_BYTES;
}

/** "PNG, JPG, WEBP or HEIC up to 15 MB" / "Upload the file from your 3D program (.stl, .obj, .3mf)". */
export function formatHint(kind: UploadKind): string {
  return kind === "image" ? "PNG, JPG, WEBP or HEIC up to 15 MB" : "Upload the file from your 3D program (.stl, .obj, .3mf) · up to 50 MB";
}

/** The contract's format id for a file name (`photo.JPEG` → `jpg`), or undefined when it isn't one we take. */
export function fileFormat(name: string): ContentFormat | undefined {
  const ext = name.toLowerCase().split(".").pop() ?? "";
  const normalised = ext === "jpeg" ? "jpg" : ext;
  return [...IMAGE_FORMATS, ...MODEL_FORMATS].find((f) => f === normalised);
}

/** 2 457 600 → "2.3 MB". */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace(/\.0$/, "")} MB`;
}

/** A friendly reason the file can't be sent, or undefined when it can. */
export function fileProblem(file: File, kind: UploadKind): string | undefined {
  const format = fileFormat(file.name);
  const allowed = kind === "image" ? IMAGE_FORMATS : MODEL_FORMATS;
  if (!format || !allowed.includes(format)) {
    return kind === "image" ? "That isn't a photo we can use. Try a PNG, JPG, WEBP or HEIC." : "That isn't a model file we can print. Save it as .stl, .obj or .3mf from your 3D program.";
  }
  if (file.size > maxBytesFor(kind)) return `That file is ${formatBytes(file.size)}; the limit is ${formatBytes(maxBytesFor(kind))}.`;
  if (file.size === 0) return "That file is empty.";
  return undefined;
}
