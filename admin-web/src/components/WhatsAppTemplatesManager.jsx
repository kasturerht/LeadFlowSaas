import React, { useState, useEffect, useRef } from 'react';
import { collection, query, getDocs, doc, setDoc, deleteDoc, getDoc } from 'firebase/firestore';
import { httpsCallable } from 'firebase/functions';
import { db, functions } from '../firebase';
import { useAuth } from '../AuthContext';
import { Plus, Edit2, Trash2, MessageSquare, AlertCircle, ChevronDown, Check, Sparkles } from 'lucide-react';

// Custom Select Component for Premium Look
const CustomSelect = ({ value, onChange, options, label, disabledOptions = [] }) => {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <div className="custom-select-container" ref={dropdownRef} style={{ position: 'relative', width: '100%' }}>
      <label className="input-label" style={{ fontSize: '11px', fontWeight: 500, color: 'rgba(255,255,255,0.6)', marginBottom: '6px', display: 'block' }}>{label}</label>
      <div 
        onClick={() => setIsOpen(!isOpen)}
        style={{
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          padding: '8px 12px', background: 'rgba(0,0,0,0.2)',
          border: 'none',
          borderRadius: '8px', cursor: 'pointer',
          boxShadow: isOpen ? '0 0 0 1px rgba(255,255,255,0.3)' : 'inset 0 0 0 1px rgba(255,255,255,0.08)',
          transition: 'all 0.2s ease', color: '#f8fafc'
        }}
      >
        <span style={{ fontSize: '13px', fontWeight: 400 }}>{value}</span>
        <ChevronDown size={16} style={{ color: 'var(--text-muted)', transform: isOpen ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s ease' }} />
      </div>

      {isOpen && (
        <div style={{
          position: 'absolute', top: 'calc(100% + 8px)', left: 0, right: 0,
          background: '#1e1e24', border: '1px solid var(--border)',
          borderRadius: '10px', padding: '6px', zIndex: 100,
          boxShadow: '0 10px 25px rgba(0,0,0,0.5)',
          display: 'flex', flexDirection: 'column', gap: '4px'
        }}>
          {options.map(opt => {
            const isDisabled = disabledOptions.includes(opt);
            return (
              <div 
                key={opt}
                onClick={() => { if(!isDisabled) { onChange(opt); setIsOpen(false); } }}
                style={{
                  padding: '8px 12px', borderRadius: '6px', cursor: isDisabled ? 'not-allowed' : 'pointer',
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  background: value === opt ? 'rgba(99, 102, 241, 0.15)' : 'transparent',
                  color: isDisabled ? 'rgba(255,255,255,0.3)' : (value === opt ? 'var(--primary)' : 'var(--text)'),
                  fontSize: '13px', transition: 'background 0.1s ease',
                  opacity: isDisabled ? 0.4 : 1
                }}
                onMouseEnter={(e) => { if(!isDisabled && value !== opt) e.currentTarget.style.background = 'rgba(255, 255, 255, 0.05)' }}
                onMouseLeave={(e) => { if(!isDisabled && value !== opt) e.currentTarget.style.background = 'transparent' }}
              >
                <span>{opt} {isDisabled && <span style={{ fontSize: '11px', fontStyle: 'italic', marginLeft: '6px' }}>(Exists)</span>}</span>
                {value === opt && <Check size={16} color="var(--primary)" />}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

// Premium Toggle Switch
const ToggleSwitch = ({ checked, onChange, label }) => (
  <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', userSelect: 'none' }}>
    <div style={{ position: 'relative', width: '36px', height: '20px' }}>
      <input 
        type="checkbox" 
        checked={checked} 
        onChange={e => onChange(e.target.checked)} 
        style={{ opacity: 0, width: 0, height: 0, position: 'absolute' }} 
      />
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
        background: checked ? '#10b981' : 'rgba(255, 255, 255, 0.15)',
        borderRadius: '20px', transition: 'background-color 0.3s ease',
        boxShadow: checked ? 'none' : 'inset 0 1px 3px rgba(0,0,0,0.2)'
      }}>
        <div style={{
          position: 'absolute', top: '2px', left: checked ? '18px' : '2px',
          width: '16px', height: '16px', background: '#fff',
          borderRadius: '50%', transition: 'all 0.3s cubic-bezier(0.68, -0.55, 0.265, 1.55)',
          boxShadow: '0 1px 3px rgba(0,0,0,0.4)'
        }} />
      </div>
    </div>
    <span style={{ fontSize: '12px', color: checked ? '#fff' : 'rgba(255,255,255,0.6)', fontWeight: 600, transition: 'color 0.3s ease' }}>{label}</span>
  </label>
);

export default function WhatsAppTemplatesManager() {
  const { orgId } = useAuth();
  const [templates, setTemplates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isGenerating, setIsGenerating] = useState(false);
  
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [expandedId, setExpandedId] = useState(null);
  const [showTagMenu, setShowTagMenu] = useState(false);
  const tagMenuRef = useRef(null);
  
  const [statusTrigger, setStatusTrigger] = useState('Product Enquiry');
  const [language, setLanguage] = useState('Marathi');
  const [templateText, setTemplateText] = useState('');
  const [isActive, setIsActive] = useState(true);

  const STATUS_OPTIONS = ['Product Inquiry Only', 'Order Placed', 'Call Not Answered', 'Follow-up', 'Pending', 'Invalid', 'Not Interested'];
  const LANGUAGES = ['Marathi', 'English', 'Hindi', 'Gujarati'];

  // Identify which languages are already taken for the current status trigger
  const takenLanguages = templates
    .filter(t => t.statusTrigger === statusTrigger && t.id !== editingId)
    .map(t => t.language);
  const isDuplicate = takenLanguages.includes(language);

  useEffect(() => {
    if (orgId) {
      fetchTemplates();
    }
  }, [orgId, db]);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (tagMenuRef.current && !tagMenuRef.current.contains(event.target)) {
        setShowTagMenu(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const fetchTemplates = async () => {
    try {
      const q = query(collection(db, 'organizations', orgId, 'whatsapp_templates'));
      const snapshot = await getDocs(q);
      const data = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      setTemplates(data);
    } catch (err) {
      console.error("Error fetching templates:", err);
    } finally {
      setLoading(false);
    }
  };

  const generateWithAI = async () => {
    if (!statusTrigger || !language) {
      alert("Please select a Status and Language first.");
      return;
    }

    setIsGenerating(true);
    try {
      // Fetch org details to get orgName and products
      const orgDoc = await getDoc(doc(db, 'organizations', orgId));
      const orgData = orgDoc.exists() ? orgDoc.data() : {};
      
      const prodsSnap = await getDocs(collection(db, 'organizations', orgId, 'products'));
      const productNames = prodsSnap.docs.map(d => d.data().name).join(', ');

      const generateTemplate = httpsCallable(functions, 'generateWhatsAppTemplate');
      const result = await generateTemplate({
        status: statusTrigger,
        language: language,
        orgName: orgData.orgName || 'Our Company',
        products: productNames
      });

      if (result.data && result.data.text) {
        setTemplateText(result.data.text);
      }
    } catch (err) {
      console.error("AI Generation failed:", err);
      alert(err.message || "AI is currently busy or an error occurred. Please try again later.");
    } finally {
      setIsGenerating(false);
    }
  };

  const handleSave = async (e) => {
    if(e) e.preventDefault();
    if (!templateText.trim()) return;

    if (/\{[^{}]+\}/.test(templateText) && !/\{\{[^{}]+\}\}/.test(templateText)) {
      alert("Warning: It looks like you have malformed tags. Please use {{tag}} format.");
    }

    const docId = editingId || `template_${Date.now()}`;
    const payload = {
      statusTrigger,
      language,
      templateText,
      isActive,
      updatedAt: new Date().toISOString()
    };

    try {
      await setDoc(doc(db, 'organizations', orgId, 'whatsapp_templates', docId), payload);
      setShowModal(false);
      resetForm();
      fetchTemplates();
    } catch (err) {
      console.error("Error saving template:", err);
      alert("Failed to save template: " + (err.message || err));
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm("Are you sure you want to delete this template?")) {
      try {
        await deleteDoc(doc(db, 'organizations', orgId, 'whatsapp_templates', id));
        fetchTemplates();
      } catch (err) {
        console.error("Error deleting template:", err);
      }
    }
  };

  const resetForm = () => {
    setEditingId(null);
    setStatusTrigger('Product Inquiry Only');
    setLanguage('Marathi');
    setTemplateText('');
    setIsActive(true);
  };

  const openEdit = (t) => {
    setEditingId(t.id);
    setStatusTrigger(t.statusTrigger);
    setLanguage(t.language);
    setTemplateText(t.templateText);
    setIsActive(t.isActive);
    setShowModal(true);
  };

  const insertTag = (tag) => {
    setTemplateText((prev) => prev + tag);
  };

  const smartTagsGrouped = [
    {
      category: 'Customer Info',
      tags: [
        { label: 'Customer Name', value: '{{customer_name}}' },
        { label: 'Delivery Address', value: '{{delivery_address}}' },
      ]
    },
    {
      category: 'Order Details',
      tags: [
        { label: 'Product List', value: '{{product_list}}' },
        { label: 'Product & Qty', value: '{{product_list_with_quantity}}' },
      ]
    },
    {
      category: 'Pricing & Discounts',
      tags: [
        { label: 'Regular Price', value: '{{regular_price}}' },
        { label: 'Special Price', value: '{{special_price}}' },
        { label: 'Saved Amount', value: '{{saved_amount}}' },
        { label: 'Discount %', value: '{{discount_percentage}}' },
      ]
    },
    {
      category: 'Payment',
      tags: [
        { label: 'Payment Status', value: '{{payment_status}}' },
        { label: 'UPI Link', value: '{{upi_payment_link}}' },
      ]
    },
    {
      category: 'Company',
      tags: [
        { label: 'Org Name', value: '{{org_name}}' },
        { label: 'Support Number', value: '{{support_number}}' },
      ]
    }
  ];

  return (
    <div>
      <div className="page-header">
        <div className="page-title-group">
          <h1 className="page-title" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{ background: 'linear-gradient(135deg, #6366f1, #a855f7)', padding: '10px', borderRadius: '12px', display: 'flex', boxShadow: '0 4px 15px rgba(99, 102, 241, 0.4)' }}>
              <MessageSquare size={24} color="#fff" />
            </div>
            WhatsApp Templates
          </h1>
          <p className="page-subtitle">
            Design premium automated conversations based on lead status.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <button className="btn-primary" onClick={() => { resetForm(); setShowModal(true); }} style={{ padding: '12px 24px', fontSize: '15px', borderRadius: '12px', boxShadow: '0 4px 15px rgba(99, 102, 241, 0.3)' }}>
            <Plus size={18} style={{ marginRight: '6px' }} /> New Template
          </button>
        </div>
      </div>

      <div className="glass-panel" style={{ padding: '24px', minHeight: '60vh' }}>

      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '40px', color: 'var(--text-muted)' }}>Loading...</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {templates.map(t => {
            const isExpanded = expandedId === t.id;
            return (
              <div key={t.id} style={{
                background: 'linear-gradient(145deg, rgba(255,255,255,0.03) 0%, rgba(255,255,255,0.01) 100%)',
                border: `1px solid ${isExpanded ? 'rgba(99, 102, 241, 0.4)' : 'rgba(255,255,255,0.05)'}`,
                borderRadius: '12px',
                overflow: 'hidden',
                transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                boxShadow: isExpanded ? '0 8px 30px rgba(0,0,0,0.2)' : '0 2px 10px rgba(0,0,0,0.1)'
              }}>
                <div 
                  onClick={() => setExpandedId(isExpanded ? null : t.id)}
                  style={{ 
                    display: 'flex', alignItems: 'center', padding: '16px 20px', cursor: 'pointer', gap: '20px' 
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(255,255,255,0.02)'}
                  onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                >
                  <div style={{ width: '150px', flexShrink: 0 }}>
                    <span style={{ 
                      background: t.isActive ? 'rgba(34, 197, 94, 0.15)' : 'rgba(239, 68, 68, 0.15)', 
                      color: t.isActive ? '#4ade80' : '#f87171',
                      padding: '4px 12px', borderRadius: '20px', fontSize: '12px', fontWeight: 600,
                      display: 'inline-block', border: `1px solid ${t.isActive ? 'rgba(34, 197, 94, 0.3)' : 'rgba(239, 68, 68, 0.3)'}`
                    }}>
                      {t.statusTrigger}
                    </span>
                  </div>
                  
                  <div style={{ width: '90px', flexShrink: 0, color: 'var(--text-muted)', fontSize: '14px', fontWeight: 500 }}>
                    {t.language}
                  </div>

                  <div style={{ 
                    flex: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', 
                    color: 'var(--text)', fontSize: '14px', opacity: 0.8,
                    fontFamily: 'Inter, system-ui, sans-serif'
                  }}>
                    {t.templateText.replace(/\n/g, ' ')}
                  </div>

                  <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }} onClick={e => e.stopPropagation()}>
                    <button onClick={() => openEdit(t)} className="icon-action-btn edit-btn">
                      <Edit2 size={16} />
                    </button>
                    <button onClick={() => handleDelete(t.id)} className="icon-action-btn delete-btn">
                      <Trash2 size={16} />
                    </button>
                    <div style={{ width: '1px', height: '24px', background: 'rgba(255,255,255,0.1)', margin: '0 8px' }}></div>
                    <div style={{ 
                      color: 'var(--text-muted)', display: 'flex', alignItems: 'center', justifyContent: 'center',
                      transform: isExpanded ? 'rotate(180deg)' : 'none', 
                      transition: 'transform 0.3s ease', cursor: 'pointer', padding: '4px'
                    }} onClick={() => setExpandedId(isExpanded ? null : t.id)}>
                      <ChevronDown size={20} />
                    </div>
                  </div>
                </div>

                {isExpanded && (
                  <div style={{ 
                    padding: '0 20px 20px 20px', 
                    borderTop: '1px solid rgba(255,255,255,0.05)',
                    animation: 'fadeIn 0.3s ease forwards'
                  }}>
                    <div style={{ 
                      marginTop: '16px', background: 'rgba(0,0,0,0.3)', padding: '20px', 
                      borderRadius: '10px', fontSize: '14px', lineHeight: '1.6', 
                      whiteSpace: 'pre-wrap', color: '#e2e8f0', border: '1px solid rgba(255,255,255,0.02)',
                      fontFamily: 'Inter, system-ui, sans-serif'
                    }}>
                      {t.templateText}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
          {templates.length === 0 && (
            <div style={{ textAlign: 'center', padding: '60px', background: 'rgba(255,255,255,0.02)', borderRadius: '16px', border: '1px dashed rgba(255,255,255,0.1)' }}>
              <MessageSquare size={48} color="var(--text-muted)" style={{ marginBottom: '16px', opacity: 0.5 }} />
              <h3 style={{ margin: '0 0 8px 0', color: 'var(--text)' }}>No templates yet</h3>
              <p style={{ margin: 0, color: 'var(--text-muted)' }}>Create your first WhatsApp template to automate your customer communication.</p>
            </div>
          )}
        </div>
      )}
      </div>

      {/* Premium Glassmorphism Modal */}
      {showModal && (
        <div className="modal-overlay" style={{ padding: '24px', background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(4px)' }}>
          <div style={{
            width: '100%', maxWidth: '520px',
            background: 'rgba(15, 15, 18, 0.95)', backdropFilter: 'blur(24px)',
            border: '1px solid rgba(255,255,255,0.08)',
            boxShadow: '0 24px 48px rgba(0,0,0,0.4), 0 0 0 1px rgba(255,255,255,0.02)',
            borderRadius: '16px', display: 'flex', flexDirection: 'column',
            maxHeight: 'calc(100vh - 48px)',
            animation: 'slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
            fontFamily: 'Inter, system-ui, sans-serif'
          }}>
            {/* Header */}
            <div style={{ padding: '20px 24px 12px 24px', display: 'flex', alignItems: 'center', gap: '12px', flexShrink: 0 }}>
              <div style={{ background: 'linear-gradient(135deg, rgba(255,255,255,0.1), rgba(255,255,255,0.02))', border: '1px solid rgba(255,255,255,0.05)', padding: '6px', borderRadius: '8px' }}>
                <MessageSquare size={16} color="#e2e8f0" />
              </div>
              <h3 style={{ margin: 0, fontSize: '15px', fontWeight: 500, color: '#f8fafc', letterSpacing: '-0.2px' }}>
                {editingId ? 'Edit Template' : 'New Template'}
              </h3>
            </div>

            {/* Body */}
            <div style={{ padding: '8px 24px', display: 'flex', flexDirection: 'column', gap: '20px', overflowY: 'visible' }}>
              <div style={{ display: 'flex', gap: '16px' }}>
                <CustomSelect label="Trigger" value={statusTrigger} onChange={setStatusTrigger} options={STATUS_OPTIONS} />
                <CustomSelect label="Language" value={language} onChange={setLanguage} options={LANGUAGES} disabledOptions={takenLanguages} />
              </div>

              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                  <label style={{ fontSize: '11px', fontWeight: 500, color: 'rgba(255,255,255,0.6)', display: 'block', margin: 0 }}>Message Content</label>
                  
                  <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                    
                    {/* Insert Variable Dropdown */}
                    <div style={{ position: 'relative' }} ref={tagMenuRef}>
                      <button 
                        onClick={() => setShowTagMenu(!showTagMenu)}
                        style={{
                          background: 'rgba(255,255,255,0.03)', border: 'none',
                          boxShadow: 'inset 0 0 0 1px rgba(255,255,255,0.08)',
                          color: '#e2e8f0', padding: '4px 8px', borderRadius: '6px',
                          fontSize: '11px', fontWeight: 500, cursor: 'pointer',
                          display: 'flex', alignItems: 'center', gap: '4px', transition: 'all 0.2s ease'
                        }}
                        onMouseEnter={(e) => { e.currentTarget.style.background = 'rgba(255,255,255,0.08)' }}
                        onMouseLeave={(e) => { e.currentTarget.style.background = 'rgba(255,255,255,0.03)' }}
                      >
                        <span style={{ fontFamily: 'monospace', color: '#818cf8', fontWeight: 700 }}>{'{}'}</span> Insert <ChevronDown size={12} />
                      </button>

                      {showTagMenu && (
                        <>
                          {/* Custom scrollbar for dropdown */}
                          <style>{`
                            .smart-tags-dropdown::-webkit-scrollbar { width: 6px; }
                            .smart-tags-dropdown::-webkit-scrollbar-track { background: transparent; }
                            .smart-tags-dropdown::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.1); border-radius: 10px; }
                            .smart-tags-dropdown::-webkit-scrollbar-thumb:hover { background: rgba(255,255,255,0.2); }
                          `}</style>
                          
                          <div className="smart-tags-dropdown" style={{
                            position: 'absolute', top: '110%', right: 0, zIndex: 100,
                            background: 'rgba(20, 20, 30, 0.95)', backdropFilter: 'blur(16px)',
                            border: '1px solid rgba(255,255,255,0.1)',
                            borderRadius: '12px', width: '240px', maxHeight: '280px', overflowY: 'auto',
                            boxShadow: '0 20px 40px rgba(0,0,0,0.6), 0 0 0 1px rgba(255,255,255,0.05)', 
                            padding: '8px', animation: 'fadeIn 0.2s cubic-bezier(0.16, 1, 0.3, 1)'
                          }}>
                            {smartTagsGrouped.map((group, gIdx) => (
                              <div key={group.category} style={{ marginBottom: gIdx === smartTagsGrouped.length - 1 ? 0 : '12px' }}>
                                <div style={{ 
                                  fontSize: '9px', fontWeight: 700, color: '#a855f7', 
                                  padding: '2px 8px', textTransform: 'uppercase', letterSpacing: '0.5px',
                                  marginBottom: '2px'
                                }}>
                                  {group.category}
                                </div>
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                                  {group.tags.map(tag => (
                                    <div 
                                      key={tag.value}
                                      onClick={() => { insertTag(tag.value); setShowTagMenu(false); }}
                                      style={{
                                        padding: '6px 8px', borderRadius: '6px', color: '#e2e8f0',
                                        fontSize: '11px', cursor: 'pointer', transition: 'all 0.2s ease',
                                        display: 'flex', alignItems: 'center', justifyContent: 'space-between'
                                      }}
                                      onMouseEnter={e => {
                                        e.currentTarget.style.background = 'rgba(99, 102, 241, 0.15)';
                                        e.currentTarget.style.color = '#818cf8';
                                        e.currentTarget.querySelector('.tag-insert-btn').style.opacity = '1';
                                      }}
                                      onMouseLeave={e => {
                                        e.currentTarget.style.background = 'transparent';
                                        e.currentTarget.style.color = '#e2e8f0';
                                        e.currentTarget.querySelector('.tag-insert-btn').style.opacity = '0';
                                      }}
                                    >
                                      <span style={{ fontWeight: 500 }}>{tag.label}</span>
                                      <div className="tag-insert-btn" style={{ 
                                        opacity: 0, transition: 'opacity 0.2s ease',
                                        background: 'rgba(99, 102, 241, 0.2)', color: '#818cf8', 
                                        padding: '2px 6px', borderRadius: '8px', fontSize: '9px', fontWeight: 600
                                      }}>Insert</div>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            ))}
                          </div>
                        </>
                      )}
                    </div>

                    {/* AI Generate Button */}
                    <button 
                      onClick={generateWithAI}
                      disabled={isGenerating || isDuplicate}
                      style={{
                        background: 'rgba(255,255,255,0.08)',
                        border: 'none', color: '#fff', padding: '4px 8px', borderRadius: '6px',
                        fontSize: '11px', fontWeight: 500, cursor: (isGenerating || isDuplicate) ? 'not-allowed' : 'pointer',
                        display: 'flex', alignItems: 'center', gap: '4px', opacity: (isGenerating || isDuplicate) ? 0.5 : 1,
                        transition: 'all 0.2s ease'
                      }}
                      onMouseEnter={(e) => { if(!isGenerating && !isDuplicate) e.currentTarget.style.background = 'rgba(255,255,255,0.12)'; }}
                      onMouseLeave={(e) => { if(!isGenerating && !isDuplicate) e.currentTarget.style.background = 'rgba(255,255,255,0.08)'; }}
                    >
                      <Sparkles size={14} />
                      {isGenerating ? 'Generating...' : 'AI Generate'}
                    </button>
                  </div>
                </div>

                <div style={{ position: 'relative' }}>
                  <textarea 
                    value={templateText}
                    onChange={e => setTemplateText(e.target.value)}
                    placeholder="Start typing..."
                    disabled={isGenerating}
                    style={{ 
                      width: '100%', height: '140px', minHeight: '100px', resize: 'vertical', 
                      padding: '12px', borderRadius: '8px', 
                      background: 'rgba(0,0,0,0.2)', border: 'none',
                      boxShadow: 'inset 0 0 0 1px rgba(255,255,255,0.05)',
                      color: '#f8fafc', fontSize: '13px', lineHeight: '1.5',
                      fontFamily: 'Inter, system-ui, sans-serif',
                      outline: 'none', transition: 'box-shadow 0.2s ease',
                      opacity: isGenerating ? 0.5 : 1
                    }}
                    onFocus={(e) => e.target.style.boxShadow = 'inset 0 0 0 1px rgba(255,255,255,0.3)'}
                    onBlur={(e) => e.target.style.boxShadow = 'inset 0 0 0 1px rgba(255,255,255,0.05)'}
                  />
                  {isGenerating && (
                    <div style={{
                      position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
                      display: 'flex', justifyContent: 'center', alignItems: 'center',
                      background: 'rgba(15, 15, 18, 0.6)', backdropFilter: 'blur(2px)', borderRadius: '8px',
                      color: '#f8fafc', fontWeight: 500, fontSize: '13px', gap: '8px'
                    }}>
                      <div className="ai-spinner" style={{ width: '14px', height: '14px', borderTopColor: '#f8fafc' }}></div> Generating...
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* Footer */}
            <div style={{ padding: '16px 24px', borderTop: '1px solid rgba(255,255,255,0.05)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0 }}>
              <div style={{ transform: 'scale(0.85)', transformOrigin: 'left center', display: 'flex', alignItems: 'center' }}>
                <ToggleSwitch checked={isActive} onChange={setIsActive} label="Active" />
              </div>
              <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                {isDuplicate && (
                  <span style={{ color: '#ef4444', fontSize: '12px', fontWeight: 500, display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <AlertCircle size={12} /> Already exists
                  </span>
                )}
                <button 
                  onClick={() => setShowModal(false)} 
                  style={{ background: 'transparent', border: 'none', color: 'rgba(255,255,255,0.6)', padding: '6px 12px', fontSize: '13px', fontWeight: 500, cursor: 'pointer' }}
                  onMouseEnter={e => e.currentTarget.style.color = '#fff'}
                  onMouseLeave={e => e.currentTarget.style.color = 'rgba(255,255,255,0.6)'}
                >
                  Cancel
                </button>
                <button 
                  onClick={handleSave} 
                  disabled={isDuplicate} 
                  style={{ 
                    background: 'rgba(255,255,255,1)', color: '#000', border: 'none',
                    padding: '6px 16px', borderRadius: '6px', fontSize: '13px', fontWeight: 600, 
                    opacity: isDuplicate ? 0.3 : 1, cursor: isDuplicate ? 'not-allowed' : 'pointer',
                    boxShadow: '0 2px 8px rgba(255,255,255,0.2)'
                  }}
                >
                  {editingId ? 'Update' : 'Save'}
                </button>
              </div>
            </div>
          </div>
          
          <style>{`
            @keyframes modalSlideIn {
              from { opacity: 0; transform: translateY(20px) scale(0.98); }
              to { opacity: 1; transform: translateY(0) scale(1); }
            }
            @keyframes spin {
              to { transform: rotate(360deg); }
            }
            @keyframes fadeIn {
              from { opacity: 0; transform: translateY(-5px); }
              to { opacity: 1; transform: translateY(0); }
            }
            .ai-spinner {
              width: 16px; height: 16px; border: 2px solid rgba(168, 85, 247, 0.3);
              border-top-color: #a855f7; border-radius: 50%;
              animation: spin 1s linear infinite;
            }
            .icon-action-btn {
              background: transparent; border: none; padding: 6px; border-radius: 6px;
              cursor: pointer; display: flex; align-items: center; justify-content: center;
              transition: all 0.2s ease;
            }
            .edit-btn { color: #818cf8; }
            .edit-btn:hover { background: rgba(129, 140, 248, 0.1); }
            .delete-btn { color: #f87171; }
            .delete-btn:hover { background: rgba(248, 113, 113, 0.1); }
          `}</style>
        </div>
      )}
    </div>
  );
}
