/**
 * Renders a product image if a real asset exists, otherwise a clearly-labeled placeholder.
 * The placeholder is deliberately obvious so it can never be mistaken for real product
 * functionality (per the requirement to avoid fake/misleading screenshots).
 */
export default function MediaPlaceholder({
  src,
  alt,
  label,
  className = '',
}: {
  src?: string;
  alt: string;
  label?: string;
  className?: string;
}) {
  // In this scaffold, real assets aren't bundled; always show the labeled placeholder.
  // When real images are added under public/media, switch to an <img> with onError fallback.
  return (
    <div
      role="img"
      aria-label={alt}
      className={`flex items-center justify-center rounded-xl border-2 border-dashed border-slate-300 bg-slate-100 text-slate-400 ${className}`}
      style={{ minHeight: 220 }}
      data-src={src}
    >
      <span className="text-sm font-medium">{label ?? 'Screenshot placeholder'}</span>
    </div>
  );
}
