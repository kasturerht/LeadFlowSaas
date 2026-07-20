import React, { useState, useEffect } from 'react';
import { db } from '../firebase';
import { useAuth } from '../AuthContext';
import { collection, query, onSnapshot, doc, setDoc, deleteDoc, orderBy } from 'firebase/firestore';
import { X, Plus, Edit2, Trash2 } from 'lucide-react';

const COLORS = [
  '#ef4444', // Red
  '#f97316', // Orange
  '#eab308', // Yellow
  '#22c55e', // Green
  '#06b6d4', // Cyan
  '#3b82f6', // Blue
  '#8b5cf6', // Violet
  '#d946ef', // Fuchsia
  '#ec4899', // Pink
];

const ICONS = [
  '💊', '🧴', '🥃', '📦', '🌿', '🍃', '🔴', '🍏', '🍂', '🍪', '🌟', '🧼', '💧', '⚡', '❤️'
];

export default function CategoryMaster({ onClose }) {
  const { orgId } = useAuth();
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  
  const [editingId, setEditingId] = useState(null);
  const [name, setName] = useState('');
  const [color, setColor] = useState(COLORS[5]);
  const [icon, setIcon] = useState(ICONS[0]);

  useEffect(() => {
    if (!orgId) return;
    const q = query(collection(db, "organizations", orgId, "categories"), orderBy("name"));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const cats = [];
      snapshot.forEach(docSnap => cats.push({ id: docSnap.id, ...docSnap.data() }));
      
      setCategories(cats);
      setLoading(false);
    }, (error) => {
      console.error("Error fetching categories:", error);
      alert("Error fetching categories: " + error.message);
      setLoading(false);
    });
    return () => unsubscribe();
  }, [orgId]);

  const handleSave = async (e) => {
    e.preventDefault();
    if (!name.trim()) return;

    try {
      if (editingId) {
        await setDoc(doc(db, "organizations", orgId, "categories", editingId), { name, color, icon, isActive: true }, { merge: true });
      } else {
        const id = "cat_" + Date.now();
        await setDoc(doc(db, "organizations", orgId, "categories", id), { name, color, icon, isActive: true });
      }
      resetForm();
    } catch (err) {
      console.error(err);
      alert("Error saving category");
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm("Delete this category? Ensure no products are using it first.")) {
      await deleteDoc(doc(db, "organizations", orgId, "categories", id));
    }
  };

  const openEdit = (c) => {
    setEditingId(c.id);
    setName(c.name);
    setColor(c.color);
    setIcon(c.icon);
  };

  const resetForm = () => {
    setEditingId(null);
    setName('');
    setColor(COLORS[5]);
    setIcon(ICONS[0]);
  };

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(3px)', zIndex: 100, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div className="glass-panel" style={{ padding: '20px', width: '100%', maxWidth: '420px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
        
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 style={{ fontSize: '16px', fontWeight: 600 }}>Category Master</h2>
          <button 
            onClick={onClose} 
            style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Existing Categories List */}
        <div style={{ maxHeight: '200px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {loading ? (
            <div style={{ fontSize: '12px', color: 'var(--text-muted)', textAlign: 'center' }}>Loading...</div>
          ) : categories.length === 0 ? (
            <div style={{ fontSize: '12px', color: 'var(--text-muted)', textAlign: 'center' }}>No categories found.</div>
          ) : (
            categories.map(c => (
              <div key={c.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: 'rgba(15, 23, 42, 0.4)', padding: '8px 12px', borderRadius: '6px', border: '1px solid var(--surface-border)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <div style={{ width: '24px', height: '24px', borderRadius: '4px', background: `${c.color}20`, color: c.color, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '14px', border: `1px solid ${c.color}50` }}>
                    {c.icon}
                  </div>
                  <span style={{ fontSize: '13px', fontWeight: 500 }}>{c.name}</span>
                </div>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button onClick={() => openEdit(c)} style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}><Edit2 size={14} /></button>
                  <button onClick={() => handleDelete(c.id)} style={{ background: 'none', border: 'none', color: 'var(--danger)', cursor: 'pointer' }}><Trash2 size={14} /></button>
                </div>
              </div>
            ))
          )}
        </div>

        {/* Add/Edit Form */}
        <div style={{ borderTop: '1px solid var(--surface-border)', paddingTop: '16px' }}>
          <h3 style={{ fontSize: '13px', fontWeight: 500, marginBottom: '12px' }}>{editingId ? 'Edit Category' : 'Add New Category'}</h3>
          <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            
            <div>
              <label className="form-label">Category Name</label>
              <input type="text" className="input-field" value={name} onChange={e => setName(e.target.value)} required placeholder="e.g. Health Supplements" />
            </div>

            <div style={{ display: 'flex', gap: '16px' }}>
              <div style={{ flex: 1 }}>
                <label className="form-label">Theme Color</label>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                  {COLORS.map(c => (
                    <div 
                      key={c} 
                      onClick={() => setColor(c)}
                      style={{ width: '20px', height: '20px', borderRadius: '50%', background: c, cursor: 'pointer', border: color === c ? '2px solid white' : '2px solid transparent', boxShadow: color === c ? `0 0 0 2px ${c}` : 'none' }}
                    />
                  ))}
                </div>
              </div>
              <div style={{ flex: 1 }}>
                <label className="form-label">Icon</label>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', height: '60px', overflowY: 'auto', background: 'rgba(0,0,0,0.2)', padding: '4px', borderRadius: '6px' }}>
                  {ICONS.map(i => (
                    <div 
                      key={i}
                      onClick={() => setIcon(i)}
                      style={{ fontSize: '16px', cursor: 'pointer', padding: '2px', background: icon === i ? 'var(--primary)' : 'transparent', borderRadius: '4px' }}
                    >
                      {i}
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '8px', marginTop: '4px' }}>
              {editingId && (
                <button type="button" onClick={resetForm} className="btn-secondary" style={{ flex: 1 }}>Cancel</button>
              )}
              <button type="submit" className="btn-primary" style={{ flex: editingId ? 1 : '1 1 100%' }}>
                {editingId ? 'Update' : 'Add Category'}
              </button>
            </div>
          </form>
        </div>

      </div>
    </div>
  );
}
