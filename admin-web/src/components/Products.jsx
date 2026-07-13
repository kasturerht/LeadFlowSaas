import React, { useState, useEffect } from 'react';
import { db } from '../firebase';
import { collection, query, orderBy, onSnapshot, doc, setDoc } from 'firebase/firestore';
import { Tag, Plus, Edit2, Archive, ArchiveRestore, X } from 'lucide-react';

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

  // Combo states
  const [productType, setProductType] = useState('single'); // 'single' | 'combo'
  const [selectedSubProducts, setSelectedSubProducts] = useState([]); // array of { productId, quantity }

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
    setProductType('single');
    setSelectedSubProducts([]);
    setShowModal(true);
  };

  const openEdit = (p) => {
    setEditingId(p.id);
    setName(p.name);
    setPrice(p.price);
    setDescription(p.description || '');
    setEmojiIcon(p.emojiIcon || '📦');
    setSortOrder(p.sortOrder || 1);
    setProductType(p.type || 'single');
    setSelectedSubProducts(p.bundledProducts || []);
    setShowModal(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!name || price === '') return;

    const totalQty = selectedSubProducts.reduce((sum, item) => sum + item.quantity, 0);

    if (productType === 'combo' && totalQty < 2) {
      alert("Please select at least 2 items (total quantity) for a combo bundle.");
      return;
    }

    // Auto-generate description if empty for combos
    const computedDesc = productType === 'combo' && !description
      ? "Combo of: " + selectedSubProducts.map(item => {
          const pr = products.find(p => p.id === item.productId);
          return pr ? `${item.quantity} x ${pr.name}` : null;
        }).filter(Boolean).join(", ")
      : description;
    
    const prodData = {
      name,
      price: Number(price),
      description: computedDesc,
      emojiIcon,
      sortOrder: Number(sortOrder) || 1,
      isActive: true,
      type: productType,
      bundledProducts: productType === 'combo' ? selectedSubProducts : [],
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
      { id: "prod_1", name: "Spirulina", price: 999.0, description: "Premium Organic Spirulina", emojiIcon: "🌿", sortOrder: 1, isActive: true, type: 'single', bundledProducts: [] },
      { id: "prod_2", name: "Sea Buckthorn", price: 1299.0, description: "Himalayan Sea Buckthorn Juice", emojiIcon: "🥃", sortOrder: 2, isActive: true, type: 'single', bundledProducts: [] },
      { id: "prod_3", name: "Spirulina Face Pack", price: 499.0, description: "Rejuvenating Face Pack", emojiIcon: "🧴", sortOrder: 3, isActive: true, type: 'single', bundledProducts: [] },
      { id: "prod_4", name: "Spirulina Cookies", price: 299.0, description: "Healthy Snack Cookies", emojiIcon: "🍪", sortOrder: 4, isActive: true, type: 'single', bundledProducts: [] },
      { id: "prod_5", name: "Multiple / Combos", price: 0.0, description: "Custom combo package", emojiIcon: "📦", sortOrder: 5, isActive: true, type: 'single', bundledProducts: [] }
    ];
    for (const p of defaultProducts) {
      const { id, ...data } = p;
      await setDoc(doc(db, "products", id), data, { merge: true });
    }
  };

  // Helper to safely increment or decrement product quantity inside combo
  const updateSubProductQuantity = (productId, change) => {
    setSelectedSubProducts(prev => {
      const existing = prev.find(item => item.productId === productId);
      if (existing) {
        const newQty = existing.quantity + change;
        if (newQty <= 0) {
          return prev.filter(item => item.productId !== productId);
        }
        return prev.map(item => item.productId === productId ? { ...item, quantity: newQty } : item);
      } else {
        if (change > 0) {
          return [...prev, { productId, quantity: change }];
        }
        return prev;
      }
    });
  };

  // Filter products list to only include single products for bundling
  // Ensure we also include products that might be disabled now but are already selected in the combo being edited
  const availableSingleProducts = products.filter(p => {
    const isCurrentSelection = selectedSubProducts.some(item => item.productId === p.id);
    return p.id !== editingId && (p.type || 'single') === 'single' && (p.isActive || isCurrentSelection);
  });

  // Calculate combo total value
  const totalComboValue = selectedSubProducts.reduce((sum, item) => {
    const p = products.find(prod => prod.id === item.productId);
    return sum + (p ? p.price * item.quantity : 0);
  }, 0);

  const savingsAmount = totalComboValue - Number(price || 0);
  const savingsPercent = totalComboValue > 0 ? Math.round((savingsAmount / totalComboValue) * 100) : 0;

  return (
    <div>
      <div className="page-header">
        <div className="page-title-group">
          <h1 className="page-title" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Tag size={18} style={{ color: 'var(--primary)' }} /> Product Catalog
          </h1>
          <p className="page-subtitle">Manage products for your telecallers.</p>
        </div>
        <button className="btn-primary" onClick={openAdd} style={{ padding: '6px 12px', fontSize: '13px' }}>
          <Plus size={14} /> Add Product
        </button>
      </div>

      <div className="glass-panel" style={{ overflow: 'hidden' }}>
        {loading ? (
          <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)' }}>Loading products...</div>
        ) : (
          <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--surface-border)' }}>
                <th style={{ padding: '10px 12px', color: 'var(--text-muted)' }}>Sort</th>
                <th style={{ padding: '10px 12px', color: 'var(--text-muted)' }}>Product</th>
                <th style={{ padding: '10px 12px', color: 'var(--text-muted)' }}>Price (₹)</th>
                <th style={{ padding: '10px 12px', color: 'var(--text-muted)' }}>Status</th>
                <th style={{ padding: '10px 12px', color: 'var(--text-muted)', textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {products.map(p => {
                const isCombo = p.type === 'combo';
                
                const bundledNames = isCombo && p.bundledProducts
                  ? p.bundledProducts.map(item => {
                      const pr = products.find(prod => prod.id === item.productId);
                      return pr ? `${item.quantity} x ${pr.emojiIcon} ${pr.name}` : null;
                    }).filter(Boolean).join(" + ")
                  : null;

                const originalTotal = isCombo && p.bundledProducts
                  ? p.bundledProducts.reduce((sum, item) => {
                      const pr = products.find(prod => prod.id === item.productId);
                      return sum + (pr ? pr.price * item.quantity : 0);
                    }, 0)
                  : 0;

                // Check if any component product in combo is deactivated
                const hasDeactivatedComponent = isCombo && p.bundledProducts
                  ? p.bundledProducts.some(item => {
                      const pr = products.find(prod => prod.id === item.productId);
                      return pr && !pr.isActive;
                    })
                  : false;

                return (
                  <tr key={p.id} style={{ borderBottom: '1px solid var(--surface-border)', opacity: p.isActive ? 1 : 0.5 }}>
                    <td style={{ padding: '10px 12px', color: 'var(--text-muted)' }}>#{p.sortOrder || '-'}</td>
                    <td style={{ padding: '10px 12px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <div style={{ fontSize: '18px' }}>{p.emojiIcon || '📦'}</div>
                        <div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
                            <span style={{ fontWeight: 500 }}>{p.name}</span>
                            {isCombo && (
                              <span className="badge" style={{ background: 'rgba(129, 140, 248, 0.15)', color: '#818cf8', border: '1px solid rgba(129, 140, 248, 0.25)', padding: '1px 4px', fontSize: '9px' }}>
                                Combo
                              </span>
                            )}
                            {hasDeactivatedComponent && (
                              <span className="badge badge-danger" style={{ padding: '1px 4px', fontSize: '9px' }}>
                                ⚠️ Contains Deactivated Item
                              </span>
                            )}
                          </div>
                          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{p.description}</div>
                          {isCombo && bundledNames && (
                            <div style={{ fontSize: '10px', color: 'var(--primary)', fontWeight: 500, marginTop: '2px' }}>
                              🎁 Pack: {bundledNames}
                            </div>
                          )}
                        </div>
                      </div>
                    </td>
                    <td style={{ padding: '10px 12px', fontWeight: 500 }}>
                      {isCombo ? (
                        <div style={{ display: 'flex', flexDirection: 'column' }}>
                          <span>₹{p.price}</span>
                          {originalTotal > 0 && originalTotal !== p.price && (
                            <span style={{ textDecoration: 'line-through', fontSize: '10px', color: 'var(--text-muted)' }}>
                              ₹{originalTotal}
                            </span>
                          )}
                        </div>
                      ) : (
                        `₹${p.price}`
                      )}
                    </td>
                    <td style={{ padding: '10px 12px' }}>
                      {p.isActive ? (
                        <span className="badge badge-success">Active</span>
                      ) : (
                        <span className="badge badge-danger">Disabled</span>
                      )}
                    </td>
                    <td style={{ padding: '10px 12px', textAlign: 'right' }}>
                      <button 
                        style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', marginRight: '8px' }} 
                        onClick={() => openEdit(p)}
                      >
                        <Edit2 size={13} />
                      </button>
                      <button 
                        style={{ background: 'transparent', border: 'none', color: p.isActive ? 'var(--danger)' : 'var(--secondary)', cursor: 'pointer' }} 
                        onClick={() => toggleStatus(p)}
                      >
                        {p.isActive ? <Archive size={13} /> : <ArchiveRestore size={13} />}
                      </button>
                    </td>
                  </tr>
                );
              })}
              {products.length === 0 && (
                <tr>
                  <td colSpan="5" style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)' }}>
                    <div style={{ marginBottom: '10px' }}>No products found.</div>
                    <button 
                      className="btn-secondary" 
                      style={{ margin: '0 auto', fontSize: '12px' }} 
                      onClick={seedProducts}
                    >
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
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(3px)', zIndex: 100, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div className="glass-panel" style={{ padding: '20px', width: '100%', maxWidth: '340px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
              <h2 style={{ fontSize: '15px', fontWeight: 600 }}>{editingId ? 'Edit Product' : 'Add Product'}</h2>
              <button 
                onClick={() => setShowModal(false)} 
                style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
              >
                <X size={16} />
              </button>
            </div>
            
            <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {/* Product Type Toggle */}
              <div>
                <label className="form-label">Product Type</label>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button
                    type="button"
                    className={productType === 'single' ? "btn-primary" : "btn-secondary"}
                    style={{ flex: 1, fontSize: '11px', padding: '5px', height: '28px' }}
                    onClick={() => setProductType('single')}
                  >
                    Single Product
                  </button>
                  <button
                    type="button"
                    className={productType === 'combo' ? "btn-primary" : "btn-secondary"}
                    style={{ flex: 1, fontSize: '11px', padding: '5px', height: '28px' }}
                    onClick={() => setProductType('combo')}
                  >
                    Combo Bundle
                  </button>
                </div>
              </div>

              {/* Combo Products Quantity Selector */}
              {productType === 'combo' && (
                <div>
                  <label className="form-label">Configure Bundle Quantities</label>
                  <div style={{ 
                    maxHeight: '130px', 
                    overflowY: 'auto', 
                    border: '1px solid var(--surface-border)', 
                    borderRadius: '6px', 
                    padding: '6px 8px',
                    background: 'rgba(15, 23, 42, 0.4)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '6px'
                  }}>
                    {availableSingleProducts.length === 0 ? (
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)', textAlign: 'center', padding: '10px' }}>
                        No active single products available.
                      </div>
                    ) : (
                      availableSingleProducts.map(p => {
                        const matchedItem = selectedSubProducts.find(x => x.productId === p.id);
                        const qty = matchedItem ? matchedItem.quantity : 0;
                        
                        return (
                          <div 
                            key={p.id} 
                            style={{ 
                              display: 'flex', 
                              alignItems: 'center', 
                              justifyContent: 'space-between', 
                              padding: '4px 6px',
                              borderRadius: '4px',
                              background: qty > 0 ? 'rgba(79, 70, 229, 0.1)' : 'transparent',
                              border: qty > 0 ? '1px solid rgba(79, 70, 229, 0.2)' : '1px solid transparent',
                              transition: 'all 0.2s ease'
                            }}
                          >
                            <div style={{ display: 'flex', flexDirection: 'column', fontSize: '12px' }}>
                              <span>{p.emojiIcon} {p.name} {!p.isActive && <span style={{ color: 'var(--danger)', fontSize: '10px' }}>(Deactivated)</span>}</span>
                              <span style={{ fontSize: '10px', color: 'var(--text-muted)' }}>₹{p.price} / unit</span>
                            </div>
                            
                            {/* Quantity Stepper widget */}
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                              <button 
                                type="button" 
                                className="btn-secondary" 
                                style={{ padding: '0px', height: '20px', width: '20px', minWidth: '20px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '14px', fontWeight: 'bold' }}
                                onClick={() => updateSubProductQuantity(p.id, -1)}
                              >
                                -
                              </button>
                              <span style={{ minWidth: '16px', textAlign: 'center', fontWeight: '600', fontSize: '12px' }}>
                                {qty}
                              </span>
                              <button 
                                type="button" 
                                className="btn-secondary" 
                                style={{ padding: '0px', height: '20px', width: '20px', minWidth: '20px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '14px', fontWeight: 'bold' }}
                                onClick={() => updateSubProductQuantity(p.id, 1)}
                              >
                                +
                              </button>
                            </div>
                          </div>
                        );
                      })
                    )}
                  </div>
                  {selectedSubProducts.length > 0 && (
                    <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px', textAlign: 'right' }}>
                      Original Total Value: <strong>₹{totalComboValue}</strong>
                    </div>
                  )}
                </div>
              )}

              <div>
                <label className="form-label">Name</label>
                <input 
                  type="text" 
                  className="input-field" 
                  style={{ padding: '6px 10px', fontSize: '12px' }}
                  value={name} 
                  onChange={e => setName(e.target.value)} 
                  required 
                />
              </div>

              <div style={{ display: 'flex', gap: '10px' }}>
                <div style={{ flex: 1 }}>
                  <label className="form-label">Price</label>
                  <input 
                    type="number" 
                    className="input-field" 
                    style={{ padding: '6px 10px', fontSize: '12px' }}
                    value={price} 
                    onChange={e => setPrice(e.target.value)} 
                    required 
                  />
                  {productType === 'combo' && totalComboValue > 0 && price !== '' && (
                    <div style={{ fontSize: '10px', color: savingsAmount >= 0 ? 'var(--secondary)' : 'var(--danger)', marginTop: '2px', fontWeight: 500 }}>
                      {savingsAmount >= 0 
                        ? `Saves ₹${savingsAmount} (${savingsPercent}%)`
                        : `Higher by ₹${Math.abs(savingsAmount)}`
                      }
                    </div>
                  )}
                </div>
                <div style={{ flex: 1 }}>
                  <label className="form-label">Sort Order</label>
                  <input 
                    type="number" 
                    className="input-field" 
                    style={{ padding: '6px 10px', fontSize: '12px' }}
                    value={sortOrder} 
                    onChange={e => setSortOrder(e.target.value)} 
                    required 
                  />
                </div>
              </div>

              <div style={{ position: 'relative' }}>
                <label className="form-label">Emoji Icon</label>
                <div 
                  className="input-field" 
                  style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', cursor: 'pointer', userSelect: 'none', padding: '6px 10px', fontSize: '12px' }}
                  onClick={() => setShowEmojiPicker(!showEmojiPicker)}
                >
                  <span style={{ fontSize: '16px' }}>{emojiIcon}</span>
                  <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Change</span>
                </div>
                
                {showEmojiPicker && (
                  <div className="glass-panel" style={{ 
                    position: 'absolute', top: '100%', left: 0, right: 0, marginTop: '4px', 
                    padding: '8px', zIndex: 110, display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: '4px',
                    background: 'var(--background)'
                  }}>
                    {EMOJI_OPTIONS.map(emoji => (
                      <div 
                        key={emoji}
                        onClick={() => { setEmojiIcon(emoji); setShowEmojiPicker(false); }}
                        style={{ 
                          textAlign: 'center', fontSize: '18px', cursor: 'pointer', padding: '4px',
                          borderRadius: '4px', background: emojiIcon === emoji ? 'rgba(79, 70, 229, 0.2)' : 'transparent',
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
                <label className="form-label">Description</label>
                <textarea 
                  className="input-field" 
                  style={{ padding: '6px 10px', fontSize: '12px' }}
                  value={description} 
                  onChange={e => setDescription(e.target.value)} 
                  rows="2" 
                  placeholder={productType === 'combo' ? "Optional. Leave blank to auto-generate." : ""}
                />
              </div>
              
              <div style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
                <button 
                  type="button" 
                  onClick={() => setShowModal(false)} 
                  className="btn-secondary"
                  style={{ flex: 1, fontSize: '12px', padding: '6px 10px' }}
                >
                  Cancel
                </button>
                <button 
                  type="submit" 
                  className="btn-primary"
                  style={{ flex: 1, fontSize: '12px', padding: '6px 10px' }}
                >
                  Save
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
