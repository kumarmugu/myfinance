import { api } from './client';

export interface SubscriptionView {
  state: string;
  planCode: string | null;
  planName: string | null;
  inTrial: boolean;
  trialEndsAt: string | null;
  trialDaysRemaining: number;
  currentPeriodEnd: string | null;
  cancelledAt: string | null;
  grantsAccess: boolean;
}

export interface PaymentView {
  id: number;
  amount: number | null;
  currency: string | null;
  status: string | null;
  method: string | null;
  receiptUrl: string | null;
  failureReason: string | null;
  date: string | null;
}

export async function getSubscription(): Promise<SubscriptionView> {
  const { data } = await api.get<SubscriptionView>('/portal/subscription');
  return data;
}

export async function getPayments(): Promise<PaymentView[]> {
  const { data } = await api.get<PaymentView[]>('/portal/payments');
  return data;
}

export async function startCheckout(planCode: string, method: string): Promise<{ redirectUrl: string }> {
  const { data } = await api.post('/portal/checkout', { planCode, method });
  return data;
}

export async function cancelSubscription(atPeriodEnd = true): Promise<{ message: string }> {
  const { data } = await api.post(`/portal/cancel?atPeriodEnd=${atPeriodEnd}`);
  return data;
}
