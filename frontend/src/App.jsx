import { Home, UtensilsCrossed } from 'lucide-react';
import HomePage from './pages/HomePage.jsx';
import GuestMenuPage from './pages/GuestMenuPage.jsx';
import StudioPage from './pages/StudioPage.jsx';
import AdminPage from './pages/AdminPage.jsx';
import Navbar from './components/Navbar.jsx';
import AuthModal from './components/AuthModal.jsx';
import { useAuth } from './context/AuthContext.jsx';
import { linkProps, matchRoute, useLocation } from './utils/router.js';

export default function App() {
  const { pathname } = useLocation();
  const { authDialog, closeAuth } = useAuth();
  const route = matchRoute(pathname);

  return (
    <>
      {route.name === 'home' && <HomePage />}
      {route.name === 'menu' && <GuestMenuPage key={route.slug} slug={route.slug} />}
      {route.name === 'studio' && <StudioPage />}
      {route.name === 'admin' && <AdminPage />}
      {route.name === 'not-found' && <NotFound />}
      {authDialog && <AuthModal onClose={closeAuth} />}
    </>
  );
}

function NotFound() {
  return (
    <div className="min-h-screen">
      <Navbar />
      <div className="mx-auto grid max-w-xl place-items-center px-6 py-24 text-center">
        <UtensilsCrossed size={30} className="text-zinc-600" aria-hidden="true" />
        <h1 className="mt-4 font-display text-4xl font-semibold">This page isn’t on the menu</h1>
        <a {...linkProps('/')} className="tap mt-6 flex h-12 items-center gap-2 rounded-full border hairline px-5 text-sm text-zinc-200">
          <Home size={16} aria-hidden="true" /> Atelier home
        </a>
      </div>
    </div>
  );
}
