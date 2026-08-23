import { X, CheckCircle, AlertCircle, Info } from 'lucide-react';
import { useToast, ToastType } from '../contexts/ToastContext';

const STYLES: Record<ToastType, { bg: string; border: string; text: string; icon: typeof CheckCircle }> = {
  success: { bg: 'bg-green-50', border: 'border-green-300', text: 'text-green-800', icon: CheckCircle },
  error: { bg: 'bg-red-50', border: 'border-red-300', text: 'text-red-800', icon: AlertCircle },
  info: { bg: 'bg-blue-50', border: 'border-blue-300', text: 'text-blue-800', icon: Info },
};

export default function ToastContainer() {
  const { toasts, removeToast } = useToast();

  if (toasts.length === 0) return null;

  return (
    <div className="fixed top-4 right-4 z-[9999] flex flex-col gap-2 max-w-sm w-full pointer-events-none">
      {toasts.map(toast => {
        const style = STYLES[toast.type];
        const Icon = style.icon;
        return (
          <div
            key={toast.id}
            className={`pointer-events-auto flex items-start gap-3 px-4 py-3 rounded-lg border shadow-lg ${style.bg} ${style.border} animate-slide-in`}
            role="alert"
          >
            <Icon size={18} className={`${style.text} shrink-0 mt-0.5`} />
            <p className={`text-sm font-medium ${style.text} flex-1 whitespace-pre-line`}>{toast.message}</p>
            <button
              onClick={() => removeToast(toast.id)}
              className={`${style.text} opacity-60 hover:opacity-100 shrink-0`}
              aria-label="Dismiss"
            >
              <X size={16} />
            </button>
          </div>
        );
      })}
    </div>
  );
}
