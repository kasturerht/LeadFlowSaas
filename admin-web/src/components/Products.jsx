import React, { useState, useEffect } from 'react';
import { db } from '../firebase';
import { collection, query, orderBy, onSnapshot, doc, setDoc, deleteDoc, getDocs } from 'firebase/firestore';
import { Tag, Plus, Edit2, Archive, ArchiveRestore, X, Settings2 } from 'lucide-react';
import CategoryMaster from './CategoryMaster';

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
  
  // God-Level Category & Attributes State
  const [categories, setCategories] = useState([]);
  const [showCategoryMaster, setShowCategoryMaster] = useState(false);
  const [categoryIds, setCategoryIds] = useState([]);
  const [format, setFormat] = useState('Other');
  const [sizeUnit, setSizeUnit] = useState('');
  const [selectedCategoryFilter, setSelectedCategoryFilter] = useState('all');

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

    // Fetch Categories
    const unsubCats = onSnapshot(query(collection(db, "categories"), orderBy("name")), (snapshot) => {
      const cats = [];
      snapshot.forEach(docSnap => cats.push({ id: docSnap.id, ...docSnap.data() }));
      
      if (cats.length === 0) {
        // Auto-seed default categories if empty
        const seedCategories = async () => {
          try {
            const defaults = [
              { id: "cat_skin", name: "Skin Care & Cosmetics", color: "#ec4899", icon: "🧴" },
              { id: "cat_hair", name: "Hair Care", color: "#8b5cf6", icon: "💆‍♀️" },
              { id: "cat_superfoods", name: "Superfood Powders", color: "#22c55e", icon: "🌿" },
              { id: "cat_health", name: "Health Supplements", color: "#3b82f6", icon: "💊" },
              { id: "cat_extracts", name: "Herbal Extracts", color: "#eab308", icon: "🧪" },
              { id: "cat_edibles", name: "Healthy Foods & Beverages", color: "#f97316", icon: "🍪" },
              { id: "cat_combos", name: "Value Combos", color: "#f43f5e", icon: "🎁" }
            ];
            for (const c of defaults) {
              await setDoc(doc(db, "categories", c.id), { name: c.name, color: c.color, icon: c.icon, isActive: true });
            }
          } catch (err) {
            console.error("Error seeding default categories:", err);
            alert("Permission error seeding categories. Please ensure you are logged in and Firestore rules allow writes.");
          }
        };
        seedCategories();
      }
      
      setCategories(cats);
    }, (error) => {
      console.error("Error reading categories:", error);
      alert("Error reading categories: " + error.message);
    });

    return () => {
      unsubscribe();
      unsubCats();
    };
  }, []);

  const openAdd = () => {
    setEditingId(null);
    setName('');
    setPrice('');
    setCategoryIds([]);
    setFormat('Other');
    setSizeUnit('');
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
    setCategoryIds(p.categoryIds || (p.categoryId ? [p.categoryId] : []));
    setFormat(p.format || 'Other');
    setSizeUnit(p.sizeUnit || '');
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
    if (categoryIds.length === 0) {
      alert("Please select at least one category.");
      return;
    }

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
      categoryIds,
      format,
      sizeUnit,
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
    if (!window.confirm("WARNING: This will delete all existing products and rebuild the structured catalog with Base SKUs and Combos. Proceed?")) return;
    
    try {
      // 1. Clear old Products
      const snapshot = await getDocs(query(collection(db, "products")));
      for (const docSnap of snapshot.docs) {
        await deleteDoc(doc(db, "products", docSnap.id));
      }

      // 2. Clear old Categories
      const catSnapshot = await getDocs(query(collection(db, "categories")));
      for (const docSnap of catSnapshot.docs) {
        await deleteDoc(doc(db, "categories", docSnap.id));
      }

      // 3. Define and insert 7 New Categories
      const newCategories = [
        { id: "cat_skin", name: "Skin Care & Cosmetics", color: "#ec4899", icon: "🧴", isActive: true },
        { id: "cat_hair", name: "Hair Care", color: "#8b5cf6", icon: "💆‍♀️", isActive: true },
        { id: "cat_superfoods", name: "Superfood Powders", color: "#22c55e", icon: "🌿", isActive: true },
        { id: "cat_health", name: "Health Supplements", color: "#3b82f6", icon: "💊", isActive: true },
        { id: "cat_extracts", name: "Herbal Extracts", color: "#eab308", icon: "🧪", isActive: true },
        { id: "cat_edibles", name: "Healthy Foods & Beverages", color: "#f97316", icon: "🍪", isActive: true },
        { id: "cat_combos", name: "Value Combos", color: "#f43f5e", icon: "🎁", isActive: true }
      ];

      for (const c of newCategories) {
        await setDoc(doc(db, "categories", c.id), c);
      }

      const baseProducts = [
        { id: "sku_hs01", name: "Spirulina Capsules", price: 550, categoryIds: ["cat_health"], format: "Capsule", sizeUnit: "60 units", emojiIcon: "💊" },
        { id: "sku_hs02", name: "Spirulina Tablets", price: 699, categoryIds: ["cat_health"], format: "Tablet", sizeUnit: "120 units", emojiIcon: "💊" },
        { id: "sku_hs03", name: "Sea Buckthorn Juice", price: 600, categoryIds: ["cat_edibles", "cat_health"], format: "Liquid", sizeUnit: "1 Bottle", emojiIcon: "🥃" },
        { id: "sku_pc01", name: "Hair Oil", price: 200, categoryIds: ["cat_hair"], format: "Oil", sizeUnit: "100ml", emojiIcon: "🧴" },
        { id: "sku_pc02", name: "Shampoo", price: 200, categoryIds: ["cat_hair"], format: "Liquid", sizeUnit: "100ml", emojiIcon: "🧴" },
        { id: "sku_pc03", name: "Face Wash", price: 200, categoryIds: ["cat_skin"], format: "Liquid", sizeUnit: "100ml", emojiIcon: "🧴" },
        { id: "sku_pc04", name: "Natural Soap", price: 60, categoryIds: ["cat_skin"], format: "Other", sizeUnit: "1 bar", emojiIcon: "🧼" },
        { id: "sku_pc05", name: "Spirulina Face Pack", price: 150, categoryIds: ["cat_skin", "cat_superfoods"], format: "Powder", sizeUnit: "50g", emojiIcon: "🌿" },
        { id: "sku_pc06", name: "Spirulina Korean Cream", price: 999, categoryIds: ["cat_skin"], format: "Cream", sizeUnit: "25g", emojiIcon: "🌟" },
        { id: "sku_pc07", name: "Spirulina Bride Cream", price: 1199, categoryIds: ["cat_skin"], format: "Cream", sizeUnit: "25g", emojiIcon: "🌟" },
        { id: "sku_pe01", name: "Moringa Powder", price: 200, categoryIds: ["cat_superfoods"], format: "Powder", sizeUnit: "100g", emojiIcon: "🍃" },
        { id: "sku_pe02", name: "Beet Root Powder", price: 200, categoryIds: ["cat_superfoods"], format: "Powder", sizeUnit: "100g", emojiIcon: "🔴" },
        { id: "sku_pe03", name: "Amla Powder", price: 200, categoryIds: ["cat_superfoods"], format: "Powder", sizeUnit: "100g", emojiIcon: "🍏" },
        { id: "sku_pe04", name: "Ashwagandha Powder", price: 200, categoryIds: ["cat_superfoods"], format: "Powder", sizeUnit: "100g", emojiIcon: "🍂" },
        { id: "sku_pe05", name: "Ashwagandha Extract Tablets", price: 350, categoryIds: ["cat_extracts", "cat_health"], format: "Tablet", sizeUnit: "60 units", emojiIcon: "💊" },
        { id: "sku_pe06", name: "Moringa Extract Tablets", price: 350, categoryIds: ["cat_extracts", "cat_health"], format: "Tablet", sizeUnit: "60 units", emojiIcon: "💊" },
        { id: "sku_ed01", name: "Spirulina Cookies", price: 200, categoryIds: ["cat_edibles", "cat_health"], format: "Edible", sizeUnit: "200g", emojiIcon: "🍪" },
      ];

      const comboProducts = [
        { 
          id: "combo_cb01", 
          name: "Spirulina Capsules - 3 Months Supply", 
          price: 1600, 
          categoryIds: ["cat_combos", "cat_health"],
          format: "Combo",
          sizeUnit: "3 packs",
          emojiIcon: "📦",
          bundledProducts: [ { productId: "sku_hs01", quantity: 3 } ]
        },
        { 
          id: "combo_cb02", 
          name: "Spirulina Tablets - 3 Months Supply", 
          price: 1800, 
          categoryIds: ["cat_combos", "cat_health"],
          format: "Combo",
          sizeUnit: "3 packs",
          emojiIcon: "📦",
          bundledProducts: [ { productId: "sku_hs02", quantity: 3 } ]
        },
        { 
          id: "combo_cb03", 
          name: "Sea Buckthorn - Double Pack", 
          price: 1200, 
          categoryIds: ["cat_combos", "cat_edibles"],
          format: "Combo",
          sizeUnit: "2 packs",
          emojiIcon: "📦",
          bundledProducts: [ { productId: "sku_hs03", quantity: 2 } ]
        },
        { 
          id: "combo_cb04", 
          name: "3 Months Ultimate Health Combo", 
          price: 3600, 
          categoryIds: ["cat_combos", "cat_health"],
          format: "Combo",
          sizeUnit: "6 packs",
          emojiIcon: "📦",
          bundledProducts: [ { productId: "sku_hs02", quantity: 3 }, { productId: "sku_hs03", quantity: 3 } ]
        }
      ];

      let sortOrder = 1;
      
      // Insert Base SKUs
      for (const prod of baseProducts) {
        const { id, ...prodData } = prod;
        const data = {
          ...prodData,
          description: "",
          sortOrder: sortOrder++,
          isActive: true,
          type: 'single',
          bundledProducts: [],
          updatedAt: new Date().toISOString()
        };
        await setDoc(doc(db, "products", id), data);
      }

      // Insert Combos
      for (const prod of comboProducts) {
        const { id, ...prodData } = prod;
        const data = {
          ...prodData,
          description: "",
          sortOrder: sortOrder++,
          isActive: true,
          type: 'combo',
          updatedAt: new Date().toISOString()
        };
        await setDoc(doc(db, "products", id), data);
      }

      alert("Database Reset Complete: 7 Premium Categories & 21 Mapped Products added successfully!");
    } catch (err) {
      console.error(err);
      alert("Error migrating products & categories!");
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
        <div style={{ display: 'flex', gap: '10px' }}>
          <button className="btn-secondary" onClick={seedProducts} style={{ padding: '6px 12px', fontSize: '13px', border: '1px solid var(--danger)', color: 'var(--danger)' }}>
            Migrate 21 Products
          </button>
          <button className="btn-secondary" onClick={() => setShowCategoryMaster(true)} style={{ padding: '6px 12px', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Settings2 size={14} /> Categories
          </button>
          <button className="btn-primary" onClick={openAdd} style={{ padding: '6px 12px', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Plus size={14} /> Add Product
          </button>
        </div>
      </div>

      <div className="glass-panel" style={{ overflow: 'hidden' }}>
        
        {/* Category Filters */}
        <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--surface-border)', display: 'flex', gap: '8px', overflowX: 'auto' }}>
          <button 
            className={selectedCategoryFilter === 'all' ? 'btn-primary' : 'btn-secondary'} 
            style={{ padding: '4px 12px', fontSize: '12px', borderRadius: '16px', whiteSpace: 'nowrap' }}
            onClick={() => setSelectedCategoryFilter('all')}
          >
            All Products
          </button>
          {categories.map(c => (
            <button 
              key={c.id}
              className={selectedCategoryFilter === c.id ? 'btn-primary' : 'btn-secondary'} 
              style={{ 
                padding: '4px 12px', fontSize: '12px', borderRadius: '16px', whiteSpace: 'nowrap',
                background: selectedCategoryFilter === c.id ? c.color : 'transparent',
                borderColor: selectedCategoryFilter === c.id ? c.color : 'var(--surface-border)',
                color: selectedCategoryFilter === c.id ? '#fff' : 'var(--text-muted)'
              }}
              onClick={() => setSelectedCategoryFilter(c.id)}
            >
              {c.icon} {c.name}
            </button>
          ))}
        </div>

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
              {products.filter(p => selectedCategoryFilter === 'all' || (p.categoryIds && p.categoryIds.includes(selectedCategoryFilter)) || p.categoryId === selectedCategoryFilter).map(p => {
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
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div style={{ fontSize: '20px' }}>{p.emojiIcon || '📦'}</div>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
                            <span style={{ fontWeight: 500, fontSize: '13px' }}>{p.name}</span>
                            
                            {/* Render Multi-Category Badges */}
                            {p.categoryIds && p.categoryIds.map(catId => {
                              const c = categories.find(cat => cat.id === catId);
                              if (!c) return null;
                              return (
                                <span 
                                  key={c.id}
                                  className="badge" 
                                  style={{ 
                                    background: `${c.color}20`, 
                                    color: c.color, 
                                    border: `1px solid ${c.color}40`, 
                                    padding: '2px 6px', fontSize: '9px' 
                                  }}
                                >
                                  {c.icon} {c.name}
                                </span>
                              );
                            })}
                            
                            {/* Fallback for legacy data */}
                            {!p.categoryIds && p.categoryId && categories.find(c => c.id === p.categoryId) && (
                              <span 
                                className="badge" 
                                style={{ 
                                  background: `${categories.find(c => c.id === p.categoryId).color}20`, 
                                  color: categories.find(c => c.id === p.categoryId).color, 
                                  border: `1px solid ${categories.find(c => c.id === p.categoryId).color}40`, 
                                  padding: '2px 6px', fontSize: '9px' 
                                }}
                              >
                                {categories.find(c => c.id === p.categoryId).icon} {categories.find(c => c.id === p.categoryId).name}
                              </span>
                            )}

                            {isCombo && (
                              <span className="badge" style={{ background: 'rgba(129, 140, 248, 0.15)', color: '#818cf8', border: '1px solid rgba(129, 140, 248, 0.25)', padding: '2px 6px', fontSize: '9px' }}>
                                Combo
                              </span>
                            )}
                            {hasDeactivatedComponent && (
                              <span className="badge badge-danger" style={{ padding: '1px 4px', fontSize: '9px' }}>
                                ⚠️ Deactivated Item
                              </span>
                            )}
                          </div>
                          
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '11px', color: 'var(--text-muted)' }}>
                            {p.format && p.format !== 'Other' && <span>Format: {p.format}</span>}
                            {p.sizeUnit && <span>Size: {p.sizeUnit}</span>}
                          </div>
                          
                          {p.description && <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{p.description}</div>}
                          
                          {isCombo && bundledNames && (
                            <div style={{ fontSize: '10px', color: 'var(--primary)', fontWeight: 500, marginTop: '2px' }}>
                              🎁 Contains: {bundledNames}
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
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(3px)', zIndex: 100, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px' }}>
          <div className="glass-panel" style={{ padding: '24px', width: '100%', maxWidth: '500px', maxHeight: '90vh', overflowY: 'auto', border: '1px solid rgba(255,255,255,0.08)', boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h2 style={{ fontSize: '18px', fontWeight: 600, letterSpacing: '-0.3px', color: '#fff' }}>{editingId ? 'Edit Product' : 'Add New Product'}</h2>
              <button 
                onClick={() => setShowModal(false)} 
                style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
              >
                <X size={16} />
              </button>
            </div>
            
            <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {/* Product Type Toggle */}
              <div>
                <label className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>Product Type</label>
                <div style={{ display: 'flex', gap: '8px', background: 'rgba(0,0,0,0.2)', padding: '4px', borderRadius: '8px', border: '1px solid var(--surface-border)' }}>
                  <button
                    type="button"
                    className={productType === 'single' ? "btn-primary" : ""}
                    style={{ 
                      flex: 1, fontSize: '12px', padding: '6px 12px', height: '32px', borderRadius: '6px',
                      background: productType === 'single' ? 'var(--primary)' : 'transparent',
                      border: 'none', color: productType === 'single' ? '#fff' : 'var(--text-muted)',
                      fontWeight: productType === 'single' ? 500 : 400
                    }}
                    onClick={() => setProductType('single')}
                  >
                    Single Product
                  </button>
                  <button
                    type="button"
                    className={productType === 'combo' ? "btn-primary" : ""}
                    style={{ 
                      flex: 1, fontSize: '12px', padding: '6px 12px', height: '32px', borderRadius: '6px',
                      background: productType === 'combo' ? 'var(--primary)' : 'transparent',
                      border: 'none', color: productType === 'combo' ? '#fff' : 'var(--text-muted)',
                      fontWeight: productType === 'combo' ? 500 : 400
                    }}
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
                <label className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>Name</label>
                <input 
                  type="text" 
                  className="input-field" 
                  style={{ padding: '10px 12px', fontSize: '13px', borderRadius: '6px', transition: 'all 0.2s ease' }}
                  value={name} 
                  onChange={e => setName(e.target.value)} 
                  placeholder="e.g. Premium Spirulina Capsules"
                  required 
                />
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                {/* Categories - Full Width */}
                <div>
                  <label className="form-label" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>
                    <span>Categories</span>
                    <span style={{ fontSize: '9px', color: 'var(--text-muted)', textTransform: 'none' }}>(Select multiple)</span>
                  </label>
                  <div style={{ 
                    display: 'flex', flexWrap: 'wrap', gap: '8px', padding: '10px', 
                    border: '1px solid var(--surface-border)', borderRadius: '8px', 
                    background: 'rgba(15, 23, 42, 0.4)', minHeight: '44px' 
                  }}>
                    {categories.map(c => {
                      const isSelected = categoryIds.includes(c.id);
                      return (
                        <div 
                          key={c.id}
                          onClick={() => {
                            if (isSelected) {
                              setCategoryIds(categoryIds.filter(id => id !== c.id));
                            } else {
                              setCategoryIds([...categoryIds, c.id]);
                            }
                          }}
                          style={{
                            padding: '6px 12px', fontSize: '12px', borderRadius: '20px', cursor: 'pointer',
                            background: isSelected ? c.color : 'transparent',
                            color: isSelected ? '#fff' : 'var(--text-muted)',
                            border: `1px solid ${isSelected ? c.color : 'var(--surface-border)'}`,
                            display: 'flex', alignItems: 'center', gap: '6px',
                            transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
                            userSelect: 'none',
                            fontWeight: isSelected ? 500 : 400
                          }}
                        >
                          <span style={{ fontSize: '14px' }}>{c.icon}</span> {c.name}
                        </div>
                      );
                    })}
                    {categories.length === 0 && <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>No categories found.</span>}
                  </div>
                </div>

                {/* Format & Size - 50/50 Split */}
                <div style={{ display: 'flex', gap: '12px' }}>
                  <div style={{ flex: 1 }}>
                    <label className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>Format</label>
                    <select className="input-field" style={{ padding: '10px 12px', fontSize: '13px', borderRadius: '6px' }} value={format} onChange={e => setFormat(e.target.value)} required>
                      {['Tablet', 'Capsule', 'Powder', 'Liquid', 'Cream', 'Oil', 'Edible', 'Combo', 'Other'].map(f => <option key={f} value={f}>{f}</option>)}
                    </select>
                  </div>
                  <div style={{ flex: 1 }}>
                    <label className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>Size / Unit</label>
                    <input type="text" className="input-field" style={{ padding: '10px 12px', fontSize: '13px', borderRadius: '6px' }} value={sizeUnit} onChange={e => setSizeUnit(e.target.value)} placeholder="e.g. 100g, 60 units" />
                  </div>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '12px' }}>
                <div style={{ flex: 1 }}>
                  <label className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>Price (₹)</label>
                  <input 
                    type="number" 
                    className="input-field" 
                    style={{ padding: '10px 12px', fontSize: '13px', borderRadius: '6px' }}
                    value={price} 
                    onChange={e => setPrice(e.target.value)} 
                    required 
                  />
                  {productType === 'combo' && totalComboValue > 0 && price !== '' && (
                    <div style={{ fontSize: '11px', color: savingsAmount >= 0 ? 'var(--secondary)' : 'var(--danger)', marginTop: '4px', fontWeight: 500 }}>
                      {savingsAmount >= 0 
                        ? `🎉 Saves ₹${savingsAmount} (${savingsPercent}%)`
                        : `⚠️ Higher by ₹${Math.abs(savingsAmount)}`
                      }
                    </div>
                  )}
                </div>
                <div style={{ flex: 1 }}>
                  <label className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>Sort Order</label>
                  <input 
                    type="number" 
                    className="input-field" 
                    style={{ padding: '10px 12px', fontSize: '13px', borderRadius: '6px' }}
                    value={sortOrder} 
                    onChange={e => setSortOrder(e.target.value)} 
                    required 
                  />
                </div>
              </div>

              <div style={{ position: 'relative' }}>
                <label className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>Emoji Icon</label>
                <div 
                  className="input-field" 
                  style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', cursor: 'pointer', userSelect: 'none', padding: '10px 12px', fontSize: '13px', borderRadius: '6px' }}
                  onClick={() => setShowEmojiPicker(!showEmojiPicker)}
                >
                  <span style={{ fontSize: '18px' }}>{emojiIcon}</span>
                  <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Change Icon</span>
                </div>
                
                {showEmojiPicker && (
                  <div className="glass-panel" style={{ 
                    position: 'absolute', top: '100%', left: 0, right: 0, marginTop: '8px', 
                    padding: '12px', zIndex: 110, display: 'grid', gridTemplateColumns: 'repeat(8, 1fr)', gap: '6px',
                    background: 'var(--background)',
                    border: '1px solid var(--surface-border)',
                    boxShadow: '0 10px 25px rgba(0,0,0,0.5)'
                  }}>
                    {EMOJI_OPTIONS.map(emoji => (
                      <div 
                        key={emoji}
                        onClick={() => { setEmojiIcon(emoji); setShowEmojiPicker(false); }}
                        style={{ 
                          textAlign: 'center', fontSize: '20px', cursor: 'pointer', padding: '6px',
                          borderRadius: '8px', background: emojiIcon === emoji ? 'rgba(79, 70, 229, 0.2)' : 'transparent',
                          border: emojiIcon === emoji ? '1px solid var(--primary)' : '1px solid transparent',
                          transition: 'all 0.2s ease'
                        }}
                        onMouseOver={e => e.currentTarget.style.background = emojiIcon === emoji ? 'rgba(79, 70, 229, 0.2)' : 'rgba(255,255,255,0.05)'}
                        onMouseOut={e => e.currentTarget.style.background = emojiIcon === emoji ? 'rgba(79, 70, 229, 0.2)' : 'transparent'}
                      >
                        {emoji}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div>
                <label className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>Description</label>
                <textarea 
                  className="input-field" 
                  style={{ padding: '10px 12px', fontSize: '13px', borderRadius: '6px', resize: 'vertical' }}
                  value={description} 
                  onChange={e => setDescription(e.target.value)} 
                  rows="2" 
                  placeholder={productType === 'combo' ? "Optional. Leave blank to auto-generate." : "Add a short description..."}
                />
              </div>
              
              <div style={{ display: 'flex', gap: '12px', marginTop: '16px' }}>
                <button 
                  type="button" 
                  onClick={() => setShowModal(false)} 
                  className="btn-secondary"
                  style={{ flex: 1, fontSize: '13px', padding: '10px', borderRadius: '8px' }}
                >
                  Cancel
                </button>
                <button 
                  type="submit" 
                  className="btn-primary"
                  style={{ flex: 1, fontSize: '13px', padding: '10px', borderRadius: '8px', fontWeight: 600, boxShadow: '0 4px 12px rgba(79, 70, 229, 0.3)' }}
                >
                  {editingId ? 'Update Product' : 'Save Product'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {showCategoryMaster && (
        <CategoryMaster onClose={() => setShowCategoryMaster(false)} />
      )}
    </div>
  );
}
