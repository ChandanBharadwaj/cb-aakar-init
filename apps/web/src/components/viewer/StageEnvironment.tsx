"use client";

import { Suspense, useState } from "react";
import { Environment, Lightformer } from "@react-three/drei";
import type { BackdropPreset, DreiPreset, StageLight } from "@/lib/viewer/environments";
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
  /** How the backdrop is implemented (`presetFor` in `src/lib/viewer/environments.ts`). */
  preset: BackdropPreset;
}

/** The lights and reflections of a backdrop: a drei HDRI, or a stage built from data that downloads nothing. */
export function StageEnvironment({ preset }: StageEnvironmentProps) {
  return preset.kind === "built" ? <BuiltStage lights={preset.lights} glows={preset.glows} /> : <HdriStage preset={preset.preset} />;
}

/**
 * drei's HDRI presets download at runtime from the drei-assets CDN. If that fails
 * (offline, blocked host), the boundary swallows the error and we lean on the lights.
 */
function HdriStage({ preset }: { preset: DreiPreset }) {
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

/**
 * A stage built here (the comic rooftop): its lights, and its glows rendered once into a small local environment map
 * so glossy finishes still catch reflections. Nothing is fetched, so it looks the same offline.
 */
function BuiltStage({ lights, glows }: Pick<Extract<BackdropPreset, { kind: "built" }>, "lights" | "glows">) {
  return (
    <>
      {lights.map((light, i) => (
        <BuiltLight key={i} light={light} />
      ))}
      {glows.length > 0 && (
        <Environment resolution={64} frames={1} environmentIntensity={0.8}>
          {glows.map((glow, i) => (
            <Lightformer key={i} form="rect" color={glow.color} intensity={glow.intensity} position={glow.position} scale={glow.scale} target={[0, 0.4, 0]} />
          ))}
        </Environment>
      )}
    </>
  );
}

function BuiltLight({ light }: { light: StageLight }) {
  switch (light.kind) {
    case "ambient":
      return <ambientLight color={light.color} intensity={light.intensity} />;
    case "hemisphere":
      return <hemisphereLight args={[light.sky, light.ground, light.intensity]} />;
    case "directional":
      return <directionalLight color={light.color} intensity={light.intensity} position={light.position} />;
    case "spot":
      // aimed at the stage's origin, the piece's footprint; decay 2 and no cut-off distance (physical falloff)
      return <spotLight color={light.color} intensity={light.intensity} position={light.position} angle={light.angle} penumbra={light.penumbra} decay={2} distance={0} />;
  }
}
