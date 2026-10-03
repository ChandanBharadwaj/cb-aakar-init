"use client";

import { useEffect, useLayoutEffect, useMemo } from "react";
import { useThree } from "@react-three/fiber";
import * as THREE from "three";
import { inkMaterial, outlineHull } from "@/lib/viewer/materials";

export interface InkOutline {
  color: string;
  /** Line width in CSS pixels, the same at any zoom. */
  thicknessPx: number;
}

/**
 * An ink line round each of `meshes` (the comic_pop look): an inverted hull added under every mesh, drawn in one flat
 * colour from its back faces and pushed a few pixels out on screen. Nothing when `outline` is absent. The hulls and
 * their material are disposed when the meshes, the outline or the component go.
 */
export function useInkOutline(meshes: readonly THREE.Mesh[], outline: InkOutline | undefined): void {
  const size = useThree((s) => s.size);
  const invalidate = useThree((s) => s.invalidate);
  const color = outline?.color;
  const thickness = outline?.thicknessPx;
  const material = useMemo(() => (color !== undefined && thickness !== undefined ? inkMaterial(color, thickness) : undefined), [color, thickness]);
  useEffect(() => () => material?.dispose(), [material]);

  // The offset is in CSS pixels of the canvas: keep the shader's idea of its size current.
  useEffect(() => {
    if (!material) return;
    (material.uniforms.size?.value as THREE.Vector2 | undefined)?.set(Math.max(size.width, 1), Math.max(size.height, 1));
    invalidate();
  }, [material, size.width, size.height, invalidate]);

  useLayoutEffect(() => {
    if (!material) return;
    const hulls = meshes.map((mesh) => {
      const hull = new THREE.Mesh(outlineHull(mesh.geometry), material);
      hull.name = "aakar:ink-outline";
      hull.raycast = () => undefined;
      mesh.add(hull);
      return hull;
    });
    invalidate();
    return () => {
      for (const hull of hulls) {
        hull.removeFromParent();
        hull.geometry.dispose();
      }
    };
  }, [meshes, material, invalidate]);
}
