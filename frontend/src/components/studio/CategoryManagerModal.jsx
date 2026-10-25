import { useState } from 'react';
import { ArrowDown, ArrowUp, Check, FolderPlus, GripVertical, LoaderCircle, Pencil, Trash2, X } from 'lucide-react';
import { api } from '../../api/client.js';
import { buzz, cx } from '../../utils/format.js';
import { useToast } from '../Toast.jsx';

export default function CategoryManagerModal({ categories: initialCategories, onClose, onCategoriesUpdated }) {
  const toast = useToast();
  const [categories, setCategories] = useState(() => {
    const seen = new Set();
    const unique = [];
    for (const c of (initialCategories || [])) {
      const norm = (c.name || '').trim().toLowerCase();
      if (norm && !seen.has(norm)) {
        seen.add(norm);
        unique.push(c);
      }
    }
    return unique;
  });
  const [newCatName, setNewCatName] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [editName, setEditName] = useState('');
  const [busy, setBusy] = useState(false);

  const handleAdd = async (e) => {
    e?.preventDefault();
    const name = newCatName.trim();
    if (!name || busy) return;
    setBusy(true);
    buzz(10);
    try {
      const created = await api.createCategory({ name });
      const next = [...categories, created];
      setCategories(next);
      setNewCatName('');
      onCategoriesUpdated?.(next);
      toast(`Category “${name}” created`);
    } catch (err) {
      toast(err.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  const handleStartRename = (cat) => {
    setEditingId(cat.id);
    setEditName(cat.name);
  };

  const handleSaveRename = async (id) => {
    const name = editName.trim();
    if (!name || busy) return;
    setBusy(true);
    buzz(10);
    try {
      const updated = await api.updateCategory(id, { name, isActive: true });
      const next = categories.map((c) => (c.id === id ? updated : c));
      setCategories(next);
      setEditingId(null);
      onCategoriesUpdated?.(next);
      toast('Category renamed');
    } catch (err) {
      toast(err.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async (id, name) => {
    if (!window.confirm(`Delete category “${name}”? Dishes in this category will keep their label.`)) return;
    setBusy(true);
    buzz(10);
    try {
      await api.deleteCategory(id);
      const next = categories.filter((c) => c.id !== id);
      setCategories(next);
      onCategoriesUpdated?.(next);
      toast(`Category “${name}” removed`);
    } catch (err) {
      toast(err.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  const handleMove = async (index, direction) => {
    const target = index + direction;
    if (target < 0 || target >= categories.length || busy) return;
    buzz(8);
    const next = [...categories];
    const [moved] = next.splice(index, 1);
    next.splice(target, 0, moved);
    setCategories(next);
    try {
      await api.reorderCategories(next.map((c) => c.id));
      onCategoriesUpdated?.(next);
    } catch (err) {
      toast(err.message, 'error');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-black/80 backdrop-blur-md" onClick={onClose} />

      <div className="relative flex max-h-[85vh] w-full max-w-xl flex-col overflow-hidden rounded-3xl border hairline bg-zinc-950 shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b hairline px-6 py-5">
          <div className="flex items-center gap-3">
            <div className="grid h-10 w-10 place-items-center rounded-2xl bg-amber-500/15 text-amber-300">
              <FolderPlus size={20} />
            </div>
            <div>
              <h3 className="font-display text-xl font-semibold text-zinc-100">Category Manager</h3>
              <p className="text-xs text-zinc-400">Organize and customize your menu sections</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="tap grid h-9 w-9 place-items-center rounded-full text-zinc-400 hover:bg-zinc-900 hover:text-zinc-100"
          >
            <X size={18} />
          </button>
        </div>

        {/* Content */}
        <div className="no-scrollbar flex-1 overflow-y-auto p-6 space-y-6">
          {/* New Category Input */}
          <form onSubmit={handleAdd} className="flex gap-2">
            <input
              type="text"
              value={newCatName}
              onChange={(e) => setNewCatName(e.target.value)}
              placeholder="e.g. Tasting Menu, Wood-Fired Pizza, Cocktails…"
              maxLength={80}
              disabled={busy}
              className="h-12 flex-1 rounded-2xl border hairline bg-black/40 px-4 text-sm text-zinc-100 placeholder:text-zinc-600 focus:border-amber-500/60 focus:outline-none focus:ring-2 focus:ring-amber-500/20"
            />
            <button
              type="submit"
              disabled={!newCatName.trim() || busy}
              className={cx(
                'tap flex h-12 items-center gap-2 rounded-2xl px-5 text-sm font-bold text-zinc-950 transition-all',
                newCatName.trim() && !busy
                  ? 'bg-amber-500 hover:bg-amber-400'
                  : 'cursor-not-allowed bg-zinc-800 text-zinc-500',
              )}
            >
              <FolderPlus size={16} />
              Add
            </button>
          </form>

          {/* Categories List */}
          <div className="space-y-2">
            <div className="text-xs font-semibold uppercase tracking-wider text-zinc-500">
              Active Menu Categories ({categories.length})
            </div>

            {categories.length === 0 ? (
              <p className="py-8 text-center text-sm text-zinc-500">No custom categories yet. Add one above.</p>
            ) : (
              categories.map((cat, idx) => (
                <div
                  key={cat.id}
                  className="flex items-center justify-between rounded-2xl border hairline bg-zinc-900/40 p-3 transition-colors hover:bg-zinc-900/80"
                >
                  <div className="flex flex-1 items-center gap-3 min-w-0 pr-3">
                    <div className="flex flex-col gap-0.5">
                      <button
                        type="button"
                        disabled={idx === 0 || busy}
                        onClick={() => handleMove(idx, -1)}
                        className="tap text-zinc-500 hover:text-amber-300 disabled:opacity-20"
                        title="Move up"
                      >
                        <ArrowUp size={13} />
                      </button>
                      <button
                        type="button"
                        disabled={idx === categories.length - 1 || busy}
                        onClick={() => handleMove(idx, 1)}
                        className="tap text-zinc-500 hover:text-amber-300 disabled:opacity-20"
                        title="Move down"
                      >
                        <ArrowDown size={13} />
                      </button>
                    </div>

                    {editingId === cat.id ? (
                      <div className="flex flex-1 items-center gap-2">
                        <input
                          type="text"
                          value={editName}
                          onChange={(e) => setEditName(e.target.value)}
                          onKeyDown={(e) => e.key === 'Enter' && handleSaveRename(cat.id)}
                          maxLength={80}
                          className="h-8 flex-1 rounded-xl border hairline bg-black/60 px-2.5 text-xs text-zinc-100 focus:border-amber-500 focus:outline-none"
                          autoFocus
                        />
                        <button
                          type="button"
                          onClick={() => handleSaveRename(cat.id)}
                          className="tap grid h-8 w-8 place-items-center rounded-xl bg-amber-500 text-zinc-950"
                        >
                          <Check size={14} />
                        </button>
                        <button
                          type="button"
                          onClick={() => setEditingId(null)}
                          className="tap grid h-8 w-8 place-items-center rounded-xl text-zinc-400 hover:text-zinc-200"
                        >
                          <X size={14} />
                        </button>
                      </div>
                    ) : (
                      <span className="truncate text-sm font-semibold text-zinc-200">{cat.name}</span>
                    )}
                  </div>

                  {editingId !== cat.id && (
                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        onClick={() => handleStartRename(cat)}
                        className="tap grid h-8 w-8 place-items-center rounded-xl text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100"
                        title="Rename"
                      >
                        <Pencil size={13} />
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDelete(cat.id, cat.name)}
                        className="tap grid h-8 w-8 place-items-center rounded-xl text-zinc-500 hover:bg-red-500/10 hover:text-red-400"
                        title="Delete"
                      >
                        <Trash2 size={13} />
                      </button>
                    </div>
                  )}
                </div>
              ))
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end border-t hairline bg-zinc-950/80 px-6 py-4">
          <button
            type="button"
            onClick={onClose}
            className="tap rounded-full bg-amber-500 px-6 py-2.5 text-sm font-bold text-zinc-950 hover:bg-amber-400"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
}
