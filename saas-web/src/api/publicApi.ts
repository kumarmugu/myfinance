import { api } from './client';

export interface PublicConfig {
  trialDays: number;
  paymentProvider: string;
  stripePublishableKey: string;
  loginUrl: string;
}

export interface PlanView {
  code: string;
  name: string;
  description: string;
  price: number;
  currency: string;
  billingPeriod: string | null;
  trialDays: number;
  features: string[];
  recommended: boolean;
  displayOrder: number;
}

export async function fetchPublicConfig(): Promise<PublicConfig> {
  const { data } = await api.get<PublicConfig>('/public/config');
  return data;
}

export async function fetchPlans(): Promise<PlanView[]> {
  const { data } = await api.get<PlanView[]>('/public/plans');
  return data;
}
