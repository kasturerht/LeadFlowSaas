import React, { useState } from 'react';
import { X, Building2, UserCircle, MessageSquare, CreditCard, Mail, Phone, MapPin, Clock, ShieldCheck, Globe } from 'lucide-react';

export default function CreateTenantModal({ onClose, onSubmit }) {
  const [formData, setFormData] = useState({
    orgName: '',
    adminName: '',
    adminEmail: '',
    adminPhone: '',
    adminPassword: '',
    planType: 'basic',
    maxUsers: 5,
    billingCycle: 'monthly',
    supportPhone: '',
    supportEmail: '',
    website: '',
    officeAddress: '',
    officeHours: ''
  });
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handlePlanChange = (e) => {
    const plan = e.target.value;
    let maxU = 5;
    if (plan === 'pro') maxU = 20;
    if (plan === 'enterprise') maxU = 999;
    
    setFormData({ 
      ...formData, 
      planType: plan, 
      maxUsers: maxU 
    });
  };

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsSubmitting(true);
    
    let mrr = 1000;
    if (formData.planType === 'pro') mrr = 3000;
    if (formData.planType === 'enterprise') mrr = 10000;
    
    await onSubmit({ ...formData, mrr });
    setIsSubmitting(false);
  };

  return (
    <div className="modal-overlay" style={{ backdropFilter: 'blur(20px)', background: 'rgba(0,0,0,0.8)' }}>
      <div 
        className="modal-content" 
        style={{ 
          maxWidth: '800px', 
          width: '95%',
          maxHeight: '90vh', 
          overflowY: 'auto',
          background: 'linear-gradient(145deg, #0f172a, #020617)',
          border: '1px solid rgba(99, 102, 241, 0.2)',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.7), 0 0 40px rgba(99, 102, 241, 0.1)',
          borderRadius: '24px'
        }}
      >
        <div className="modal-header" style={{ background: 'transparent', padding: '24px 32px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
          <div>
            <h3 style={{ fontSize: '22px', fontWeight: 700, color: '#fff', display: 'flex', alignItems: 'center', gap: '10px' }}>
              <span style={{ background: 'rgba(99,102,241,0.2)', color: '#818cf8', padding: '8px', borderRadius: '12px' }}><Building2 size={24} /></span>
              Provision Client Workspace
            </h3>
            <p style={{ color: 'var(--text-muted)', fontSize: '13px', marginTop: '6px', marginLeft: '48px' }}>
              Configure organization details, admin access, and messaging profile.
            </p>
          </div>
          <button className="icon-btn" onClick={onClose} style={{ alignSelf: 'flex-start', background: 'rgba(255,255,255,0.05)', borderRadius: '12px', padding: '8px' }}><X size={20} /></button>
        </div>
        
        <form onSubmit={handleSubmit} style={{ padding: '32px' }}>
          
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '32px' }}>
            
            {/* LEFT COLUMN */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              
              {/* Company Details */}
              <div className="glass-panel" style={{ padding: '20px', background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.05)' }}>
                <h4 style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#e2e8f0', fontSize: '14px', marginBottom: '16px', fontWeight: 600 }}>
                  <Building2 size={16} color="#818cf8" /> Organization Details
                </h4>
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Workspace Name</label>
                  <input type="text" name="orgName" className="input-field" required placeholder="e.g. Acme Corp" value={formData.orgName} onChange={handleChange} style={{ background: 'rgba(0,0,0,0.3)', border: '1px solid rgba(255,255,255,0.1)' }} />
                </div>
              </div>

              {/* Admin Account */}
              <div className="glass-panel" style={{ padding: '20px', background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.05)' }}>
                <h4 style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#e2e8f0', fontSize: '14px', marginBottom: '16px', fontWeight: 600 }}>
                  <UserCircle size={16} color="#34d399" /> Super Admin Profile
                </h4>
                
                <div className="form-group" style={{ marginBottom: '16px' }}>
                  <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Full Name</label>
                  <input type="text" name="adminName" className="input-field" required placeholder="Jane Doe" value={formData.adminName} onChange={handleChange} style={{ background: 'rgba(0,0,0,0.3)' }} />
                </div>
                
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '16px' }}>
                  <div className="form-group" style={{ marginBottom: 0 }}>
                    <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Email Address</label>
                    <div style={{ position: 'relative' }}>
                      <Mail size={14} style={{ position: 'absolute', left: '12px', top: '12px', color: '#64748b' }} />
                      <input type="email" name="adminEmail" className="input-field" required placeholder="admin@domain.com" value={formData.adminEmail} onChange={handleChange} style={{ paddingLeft: '36px', background: 'rgba(0,0,0,0.3)' }} />
                    </div>
                  </div>
                  <div className="form-group" style={{ marginBottom: 0 }}>
                    <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Phone</label>
                    <div style={{ position: 'relative' }}>
                      <Phone size={14} style={{ position: 'absolute', left: '12px', top: '12px', color: '#64748b' }} />
                      <input type="tel" name="adminPhone" className="input-field" placeholder="+91..." value={formData.adminPhone} onChange={handleChange} style={{ paddingLeft: '36px', background: 'rgba(0,0,0,0.3)' }} />
                    </div>
                  </div>
                </div>

                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Temporary Password</label>
                  <div style={{ position: 'relative' }}>
                    <ShieldCheck size={14} style={{ position: 'absolute', left: '12px', top: '12px', color: '#64748b' }} />
                    <input type="password" name="adminPassword" className="input-field" required placeholder="Minimum 6 chars" minLength="6" value={formData.adminPassword} onChange={handleChange} style={{ paddingLeft: '36px', background: 'rgba(0,0,0,0.3)' }} />
                  </div>
                </div>
              </div>

              {/* Billing Plan */}
              <div className="glass-panel" style={{ padding: '20px', background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.05)' }}>
                <h4 style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#e2e8f0', fontSize: '14px', marginBottom: '16px', fontWeight: 600 }}>
                  <CreditCard size={16} color="#fbbf24" /> Subscription & Limits
                </h4>
                <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '12px' }}>
                  <div className="form-group" style={{ marginBottom: 0 }}>
                    <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Plan Tier</label>
                    <select name="planType" className="input-field" value={formData.planType} onChange={handlePlanChange} style={{ background: 'rgba(0,0,0,0.3)' }}>
                      <option value="basic">Basic (₹1,000/mo) - 5 Users</option>
                      <option value="pro">Pro (₹3,000/mo) - 20 Users</option>
                      <option value="enterprise">Enterprise (₹10,000/mo) - Unlmt</option>
                    </select>
                  </div>
                  <div className="form-group" style={{ marginBottom: 0 }}>
                    <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Max Seats</label>
                    <input type="number" name="maxUsers" className="input-field" required value={formData.maxUsers} onChange={handleChange} style={{ background: 'rgba(0,0,0,0.3)' }} />
                  </div>
                </div>
              </div>

            </div>

            {/* RIGHT COLUMN */}
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              
              <div className="glass-panel" style={{ padding: '20px', background: 'rgba(99, 102, 241, 0.05)', border: '1px solid rgba(99, 102, 241, 0.15)', height: '100%' }}>
                <h4 style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#e2e8f0', fontSize: '14px', marginBottom: '8px', fontWeight: 600 }}>
                  <MessageSquare size={16} color="#60a5fa" /> Messaging Profile (WhatsApp)
                </h4>
                <p style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '20px', lineHeight: '1.4' }}>
                  These details will be dynamically injected into automated WhatsApp messages sent by this workspace's telecallers.
                </p>

                <div className="form-group" style={{ marginBottom: '16px' }}>
                  <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Customer Support Phone</label>
                  <div style={{ position: 'relative' }}>
                    <Phone size={14} style={{ position: 'absolute', left: '12px', top: '12px', color: '#64748b' }} />
                    <input type="tel" name="supportPhone" className="input-field" placeholder="e.g. +91 1800 123 456" value={formData.supportPhone} onChange={handleChange} style={{ paddingLeft: '36px', background: 'rgba(0,0,0,0.4)', borderColor: 'rgba(255,255,255,0.05)' }} />
                  </div>
                </div>

                <div className="form-group" style={{ marginBottom: '16px' }}>
                  <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Support Email</label>
                  <div style={{ position: 'relative' }}>
                    <Mail size={14} style={{ position: 'absolute', left: '12px', top: '12px', color: '#64748b' }} />
                    <input type="email" name="supportEmail" className="input-field" placeholder="support@company.com" value={formData.supportEmail} onChange={handleChange} style={{ paddingLeft: '36px', background: 'rgba(0,0,0,0.4)', borderColor: 'rgba(255,255,255,0.05)' }} />
                  </div>
                </div>

                <div className="form-group" style={{ marginBottom: '16px' }}>
                  <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Website</label>
                  <div style={{ position: 'relative' }}>
                    <Globe size={14} style={{ position: 'absolute', left: '12px', top: '12px', color: '#64748b' }} />
                    <input type="text" name="website" className="input-field" placeholder="www.company.com" value={formData.website} onChange={handleChange} style={{ paddingLeft: '36px', background: 'rgba(0,0,0,0.4)', borderColor: 'rgba(255,255,255,0.05)' }} />
                  </div>
                </div>

                <div className="form-group" style={{ marginBottom: '16px' }}>
                  <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Office Address (For Walk-ins)</label>
                  <div style={{ position: 'relative' }}>
                    <MapPin size={14} style={{ position: 'absolute', left: '12px', top: '12px', color: '#64748b' }} />
                    <textarea name="officeAddress" className="input-field" placeholder="Building, Street, City, PIN" rows="2" value={formData.officeAddress} onChange={handleChange} style={{ paddingLeft: '36px', background: 'rgba(0,0,0,0.4)', borderColor: 'rgba(255,255,255,0.05)', resize: 'vertical' }} />
                  </div>
                </div>

                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Operating Hours</label>
                  <div style={{ position: 'relative' }}>
                    <Clock size={14} style={{ position: 'absolute', left: '12px', top: '12px', color: '#64748b' }} />
                    <input type="text" name="officeHours" className="input-field" placeholder="Mon-Sat: 10AM - 6PM" value={formData.officeHours} onChange={handleChange} style={{ paddingLeft: '36px', background: 'rgba(0,0,0,0.4)', borderColor: 'rgba(255,255,255,0.05)' }} />
                  </div>
                </div>

              </div>
            </div>

          </div>

          {/* Action Bar */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '32px', paddingTop: '24px', borderTop: '1px solid rgba(255,255,255,0.05)' }}>
            <p style={{ fontSize: '12px', color: '#64748b' }}>A welcome email will be sent automatically to the Super Admin.</p>
            <div style={{ display: 'flex', gap: '16px' }}>
              <button type="button" onClick={onClose} disabled={isSubmitting} style={{ background: 'transparent', border: 'none', color: '#94a3b8', fontSize: '14px', fontWeight: 500, cursor: 'pointer' }}>
                Cancel
              </button>
              <button type="submit" disabled={isSubmitting} style={{ background: 'linear-gradient(to right, #6366f1, #8b5cf6)', color: '#fff', border: 'none', padding: '10px 24px', borderRadius: '12px', fontSize: '14px', fontWeight: 600, cursor: 'pointer', boxShadow: '0 4px 15px rgba(99, 102, 241, 0.4)', transition: 'all 0.2s', display: 'flex', alignItems: 'center', gap: '8px' }}>
                {isSubmitting ? 'Provisioning Environment...' : 'Deploy Workspace'}
              </button>
            </div>
          </div>

        </form>
      </div>
    </div>
  );
}
