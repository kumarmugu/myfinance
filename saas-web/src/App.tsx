import type { ReactNode } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { ConfigProvider } from './context/ConfigContext';
import MarketingLayout from './components/MarketingLayout';
import Home from './pages/Home';
import Features from './pages/Features';
import HowItWorks from './pages/HowItWorks';
import Pricing from './pages/Pricing';
import Faq from './pages/Faq';
import Contact from './pages/Contact';
import { Terms, Privacy } from './pages/Legal';
import Signup from './pages/Signup';
import VerifyEmail from './pages/VerifyEmail';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import Portal from './pages/Portal';

/**
 * App shell. Marketing + auth pages render inside MarketingLayout (header/footer).
 * The billing portal also uses the layout so users always have consistent navigation,
 * but it never exposes finance-app internal pages.
 */
export default function App() {
  const withLayout = (el: ReactNode) => <MarketingLayout>{el}</MarketingLayout>;

  return (
    <ConfigProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={withLayout(<Home />)} />
          <Route path="/features" element={withLayout(<Features />)} />
          <Route path="/how-it-works" element={withLayout(<HowItWorks />)} />
          <Route path="/pricing" element={withLayout(<Pricing />)} />
          <Route path="/faq" element={withLayout(<Faq />)} />
          <Route path="/contact" element={withLayout(<Contact />)} />
          <Route path="/legal/terms" element={withLayout(<Terms />)} />
          <Route path="/legal/privacy" element={withLayout(<Privacy />)} />

          <Route path="/signup" element={withLayout(<Signup />)} />
          <Route path="/verify-email" element={withLayout(<VerifyEmail />)} />
          <Route path="/forgot-password" element={withLayout(<ForgotPassword />)} />
          <Route path="/reset-password" element={withLayout(<ResetPassword />)} />

          <Route path="/portal/billing" element={withLayout(<Portal />)} />
          <Route path="/portal" element={withLayout(<Portal />)} />

          <Route path="*" element={withLayout(<Home />)} />
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  );
}
