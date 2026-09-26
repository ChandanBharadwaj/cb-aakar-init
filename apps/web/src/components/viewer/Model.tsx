"use client";

import { useLayoutEffect, useMemo } from "react";
import { useGLTF } from "@react-three/drei";
import * as THREE from "three";
import { applyMaterial } from "@/lib/viewer/materials";

export interface ModelProps {
  url: string;
  material: THREE.Material;
  /** Longest side of the model in scene units after fitting (the camera rig is tuned for 1). */
  fit?: number;
}

/** Loads the preview GLB (Draco-aware), fits it to the stage with its base on the floor, and dresses it in the chosen finish. */
export function Model({ url, material, fit = 1 }: ModelProps) {
  const gltf = useGLTF(url);
  // Clone so a re-dressed scene never leaks back into drei's loader cache.
  const object = useMemo(() => gltf.scene.clone(true), [gltf.scene]);

  const { scale, position } = useMemo(() => {
    const box = new THREE.Box3().setFromObject(object);
    const size = box.getSize(new THREE.Vector3());
    const centre = box.getCenter(new THREE.Vector3());
    const longest = Math.max(size.x, size.y, size.z) || 1;
    const s = fit / longest;
    return { scale: s, position: [-centre.x * s, -box.min.y * s, -centre.z * s] as [number, number, number] };
  }, [object, fit]);

  useLayoutEffect(() => {
    applyMaterial(object, material);
  }, [object, material]);

  return <primitive object={object} scale={scale} position={position} />;
}
