// Validates every JSON Schema compiles, every example validates against its schema,
// and every OpenAPI document parses. Run with `pnpm --filter @aakar/contracts validate`.
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
  ["examples/design.completed.example.json", "https://aakar.studio/schemas/events/design.completed.v1.json"],
  // The families seed in design-tokens is a contract instance too (Flyway V9 seeds from it; a drift test guards both).
  ["../design-tokens/families.json", "https://aakar.studio/schemas/template-family.v1.json"],
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

for (const f of readdirSync(join(root, "openapi")).filter((f) => f.endsWith(".yaml"))) {
  const doc = YAML.parse(readFileSync(join(root, "openapi", f), "utf8"));
  if (!doc.openapi || !doc.paths) throw new Error(`${f} is not an OpenAPI document`);
  console.log(`✓ openapi/${f} parses (${Object.keys(doc.paths).length} paths)`);
}
if (failed) process.exit(1);
