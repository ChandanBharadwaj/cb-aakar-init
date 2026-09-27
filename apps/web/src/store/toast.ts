"use client";

import { create } from "zustand";

export interface ToastInput {
  message: string;
  action?: { href: string; label: string };
  tone?: "neutral" | "success" | "danger";
}

export interface Toast extends ToastInput {
  id: number;
}

interface ToastState {
  toasts: Toast[];
  push(toast: ToastInput, ms?: number): number;
  dismiss(id: number): void;
}

let seq = 0;

export const useToastStore = create<ToastState>()((set) => ({
  toasts: [],
  push: (toast, ms = 5000) => {
    const id = ++seq;
    set((s) => ({ toasts: [...s.toasts.slice(-2), { ...toast, id }] }));
    if (ms > 0) setTimeout(() => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })), ms);
    return id;
  },
  dismiss: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));

/** Show a short message at the bottom of the page, e.g. after adding to the cart. */
export function toast(input: ToastInput, ms?: number): number {
  return useToastStore.getState().push(input, ms);
}
