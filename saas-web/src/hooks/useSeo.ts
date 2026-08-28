import { useEffect } from 'react';

/**
 * Minimal client-side SEO: sets the document title and meta description per page.
 * (For full SEO, pre-rendering/SSR would be added later; titles/descriptions here
 * improve UX and are picked up by crawlers that execute JS.)
 */
export function useSeo(title: string, description?: string): void {
  useEffect(() => {
    const previous = document.title;
    document.title = title;
    let metaEl: HTMLMetaElement | null = null;
    if (description) {
      metaEl = document.querySelector('meta[name="description"]');
      if (metaEl) {
        metaEl.setAttribute('content', description);
      }
    }
    return () => {
      document.title = previous;
    };
  }, [title, description]);
}
