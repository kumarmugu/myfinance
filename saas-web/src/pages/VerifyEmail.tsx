import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { verifyEmail } from '../api/authApi';
import { site } from '../content/site';
import { useSeo } from '../hooks/useSeo';
import { useLoginUrl } from '../context/ConfigContext';

export default function VerifyEmail() {
  useSeo(`Verify email - ${site.brand}`);
  const [params] = useSearchParams();
  const token = params.get('token');
  const loginUrl = useLoginUrl();
  const [status, setStatus] = useState<'verifying' | 'ok' | 'error'>('verifying');

  useEffect(() => {
    if (!token) { setStatus('error'); return; }
    verifyEmail(token).then(() => setStatus('ok')).catch(() => setStatus('error'));
  }, [token]);

  return (
    <div className="max-w-md mx-auto px-4 py-20 text-center">
      {status === 'verifying' && <p className="text-slate-600">Verifying your email…</p>}
      {status === 'ok' && (
        <>
          <h1 className="text-2xl font-bold text-slate-900">Email verified</h1>
          <p className="mt-3 text-slate-600">Your account is active. You can now log in to MyFinance.</p>
          <a href={loginUrl} className="inline-block mt-6 bg-indigo-600 text-white font-semibold px-6 py-3 rounded-lg hover:bg-indigo-700">
            Go to login
          </a>
        </>
      )}
      {status === 'error' && (
        <>
          <h1 className="text-2xl font-bold text-slate-900">Verification failed</h1>
          <p className="mt-3 text-slate-600">This link is invalid or has expired. Please sign up again or request a new link.</p>
        </>
      )}
    </div>
  );
}
