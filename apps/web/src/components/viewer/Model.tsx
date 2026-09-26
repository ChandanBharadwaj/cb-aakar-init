"use client";

import { useLayoutEffect, useMemo, useRef } from "react";
import { useGLTF } from "@react-three/drei";
import * as THREE from "three";
import { applyMaterial } from "@/lib/viewer/materials";

export interface ModelProps {
  url: string;
  material: THREE.Material;
  /** Longest side of the model in scene units after fitting (the camera rig is tuned for 1). */
  fit?: number;
  /** Reports the mounted model's world-space bounding sphere so the camera rig can frame it exactly. */
  onFramed?(frame: ModelFrame): void;
}

export interface ModelFrame {
  center: [number, number, number];
  radius: number;
}

/** Loads the preview GLB (Draco-aware), fits it to the stage with its base on the floor, and dresses it in the chosen finish. */
export function Model({ url, material, fit = 1, onFramed }: ModelProps) {
  const ref = useRef<THREE.Object3D>(null);
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

  // Measure what is actually on stage (after scale/position are applied) rather than trusting the
  // pre-fit box: this is what the camera rig frames, whatever units the GLB was exported in.
  useLayoutEffect(() => {
    const mounted = ref.current;
    if (!mounted || !onFramed) return;
    mounted.updateWorldMatrix(true, true);
    const sphere = new THREE.Box3().setFromObject(mounted).getBoundingSphere(new THREE.Sphere());
    onFramed({ center: [sphere.center.x, sphere.center.y, sphere.center.z], radius: sphere.radius });
  }, [object, scale, position, onFramed]);

  return <primitive ref={ref} object={object} scale={scale} position={position} />;
}
