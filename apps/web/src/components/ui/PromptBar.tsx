"use client";

import { useRouter } from "next/navigation";
import { useId, useState } from "react";

export interface PromptBarProps {
  placeholder?: string;
  examples?: readonly string[];
  buttonLabel?: string;
  autoFocus?: boolean;
  /** Where the prompt goes; default /create?prompt=… */
  target?: string;
  className?: string;
}

/** The central prompt bar from the Home board. Submitting navigates to /create?prompt=… */
export function PromptBar({
  placeholder = "What do you want to create today?",
  examples = [],
  buttonLabel = "Create",
  autoFocus = false,
  target = "/create",
  className,
}: PromptBarProps) {
  const router = useRouter();
  const [value, setValue] = useState("");
  const id = useId();

  function go(prompt: string) {
    const trimmed = prompt.trim();
    router.push(trimmed ? `${target}?prompt=${encodeURIComponent(trimmed)}` : target);
  }

  return (
    <div className={["grid gap-3", className].filter(Boolean).join(" ")}>
      <form
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          go(value);
        }}
        className="flex max-w-[560px] items-center gap-2.5 rounded-pill border border-surface-border bg-surface-card p-2 pl-5 shadow-card"
      >
        <label htmlFor={id} className="sr-only">
          Describe what you want to create
        </label>
        <input
          id={id}
          type="text"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder={placeholder}
          maxLength={500}
          autoFocus={autoFocus}
          autoComplete="off"
          className="min-w-0 flex-1 bg-transparent text-[15px] outline-none placeholder:text-surface-muted"
        />
        <button type="submit" className="ak-btn ak-btn-primary ak-btn-pill px-5">
          {buttonLabel}
        </button>
      </form>
      {examples.length > 0 && (
        <ul className="flex flex-wrap gap-2" aria-label="Example ideas">
          {examples.map((ex) => (
            <li key={ex}>
              <button type="button" className="ak-chip" onClick={() => go(ex)}>
                {ex}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
