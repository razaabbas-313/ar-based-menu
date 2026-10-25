import { useRef, useState } from 'react';
import { Check, CircleAlert, Cloud, FolderUp, LoaderCircle, Plus, Sparkles, Trash2, Upload, X } from 'lucide-react';
import { uploadDishPhoto } from '../../utils/upload.js';
import { downsampleImage, prettifyName } from '../../utils/media.js';
import { buzz, cx, fmtBytes } from '../../utils/format.js';
import { useToast } from '../Toast.jsx';
import LuxurySelect from '../ui/LuxurySelect.jsx';

const MAX_BATCH = 15;
const CONCURRENCY = 3;

export default function BatchUploadStudio({ currencyLabel, categories = [], onDishesAdded, onDone }) {
  const toast = useToast();
  const fileInputRef = useRef(null);
  const [items, setItems] = useState([]);
  const [processingFiles, setProcessingFiles] = useState(false);
  const [busy, setBusy] = useState(false);
  const [results, setResults] = useState(null);

  const defaultCategory = categories?.[0]?.name || 'MAINS';
  const defaultCategoryId = categories?.[0]?.id || null;

  const handleFiles = async (fileList) => {
    if (!fileList || fileList.length === 0) return;
    const selected = Array.from(fileList).filter((f) => f.type.startsWith('image/'));
    const remainingSlots = MAX_BATCH - items.length;

    if (remainingSlots <= 0) {
      toast(`Maximum batch limit is ${MAX_BATCH} dishes at once.`, 'error');
      return;
    }

    const toProcess = selected.slice(0, remainingSlots);
    setProcessingFiles(true);
    buzz(10);

    const newItems = [];
    for (const file of toProcess) {
      try {
        const processed = await downsampleImage(file);
        newItems.push({
          id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
          file,
          photo: processed,
          title: prettifyName(file.name) || 'Seasonal Special',
          price: '',
          category: defaultCategory,
          categoryId: defaultCategoryId,
          status: 'ready', // ready | uploading | done | error
          error: '',
          dish: null,
        });
      } catch (err) {
        console.error('Downsample error', err);
      }
    }

    setItems((prev) => [...prev, ...newItems]);
    setProcessingFiles(false);
  };

  const updateItem = (id, field, value) => {
    setItems((prev) => prev.map((item) => (item.id === id ? { ...item, [field]: value } : item)));
  };

  const removeItem = (id) => {
    buzz(8);
    setItems((prev) => {
      const item = prev.find((it) => it.id === id);
      if (item?.photo?.url) URL.revokeObjectURL(item.photo.url);
      return prev.filter((it) => it.id !== id);
    });
  };

  const canPublish =
    items.length > 0 &&
    !busy &&
    items.every((it) => it.title.trim() && it.price !== '' && !Number.isNaN(Number(it.price)));

  const handlePublishAll = async () => {
    if (!canPublish) return;
    setBusy(true);
    buzz(20);

    const publishedDishes = [];
    let failedCount = 0;

    // Concurrency queue
    const queue = [...items];
    const updateItemStatus = (id, patch) => {
      setItems((prev) => prev.map((it) => (it.id === id ? { ...it, ...patch } : it)));
    };

    const runWorker = async () => {
      while (queue.length > 0) {
        const item = queue.shift();
        if (!item) break;

        updateItemStatus(item.id, { status: 'uploading' });

        try {
          const dish = await uploadDishPhoto({
            photo: item.photo,
            meta: {
              title: item.title.trim(),
              price: item.price,
              category: item.category,
              categoryId: item.categoryId,
              special: false,
              available: true,
            },
          });

          publishedDishes.push(dish);
          updateItemStatus(item.id, { status: 'done', dish });
        } catch (err) {
          failedCount += 1;
          updateItemStatus(item.id, { status: 'error', error: err.message });
        }
      }
    };

    const workers = Array.from({ length: Math.min(CONCURRENCY, items.length) }, () => runWorker());
    await Promise.all(workers);

    setBusy(false);
    buzz(30);

    if (publishedDishes.length > 0) {
      onDishesAdded?.(publishedDishes);
      toast(`Successfully published ${publishedDishes.length} dishes to menu!`);
    }

    setResults({
      success: publishedDishes.length,
      failed: failedCount,
    });
  };

  const handleReset = () => {
    items.forEach((it) => {
      if (it.photo?.url) URL.revokeObjectURL(it.photo.url);
    });
    setItems([]);
    setResults(null);
  };

  return (
    <div className="space-y-6">
      {/* Top Banner / Dropzone */}
      {items.length === 0 ? (
        <div
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault();
            handleFiles(e.dataTransfer.files);
          }}
          onClick={() => fileInputRef.current?.click()}
          className="tap flex flex-col items-center justify-center rounded-3xl border-2 border-dashed hairline bg-zinc-950/60 p-12 text-center transition-colors hover:border-amber-500/50 hover:bg-amber-500/5 cursor-pointer"
        >
          <div className="grid h-16 w-16 place-items-center rounded-2xl bg-amber-500/10 text-amber-300">
            <FolderUp size={32} />
          </div>
          <h3 className="mt-4 font-display text-2xl font-semibold text-zinc-100">
            Drag &amp; drop multiple food photos
          </h3>
          <p className="mt-1.5 max-w-md text-sm text-zinc-400">
            Onboard up to 15 dishes in one click. Instant cloud optimization directly to your live guest menu.
          </p>
          <button
            type="button"
            className="mt-6 flex h-11 items-center gap-2 rounded-full bg-amber-500 px-6 text-sm font-bold text-zinc-950"
          >
            <Upload size={16} /> Choose food photos
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            multiple
            className="hidden"
            onChange={(e) => handleFiles(e.target.files)}
          />
        </div>
      ) : (
        <div className="flex items-center justify-between gap-4">
          <div>
            <h3 className="font-display text-2xl font-semibold text-zinc-100">
              Batch Quick-Fill ({items.length}/{MAX_BATCH} dishes)
            </h3>
            <p className="text-xs text-zinc-400">
              Adjust title, price and category for each plate before publishing.
            </p>
          </div>
          <div className="flex items-center gap-2">
            {items.length < MAX_BATCH && !busy && (
              <>
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className="tap flex h-10 items-center gap-2 rounded-full border hairline px-4 text-xs font-semibold text-zinc-300 hover:bg-zinc-900"
                >
                  <Plus size={15} /> Add more
                </button>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/*"
                  multiple
                  className="hidden"
                  onChange={(e) => handleFiles(e.target.files)}
                />
              </>
            )}
            {!busy && (
              <button
                type="button"
                onClick={handleReset}
                className="tap h-10 rounded-full border hairline px-4 text-xs font-semibold text-zinc-400 hover:text-red-400"
              >
                Clear all
              </button>
            )}
          </div>
        </div>
      )}

      {/* Grid of items */}
      {items.length > 0 && (
        <div className="space-y-3">
          <div className="grid grid-cols-1 gap-3">
            {items.map((item, index) => (
              <div
                key={item.id}
                className={cx(
                  'flex flex-col gap-3 rounded-2xl border p-3.5 transition-colors sm:flex-row sm:items-center sm:gap-4',
                  item.status === 'done'
                    ? 'border-emerald-500/30 bg-emerald-950/10'
                    : item.status === 'error'
                      ? 'border-red-500/30 bg-red-950/10'
                      : item.status === 'uploading'
                        ? 'border-amber-500/40 bg-amber-500/5'
                        : 'hairline bg-zinc-900/40',
                )}
              >
                {/* Thumbnail */}
                <div className="relative h-16 w-16 shrink-0 overflow-hidden rounded-xl bg-black">
                  <img src={item.photo.url} alt="" className="h-full w-full object-cover" />
                  <span className="absolute bottom-1 right-1 rounded bg-black/70 px-1 py-0.5 text-[9px] font-mono text-zinc-300">
                    {fmtBytes(item.photo.finalSize)}
                  </span>
                  {item.status === 'done' && (
                    <div className="absolute inset-0 flex items-center justify-center bg-emerald-950/70 text-emerald-300">
                      <Check size={20} strokeWidth={3} />
                    </div>
                  )}
                  {item.status === 'uploading' && (
                    <div className="absolute inset-0 flex items-center justify-center bg-black/60 text-amber-400">
                      <LoaderCircle size={20} className="animate-spin" />
                    </div>
                  )}
                </div>

                {/* Title Input */}
                <div className="flex-1 min-w-0">
                  <span className="mb-1 block text-[10px] font-semibold uppercase tracking-wider text-zinc-500">
                    Dish #{index + 1} Title
                  </span>
                  <input
                    type="text"
                    value={item.title}
                    disabled={busy || item.status === 'done'}
                    onChange={(e) => updateItem(item.id, 'title', e.target.value)}
                    placeholder="Dish Name"
                    className="h-10 w-full rounded-xl border hairline bg-black/50 px-3 text-sm font-medium text-zinc-100 placeholder:text-zinc-600 focus:border-amber-500 focus:outline-none"
                  />
                </div>

                {/* Price Input */}
                <div className="w-full sm:w-28">
                  <span className="mb-1 block text-[10px] font-semibold uppercase tracking-wider text-zinc-500">
                    Price ({currencyLabel})
                  </span>
                  <input
                    type="text"
                    inputMode="decimal"
                    value={item.price}
                    disabled={busy || item.status === 'done'}
                    onChange={(e) =>
                      updateItem(
                        item.id,
                        'price',
                        e.target.value.replace(/[^0-9.]/g, '').replace(/(\..*)\./g, '$1'),
                      )
                    }
                    placeholder="0.00"
                    className="h-10 w-full rounded-xl border hairline bg-black/50 px-3 font-mono text-sm text-amber-200 placeholder:text-zinc-600 focus:border-amber-500 focus:outline-none"
                  />
                </div>

                {/* Category Select */}
                <div className="w-full sm:w-44">
                  <span className="mb-1 block text-[10px] font-semibold uppercase tracking-wider text-zinc-500">
                    Category
                  </span>
                  <LuxurySelect
                    value={item.categoryId ? String(item.categoryId) : item.category}
                    disabled={busy || item.status === 'done'}
                    options={
                      categories.length > 0
                        ? categories.map((c) => ({ value: String(c.id), label: c.name }))
                        : [
                            { value: 'STARTERS', label: 'Starters' },
                            { value: 'MAINS', label: 'Mains' },
                            { value: 'CHEF_SPECIALS', label: "Chef's Specials" },
                            { value: 'DESSERTS', label: 'Desserts' },
                            { value: 'DRINKS', label: 'Drinks' },
                          ]
                    }
                    onChange={(val) => {
                      const matched = categories.find((c) => String(c.id) === String(val));
                      if (matched) {
                        updateItem(item.id, 'categoryId', matched.id);
                        updateItem(item.id, 'category', matched.name);
                      } else {
                        updateItem(item.id, 'categoryId', null);
                        updateItem(item.id, 'category', val);
                      }
                    }}
                    buttonClassName="h-10 rounded-xl bg-black/50 border hairline"
                    dropdownClassName="min-w-[11rem]"
                  />
                </div>

                {/* Remove button */}
                {!busy && item.status !== 'done' && (
                  <button
                    type="button"
                    onClick={() => removeItem(item.id)}
                    className="tap grid h-10 w-10 shrink-0 place-items-center self-end rounded-xl text-zinc-500 hover:bg-red-500/10 hover:text-red-400 sm:self-center"
                    title="Remove plate"
                  >
                    <Trash2 size={16} />
                  </button>
                )}
              </div>
            ))}
          </div>

          {/* Action Bar */}
          <div className="flex flex-col gap-3 rounded-2xl border hairline bg-black/40 p-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="text-xs text-zinc-400">
              {results ? (
                <span className="text-emerald-300 font-semibold">
                  Published {results.success} dishes{results.failed > 0 ? ` (${results.failed} failed)` : ''}!
                </span>
              ) : (
                <span>All photos are optimized on your device and stream directly to your menu.</span>
              )}
            </div>

            <div className="flex items-center gap-3">
              {results ? (
                <button
                  type="button"
                  onClick={onDone}
                  className="tap flex h-12 items-center gap-2 rounded-full bg-amber-500 px-6 text-sm font-bold text-zinc-950 shadow-fab"
                >
                  <Check size={16} /> View Dishes
                </button>
              ) : (
                <button
                  type="button"
                  disabled={!canPublish}
                  onClick={handlePublishAll}
                  className={cx(
                    'tap flex h-12 items-center gap-2 rounded-full px-7 text-sm font-bold text-zinc-950 transition-all',
                    canPublish
                      ? 'bg-gradient-to-b from-amber-400 to-amber-600 shadow-fab hover:brightness-105'
                      : 'cursor-not-allowed bg-zinc-800 text-zinc-500',
                  )}
                >
                  {busy ? <LoaderCircle size={16} className="animate-spin" /> : <Sparkles size={16} />}
                  {busy ? 'Publishing dishes…' : `Publish All (${items.length}) Dishes`}
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
