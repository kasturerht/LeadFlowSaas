import React, { useState, useEffect } from 'react';
import { db } from '../firebase';
import { useAuth } from '../AuthContext';
import { collection, query, orderBy, onSnapshot, doc, setDoc, deleteDoc, getDocs } from 'firebase/firestore';
import { Tag, Plus, Edit2, Archive, ArchiveRestore, X, Settings2 } from 'lucide-react';
import CategoryMaster from './CategoryMaster';

export default function Products() {
  const { orgId } = useAuth();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  
  const [editingId, setEditingId] = useState(null);
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [mrp, setMrp] = useState('');
  const [offerPrice, setOfferPrice] = useState('');
  const [bottomPrice, setBottomPrice] = useState('');
  const [shippingFee, setShippingFee] = useState(50);
  const [consumptionDays, setConsumptionDays] = useState(30);
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

  // Migration Security State
  const [showMigrateConfirm, setShowMigrateConfirm] = useState(false);
  const [migratePassword, setMigratePassword] = useState('');
  const [isMigrating, setIsMigrating] = useState(false);

  const EMOJI_OPTIONS = [
    '🌿', '💊', '🧴', '🥃', '🍯', '🍵', 
    '🥥', '🥑', '🍎', '🍋', '🫐', '🥕',
    '💧', '🌟', '⚡', '🩸', '🦴', '🧠', 
    '❤️', '👁️', '🦷', '💪', '🥗', '🥤',
    '⚕️', '🍃', '🍄', '🌺', '📦', '🎁'
  ];

  useEffect(() => {
    if (!orgId) return;
    const q = query(collection(db, "organizations", orgId, "products"), orderBy("sortOrder", "asc"));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const prods = [];
      snapshot.forEach(docSnap => prods.push({ id: docSnap.id, ...docSnap.data() }));
      setProducts(prods);
      setLoading(false);
    });

    // Fetch Categories
    const unsubCats = onSnapshot(query(collection(db, "organizations", orgId, "categories"), orderBy("name")), (snapshot) => {
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
              await setDoc(doc(db, "organizations", orgId, "categories", c.id), { name: c.name, color: c.color, icon: c.icon, isActive: true });
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
  }, [orgId]);

  const openAdd = () => {
    setEditingId(null);
    setName('');
    setPrice('');
    setMrp('');
    setOfferPrice('');
    setBottomPrice('');
    setShippingFee(50);
    setConsumptionDays(30);
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
    setMrp(p.mrp || p.price || '');
    setOfferPrice(p.offerPrice || p.price || '');
    setBottomPrice(p.bottomPrice || p.price || '');
    setShippingFee(p.shippingFee ?? 50);
    setConsumptionDays(p.consumptionDays ?? 30);
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
    if (!name || offerPrice === '' || bottomPrice === '') return;
    if (Number(bottomPrice) > Number(offerPrice)) {
      alert("Bottom Price cannot be greater than Offer Price.");
      return;
    }
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
      price: Number(offerPrice), // Legacy fallback
      mrp: Number(mrp || offerPrice),
      offerPrice: Number(offerPrice),
      bottomPrice: Number(bottomPrice),
      shippingFee: Number(shippingFee),
      consumptionDays: Number(consumptionDays) || 30,
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
        await setDoc(doc(db, "organizations", orgId, "products", editingId), prodData, { merge: true });
      } else {
        await setDoc(doc(collection(db, "organizations", orgId, "products")), prodData);
      }
      setShowModal(false);
    } catch (err) {
      console.error(err);
      alert("Error saving product");
    }
  };

  const toggleStatus = async (p) => {
    if (!orgId) return;
    if (window.confirm(`Are you sure you want to ${p.isActive ? 'disable' : 'enable'} ${p.name}?`)) {
      await setDoc(doc(db, "organizations", orgId, "products", p.id), { isActive: !p.isActive }, { merge: true });
    }
  };

  const handleMigrateClick = () => {
    setShowMigrateConfirm(true);
    setMigratePassword('');
  };

  const executeSeedProducts = async () => {
    if (!migratePassword) {
      alert("Password is required.");
      return;
    }
    
    setIsMigrating(true);
    try {
      const { EmailAuthProvider, reauthenticateWithCredential } = await import('firebase/auth');
      const { auth } = await import('../firebase');
      const credential = EmailAuthProvider.credential(auth.currentUser.email, migratePassword);
      await reauthenticateWithCredential(auth.currentUser, credential);
    } catch (err) {
      console.error(err);
      alert("Incorrect password! Access denied.");
      setIsMigrating(false);
      return;
    }

    try {
      // 1. Clear old Products
      const snapshot = await getDocs(query(collection(db, "organizations", orgId, "products")));
      for (const docSnap of snapshot.docs) {
        await deleteDoc(doc(db, "organizations", orgId, "products", docSnap.id));
      }

      // 2. Clear old Categories
      const catSnapshot = await getDocs(query(collection(db, "organizations", orgId, "categories")));
      for (const docSnap of catSnapshot.docs) {
        await deleteDoc(doc(db, "organizations", orgId, "categories", docSnap.id));
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
        await setDoc(doc(db, "organizations", orgId, "categories", c.id), c);
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
        await setDoc(doc(db, "organizations", orgId, "products", id), data);
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
        await setDoc(doc(db, "organizations", orgId, "products", id), data);
      }

      alert("Database Reset Complete: 7 Premium Categories & 21 Mapped Products added successfully!");
      setShowMigrateConfirm(false);
    } catch (err) {
      console.error(err);
      alert("Error migrating products & categories!");
    }
    setIsMigrating(false);
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
          <button className="btn-secondary" onClick={handleMigrateClick} style={{ padding: '6px 12px', fontSize: '13px', border: '1px solid var(--danger)', color: 'var(--danger)' }}>
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
          <div className="product-bento-grid">
            {products.filter(p => selectedCategoryFilter === 'all' || (p.categoryIds && p.categoryIds.includes(selectedCategoryFilter)) || p.categoryId === selectedCategoryFilter).map(p => {
              const isCombo = p.type === 'combo';
              
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
                <div key={p.id} className={`bento-card fade-in ${p.isActive ? '' : 'disabled'}`} style={{ display: 'flex', flexDirection: 'column', height: '100%', padding: '20px' }}>
                  
                  {/* TOP ROW: Emoji + Title + Badges + Actions inline */}
                  <div style={{ display: 'flex', gap: '16px', alignItems: 'flex-start', marginBottom: '16px' }}>
                    <div className="bento-emoji" style={{ height: '56px', width: '56px', fontSize: '28px', flexShrink: 0, borderRadius: '14px', background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      {p.emojiIcon || '📦'}
                    </div>
                    
                    <div style={{ flexGrow: 1, display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                        <div style={{ fontSize: '17px', fontWeight: 700, color: '#f8fafc', letterSpacing: '-0.3px', lineHeight: 1.2 }}>
                          {p.name}
                        </div>
                        <div className="bento-actions" style={{ display: 'flex', gap: '6px', marginLeft: '8px' }}>
                          <button className="action-fab" onClick={() => openEdit(p)} title="Edit"><Edit2 size={13} /></button>
                          <button className={`action-fab ${p.isActive ? 'danger' : ''}`} onClick={() => toggleStatus(p)}>
                            {p.isActive ? <Archive size={13} /> : <ArchiveRestore size={13} />}
                          </button>
                        </div>
                      </div>
                      
                      <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
                        <span style={{ fontSize: '11px', color: p.isActive ? '#10b981' : '#ef4444', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '4px' }}>
                          <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: p.isActive ? '#10b981' : '#ef4444' }}></span>
                          {p.isActive ? 'Active' : 'Disabled'}
                        </span>
                        <span style={{ fontSize: '11px', color: '#64748b' }}>•</span>
                        <span style={{ fontSize: '11px', color: '#94a3b8', fontWeight: 500 }}>SKU #{p.sortOrder || '-'}</span>
                        {hasDeactivatedComponent && (
                          <span style={{ padding: '2px 6px', background: 'rgba(239, 68, 68, 0.15)', color: '#ef4444', borderRadius: '4px', fontSize: '9px', fontWeight: 600 }}>⚠️ Issue</span>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Category and Tags Row */}
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '16px' }}>
                    {p.format && p.format !== 'Other' && (
                      <span style={{ fontSize: '10px', fontWeight: 600, color: '#cbd5e1', background: 'rgba(255,255,255,0.06)', padding: '4px 10px', borderRadius: '20px' }}>{p.format}</span>
                    )}
                    {p.sizeUnit && (
                      <span style={{ fontSize: '10px', fontWeight: 600, color: '#cbd5e1', background: 'rgba(255,255,255,0.06)', padding: '4px 10px', borderRadius: '20px' }}>{p.sizeUnit}</span>
                    )}
                    {p.categoryIds && p.categoryIds.map(catId => {
                      const c = categories.find(cat => cat.id === catId);
                      if (!c) return null;
                      return (
                        <span key={c.id} style={{ fontSize: '10px', fontWeight: 600, background: `${c.color}15`, color: c.color, border: `1px solid ${c.color}30`, padding: '4px 10px', borderRadius: '20px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                          {c.icon} {c.name}
                        </span>
                      );
                    })}
                    {!p.categoryIds && p.categoryId && categories.find(c => c.id === p.categoryId) && (
                      <span style={{ fontSize: '10px', fontWeight: 600, background: `${categories.find(c => c.id === p.categoryId).color}15`, color: categories.find(c => c.id === p.categoryId).color, border: `1px solid ${categories.find(c => c.id === p.categoryId).color}30`, padding: '4px 10px', borderRadius: '20px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                        {categories.find(c => c.id === p.categoryId).icon} {categories.find(c => c.id === p.categoryId).name}
                      </span>
                    )}
                    {isCombo && (
                      <span style={{ fontSize: '10px', fontWeight: 600, background: 'rgba(129, 140, 248, 0.15)', color: '#818cf8', border: '1px solid rgba(129, 140, 248, 0.25)', padding: '4px 10px', borderRadius: '20px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                        🎁 Combo Bundle
                      </span>
                    )}
                  </div>

                  {p.description && <div style={{ fontSize: '12px', color: '#94a3b8', lineHeight: 1.5, marginBottom: '16px' }}>{p.description}</div>}

                  {isCombo && p.bundledProducts && p.bundledProducts.length > 0 && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginBottom: '16px', background: 'rgba(0,0,0,0.1)', padding: '8px 12px', borderRadius: '8px', border: '1px dashed rgba(255,255,255,0.1)' }}>
                      <span style={{ fontSize: '10px', color: '#64748b', fontWeight: 600, textTransform: 'uppercase' }}>Includes</span>
                      {p.bundledProducts.map((item, idx) => {
                        const pr = products.find(prod => prod.id === item.productId);
                        if (!pr) return null;
                        return (
                          <div key={idx} style={{ fontSize: '12px', color: '#cbd5e1' }}>
                            <span style={{ color: '#818cf8', fontWeight: 600, marginRight: '6px' }}>{item.quantity}x</span> {pr.emojiIcon} {pr.name}
                          </div>
                        );
                      })}
                    </div>
                  )}

                  <div style={{ flexGrow: 1 }}></div>

                  {/* Footer (Price & Metrics) */}
                  <div style={{ background: 'rgba(0,0,0,0.25)', borderRadius: '14px', padding: '16px', border: '1px solid rgba(255,255,255,0.03)', display: 'flex', flexDirection: 'column', gap: '14px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
                      <div style={{ display: 'flex', flexDirection: 'column' }}>
                        <span style={{ fontSize: '10px', color: '#64748b', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '4px' }}>Offer Price</span>
                        <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px' }}>
                          <span style={{ fontSize: '22px', fontWeight: 800, color: '#fff', letterSpacing: '-0.5px' }}>₹{p.offerPrice || p.price}</span>
                          <span style={{ fontSize: '13px', fontWeight: 500, color: '#64748b', textDecoration: 'line-through' }}>₹{p.mrp || Math.round(p.price * 1.2)}</span>
                        </div>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px', background: 'rgba(245, 158, 11, 0.1)', padding: '6px 10px', borderRadius: '8px', border: '1px solid rgba(245, 158, 11, 0.2)' }}>
                        <span style={{ fontSize: '13px' }}>⏱️</span>
                        <span style={{ fontSize: '11px', color: '#fcd34d', fontWeight: 700 }}>{p.consumptionDays || 30} Days</span>
                      </div>
                    </div>
                    
                    <div style={{ height: '1px', background: 'rgba(255,255,255,0.06)' }}></div>
                    
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontSize: '11px', color: '#94a3b8', fontWeight: 500 }}>Min Limit:</span>
                        <span style={{ fontSize: '13px', color: '#f472b6', fontWeight: 700 }}>₹{p.bottomPrice || p.price}</span>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontSize: '11px', color: '#94a3b8', fontWeight: 500 }}>Shipping:</span>
                        <span style={{ fontSize: '13px', color: '#34d399', fontWeight: 700 }}>{(p.shippingFee ?? 50) === 0 ? 'Free' : `+₹${p.shippingFee ?? 50}`}</span>
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
            
            {products.length === 0 && (
              <div style={{ gridColumn: '1 / -1', padding: '40px 20px', textAlign: 'center', color: 'var(--text-muted)', background: 'rgba(0,0,0,0.2)', borderRadius: '12px', border: '1px dashed var(--surface-border)' }}>
                <div style={{ marginBottom: '16px', fontSize: '14px' }}>No products found in this category.</div>
                <button className="btn-primary" style={{ fontSize: '13px' }} onClick={seedProducts}>
                  Seed Default Catalog
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {showMigrateConfirm && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(8px)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px' }}>
          <div className="cyber-modal fade-in" style={{ width: '100%', maxWidth: '400px', padding: '24px', border: '1px solid var(--danger)' }}>
            <h2 style={{ fontSize: '18px', fontWeight: 600, color: '#ef4444', marginBottom: '16px' }}>Confirm Migration</h2>
            <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '20px' }}>This action is irreversible. Please verify your Super Admin password to proceed with the bulk product migration.</p>
            <input 
              type="password" 
              className="input-field" 
              placeholder="Admin Password" 
              value={migratePassword} 
              onChange={(e) => setMigratePassword(e.target.value)}
              style={{ marginBottom: '20px' }}
            />
            <div style={{ display: 'flex', gap: '10px' }}>
              <button className="btn-secondary" onClick={() => setShowMigrateConfirm(false)} style={{ flex: 1 }} disabled={isMigrating}>Cancel</button>
              <button className="btn-primary" onClick={executeSeedProducts} style={{ flex: 1, background: 'var(--danger)' }} disabled={isMigrating}>
                {isMigrating ? 'Migrating...' : 'Confirm & Migrate'}
              </button>
            </div>
          </div>
        </div>
      )}

      {showModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(8px)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px' }}>
          <div className="cyber-modal fade-in" style={{ width: '100%', maxWidth: '500px', padding: '32px', maxHeight: '90vh', overflowY: 'auto' }}>

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

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div>
                  <label className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>MRP (₹)</label>
                  <input type="number" className="input-field" style={{ padding: '10px 12px', fontSize: '13px', borderRadius: '6px' }} value={mrp} onChange={e => setMrp(e.target.value)} />
                </div>
                <div>
                  <label className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>Offer Price (₹)</label>
                  <input type="number" className="input-field" style={{ padding: '10px 12px', fontSize: '13px', borderRadius: '6px' }} value={offerPrice} onChange={e => setOfferPrice(e.target.value)} required />
                </div>
                <div>
                  <label className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>Bottom Price (₹)</label>
                  <input type="number" className="input-field" style={{ padding: '10px 12px', fontSize: '13px', borderRadius: '6px' }} value={bottomPrice} onChange={e => setBottomPrice(e.target.value)} required />
                </div>
                <div>
                  <label className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>Shipping Fee (₹)</label>
                  <input type="number" className="input-field" style={{ padding: '10px 12px', fontSize: '13px', borderRadius: '6px' }} value={shippingFee} onChange={e => setShippingFee(e.target.value)} required />
                </div>
                <div>
                  <label className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>Consumption Days</label>
                  <input type="number" className="input-field" style={{ padding: '10px 12px', fontSize: '13px', borderRadius: '6px' }} value={consumptionDays} onChange={e => setConsumptionDays(e.target.value)} placeholder="e.g. 30" required />
                </div>
              </div>

              <div>
                <label className="form-label" style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '6px' }}>Sort Order</label>
                <input 
                  type="number" 
                  className="input-field" 
                  style={{ padding: '10px 12px', fontSize: '13px', borderRadius: '6px', width: '100%' }}
                  value={sortOrder} 
                  onChange={e => setSortOrder(e.target.value)} 
                  required 
                />
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
              
              <div style={{ display: 'flex', gap: '12px', marginTop: '24px', paddingBottom: '16px' }}>
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
