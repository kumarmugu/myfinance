/**
 * Trigger a browser download for a Blob. Isolated so it can be mocked in tests.
 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.rel = 'noopener';
  a.style.display = 'none';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  // Release the object URL on the next tick so the download can start first.
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
