"use client";

import { create } from "zustand";
import { api, isApiError } from "@/lib/api/client";
import type { Session, User } from "@/lib/api/types";
import { clearToken, getToken, setToken } from "@/lib/identity";

export type SessionStatus = "loading" | "guest" | "user";

interface SessionState {
  status: SessionStatus;
  user?: User;
  /** Reads the stored token and asks the API who we are. Safe to call again after an identity change. */
  load(): Promise<void>;
  /** Stores the token from `POST /api/auth/otp/verify` and switches to the user. */
  signIn(session: Session): void;
  /** Revokes the token on the API (best effort), forgets it locally, back to guest. */
  signOut(): Promise<void>;
  setUser(user: User): void;
}

let loadSeq = 0;

export const useSessionStore = create<SessionState>()((set, get) => ({
  status: "loading",
  user: undefined,

  load: async () => {
    const seq = ++loadSeq;
    const token = getToken();
    if (!token) {
      set({ status: "guest", user: undefined });
      return;
    }
    try {
      const user = await api.auth.me();
      if (seq === loadSeq) set({ status: "user", user });
    } catch (err) {
      if (seq !== loadSeq) return;
      // A 401 already cleared the token in the client. Anything else (API down) keeps the
      // token for next time but shows the guest state so pages don't wait forever.
      if (isApiError(err) && err.status === 401) clearToken();
      set({ status: "guest", user: undefined });
    }
  },

  signIn: (session) => {
    set({ status: "user", user: session.user });
    setToken(session.access_token);
  },

  signOut: async () => {
    if (get().status === "user") {
      try {
        await api.auth.logout();
      } catch {
        /* the token is forgotten locally either way */
      }
    }
    set({ status: "guest", user: undefined });
    clearToken();
  },

  setUser: (user) => set({ user, status: "user" }),
}));

/** `{ status, user, signOut }` for nav and pages. */
export function useSession() {
  return useSessionStore();
}

/** Name to show for a user: their name, else their phone. */
export function displayName(user: User | undefined, formatPhone: (p: string) => string): string {
  if (!user) return "";
  return user.name?.trim() || formatPhone(user.phone);
}
