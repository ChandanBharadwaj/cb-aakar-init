// Validates every JSON Schema compiles, every example validates against its schema, the experiences seed names
// environments, presets and families that exist, and every OpenAPI document parses.
// Run with `pnpm --filter @aakar/contracts validate`.
import { readFileSync, readdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import YAML from "yaml";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const read = (p) => JSON.parse(readFileSync(join(root, p), "utf8"));

const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);

const schemaFiles = [
  ...readdirSync(join(root, "schemas")).filter((f) => f.endsWith(".json")).map((f) => `schemas/${f}`),
  ...readdirSync(join(root, "schemas/events")).filter((f) => f.endsWith(".json")).map((f) => `schemas/events/${f}`),
];
for (const f of schemaFiles) ajv.addSchema(read(f));
for (const f of schemaFiles) ajv.getSchema(read(f).$id) ?? ajv.compile(read(f));
console.log(`✓ ${schemaFiles.length} schemas compile`);

const examples = [
  ["examples/jharokha-phone-stand.spec.json", "https://aakar.studio/schemas/design-spec.v1.json"],
  ["examples/keychain-photo.spec.json", "https://aakar.studio/schemas/design-spec.v1.json"],
  ["examples/raw-print.spec.json", "https://aakar.studio/schemas/design-spec.v1.json"],
  ["examples/headphone-topper.spec.json", "https://aakar.studio/schemas/design-spec.v1.json"],
  ["examples/design.completed.example.json", "https://aakar.studio/schemas/events/design.completed.v1.json"],
  // The families seed in design-tokens is a contract instance too (Flyway V9 seeds from it; a drift test guards both).
  ["../design-tokens/families.json", "https://aakar.studio/schemas/template-family.v1.json"],
  // So are the experiences (Duniya) and viewer environments (Mahaul): Flyway V11, ExperiencesSeedTest.
  ["../design-tokens/experiences.json", "https://aakar.studio/schemas/experience.v1.json"],
];
let failed = 0;
for (const [file, id] of examples) {
  const validate = ajv.getSchema(id);
  const ok = validate(read(file));
  if (!ok) {
    failed++;
    console.error(`✗ ${file}`, validate.errors);
  } else {
    console.log(`✓ ${file} valid against ${id.split("/").pop()}`);
  }
}

// Live template descriptors exported from the geometry service (`make descriptors`); the mocks and API fixtures read them.
{
  const validate = ajv.getSchema("https://aakar.studio/schemas/template-descriptor.v1.json");
  const descriptors = read("examples/template-descriptors.json");
  for (const d of descriptors) {
    if (!validate(d)) {
      failed++;
      console.error(`✗ examples/template-descriptors.json#${d.id}`, validate.errors);
    }
  }
  console.log(`✓ examples/template-descriptors.json: ${descriptors.length} descriptors valid`);
}

// Experiences name environments, presets, families, motifs and styles that live elsewhere: check what a schema cannot.
{
  // Backdrops whose storefront preset has not landed yet: none now (Katha's comic_rooftop_night landed in tokens.json
  // and the viewer with PR 12b). Name a preset key here when an experience needs a backdrop before its preset is
  // built; the check below says when to remove it.
  const PENDING_PRESETS = new Set([]);
  // Motif packs whose artwork has not landed in the motif library yet: none now (Katha's comic_bursts is a tag on the
  // comic motifs since PR 12b).
  const PENDING_MOTIF_PACKS = new Set([]);
  const { environments, experiences } = read("../design-tokens/experiences.json");
  const motifLibrary = read("../design-tokens/motifs/index.json").motifs;
  const motifIds = new Set([...motifLibrary.map((m) => m.id), ...motifLibrary.flatMap((m) => m.tags ?? [])]);
  const tokens = read("../design-tokens/tokens.json");
  const familyIds = new Set(read("../design-tokens/families.json").families.map((f) => f.id));
  const presets = Object.keys(tokens.environments);
  const problems = [];
  const duplicates = (values) => values.filter((v, i) => values.indexOf(v) !== i);
  const colour = (value) => value.replace(/^\{color\.([a-z_]+)\}$/, (_, name) => tokens.color[name] ?? value);

  for (const [what, values] of [
    ["environment id", environments.map((e) => e.id)],
    ["experience id", experiences.map((x) => x.id)],
    ["experience slug", experiences.map((x) => x.slug)],
  ]) {
    for (const d of new Set(duplicates(values))) problems.push(`${what} '${d}' appears more than once`);
  }
  for (const e of environments) {
    if (!presets.includes(e.preset_key) && !PENDING_PRESETS.has(e.preset_key)) {
      problems.push(`environment ${e.id}: preset_key '${e.preset_key}' is neither a tokens.json environment (${presets.join(", ")}) nor pending`);
    }
    const preset = tokens.environments[e.preset_key];
    if (preset && e.id === e.preset_key) {
      // While tokens.json still describes the six storefront backdrops, the reference data must not drift from it.
      if (e.label !== preset.label) problems.push(`environment ${e.id}: label '${e.label}' differs from tokens.json '${preset.label}'`);
      if (e.palette && e.palette[0].toUpperCase() !== colour(preset.bg).toUpperCase()) {
        problems.push(`environment ${e.id}: palette starts with ${e.palette[0]}, but its tokens.json backdrop is ${colour(preset.bg)}`);
      }
    }
  }
  for (const key of PENDING_PRESETS) {
    if (presets.includes(key)) problems.push(`preset '${key}' is a tokens.json environment now: remove it from PENDING_PRESETS in validate.mjs`);
  }
  for (const pack of PENDING_MOTIF_PACKS) {
    if (motifIds.has(pack)) problems.push(`motif pack '${pack}' is in the motif library now: remove it from PENDING_MOTIF_PACKS in validate.mjs`);
  }
  const environmentIds = new Set(environments.map((e) => e.id));
  const brandColours = new Set(Object.values(tokens.color).map((c) => c.toUpperCase()));
  for (const x of experiences) {
    if (!environmentIds.has(x.environment)) problems.push(`experience ${x.id}: environment '${x.environment}' is not in environments`);
    for (const a of x.avatars) if (!familyIds.has(a)) problems.push(`experience ${x.id}: avatar '${a}' is not a family in families.json`);
    for (const m of x.motif_pack ?? []) {
      if (!motifIds.has(m) && !PENDING_MOTIF_PACKS.has(m)) {
        problems.push(`experience ${x.id}: motif_pack '${m}' is neither a motif (or motif tag) in design-tokens/motifs/index.json nor pending`);
      }
    }
    if (!brandColours.has(x.surface.accent.toUpperCase())) problems.push(`experience ${x.id}: accent ${x.surface.accent} is not a tokens.json brand colour`);
    for (const w of x.season ?? []) {
      // Calendar-date windows run forwards; month-day windows recur and may wrap the new year.
      const dates = !w.starts_on.startsWith("--") && !w.ends_on.startsWith("--");
      if (dates && w.starts_on > w.ends_on) problems.push(`experience ${x.id}: season '${w.label}' ends before it starts`);
    }
  }
  // An experience's default style is a design-spec style value.
  const specStyles = ajv.getSchema("https://aakar.studio/schemas/design-spec.v1.json").schema.properties.style.enum;
  const experienceStyles = ajv.getSchema("https://aakar.studio/schemas/experience.v1.json").schema.$defs.experience.properties.style.enum;
  if (JSON.stringify(specStyles) !== JSON.stringify(experienceStyles)) {
    problems.push(`experience.v1.json style values (${experienceStyles}) differ from design-spec.v1.json (${specStyles})`);
  }

  if (problems.length) {
    failed++;
    for (const p of problems) console.error(`✗ experiences.json: ${p}`);
  } else {
    const pending = environments.filter((e) => PENDING_PRESETS.has(e.preset_key)).map((e) => e.id);
    const packs = experiences.flatMap((x) => x.motif_pack ?? []).filter((m) => PENDING_MOTIF_PACKS.has(m));
    console.log(`✓ experiences.json: ${experiences.length} experiences name known environments, families and motifs; `
      + `${environments.length} environments (preset pending: ${pending.join(", ") || "none"}; motif pack pending: ${packs.join(", ") || "none"})`);
  }
}

for (const f of readdirSync(join(root, "openapi")).filter((f) => f.endsWith(".yaml"))) {
  const doc = YAML.parse(readFileSync(join(root, "openapi", f), "utf8"));
  if (!doc.openapi || !doc.paths) throw new Error(`${f} is not an OpenAPI document`);
  console.log(`✓ openapi/${f} parses (${Object.keys(doc.paths).length} paths)`);
}
if (failed) process.exit(1);
