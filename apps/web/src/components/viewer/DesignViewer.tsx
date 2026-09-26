"use client";

import { Suspense, useEffect, useMemo, useRef, useState, type ComponentRef } from "react";
import { Canvas, useThree } from "@react-three/fiber";
import { ContactShadows, OrbitControls } from "@react-three/drei";
import * as THREE from "three";
import type { MaterialPbr } from "@/lib/api/types";
import { physicalMaterialFrom } from "@/lib/viewer/materials";
import { environmentBackground, presetFor } from "@/lib/viewer/environments";
import { ErrorBoundary } from "./ErrorBoundary";
import { Model } from "./Model";
import { PlaceholderForm } from "./PlaceholderForm";
import { StageEnvironment } from "./StageEnvironment";

const CAMERA_POS: [number, number, number] = [1.35, 0.95, 1.85];
const TARGET: [number, number, number] = [0, 0.42, 0];

export interface DesignViewerProps {
  /** Preview GLB URL from `latest_version.assets.glb.url`; none renders the stand-in form. */
  glbUrl?: string;
  pbr: MaterialPbr;
  environment?: string;
  /** Dim the stage (a new version is being sculpted). */
  dimmed?: boolean;
  /** Called when the GLB fails to load; the viewer falls back to the stand-in form. */
  onModelError?(error: Error): void;
  className?: string;
}

function CameraRig({ resetKey }: { resetKey: number }) {
  const controls = useRef<ComponentRef<typeof OrbitControls>>(null);
  const camera = useThree((s) => s.camera);
  useEffect(() => {
    if (resetKey === 0) return;
    camera.position.set(...CAMERA_POS);
    controls.current?.target.set(...TARGET);
    controls.current?.update();
  }, [resetKey, camera]);
  return (
    <OrbitControls
      ref={controls}
      makeDefault
      target={TARGET}
      enablePan={false}
      enableDamping
      dampingFactor={0.08}
      minDistance={0.9}
      maxDistance={4.5}
      minPolarAngle={0.25}
      maxPolarAngle={Math.PI / 2 + 0.04}
      rotateSpeed={0.7}
      zoomSpeed={0.6}
    />
  );
}

export function DesignViewer({ glbUrl, pbr, environment, dimmed, onModelError, className }: DesignViewerProps) {
  const [resetKey, setResetKey] = useState(0);
  const material = useMemo(() => physicalMaterialFrom(pbr), [pbr]);
  useEffect(() => () => material.dispose(), [material]);

  const preset = presetFor(environment);
  const backdrop = environmentBackground(environment);

  return (
    <div
      className={["relative h-full w-full select-none", className].filter(Boolean).join(" ")}
      style={{
        background: `radial-gradient(60% 55% at 50% 62%, rgba(216,174,91,.16), transparent 70%), linear-gradient(180deg, ${backdrop} 0%, #1B2238 100%)`,
        transition: "opacity var(--ak-motion-slow) var(--ak-ease), filter var(--ak-motion-slow) var(--ak-ease)",
        opacity: dimmed ? 0.55 : 1,
        filter: dimmed ? "saturate(.6)" : "none",
      }}
    >
      <Canvas
        dpr={[1, 1.75]}
        camera={{ position: CAMERA_POS, fov: 34, near: 0.05, far: 50 }}
        gl={{ antialias: true, alpha: true, toneMapping: THREE.ACESFilmicToneMapping, toneMappingExposure: 1.05 }}
        onDoubleClick={() => setResetKey((k) => k + 1)}
        aria-label="3D preview of your piece. Drag to turn, scroll to zoom, double-click to reset."
        role="img"
      >
        <StageEnvironment preset={preset} />
        <group position={[0, 0, 0]}>
          {glbUrl ? (
            <ErrorBoundary fallback={<PlaceholderForm material={material} />} onError={onModelError} resetKey={glbUrl}>
              <Suspense fallback={<PlaceholderForm material={material} ghost />}>
                <Model url={glbUrl} material={material} />
              </Suspense>
            </ErrorBoundary>
          ) : (
            <PlaceholderForm material={material} />
          )}
        </group>
        <ContactShadows position={[0, -0.001, 0]} opacity={0.55} scale={3.2} blur={2.6} far={1.4} resolution={512} color="#0E1220" frames={60} />
        <CameraRig resetKey={resetKey} />
      </Canvas>
    </div>
  );
}
