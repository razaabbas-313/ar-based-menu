import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { CircleCheck, TriangleAlert } from 'lucide-react';
import { cx } from '../utils/format.js';

const ToastContext = createContext(() => {});

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const seq = useRef(0);

  const toast = useCallback((text, tone = 'ok') => {
    const id = ++seq.current;
    setToasts((list) => [...list.slice(-2), { id, text, tone }]);
    setTimeout(() => setToasts((list) => list.filter((t) => t.id !== id)), 4000);
  }, []);

  const value = useMemo(() => toast, [toast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className="pointer-events-none fixed inset-x-0 top-24 z-[80] flex flex-col items-center gap-2 px-4"
        role="status"
        aria-live="polite"
      >
        {toasts.map((t) => (
          <div
            key={t.id}
            className={cx(
              'sheet glass pointer-events-auto flex max-w-md items-center gap-2 rounded-full border px-4 py-3 text-sm shadow-xl',
              t.tone === 'error' ? 'border-red-500/40 text-red-200' : 'border-amber-500/30 text-amber-100',
            )}
          >
            {t.tone === 'error' ? <TriangleAlert size={16} className="shrink-0" /> : <CircleCheck size={16} className="shrink-0" />}
            <span>{t.text}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
