"use client";

import { create } from "zustand";
import { api, toProblem } from "@/lib/api/client";
import type { Cart, CartItemPatch, Problem } from "@/lib/api/types";

export type CartStatus = "idle" | "loading" | "ready" | "error";

interface CartState {
  cart?: Cart;
  status: CartStatus;
  problem?: Problem;
  /** Items in the cart (sum of qty), kept optimistic while a change is in flight. */
  count: number;
  /** Item ids with a change in flight, for per-row disabled states. */
  pending: Record<string, boolean>;
  refresh(): Promise<void>;
  add(versionId: string, material: string, qty?: number): Promise<Cart>;
  update(itemId: string, patch: CartItemPatch): Promise<Cart>;
  remove(itemId: string): Promise<Cart>;
  clear(): Promise<Cart>;
  /** Replace the cart from a response elsewhere (e.g. after sign-in). */
  setCart(cart: Cart): void;
}

export function countItems(cart: Cart | undefined): number {
  return cart?.items.reduce((n, i) => n + i.qty, 0) ?? 0;
}

export const useCartStore = create<CartState>()((set, get) => {
  const settle = (cart: Cart): Cart => {
    set({ cart, status: "ready", problem: undefined, count: countItems(cart) });
    return cart;
  };
  const fail = (err: unknown): never => {
    set({ status: get().cart ? "ready" : "error", problem: toProblem(err), count: countItems(get().cart) });
    throw err;
  };
  const mark = (itemId: string, on: boolean) =>
    set((s) => {
      const pending = { ...s.pending };
      if (on) pending[itemId] = true;
      else delete pending[itemId];
      return { pending };
    });

  return {
    cart: undefined,
    status: "idle",
    problem: undefined,
    count: 0,
    pending: {},

    refresh: async () => {
      set((s) => ({ status: s.cart ? s.status : "loading" }));
      try {
        settle(await api.cart.get());
      } catch (err) {
        set({ status: get().cart ? "ready" : "error", problem: toProblem(err) });
      }
    },

    add: async (versionId, material, qty = 1) => {
      set((s) => ({ count: s.count + qty }));
      try {
        return settle(await api.cart.add({ version_id: versionId, material, qty }));
      } catch (err) {
        return fail(err);
      }
    },

    update: async (itemId, patch) => {
      const item = get().cart?.items.find((i) => i.id === itemId);
      if (item && patch.qty !== undefined) set((s) => ({ count: s.count - item.qty + (patch.qty ?? item.qty) }));
      mark(itemId, true);
      try {
        return settle(await api.cart.update(itemId, patch));
      } catch (err) {
        return fail(err);
      } finally {
        mark(itemId, false);
      }
    },

    remove: async (itemId) => {
      const item = get().cart?.items.find((i) => i.id === itemId);
      if (item) set((s) => ({ count: Math.max(0, s.count - item.qty) }));
      mark(itemId, true);
      try {
        return settle(await api.cart.remove(itemId));
      } catch (err) {
        return fail(err);
      } finally {
        mark(itemId, false);
      }
    },

    clear: async () => {
      set({ count: 0 });
      try {
        return settle(await api.cart.clear());
      } catch (err) {
        return fail(err);
      }
    },

    setCart: (cart) => settle(cart),
  };
});
