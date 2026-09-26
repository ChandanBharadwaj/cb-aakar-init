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

/** Apply one material to every mesh in a loaded scene (shadows on). */
export function applyMaterial(root: THREE.Object3D, material: THREE.Material): void {
  root.traverse((obj) => {
    if ((obj as THREE.Mesh).isMesh) {
      const mesh = obj as THREE.Mesh;
      mesh.material = material;
      mesh.castShadow = true;
      mesh.receiveShadow = true;
    }
  });
}
