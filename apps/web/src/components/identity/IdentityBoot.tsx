"use client";

import { useEffect } from "react";
import { ensureGuestId, onIdentityChange } from "@/lib/identity";
import { useCartStore } from "@/store/cart";
import { useSessionStore } from "@/store/session";

/**
 * Mounted once in the root layout. Creates the guest id, loads the session and the cart,
 * and reloads both whenever the token is set or cleared (sign-in, sign-out, a 401).
 */
export function IdentityBoot() {
  useEffect(() => {
    ensureGuestId();
    const boot = () => {
      void useSessionStore.getState().load();
      void useCartStore.getState().refresh();
    };
    boot();
    return onIdentityChange(boot);
  }, []);
  return null;
}
