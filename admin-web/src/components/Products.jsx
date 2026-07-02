import React, { useState, useEffect } from 'react';
import { db } from '../firebase';
import { collection, query, orderBy, onSnapshot, doc, setDoc } from 'firebase/firestore';
import { Tag, Plus, Edit2, Archive, ArchiveRestore } from 'lucide-react';

export default function Products() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  
  const [editingId, setEditingId] = useState(null);
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [description, setDescription] = useState('');
  const [emojiIcon, setEmojiIcon] = useState('📦');
  const [sortOrder, setSortOrder] = useState('');
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);

  const EMOJI_OPTIONS = [
    '🌿', '💊', '🧴', '🥃', '🍯', '🍵', 
    '🥥', '🥑', '🍎', '🍋', '🫐', '🥕',
    '💧', '🌟', '⚡', '🩸', '🦴', '🧠', 
    '❤️', '👁️', '🦷', '💪', '🥗', '🥤',
    '⚕️', '🍃', '🍄', '🌺', '📦', '🎁'
  ];

  useEffect(() => {
    const q = query(collection(db, "products"), orderBy("sortOrder", "asc"));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const prods = [];
      snapshot.forEach(docSnap => prods.push({ id: docSnap.id, ...docSnap.data() }));
      setProducts(prods);
      setLoading(false);
    });
    return () => unsubscribe();
  }, []);

  const openAdd = () => {
    setEditingId(null);
    setName('');
    setPrice('');
    setDescription('');
    setEmojiIcon('📦');
    setSortOrder(products.length + 1);
    setShowModal(true);
  };

  const openEdit = (p) => {
    setEditingId(p.id);
    setName(p.name);
    setPrice(p.price);
    setDescription(p.description || '');
    setEmojiIcon(p.emojiIcon || '📦');
    setSortOrder(p.sortOrder || 1);
    setShowModal(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!name || price === '') return;
    
    const prodData = {
      name,
      price: Number(price),
      description,
      emojiIcon,
      sortOrder: Number(sortOrder) || 1,
      isActive: true,
      updatedAt: new Date().toISOString()
    };

    try {
      if (editingId) {
        await setDoc(doc(db, "products", editingId), prodData, { merge: true });
      } else {
        await setDoc(doc(collection(db, "products")), prodData);
      }
      setShowModal(false);
    } catch (err) {
      console.error(err);
      alert("Error saving product");
    }
  };

  const toggleStatus = async (p) => {
    if (window.confirm(`Are you sure you want to ${p.isActive ? 'disable' : 'enable'} ${p.name}?`)) {
      await setDoc(doc(db, "products", p.id), { isActive: !p.isActive }, { merge: true });
    }
  };

  const seedProducts = async () => {
    if (!window.confirm("Add 5 default products?")) return;
    const defaultProducts = [
      { id: "prod_1", name: "Spirulina", price: 999.0, description: "Premium Organic Spirulina", emojiIcon: "🌿", sortOrder: 1, isActive: true },
      { id: "prod_2", name: "Sea Buckthorn", price: 1299.0, description: "Himalayan Sea Buckthorn Juice", emojiIcon: "🥃", sortOrder: 2, isActive: true },
      { id: "prod_3", name: "Spirulina Face Pack", price: 499.0, description: "Rejuvenating Face Pack", emojiIcon: "🧴", sortOrder: 3, isActive: true },
      { id: "prod_4", name: "Spirulina Cookies", price: 299.0, description: "Healthy Snack Cookies", emojiIcon: "🍪", sortOrder: 4, isActive: true },
      { id: "prod_5", name: "Multiple / Combos", price: 0.0, description: "Custom combo package", emojiIcon: "📦", sortOrder: 5, isActive: true }
    ];
    for (const p of defaultProducts) {
      const { id, ...data } = p;
      await setDoc(doc(db, "products", id), data, { merge: true });
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Tag size={24} style={{ color: 'var(--primary)' }} /> Product Catalog
          </h1>
          <p style={{ color: 'var(--text-muted)', marginTop: '4px' }}>Manage products for your telecallers.</p>
        </div>
        <button className="btn-primary" onClick={openAdd}>
          <Plus size={18} /> Add Product
        </button>
      </div>

      <div className="glass-panel" style={{ overflow: 'hidden' }}>
        {loading ? (
          <div style={{ padding: '32px', textAlign: 'center', color: 'var(--text-muted)' }}>Loading products...</div>
        ) : (
          <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--surface-border)' }}>
                <th style={{ padding: '16px', color: 'var(--text-muted)' }}>Sort</th>
                <th style={{ padding: '16px', color: 'var(--text-muted)' }}>Product</th>
                <th style={{ padding: '16px', color: 'var(--text-muted)' }}>Price (₹)</th>
                <th style={{ padding: '16px', color: 'var(--text-muted)' }}>Status</th>
                <th style={{ padding: '16px', color: 'var(--text-muted)', textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {products.map(p => (
                <tr key={p.id} style={{ borderBottom: '1px solid var(--surface-border)' }}>
                  <td style={{ padding: '16px', color: 'var(--text-muted)' }}>#{p.sortOrder || '-'}</td>
                  <td style={{ padding: '16px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      <div style={{ fontSize: '24px' }}>{p.emojiIcon || '📦'}</div>
                      <div>
                        <div style={{ fontWeight: 500 }}>{p.name}</div>
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{p.description}</div>
                      </div>
                    </div>
                  </td>
                  <td style={{ padding: '16px', fontWeight: 500 }}>₹{p.price}</td>
                  <td style={{ padding: '16px' }}>
                    <span style={{ 
                      padding: '4px 10px', 
                      borderRadius: '20px', 
                      fontSize: '12px', 
                      fontWeight: 500,
                      background: p.isActive ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)',
                      color: p.isActive ? 'var(--secondary)' : 'var(--danger)'
                    }}>
                      {p.isActive ? 'Active' : 'Disabled'}
                    </span>
                  </td>
                  <td style={{ padding: '16px', textAlign: 'right' }}>
                    <button style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', marginRight: '12px' }} onClick={() => openEdit(p)}>
                      <Edit2 size={16} />
                    </button>
                    <button style={{ background: 'transparent', border: 'none', color: p.isActive ? 'var(--danger)' : 'var(--secondary)', cursor: 'pointer' }} onClick={() => toggleStatus(p)}>
                      {p.isActive ? <Archive size={16} /> : <ArchiveRestore size={16} />}
                    </button>
                  </td>
                </tr>
              ))}
              {products.length === 0 && (
                <tr>
                  <td colSpan="5" style={{ padding: '32px', textAlign: 'center', color: 'var(--text-muted)' }}>
                    <div style={{ marginBottom: '16px' }}>No products found.</div>
                    <button className="btn-primary" style={{ margin: '0 auto', background: 'rgba(79, 70, 229, 0.2)', border: '1px solid var(--primary)', color: 'var(--primary)' }} onClick={seedProducts}>
                      Seed 5 Default Products
                    </button>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      {showModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)', zIndex: 50, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div className="glass-panel" style={{ padding: '24px', width: '100%', maxWidth: '400px' }}>
            <h2 style={{ marginBottom: '20px', fontSize: '20px' }}>{editingId ? 'Edit Product' : 'Add Product'}</h2>
            <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div>
                <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', color: 'var(--text-muted)' }}>Name</label>
                <input type="text" className="input-field" value={name} onChange={e => setName(e.target.value)} required />
              </div>
              <div style={{ display: 'flex', gap: '16px' }}>
                <div style={{ flex: 1 }}>
                  <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', color: 'var(--text-muted)' }}>Price</label>
                  <input type="number" className="input-field" value={price} onChange={e => setPrice(e.target.value)} required />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', color: 'var(--text-muted)' }}>Sort Order</label>
                  <input type="number" className="input-field" value={sortOrder} onChange={e => setSortOrder(e.target.value)} required />
                </div>
              </div>
              <div style={{ position: 'relative' }}>
                <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', color: 'var(--text-muted)' }}>Emoji Icon</label>
                <div 
                  className="input-field" 
                  style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', cursor: 'pointer', userSelect: 'none' }}
                  onClick={() => setShowEmojiPicker(!showEmojiPicker)}
                >
                  <span style={{ fontSize: '20px' }}>{emojiIcon}</span>
                  <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Click to change</span>
                </div>
                
                {showEmojiPicker && (
                  <div className="glass-panel" style={{ 
                    position: 'absolute', top: '100%', left: 0, right: 0, marginTop: '8px', 
                    padding: '12px', zIndex: 100, display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: '8px',
                    background: 'var(--background)'
                  }}>
                    {EMOJI_OPTIONS.map(emoji => (
                      <div 
                        key={emoji}
                        onClick={() => { setEmojiIcon(emoji); setShowEmojiPicker(false); }}
                        style={{ 
                          textAlign: 'center', fontSize: '24px', cursor: 'pointer', padding: '8px',
                          borderRadius: '8px', background: emojiIcon === emoji ? 'rgba(79, 70, 229, 0.2)' : 'transparent',
                          border: emojiIcon === emoji ? '1px solid var(--primary)' : '1px solid transparent'
                        }}
                      >
                        {emoji}
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <div>
                <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', color: 'var(--text-muted)' }}>Description</label>
                <textarea className="input-field" value={description} onChange={e => setDescription(e.target.value)} rows="2" />
              </div>
              <div style={{ display: 'flex', gap: '12px', marginTop: '8px' }}>
                <button type="button" onClick={() => setShowModal(false)} style={{ flex: 1, padding: '10px', background: 'transparent', border: '1px solid var(--surface-border)', color: 'white', borderRadius: '8px', cursor: 'pointer' }}>Cancel</button>
                <button type="submit" className="btn-primary" style={{ flex: 1 }}>Save</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
