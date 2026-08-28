import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { fetchPublicConfig, type PublicConfig } from '../api/publicApi';

interface ConfigState {
  config: PublicConfig | null;
  loading: boolean;
}

const ConfigContext = createContext<ConfigState>({ config: null, loading: true });

export function ConfigProvider({ children }: { children: ReactNode }) {
  const [config, setConfig] = useState<PublicConfig | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    fetchPublicConfig()
      .then((c) => { if (active) setConfig(c); })
      .catch(() => { /* config is best-effort; UI has sensible fallbacks */ })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  return <ConfigContext.Provider value={{ config, loading }}>{children}</ConfigContext.Provider>;
}

export function useConfig(): ConfigState {
  return useContext(ConfigContext);
}

/** The finance-app login URL from server config, with a dev fallback. */
export function useLoginUrl(): string {
  const { config } = useConfig();
  return config?.loginUrl || 'http://localhost:5173';
}
