export interface PageHeaderProps {
  eyebrow?: string;
  title: string;
  description?: React.ReactNode;
  actions?: React.ReactNode;
}

export function PageHeader({ eyebrow, title, description, actions }: PageHeaderProps) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="grid gap-1.5">
        {eyebrow && <span className="ak-eyebrow">{eyebrow}</span>}
        <h1 className="font-display text-[34px] font-semibold leading-none">{title}</h1>
        {description && <p className="max-w-prose text-sm text-surface-muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}
