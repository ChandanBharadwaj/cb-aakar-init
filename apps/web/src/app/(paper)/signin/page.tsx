import type { Metadata } from "next";
import { safeNext } from "@/lib/identity";
import { SignIn } from "@/components/identity/SignIn";

export const metadata: Metadata = { title: "Sign in" };

interface SignInPageProps {
  searchParams: Promise<{ next?: string }>;
}

export default async function SignInPage({ searchParams }: SignInPageProps) {
  const { next } = await searchParams;
  return (
    <main className="flex flex-1 items-start justify-center px-4 py-10 sm:py-16">
      <SignIn next={safeNext(next)} />
    </main>
  );
}
