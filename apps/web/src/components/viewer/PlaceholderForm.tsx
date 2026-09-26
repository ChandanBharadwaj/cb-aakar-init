"use client";

import { useMemo } from "react";
import * as THREE from "three";

export interface PlaceholderFormProps {
  material: THREE.Material;
  /** Ghost mode while a real preview loads. */
  ghost?: boolean;
}

/** A stand-in form (a fluted, lathe-turned vessel) shown while the preview loads or when there is none yet. */
export function PlaceholderForm({ material, ghost }: PlaceholderFormProps) {
  const geometry = useMemo(() => {
    const pts: THREE.Vector2[] = [];
    const steps = 28;
    for (let i = 0; i <= steps; i++) {
      const t = i / steps;
      const r = 0.22 + 0.16 * Math.sin(t * Math.PI) - 0.06 * Math.pow(t, 3) + 0.03 * Math.sin(t * Math.PI * 9) * (1 - t);
      pts.push(new THREE.Vector2(Math.max(0.001, r), t * 0.9));
    }
    const g = new THREE.LatheGeometry(pts, 48);
    g.computeVertexNormals();
    return g;
  }, []);

  const ghostMaterial = useMemo(
    () => new THREE.MeshStandardMaterial({ color: "#D8AE5B", wireframe: true, transparent: true, opacity: 0.35 }),
    [],
  );

  return <mesh geometry={geometry} material={ghost ? ghostMaterial : material} castShadow receiveShadow position={[0, 0, 0]} />;
}
