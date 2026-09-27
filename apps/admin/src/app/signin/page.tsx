import type { Metadata } from "next";
import { SignInForm } from "@/components/auth/SignInForm";

export const metadata: Metadata = { title: "Sign in" };

export default function SignInPage() {
  return (
    <div data-surface="paper" className="grid min-h-dvh place-items-center bg-jaali px-4 py-10">
      <SignInForm />
    </div>
  );
}
