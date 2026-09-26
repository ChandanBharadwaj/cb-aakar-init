import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { FlatCompat } from "@eslint/eslintrc";

const __dirname = dirname(fileURLToPath(import.meta.url));
const compat = new FlatCompat({ baseDirectory: __dirname });

const eslintConfig = [
  ...compat.extends("next/core-web-vitals", "next/typescript"),
  {
    ignores: [".next/**", "out/**", "node_modules/**", "next-env.d.ts", "src/lib/api/schema.d.ts"],
  },
  {
    rules: {
      // React Three Fiber uses three.js props (args, position, intensity ...) on JSX intrinsics.
      "react/no-unknown-property": "off",
    },
  },
];

export default eslintConfig;
