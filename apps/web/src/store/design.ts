"use client";

import { create } from "zustand";
import type { Design, DesignSpec, DesignVersion, JobStageEvent, Material, ParamValues, PriceBreakdown, Stage, Upload } from "@/lib/api/types";
import { featuresFromSpec, uploadIdOf, withFeature, type Feature } from "@/lib/features";
import { rejectionMessage } from "@/lib/uploads";
import { FALLBACK_MATERIALS } from "@/lib/viewer/materials";

export interface JobProgress {
  id: string;
  stage: Stage;
  message: string;
  percent?: number;
  versionId?: string | null;
  errorCode?: string | null;
}

/** What this tab knows about a customer upload: the file's own name, its review state, a preview URL once cleared. */
export interface UploadNote {
  name?: string;
  status?: Upload["status"];
  url?: string;
  /** Why a reviewer turned the file down (the API's words, or the default copy). */
  message?: string;
}

export type PriceState =
  | { status: "idle" }
  | { status: "loading"; previous?: PriceBreakdown }
  | { status: "ready"; price: PriceBreakdown }
  | { status: "error"; detail: string; previous?: PriceBreakdown };

interface DesignState {
  design?: Design;
  versions: DesignVersion[];
  /** The version being viewed; usually latest, but the user can step back. */
  activeVersionId?: string;
  materials: Material[];
  materialId?: string;
  price: PriceState;
  job?: JobProgress;
  paramsDraft: ParamValues;
  /** The Chhaap being edited: at most one feature per anchor; sent whole with the next Sculpt. */
  featuresDraft: Feature[];
  /**
   * Uploads seen in this tab, by id. Kept across `reset()`, so the Chhaap panel still shows a file's own name
   * after a remount or the hop from a composer to the studio.
   */
  uploads: Record<string, UploadNote>;

  setDesign(design: Design): void;
  setVersions(versions: DesignVersion[]): void;
  setActiveVersion(id: string): void;
  setMaterials(materials: Material[]): void;
  selectMaterial(id: string): void;
  setPrice(price: PriceState): void;
  startJob(id: string): void;
  applyStageEvent(ev: JobStageEvent): void;
  clearJob(): void;
  setParam(key: string, value: ParamValues[string]): void;
  resetParams(values: ParamValues): void;
  /** Put `feature` on an anchor, or clear the anchor with null. */
  setFeature(anchorId: string, feature: Feature | null): void;
  /** Rebuild the draft from a version's spec (undo, or a new version arriving). */
  resetFeatures(fromSpec: Pick<DesignSpec, "features"> | undefined): void;
  /** Note a fresh upload (with the file's name, when the browser has it) or a newer record from polling. */
  rememberUpload(upload: Upload, fileName?: string): void;
  /** Back to an empty studio; the upload notes stay. */
  reset(): void;
}

const initial = {
  design: undefined,
  versions: [] as DesignVersion[],
  activeVersionId: undefined,
  materials: FALLBACK_MATERIALS,
  materialId: undefined,
  price: { status: "idle" } as PriceState,
  job: undefined,
  paramsDraft: {} as ParamValues,
  featuresDraft: [] as Feature[],
  uploads: {} as Record<string, UploadNote>,
};

export const useDesignStore = create<DesignState>()((set, get) => ({
  ...initial,

  setDesign: (design) => {
    const latest = design.latest_version;
    set((s) => ({
      design,
      activeVersionId: latest?.id ?? s.activeVersionId,
      materialId: s.materialId ?? latest?.spec.material ?? s.materials[0]?.id,
      paramsDraft: latest ? { ...latest.spec.params } : s.paramsDraft,
      featuresDraft: latest ? featuresFromSpec(latest.spec) : s.featuresDraft,
      price: latest?.price ? { status: "ready", price: latest.price } : s.price,
    }));
  },

  setVersions: (versions) => set({ versions }),

  setActiveVersion: (id) => {
    const version = get().versions.find((v) => v.id === id) ?? (get().design?.latest_version?.id === id ? get().design?.latest_version : undefined);
    set({
      activeVersionId: id,
      paramsDraft: version ? { ...version.spec.params } : get().paramsDraft,
      featuresDraft: version ? featuresFromSpec(version.spec) : get().featuresDraft,
      price: version?.price && version.price.material_id === get().materialId ? { status: "ready", price: version.price } : { status: "idle" },
    });
  },

  setMaterials: (materials) => set({ materials }),

  selectMaterial: (id) => set({ materialId: id }),

  setPrice: (price) => set({ price }),

  startJob: (id) => set({ job: { id, stage: "queued", message: "Queued" } }),

  applyStageEvent: (ev) =>
    set((s) => ({
      job: {
        id: ev.job_id || s.job?.id || "",
        stage: ev.stage,
        message: ev.message,
        percent: ev.percent,
        versionId: ev.version_id,
        errorCode: ev.error_code,
      },
    })),

  clearJob: () => set({ job: undefined }),

  setParam: (key, value) => set((s) => ({ paramsDraft: { ...s.paramsDraft, [key]: value } })),

  resetParams: (values) => set({ paramsDraft: { ...values } }),

  setFeature: (anchorId, feature) => set((s) => ({ featuresDraft: withFeature(s.featuresDraft, anchorId, feature) })),

  resetFeatures: (fromSpec) => set({ featuresDraft: featuresFromSpec(fromSpec) }),

  rememberUpload: (upload, fileName) =>
    set((s) => {
      const prev = s.uploads[upload.id];
      const note: UploadNote = {
        name: fileName ?? prev?.name,
        status: upload.status,
        url: upload.url ?? prev?.url,
        message: upload.status === "rejected" ? rejectionMessage(upload) : undefined,
      };
      return { uploads: { ...s.uploads, [upload.id]: note } };
    }),

  reset: () => set((s) => ({ ...initial, uploads: s.uploads })),
}));

/** Selects the version currently on stage. */
export function selectActiveVersion(s: Pick<DesignState, "design" | "versions" | "activeVersionId">): DesignVersion | undefined {
  const { design, versions, activeVersionId } = s;
  if (activeVersionId) {
    return versions.find((v) => v.id === activeVersionId) ?? (design?.latest_version?.id === activeVersionId ? design.latest_version : undefined);
  }
  return design?.latest_version;
}

export function selectMaterial(s: Pick<DesignState, "materials" | "materialId">): Material | undefined {
  return s.materials.find((m) => m.id === s.materialId) ?? s.materials[0];
}

/**
 * Why these features can't be sent yet: a file the studio is still checking, or one a reviewer turned down.
 * A feature whose upload this tab never saw (a stored spec) was accepted by the API, so it never holds.
 */
export function uploadHold(features: readonly Feature[], uploads: Readonly<Record<string, UploadNote>>): "checking" | "rejected" | undefined {
  let checking = false;
  for (const f of features) {
    const id = uploadIdOf(f);
    const status = id ? uploads[id]?.status : undefined;
    if (status === "rejected") return "rejected";
    if (status === "pending_review") checking = true;
  }
  return checking ? "checking" : undefined;
}
