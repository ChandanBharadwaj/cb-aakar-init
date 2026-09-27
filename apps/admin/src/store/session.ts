"use client";

import { create } from "zustand";
import { api, readToken, writeToken } from "@/lib/api/client";
import type { Staff, StaffRole } from "@/lib/api/types";

export type SessionStatus = "unknown" | "checking" | "signed_in" | "signed_out";

interface SessionState {
  status: SessionStatus;
  staff?: Staff;
  /** Read the stored token and confirm it with GET /admin/api/auth/me. */
  restore(): Promise<void>;
  signIn(email: string, password: string): Promise<Staff>;
  signOut(): void;
}

export const useSession = create<SessionState>()((set, get) => ({
  status: "unknown",
  staff: undefined,

  async restore() {
    if (get().status === "checking") return;
    const token = readToken();
    if (!token) {
      set({ status: "signed_out", staff: undefined });
      return;
    }
    set({ status: "checking" });
    try {
      const staff = await api.auth.me();
      set({ status: "signed_in", staff });
    } catch {
      // A 401 already cleared the token (client.ts). Anything else (API down) also ends the session.
      writeToken(null);
      set({ status: "signed_out", staff: undefined });
    }
  },

  async signIn(email, password) {
    const session = await api.auth.login({ email, password });
    writeToken(session.access_token);
    set({ status: "signed_in", staff: session.staff });
    return session.staff;
  },

  signOut() {
    writeToken(null);
    set({ status: "signed_out", staff: undefined });
  },
}));

/** Owners change configuration; the studio role runs fulfilment and reads the rest. */
export function canWrite(role: StaffRole | undefined): boolean {
  return role === "owner";
}

export function useCanWrite(): boolean {
  return useSession((s) => canWrite(s.staff?.role));
}
