"use client";

import { create } from "zustand";
import type { Design, DesignVersion, JobStageEvent, Material, ParamValues, PriceBreakdown, Stage } from "@/lib/api/types";
import { FALLBACK_MATERIALS } from "@/lib/viewer/materials";

export interface JobProgress {
  id: string;
  stage: Stage;
  message: string;
  percent?: number;
  versionId?: string | null;
  errorCode?: string | null;
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
      price: latest?.price ? { status: "ready", price: latest.price } : s.price,
    }));
  },

  setVersions: (versions) => set({ versions }),

  setActiveVersion: (id) => {
    const version = get().versions.find((v) => v.id === id) ?? (get().design?.latest_version?.id === id ? get().design?.latest_version : undefined);
    set({
      activeVersionId: id,
      paramsDraft: version ? { ...version.spec.params } : get().paramsDraft,
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

  reset: () => set({ ...initial }),
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
