# Marketing media

Drop product images/screenshots here. Anything in `public/` is served at the site root,
so a file named `hero-dashboard.png` here is available at `/media/hero-dashboard.png`.

The site already references these paths (see `src/content/site.ts`). Add a file with the
matching name and it appears automatically; if a file is missing, a labeled placeholder shows
instead (so the page never breaks).

## Expected filenames

| File | Where it appears |
|------|------------------|
| `hero-dashboard.png` | Home hero |
| `feature-portfolio.png` | Features → Investment Portfolio |
| `feature-dividends.png` | Features → Dividend Tracking |
| `feature-crypto.png` | Features → Crypto Holdings |
| `feature-fd.png` | Features → Fixed Deposits |
| `feature-budget.png` | Features → Budget & Expenses |
| `feature-reports.png` | Features → Reports & Net Worth |

## Guidance

- Recommended width ~1200px; PNG or JPG/WebP all work.
- To change which file a section uses, edit the `image:` value in `src/content/site.ts`.
- To add a demo video, set `videoUrl` for the hero or a feature in `src/content/site.ts`.
- Use real screenshots only — avoid mock-ups that could be mistaken for the actual product.
