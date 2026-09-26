/** Stage surface: deep indigo for the viewer, Create and (later) AR. tokens.css swaps the variables on data-surface. */
export default function StageLayout({ children }: { children: React.ReactNode }) {
  return (
    <div data-surface="stage" className="min-h-dvh bg-jaali">
      {children}
    </div>
  );
}
