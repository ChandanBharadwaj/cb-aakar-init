"use client";

import { Suspense, useState } from "react";
import { Environment } from "@react-three/drei";
import type { DreiPreset } from "@/lib/viewer/environments";
import { ErrorBoundary } from "./ErrorBoundary";

/** Soft key + fill; the image-based environment adds reflections on top when it loads. */
export function StageLights({ strong }: { strong: boolean }) {
  const k = strong ? 1.8 : 1;
  return (
    <>
      <ambientLight intensity={0.22 * k} color="#B8BDD0" />
      <directionalLight position={[2.4, 4.2, 2.6]} intensity={1.4 * k} color="#FFF0C2" />
      <directionalLight position={[-3, 2.2, -2.4]} intensity={0.35 * k} color="#B8BDD0" />
      <pointLight position={[0, 1.6, -1.2]} intensity={0.6 * k} color="#D8AE5B" distance={6} decay={2} />
    </>
  );
}

export interface StageEnvironmentProps {
  preset: DreiPreset;
}

/**
 * drei's HDRI presets download at runtime from the drei-assets CDN. If that fails
 * (offline, blocked host), the boundary swallows the error and we lean on the lights.
 */
export function StageEnvironment({ preset }: StageEnvironmentProps) {
  const [failed, setFailed] = useState(false);
  return (
    <>
      <StageLights strong={failed} />
      {!failed && (
        <ErrorBoundary fallback={null} onError={() => setFailed(true)} resetKey={preset}>
          <Suspense fallback={null}>
            <Environment preset={preset} environmentIntensity={0.85} />
          </Suspense>
        </ErrorBoundary>
      )}
    </>
  );
}
