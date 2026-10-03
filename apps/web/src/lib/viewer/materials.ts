import * as THREE from "three";
import { materials as tokenMaterials } from "@aakar/design-tokens";
import type { Material, MaterialPbr } from "../api/types";

/** The six launch finishes from the design-tokens package, used until the API answers (same shape as /api/catalog/materials). */
export const FALLBACK_MATERIALS: Material[] = tokenMaterials.map((m) => ({
  id: m.id,
  name: m.name,
  filament: m.filament,
  density_g_cm3: m.density_g_cm3,
  finish_class: m.finish_class as Material["finish_class"],
  rate_per_g_paise: m.rate_per_g_paise,
  heat_safe: m.heat_safe,
  pbr: {
    color: m.pbr.color,
    roughness: m.pbr.roughness,
    metalness: m.pbr.metalness,
    clearcoat: m.pbr.clearcoat,
    clearcoat_roughness: m.pbr.clearcoat_roughness,
    sheen: m.pbr.sheen,
    sheen_color: m.pbr.sheen_color,
  },
}));

/** Build a three.js MeshPhysicalMaterial from a digital-material PBR preset. */
export function physicalMaterialFrom(pbr: MaterialPbr): THREE.MeshPhysicalMaterial {
  const mat = new THREE.MeshPhysicalMaterial({
    color: new THREE.Color(pbr.color),
    roughness: pbr.roughness,
    metalness: pbr.metalness,
    clearcoat: pbr.clearcoat ?? 0,
    clearcoatRoughness: pbr.clearcoat_roughness ?? 0,
    sheen: pbr.sheen ?? 0,
    sheenColor: new THREE.Color(pbr.sheen_color ?? "#FFFFFF"),
    sheenRoughness: 0.6,
    envMapIntensity: 1,
  });
  mat.name = `aakar:${pbr.color}`;
  return mat;
}

// ---- Cel shading (comic_pop) ------------------------------------------------------------------------------------------

/** Style variants the viewer draws cel-shaded with ink outlines: Katha's comic book look. Every other style is PBR. */
const CEL_SHADED_LOOKS: ReadonlySet<string> = new Set(["comic_pop"]);

export function isCelShaded(look: string | undefined): boolean {
  return look !== undefined && CEL_SHADED_LOOKS.has(look);
}

/** The ink line round a cel-shaded piece: near-black indigo, a few pixels wide whatever the zoom. */
export const INK_OUTLINE = { color: "#0E1220", thicknessPx: 2.4 } as const;

/** Light bands of the toon shading, darkest to lightest (four steps: shadow, core, light, highlight). */
const TOON_STEPS = [64, 128, 200, 255];
let toonGradient: THREE.DataTexture | undefined;

/** The shared gradient map: one texel per band, sampled without filtering so the bands stay hard. */
function toonGradientMap(): THREE.DataTexture {
  if (!toonGradient) {
    toonGradient = new THREE.DataTexture(new Uint8Array(TOON_STEPS), TOON_STEPS.length, 1, THREE.RedFormat);
    toonGradient.minFilter = THREE.NearestFilter;
    toonGradient.magFilter = THREE.NearestFilter;
    toonGradient.generateMipmaps = false;
    toonGradient.needsUpdate = true;
  }
  return toonGradient;
}

/**
 * The finish as comic art: a `MeshToonMaterial` in the finish's own colour, shaded in four hard bands, so a Terracotta
 * Silk piece still reads terracotta. The gradient map is shared and outlives the material.
 */
export function toonMaterialFrom(pbr: MaterialPbr): THREE.MeshToonMaterial {
  const mat = new THREE.MeshToonMaterial({ color: new THREE.Color(pbr.color), gradientMap: toonGradientMap() });
  mat.name = `aakar:toon:${pbr.color}`;
  return mat;
}

/** The finish for a style: cel-shaded for comic_pop, the PBR preset otherwise. */
export function materialFor(pbr: MaterialPbr, look?: string): THREE.MeshPhysicalMaterial | THREE.MeshToonMaterial {
  return isCelShaded(look) ? toonMaterialFrom(pbr) : physicalMaterialFrom(pbr);
}

/** Every mesh in a loaded scene, collected once (before anything such as an ink outline is added under them). */
export function meshesOf(root: THREE.Object3D): THREE.Mesh[] {
  const list: THREE.Mesh[] = [];
  root.traverse((obj) => {
    if ((obj as THREE.Mesh).isMesh) list.push(obj as THREE.Mesh);
  });
  return list;
}

/** Dress the piece's own meshes in one material (shadows on); outline hulls added under them keep their ink. */
export function applyMaterial(meshes: readonly THREE.Mesh[], material: THREE.Material): void {
  for (const mesh of meshes) {
    mesh.material = material;
    mesh.castShadow = true;
    mesh.receiveShadow = true;
  }
}
