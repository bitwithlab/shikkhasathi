/**
 * Prerendered landing (dist/index.prerendered.html) stays on screen until the live page has rendered,
 * so first paint is instant and the swap causes no layout shift. Called by LandingPage, any non-landing
 * first route, or a safety timeout.
 */
let done = false;
export function revealApp() {
  if (done || typeof document === 'undefined') return;
  done = true;
  document.getElementById('ssr')?.remove();
  document.getElementById('root')?.removeAttribute('hidden');
}

/** Move server markup out of #root before React takes it over; returns true when there was any. */
export function holdPrerender() {
  const root = document.getElementById('root');
  if (!root || !root.firstElementChild) return false;
  const ssr = document.createElement('div');
  ssr.id = 'ssr';
  while (root.firstChild) ssr.appendChild(root.firstChild);
  root.before(ssr);
  root.setAttribute('hidden', '');
  window.setTimeout(revealApp, 5000);
  return true;
}
