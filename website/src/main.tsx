import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { holdPrerender, revealApp } from './app/reveal';
import { router } from './app/router';
import './styles.css';

if (holdPrerender()) {
  // the landing page reveals itself; any other first route reveals once the router settles there
  const unsub = router.subscribe((s) => {
    if (s.navigation.state === 'idle' && s.location.pathname !== '/') {
      revealApp();
      unsub();
    }
  });
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
