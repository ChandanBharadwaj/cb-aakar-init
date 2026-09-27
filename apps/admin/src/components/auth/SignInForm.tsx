"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { BloomMark } from "@/components/brand/BloomMark";
import { Field } from "@/components/ui/Field";
import { MockNotice } from "@/components/ui/MockNotice";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { toProblem } from "@/lib/api/client";
import type { Problem } from "@/lib/api/types";
import { IS_LOCAL, SEEDED_ACCOUNTS } from "@/lib/profile";
import { useSession } from "@/store/session";

function safeNext(raw: string | null): string {
  return raw && raw.startsWith("/") && !raw.startsWith("//") && !raw.startsWith("/signin") ? raw : "/";
}

export function SignInForm() {
  const router = useRouter();
  // `?next=` is read after mount (not via useSearchParams) so the form itself is server-rendered.
  const [next, setNext] = useState("/");
  const status = useSession((s) => s.status);
  const restore = useSession((s) => s.restore);
  const signIn = useSession((s) => s.signIn);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>();

  useEffect(() => {
    setNext(safeNext(new URLSearchParams(window.location.search).get("next")));
  }, []);

  // Already signed in (token still valid)? Go straight through.
  useEffect(() => {
    if (status === "unknown") void restore();
    if (status === "signed_in") router.replace(next);
  }, [status, restore, router, next]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setProblem(undefined);
    try {
      await signIn(email.trim(), password);
      router.replace(next);
    } catch (err) {
      const p = toProblem(err);
      setProblem(p.status === 401 || p.code === "unauthenticated" ? { ...p, title: "Sign-in failed", detail: p.detail ?? "That email and password don't match a staff account." } : p);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="ak-card grid w-full max-w-[420px] gap-6 p-8">
      <div className="grid gap-3">
        <div className="flex items-center gap-2.5">
          <BloomMark size={30} />
          <span className="font-display text-2xl font-bold leading-none">Aakar</span>
          <span className="ak-eyebrow">Studio</span>
        </div>
        <h1 className="font-display text-[30px] font-semibold leading-tight">Staff sign-in</h1>
        <p className="text-sm text-surface-muted">Management portal for orders, pricing, materials and the catalog. Staff accounts only; customers sign in on the storefront.</p>
      </div>

      <form onSubmit={submit} className="grid gap-4" aria-busy={busy}>
        <Field label="Email">
          {(id) => <input id={id} type="email" name="email" autoComplete="username" required className="ak-input" value={email} onChange={(e) => setEmail(e.target.value)} disabled={busy} />}
        </Field>
        <Field label="Password">
          {(id) => <input id={id} type="password" name="password" autoComplete="current-password" required minLength={8} className="ak-input" value={password} onChange={(e) => setPassword(e.target.value)} disabled={busy} />}
        </Field>
        {problem && <ProblemCard compact problem={problem} />}
        <button type="submit" className="ak-btn ak-btn-primary" disabled={busy || !email || !password}>
          {busy ? "Signing in…" : "Sign in"}
        </button>
      </form>

      {IS_LOCAL && (
        <MockNotice>
          <span className="grid gap-1">
            <span>
              Seeded studio account · <code className="font-mono text-[12px]">{SEEDED_ACCOUNTS[0].email}</code> / <code className="font-mono text-[12px]">{SEEDED_ACCOUNTS[0].password}</code> (owner)
            </span>
            <span>
              Read-only configuration role · <code className="font-mono text-[12px]">{SEEDED_ACCOUNTS[1].email}</code> / <code className="font-mono text-[12px]">{SEEDED_ACCOUNTS[1].password}</code> (studio)
            </span>
            <span className="text-surface-muted">Local profile only; the production API refuses seeded accounts.</span>
          </span>
        </MockNotice>
      )}
    </div>
  );
}
