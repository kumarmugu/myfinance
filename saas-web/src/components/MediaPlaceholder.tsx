import { useState } from 'react';

/**
 * Renders a product image if a real asset exists at {@code src}, otherwise a clearly-labeled
 * placeholder. If the image fails to load (file not present under public/media), it falls back
 * to the placeholder so the page never shows a broken image.
 *
 * To add real media: drop a file in `saas-web/public/media/` and reference it as
 * `/media/<filename>` in `src/content/site.ts` (this is already wired for hero + each feature).
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
  const [failed, setFailed] = useState(false);

  const showImage = !!src && !failed;

  if (showImage) {
    return (
      <img
        src={src}
        alt={alt}
        loading="lazy"
        onError={() => setFailed(true)}
        className={`rounded-xl border border-slate-200 object-cover w-full ${className}`}
      />
    );
  }

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
