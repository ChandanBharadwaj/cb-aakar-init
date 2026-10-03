"use client";

import { useMemo } from "react";
import { useThree } from "@react-three/fiber";
import { Outlines } from "@react-three/drei";
import * as THREE from "three";
import type { ModelOutline } from "./Model";

export interface PlaceholderFormProps {
  material: THREE.Material;
  /** Ghost mode while a real preview loads. */
  ghost?: boolean;
  /** An ink line round the form (the comic_pop look); never on the ghost. */
  outline?: ModelOutline;
}

/** A stand-in form (a fluted, lathe-turned vessel) shown while the preview loads or when there is none yet. */
export function PlaceholderForm({ material, ghost, outline }: PlaceholderFormProps) {
  const dpr = useThree((s) => s.viewport.dpr);
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

  return (
    <mesh geometry={geometry} material={ghost ? ghostMaterial : material} castShadow receiveShadow position={[0, 0, 0]}>
      {outline && !ghost && <Outlines color={outline.color} thickness={outline.thicknessPx * dpr} />}
    </mesh>
  );
}
