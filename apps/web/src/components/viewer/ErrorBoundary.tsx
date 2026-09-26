"use client";

import { Component, type ReactNode } from "react";

interface Props {
  fallback: ReactNode;
  onError?(error: Error): void;
  /** Changing this key resets the boundary (e.g. a new model URL). */
  resetKey?: unknown;
  children: ReactNode;
}

interface State {
  failed: boolean;
  key?: unknown;
}

/** Minimal error boundary; works inside the R3F Canvas as well as in the DOM. */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { failed: false, key: undefined };

  static getDerivedStateFromError(): Partial<State> {
    return { failed: true };
  }

  static getDerivedStateFromProps(props: Props, state: State): Partial<State> | null {
    if (state.key !== props.resetKey) return { failed: false, key: props.resetKey };
    return null;
  }

  componentDidCatch(error: Error): void {
    this.props.onError?.(error);
  }

  render(): ReactNode {
    return this.state.failed ? this.props.fallback : this.props.children;
  }
}
