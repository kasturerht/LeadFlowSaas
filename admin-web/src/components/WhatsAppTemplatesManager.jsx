import React, { useState, useEffect, useRef } from 'react';
import { collection, query, getDocs, doc, setDoc, deleteDoc, getDoc } from 'firebase/firestore';
import { httpsCallable } from 'firebase/functions';
import { db, functions } from '../firebase';
import { useAuth } from '../AuthContext';
import { Plus, Edit2, Trash2, MessageSquare, AlertCircle, ChevronDown, Check, Sparkles } from 'lucide-react';

// Custom Select Component for Premium Look
const CustomSelect = ({ value, onChange, options, label }) => {
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
      <label className="input-label" style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '8px', display: 'block' }}>{label}</label>
      <div 
        onClick={() => setIsOpen(!isOpen)}
        style={{
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          padding: '12px 16px', background: 'rgba(255, 255, 255, 0.03)',
          border: `1px solid ${isOpen ? 'var(--primary)' : 'var(--border)'}`,
          borderRadius: '10px', cursor: 'pointer',
          boxShadow: isOpen ? '0 0 0 3px rgba(99, 102, 241, 0.15)' : 'none',
          transition: 'all 0.2s ease', color: 'var(--text)'
        }}
      >
        <span style={{ fontSize: '14px', fontWeight: 500 }}>{value}</span>
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
          {options.map(opt => (
            <div 
              key={opt}
              onClick={() => { onChange(opt); setIsOpen(false); }}
              style={{
                padding: '10px 12px', borderRadius: '6px', cursor: 'pointer',
                display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                background: value === opt ? 'rgba(99, 102, 241, 0.15)' : 'transparent',
                color: value === opt ? 'var(--primary)' : 'var(--text)',
                fontSize: '14px', transition: 'background 0.1s ease'
              }}
              onMouseEnter={(e) => { if(value !== opt) e.currentTarget.style.background = 'rgba(255, 255, 255, 0.05)' }}
              onMouseLeave={(e) => { if(value !== opt) e.currentTarget.style.background = 'transparent' }}
            >
              <span>{opt}</span>
              {value === opt && <Check size={16} color="var(--primary)" />}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

// Premium Toggle Switch
const ToggleSwitch = ({ checked, onChange, label }) => (
  <label style={{ display: 'flex', alignItems: 'center', gap: '12px', cursor: 'pointer', userSelect: 'none' }}>
    <div style={{ position: 'relative', width: '44px', height: '24px' }}>
      <input 
        type="checkbox" 
        checked={checked} 
        onChange={e => onChange(e.target.checked)} 
        style={{ opacity: 0, width: 0, height: 0, position: 'absolute' }} 
      />
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
        background: checked ? 'var(--primary)' : 'rgba(255, 255, 255, 0.1)',
        borderRadius: '34px', transition: 'background-color 0.3s ease'
      }}>
        <div style={{
          position: 'absolute', top: '2px', left: checked ? '22px' : '2px',
          width: '20px', height: '20px', background: '#fff',
          borderRadius: '50%', transition: 'left 0.3s cubic-bezier(0.68, -0.55, 0.265, 1.55)',
          boxShadow: '0 2px 4px rgba(0,0,0,0.2)'
        }} />
      </div>
    </div>
    <span style={{ fontSize: '14px', color: 'var(--text)', fontWeight: 500 }}>{label}</span>
  </label>
);

export default function WhatsAppTemplatesManager() {
  const { orgId } = useAuth();
  const [templates, setTemplates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isGenerating, setIsGenerating] = useState(false);
  
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState(null);
  
  const [statusTrigger, setStatusTrigger] = useState('Product Enquiry');
  const [language, setLanguage] = useState('Marathi');
  const [templateText, setTemplateText] = useState('');
  const [isActive, setIsActive] = useState(true);

  const STATUS_OPTIONS = ['Product Enquiry', 'Order Placed', 'Call Not Answered', 'Follow-up', 'Pending', 'Wrong Number'];
  const LANGUAGES = ['Marathi', 'English', 'Hindi', 'Gujarati'];

  useEffect(() => {
    if (orgId) {
      fetchTemplates();
    }
  }, [orgId]);

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
      alert("AI is currently busy or an error occurred. Please try again later.");
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
      alert("Failed to save template.");
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
    setStatusTrigger('Product Enquiry');
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

  const smartTags = [
    { label: 'Customer Name', value: '{{customer_name}}' },
    { label: 'Product List', value: '{{product_list}}' },
    { label: 'Organization Name', value: '{{org_name}}' },
    { label: 'Support Number', value: '{{support_number}}' },
  ];

  return (
    <div className="glass-panel" style={{ height: '100%', overflowY: 'auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
        <div>
          <h2 style={{ margin: 0, fontSize: '28px', display: 'flex', alignItems: 'center', gap: '12px', fontWeight: 700, color: '#fff' }}>
            <div style={{ background: 'linear-gradient(135deg, #6366f1, #a855f7)', padding: '10px', borderRadius: '12px', display: 'flex', boxShadow: '0 4px 15px rgba(99, 102, 241, 0.4)' }}>
              <MessageSquare size={24} color="#fff" />
            </div>
            WhatsApp Templates
          </h2>
          <p style={{ color: 'var(--text-muted)', margin: '8px 0 0 0', fontSize: '15px' }}>
            Design premium automated conversations based on lead status.
          </p>
        </div>
        <button className="btn-primary" onClick={() => { resetForm(); setShowModal(true); }} style={{ padding: '12px 24px', fontSize: '15px', borderRadius: '12px', boxShadow: '0 4px 15px rgba(99, 102, 241, 0.3)' }}>
          <Plus size={18} style={{ marginRight: '6px' }} /> New Template
        </button>
      </div>

      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '40px', color: 'var(--text-muted)' }}>Loading...</div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))', gap: '20px' }}>
          {templates.map(t => (
            <div key={t.id} style={{
              background: 'linear-gradient(145deg, rgba(255,255,255,0.03) 0%, rgba(255,255,255,0.01) 100%)',
              border: '1px solid rgba(255,255,255,0.05)',
              borderRadius: '16px',
              padding: '20px',
              display: 'flex',
              flexDirection: 'column',
              boxShadow: '0 8px 30px rgba(0,0,0,0.12)',
              backdropFilter: 'blur(10px)',
              transition: 'transform 0.2s ease, box-shadow 0.2s ease'
            }}
            onMouseEnter={(e) => { e.currentTarget.style.transform = 'translateY(-2px)'; e.currentTarget.style.boxShadow = '0 12px 40px rgba(0,0,0,0.2)'; }}
            onMouseLeave={(e) => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = '0 8px 30px rgba(0,0,0,0.12)'; }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  <span style={{ 
                    background: t.isActive ? 'rgba(34, 197, 94, 0.15)' : 'rgba(239, 68, 68, 0.15)', 
                    color: t.isActive ? '#4ade80' : '#f87171',
                    padding: '6px 12px', borderRadius: '20px', fontSize: '13px', fontWeight: 600,
                    display: 'inline-block', border: `1px solid ${t.isActive ? 'rgba(34, 197, 94, 0.3)' : 'rgba(239, 68, 68, 0.3)'}`
                  }}>
                    {t.statusTrigger}
                  </span>
                  <span style={{ fontSize: '13px', color: 'var(--text-muted)', fontWeight: 500, marginLeft: '4px' }}>{t.language}</span>
                </div>
                <div style={{ display: 'flex', gap: '4px', background: 'rgba(0,0,0,0.2)', borderRadius: '8px', padding: '4px' }}>
                  <button onClick={() => openEdit(t)} style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: '6px', borderRadius: '6px', transition: 'background 0.2s' }} onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.1)'} onMouseLeave={e => e.currentTarget.style.background = 'transparent'}>
                    <Edit2 size={16} />
                  </button>
                  <button onClick={() => handleDelete(t.id)} style={{ background: 'transparent', border: 'none', color: '#f87171', cursor: 'pointer', padding: '6px', borderRadius: '6px', transition: 'background 0.2s' }} onMouseEnter={e => e.currentTarget.style.background = 'rgba(239,68,68,0.1)'} onMouseLeave={e => e.currentTarget.style.background = 'transparent'}>
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
              <div style={{ 
                flex: 1, 
                background: 'rgba(0,0,0,0.3)', 
                padding: '16px', 
                borderRadius: '12px', 
                fontSize: '14px', 
                lineHeight: '1.6',
                whiteSpace: 'pre-wrap',
                color: '#e2e8f0',
                border: '1px solid rgba(255,255,255,0.02)'
              }}>
                {t.templateText}
              </div>
            </div>
          ))}
          {templates.length === 0 && (
            <div style={{ gridColumn: '1 / -1', textAlign: 'center', padding: '60px', background: 'rgba(255,255,255,0.02)', borderRadius: '16px', border: '1px dashed rgba(255,255,255,0.1)' }}>
              <MessageSquare size={48} color="var(--text-muted)" style={{ marginBottom: '16px', opacity: 0.5 }} />
              <h3 style={{ margin: '0 0 8px 0', color: 'var(--text)' }}>No templates yet</h3>
              <p style={{ margin: 0, color: 'var(--text-muted)' }}>Create your first WhatsApp template to automate your customer communication.</p>
            </div>
          )}
        </div>
      )}

      {/* Premium Glassmorphism Modal */}
      {showModal && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(8px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 1000, padding: '20px'
        }}>
          <div style={{
            background: '#18181b', border: '1px solid rgba(255,255,255,0.1)',
            borderRadius: '20px', width: '100%', maxWidth: '650px',
            boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)',
            display: 'flex', flexDirection: 'column',
            overflow: 'hidden', animation: 'modalSlideIn 0.3s cubic-bezier(0.16, 1, 0.3, 1)'
          }}>
            {/* Header */}
            <div style={{ padding: '24px 32px', borderBottom: '1px solid rgba(255,255,255,0.05)', display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div style={{ background: 'rgba(99, 102, 241, 0.1)', padding: '8px', borderRadius: '10px' }}>
                <Edit2 size={20} color="var(--primary)" />
              </div>
              <h3 style={{ margin: 0, fontSize: '20px', fontWeight: 600, color: '#fff' }}>
                {editingId ? 'Edit Template' : 'Create New Template'}
              </h3>
            </div>

            {/* Body */}
            <div style={{ padding: '32px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
              <div style={{ display: 'flex', gap: '20px' }}>
                <CustomSelect label="Trigger Status" value={statusTrigger} onChange={setStatusTrigger} options={STATUS_OPTIONS} />
                <CustomSelect label="Language" value={language} onChange={setLanguage} options={LANGUAGES} />
              </div>

              <div>
                <label style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '12px', display: 'block' }}>Message Design</label>
                
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
                  {/* Smart Tags Pills */}
                  <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', flex: 1 }}>
                    {smartTags.map(tag => (
                      <div 
                        key={tag.value}
                        onClick={() => insertTag(tag.value)}
                        style={{
                          background: 'rgba(99, 102, 241, 0.1)', border: '1px solid rgba(99, 102, 241, 0.2)',
                          color: '#818cf8', padding: '6px 12px', borderRadius: '20px',
                          fontSize: '12px', fontWeight: 500, cursor: 'pointer',
                          transition: 'all 0.2s ease', display: 'flex', alignItems: 'center', gap: '4px'
                        }}
                        onMouseEnter={(e) => { e.currentTarget.style.background = 'rgba(99, 102, 241, 0.2)'; e.currentTarget.style.transform = 'translateY(-1px)'; }}
                        onMouseLeave={(e) => { e.currentTarget.style.background = 'rgba(99, 102, 241, 0.1)'; e.currentTarget.style.transform = 'translateY(0)'; }}
                      >
                        <Plus size={12} />
                        {tag.label}
                      </div>
                    ))}
                  </div>

                  {/* AI Generate Button */}
                  <button 
                    onClick={generateWithAI}
                    disabled={isGenerating}
                    style={{
                      background: 'linear-gradient(135deg, #a855f7, #ec4899)',
                      border: 'none', color: '#fff', padding: '8px 16px', borderRadius: '20px',
                      fontSize: '13px', fontWeight: 600, cursor: isGenerating ? 'not-allowed' : 'pointer',
                      display: 'flex', alignItems: 'center', gap: '6px', opacity: isGenerating ? 0.7 : 1,
                      boxShadow: '0 4px 15px rgba(236, 72, 153, 0.3)', transition: 'all 0.2s ease'
                    }}
                    onMouseEnter={(e) => { if(!isGenerating) e.currentTarget.style.transform = 'scale(1.02)' }}
                    onMouseLeave={(e) => { if(!isGenerating) e.currentTarget.style.transform = 'scale(1)' }}
                  >
                    <Sparkles size={14} />
                    {isGenerating ? 'Generating...' : 'Generate using AI'}
                  </button>
                </div>

                <div style={{ position: 'relative' }}>
                  <textarea 
                    value={templateText}
                    onChange={e => setTemplateText(e.target.value)}
                    placeholder="Type your brilliant message here or use AI to generate one..."
                    disabled={isGenerating}
                    style={{ 
                      width: '100%', height: '180px', resize: 'vertical', 
                      padding: '16px', borderRadius: '12px', 
                      background: 'rgba(0,0,0,0.2)', border: '1px solid rgba(255,255,255,0.1)',
                      color: '#e2e8f0', fontSize: '15px', lineHeight: '1.5',
                      fontFamily: 'Inter, system-ui, sans-serif',
                      outline: 'none', transition: 'border-color 0.2s ease',
                      opacity: isGenerating ? 0.5 : 1
                    }}
                    onFocus={(e) => e.target.style.borderColor = 'var(--primary)'}
                    onBlur={(e) => e.target.style.borderColor = 'rgba(255,255,255,0.1)'}
                  />
                  {isGenerating && (
                    <div style={{
                      position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
                      display: 'flex', justifyContent: 'center', alignItems: 'center',
                      background: 'rgba(30, 30, 36, 0.5)', borderRadius: '12px',
                      color: '#a855f7', fontWeight: 600, fontSize: '14px', gap: '8px'
                    }}>
                      <div className="ai-spinner"></div> Generating Magic...
                    </div>
                  )}
                </div>
              </div>

              <div style={{ background: 'rgba(255,255,255,0.02)', padding: '16px 20px', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.05)' }}>
                <ToggleSwitch checked={isActive} onChange={setIsActive} label="Enable this template instantly" />
              </div>
            </div>

            {/* Footer */}
            <div style={{ padding: '20px 32px', background: 'rgba(0,0,0,0.2)', borderTop: '1px solid rgba(255,255,255,0.05)', display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
              <button onClick={() => setShowModal(false)} style={{ 
                padding: '10px 20px', background: 'transparent', border: '1px solid rgba(255,255,255,0.1)', 
                color: '#fff', borderRadius: '8px', cursor: 'pointer', fontWeight: 500, transition: 'background 0.2s' 
              }} onMouseEnter={e=>e.target.style.background='rgba(255,255,255,0.05)'} onMouseLeave={e=>e.target.style.background='transparent'}>
                Cancel
              </button>
              <button onClick={handleSave} className="btn-primary" style={{ padding: '10px 24px', borderRadius: '8px', fontWeight: 600, boxShadow: '0 4px 15px rgba(99, 102, 241, 0.4)' }}>
                {editingId ? 'Update Template' : 'Save Template'}
              </button>
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
            .ai-spinner {
              width: 16px; height: 16px; border: 2px solid rgba(168, 85, 247, 0.3);
              border-top-color: #a855f7; border-radius: 50%;
              animation: spin 1s linear infinite;
            }
          `}</style>
        </div>
      )}
    </div>
  );
}
