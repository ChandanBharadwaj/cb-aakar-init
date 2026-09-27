"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { OtpRequestResult, Problem } from "@/lib/api/types";
import { formatPhone } from "@/lib/identity";
import { BloomMark } from "@/components/brand/BloomMark";
import { useCartStore } from "@/store/cart";
import { useSession, useSessionStore } from "@/store/session";
import { toast } from "@/store/toast";

type Step = "phone" | "code" | "done";

const PHONE_RE = /^[6-9][0-9]{9}$/;

function otpMessage(p: Problem & { code: string }): string {
  switch (p.code) {
    case "otp_invalid":
      return "That code isn't right. Check the digits and try again.";
    case "otp_expired":
      return "That code has expired. Send a new one.";
    case "otp_rate_limited":
      return "Too many codes for this number just now. Wait a minute and try again.";
    case "unreachable":
      return p.detail ?? "Couldn't reach the studio.";
    default:
      return p.detail ?? p.title ?? "Something went wrong.";
  }
}

function attachedMessage(attached: { designs?: number; cart_items?: number }): string | undefined {
  const d = attached.designs ?? 0;
  const c = attached.cart_items ?? 0;
  const parts: string[] = [];
  if (d > 0) parts.push(`${d} ${d === 1 ? "design" : "designs"}`);
  if (c > 0) parts.push("cart");
  if (parts.length === 0) return undefined;
  return `Your ${parts.join(" and ")} came with you.`;
}

/** /signin: +91 phone → OTP (with the mock dev code chip in the local profile) → token → back to `next`. */
export function SignIn({ next }: { next: string }) {
  const router = useRouter();
  const { status } = useSession();
  const [step, setStep] = useState<Step>("phone");
  const [digits, setDigits] = useState("");
  const [request, setRequest] = useState<OtpRequestResult>();
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [errorCode, setErrorCode] = useState<string>();
  const phoneId = useId();
  const codeId = useId();
  const codeRef = useRef<HTMLInputElement>(null);

  // Already signed in (and not mid-flow): nothing to do here.
  useEffect(() => {
    if (status === "user" && step === "phone") router.replace(next);
  }, [status, step, next, router]);

  useEffect(() => {
    if (step === "code") codeRef.current?.focus();
  }, [step]);

  const phone = `+91${digits}`;
  const phoneValid = PHONE_RE.test(digits);

  async function sendCode() {
    if (!phoneValid) {
      setError("Enter the 10-digit mobile number, starting 6–9.");
      return;
    }
    setBusy(true);
    setError(undefined);
    setErrorCode(undefined);
    try {
      const r = await api.auth.requestOtp(phone);
      setRequest(r);
      setCode("");
      setStep("code");
    } catch (err) {
      const p = toProblem(err);
      setError(otpMessage(p));
      setErrorCode(p.code);
    } finally {
      setBusy(false);
    }
  }

  async function verify() {
    if (!request) return;
    const trimmed = code.trim();
    if (trimmed.length < 4) {
      setError("Enter the code we sent.");
      return;
    }
    setBusy(true);
    setError(undefined);
    setErrorCode(undefined);
    try {
      const session = await api.auth.verifyOtp(request.request_id, trimmed);
      useSessionStore.getState().signIn(session);
      setStep("done");
      const msg = attachedMessage(session.attached ?? {});
      if (msg) toast({ message: msg, tone: "success" });
      void useCartStore.getState().refresh();
      router.replace(next);
    } catch (err) {
      const p = toProblem(err);
      setError(otpMessage(p));
      setErrorCode(p.code);
      setBusy(false);
    }
  }

  return (
    <div className="ak-card relative mx-auto grid w-full max-w-md gap-6 overflow-hidden p-7 sm:p-8">
      <BloomMark size={200} className="pointer-events-none absolute -right-14 -top-12 opacity-[.06]" aria-hidden="true" />
      <div className="grid gap-1.5">
        <span className="ak-eyebrow">Sign in</span>
        <h1 className="font-display text-3xl font-semibold leading-tight">{step === "done" ? "Signed in" : step === "code" ? "Enter your code" : "Your phone number"}</h1>
        <p className="text-sm text-surface-muted">
          {step === "done"
            ? "Welcome back. Taking you onwards…"
            : step === "code"
              ? `We sent a code to ${formatPhone(phone)}.`
              : "One code, no password. Your designs and cart come with you."}
        </p>
      </div>

      {step === "phone" && (
        <form
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            void sendCode();
          }}
        >
          <div className="grid gap-1.5">
            <label htmlFor={phoneId} className="ak-label">
              Mobile number
            </label>
            <div className="flex items-stretch overflow-hidden rounded-control border border-surface-border bg-surface-card focus-within:border-surface-accent">
              <span className="grid place-items-center border-r border-surface-border bg-surface-bg px-3.5 text-sm font-semibold text-surface-muted" aria-hidden="true">
                +91
              </span>
              <input
                id={phoneId}
                type="tel"
                inputMode="numeric"
                autoComplete="tel-national"
                pattern="[0-9]*"
                maxLength={10}
                value={digits}
                onChange={(e) => {
                  setDigits(e.target.value.replace(/\D/g, "").slice(0, 10));
                  setError(undefined);
                }}
                placeholder="98765 43210"
                className="min-w-0 flex-1 bg-transparent px-3.5 py-2.5 text-[15px] tracking-wide outline-none placeholder:text-surface-muted/70"
                aria-describedby={error ? `${phoneId}-err` : undefined}
                aria-invalid={error ? true : undefined}
                autoFocus
              />
            </div>
            <span className="sr-only">Indian mobile, 10 digits</span>
          </div>
          {error && (
            <p id={`${phoneId}-err`} role="alert" className="text-xs text-danger">
              {error}
            </p>
          )}
          <button type="submit" className="ak-btn ak-btn-primary" disabled={busy || digits.length < 10} aria-busy={busy}>
            {busy ? "Sending your code…" : "Send code"}
          </button>
        </form>
      )}

      {step === "code" && request && (
        <form
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            void verify();
          }}
        >
          {request.dev_code && (
            <button
              type="button"
              className="ak-chip justify-start border-marigold bg-marigold/15 text-surface-text"
              onClick={() => {
                setCode(request.dev_code ?? "");
                setError(undefined);
                codeRef.current?.focus();
              }}
              title="The local profile returns the code instead of sending an SMS (ADR-0013)"
            >
              <span className="rounded-pill bg-marigold px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-ink">Mock OTP</span>
              dev code {request.dev_code} · tap to fill
            </button>
          )}
          <div className="grid gap-1.5">
            <label htmlFor={codeId} className="ak-label">
              6-digit code
            </label>
            <input
              id={codeId}
              ref={codeRef}
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              pattern="[0-9]*"
              maxLength={8}
              value={code}
              onChange={(e) => {
                setCode(e.target.value.replace(/\D/g, "").slice(0, 8));
                setError(undefined);
              }}
              placeholder="••••••"
              className="ak-input text-center font-mono text-xl tracking-[0.4em]"
              aria-describedby={error ? `${codeId}-err` : undefined}
              aria-invalid={error ? true : undefined}
            />
          </div>
          {error && (
            <p id={`${codeId}-err`} role="alert" className="text-xs text-danger">
              {error}
            </p>
          )}
          <button type="submit" className="ak-btn ak-btn-primary" disabled={busy || code.length < 4} aria-busy={busy}>
            {busy ? "Checking…" : "Sign in"}
          </button>
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-surface-muted">
            <button
              type="button"
              className="underline-offset-2 hover:underline"
              onClick={() => {
                setStep("phone");
                setError(undefined);
                setErrorCode(undefined);
              }}
            >
              Change number
            </button>
            <button type="button" className="underline-offset-2 hover:underline disabled:opacity-50" onClick={() => void sendCode()} disabled={busy}>
              {errorCode === "otp_expired" ? "Send a new code" : "Resend code"}
            </button>
          </div>
        </form>
      )}

      {step === "done" && (
        <div className="grid gap-3">
          <span className="grid h-10 w-10 place-items-center rounded-full bg-sage text-lg font-bold text-cream" aria-hidden="true">
            ✓
          </span>
          <Link href={next} className="ak-btn ak-btn-secondary ak-btn-pill justify-self-start">
            Continue
          </Link>
        </div>
      )}

      <p className="text-[11px] leading-relaxed text-surface-muted">
        By signing in you agree to receive order updates by SMS and, if you choose, WhatsApp. Custom pieces are replaced or refunded for defects and mismatches
        only.
      </p>
    </div>
  );
}
