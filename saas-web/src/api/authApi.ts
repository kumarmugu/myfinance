import { api, setToken } from './client';

export interface SignupInput {
  fullName: string;
  email: string;
  password: string;
  planCode?: string;
  acceptTerms: boolean;
}

export interface LoginInput {
  email: string;
  password: string;
}

export async function signup(
  input: SignupInput,
  honeypot?: string,
): Promise<{ message: string; provisioned: boolean }> {
  const headers = honeypot ? { 'X-Honeypot': honeypot } : undefined;
  const { data } = await api.post('/signup', input, { headers });
  return data;
}

export async function login(input: LoginInput): Promise<{ token: string; email: string; customerId: number }> {
  const { data } = await api.post('/auth/login', input);
  if (data?.token) {
    setToken(data.token);
  }
  return data;
}

export async function verifyEmail(token: string): Promise<{ message: string }> {
  const { data } = await api.post('/auth/verify-email', { token });
  return data;
}

export async function forgotPassword(email: string): Promise<{ message: string }> {
  const { data } = await api.post('/auth/forgot-password', { email });
  return data;
}

export async function resetPassword(token: string, newPassword: string): Promise<{ message: string }> {
  const { data } = await api.post('/auth/reset-password', { token, newPassword });
  return data;
}
