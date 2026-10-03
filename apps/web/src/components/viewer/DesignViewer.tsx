"use client";

import { Suspense, useCallback, useEffect, useMemo, useRef, useState, type ComponentRef } from "react";
import { Canvas, useThree } from "@react-three/fiber";
import { ContactShadows, OrbitControls } from "@react-three/drei";
import * as THREE from "three";
import type { MaterialPbr } from "@/lib/api/types";
import { INK_OUTLINE, isCelShaded, materialFor } from "@/lib/viewer/materials";
import { environmentBackdrop, environmentShadow, presetFor } from "@/lib/viewer/environments";
import { ErrorBoundary } from "./ErrorBoundary";
import { Model, type ModelFrame } from "./Model";
import { PlaceholderForm } from "./PlaceholderForm";
import { StageEnvironment } from "./StageEnvironment";

// Defaults for the stand-in form (a ~1 unit tall object on the floor). Once a real model reports its
// bounding sphere, CameraRig re-frames from it: same viewing direction, distance from the sphere.
const CAMERA_POS: [number, number, number] = [1.35, 0.95, 1.85];
const TARGET: [number, number, number] = [0, 0.42, 0];
const VIEW_DIR = new THREE.Vector3(...CAMERA_POS).sub(new THREE.Vector3(...TARGET)).normalize();
const FRAME_MARGIN = 1.18;

function framedCamera(frame: ModelFrame, fovDeg: number, aspect: number) {
  // Distance at which the bounding sphere fits the narrower of the two view angles, plus margin.
  const vFov = THREE.MathUtils.degToRad(fovDeg);
  const hFov = 2 * Math.atan(Math.tan(vFov / 2) * aspect);
  const fov = Math.min(vFov, hFov);
  const distance = (frame.radius / Math.sin(fov / 2)) * FRAME_MARGIN;
  const target = new THREE.Vector3(...frame.center);
  const position = target.clone().addScaledVector(VIEW_DIR, distance);
  return { target, position, distance };
}

export interface DesignViewerProps {
  /** Preview GLB URL from `latest_version.assets.glb.url`; none renders the stand-in form. */
  glbUrl?: string;
  pbr: MaterialPbr;
  environment?: string;
  /**
   * The piece's style variant (its `spec.style`, or a Duniya's preset while composing): `comic_pop` draws it
   * cel-shaded with ink outlines; every other style keeps the PBR finish.
   */
  look?: string;
  /** Dim the stage (a new version is being sculpted). */
  dimmed?: boolean;
  /** Called when the GLB fails to load; the viewer falls back to the stand-in form. */
  onModelError?(error: Error): void;
  /**
   * A still stage (a Duniya page's strip): no orbit controls or double-click reset, frames drawn on demand rather than
   * every tick, and hidden from assistive tech (the caller labels the strip).
   */
  still?: boolean;
  className?: string;
}

function CameraRig({ resetKey, frame }: { resetKey: number; frame: ModelFrame | null }) {
  const controls = useRef<ComponentRef<typeof OrbitControls>>(null);
  const camera = useThree((s) => s.camera);
  const aspect = useThree((s) => s.viewport.aspect);
  const framed = useMemo(() => {
    const fov = camera instanceof THREE.PerspectiveCamera ? camera.fov : 34;
    return frame ? framedCamera(frame, fov, aspect) : null;
  }, [frame, camera, aspect]);
  // Re-frame whenever a model reports its size, and on double-click reset.
  useEffect(() => {
    const target = framed ? framed.target : new THREE.Vector3(...TARGET);
    const position = framed ? framed.position : new THREE.Vector3(...CAMERA_POS);
    camera.position.copy(position);
    controls.current?.target.copy(target);
    controls.current?.update();
  }, [resetKey, framed, camera]);
  const distance = framed?.distance ?? 2.35;
  return (
    <OrbitControls
      ref={controls}
      makeDefault
      target={TARGET}
      enablePan={false}
      enableDamping
      dampingFactor={0.08}
      minDistance={distance * 0.4}
      maxDistance={distance * 2.4}
      minPolarAngle={0.25}
      maxPolarAngle={Math.PI / 2 + 0.04}
      rotateSpeed={0.7}
      zoomSpeed={0.6}
    />
  );
}

/** The still stage's fixed view: the default camera aimed at the stand-in form. */
function StillCamera() {
  const camera = useThree((s) => s.camera);
  const invalidate = useThree((s) => s.invalidate);
  useEffect(() => {
    camera.position.set(...CAMERA_POS);
    camera.lookAt(...TARGET);
    invalidate();
  }, [camera, invalidate]);
  return null;
}

export function DesignViewer({ glbUrl, pbr, environment, look, dimmed, onModelError, still, className }: DesignViewerProps) {
  const [resetKey, setResetKey] = useState(0);
  const [frame, setFrame] = useState<ModelFrame | null>(null);
  const onFramed = useCallback((next: ModelFrame) => {
    setFrame((prev) =>
      prev && Math.abs(prev.radius - next.radius) < 1e-4 && prev.center.every((c, i) => Math.abs(c - (next.center[i] ?? 0)) < 1e-4)
        ? prev
        : next,
    );
  }, []);
  const celShaded = isCelShaded(look);
  const material = useMemo(() => materialFor(pbr, celShaded ? look : undefined), [pbr, celShaded, look]);
  useEffect(() => () => material.dispose(), [material]);
  const outline = celShaded ? INK_OUTLINE : undefined;

  const preset = presetFor(environment);

  return (
    <div
      className={["relative h-full w-full select-none", className].filter(Boolean).join(" ")}
      style={{
        background: environmentBackdrop(environment),
        transition: "opacity var(--ak-motion-slow) var(--ak-ease), filter var(--ak-motion-slow) var(--ak-ease)",
        opacity: dimmed ? 0.55 : 1,
        filter: dimmed ? "saturate(.6)" : "none",
      }}
    >
      <Canvas
        dpr={[1, 1.75]}
        frameloop={still ? "demand" : "always"}
        camera={{ position: CAMERA_POS, fov: 34, near: 0.05, far: 50 }}
        gl={{ antialias: true, alpha: true, toneMapping: THREE.ACESFilmicToneMapping, toneMappingExposure: 1.05 }}
        onDoubleClick={still ? undefined : () => setResetKey((k) => k + 1)}
        aria-label={still ? undefined : "3D preview of your piece. Drag to turn, scroll to zoom, double-click to reset."}
        aria-hidden={still || undefined}
        role={still ? undefined : "img"}
      >
        <StageEnvironment preset={preset} />
        <group position={[0, 0, 0]}>
          {glbUrl ? (
            <ErrorBoundary fallback={<PlaceholderForm material={material} outline={outline} />} onError={onModelError} resetKey={glbUrl}>
              <Suspense fallback={<PlaceholderForm material={material} ghost />}>
                <Model url={glbUrl} material={material} outline={outline} onFramed={onFramed} />
              </Suspense>
            </ErrorBoundary>
          ) : (
            <PlaceholderForm material={material} outline={outline} />
          )}
        </group>
        <ContactShadows position={[0, -0.001, 0]} opacity={0.55} scale={3.2} blur={2.6} far={1.4} resolution={512} color={environmentShadow(environment)} frames={60} />
        {still ? <StillCamera /> : <CameraRig resetKey={resetKey} frame={glbUrl ? frame : null} />}
      </Canvas>
    </div>
  );
}
