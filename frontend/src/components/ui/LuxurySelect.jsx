import { useEffect, useRef, useState } from 'react';
import { Check, ChevronDown } from 'lucide-react';
import { buzz, cx } from '../../utils/format.js';

/**
 * Bespoke luxury custom dropdown (replaces native <select> platform-wide).
 * Supports glassmorphism styling, outside-click closing, keyboard Escape,
 * and custom option labels/colors.
 */
export default function LuxurySelect({
  value,
  onChange,
  options = [],
  placeholder = 'Select…',
  disabled = false,
  className = '',
  buttonClassName = '',
  dropdownClassName = '',
  align = 'left',
}) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);

  // Normalize options to [{ value, label, color }]
  const normalizedOptions = options.map((opt) =>
    typeof opt === 'object' && opt !== null
      ? opt
      : { value: opt, label: String(opt) }
  );

  const selectedOption = normalizedOptions.find((o) => o.value === value);

  useEffect(() => {
    function handleClickOutside(e) {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
      }
    }
    function handleKeyDown(e) {
      if (e.key === 'Escape') setOpen(false);
    }
    if (open) {
      document.addEventListener('mousedown', handleClickOutside);
      document.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  const handleSelect = (val) => {
    buzz(10);
    onChange(val);
    setOpen(false);
  };

  return (
    <div ref={containerRef} className={cx('relative inline-block text-left', className)}>
      <button
        type="button"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => {
          if (!disabled) {
            buzz(8);
            setOpen((prev) => !prev);
          }
        }}
        className={cx(
          'tap flex h-10 w-full items-center justify-between gap-2.5 rounded-full border hairline bg-black/40 px-3.5 py-1 text-xs font-semibold text-zinc-100 transition-all hover:border-amber-500/40 focus:border-amber-500/60 focus:outline-none disabled:cursor-not-allowed disabled:opacity-50',
          open ? 'border-amber-500/60 ring-2 ring-amber-500/15' : '',
          buttonClassName
        )}
      >
        <span className={cx('truncate', selectedOption?.color || '')}>
          {selectedOption ? selectedOption.label : placeholder}
        </span>
        <ChevronDown
          size={14}
          className={cx(
            'shrink-0 text-zinc-400 transition-transform duration-200',
            open ? 'rotate-180 text-amber-300' : ''
          )}
          aria-hidden="true"
        />
      </button>

      {open && (
        <div
          role="listbox"
          className={cx(
            'absolute z-50 mt-1.5 max-h-60 min-w-[10rem] overflow-y-auto rounded-2xl border hairline bg-zinc-950/95 p-1.5 shadow-2xl backdrop-blur-xl animate-in fade-in zoom-in-95 duration-100 no-scrollbar',
            align === 'right' ? 'right-0' : 'left-0',
            dropdownClassName
          )}
        >
          {normalizedOptions.map((opt) => {
            const isSelected = opt.value === value;
            return (
              <button
                key={String(opt.value)}
                type="button"
                role="option"
                aria-selected={isSelected}
                onClick={() => handleSelect(opt.value)}
                className={cx(
                  'tap flex w-full items-center justify-between gap-3 rounded-xl px-3 py-2 text-left text-xs font-medium transition-colors',
                  isSelected
                    ? 'bg-amber-500/15 text-amber-200 font-semibold'
                    : 'text-zinc-300 hover:bg-zinc-800/70 hover:text-zinc-100'
                )}
              >
                <span className={cx('truncate', opt.color || '')}>{opt.label}</span>
                {isSelected && <Check size={14} className="shrink-0 text-amber-400" aria-hidden="true" />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
