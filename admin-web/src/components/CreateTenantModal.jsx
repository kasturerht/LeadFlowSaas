import React, { useState } from 'react';
import { X } from 'lucide-react';

export default function CreateTenantModal({ onClose, onSubmit }) {
  const [formData, setFormData] = useState({
    orgName: '',
    adminName: '',
    adminEmail: '',
    adminPhone: '',
    adminPassword: '',
    planType: 'basic',
    maxUsers: 5,
    billingCycle: 'monthly'
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
    
    // Calculate MRR based on plan
    let mrr = 1000;
    if (formData.planType === 'pro') mrr = 3000;
    if (formData.planType === 'enterprise') mrr = 10000;
    
    await onSubmit({ ...formData, mrr });
    setIsSubmitting(false);
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: '500px', maxHeight: '90vh', overflowY: 'auto' }}>
        <div className="modal-header">
          <h3>Create New Client Organization</h3>
          <button className="icon-btn" onClick={onClose}><X size={20} /></button>
        </div>
        
        <form onSubmit={handleSubmit} style={{ padding: '20px' }}>
          <div style={{ marginBottom: '24px' }}>
            <h4 style={{ marginBottom: '12px', color: 'var(--text-primary)', fontSize: '15px' }}>1. Company Details</h4>
            <div className="form-group">
              <label>Organization Name</label>
              <input 
                type="text" 
                name="orgName" 
                className="input-field"
                required 
                placeholder="e.g. Acme Corp"
                value={formData.orgName}
                onChange={handleChange}
              />
            </div>
          </div>

          <div style={{ marginBottom: '24px' }}>
            <h4 style={{ marginBottom: '12px', color: 'var(--text-primary)', fontSize: '15px' }}>2. Subscription Plan</h4>
            <div className="form-group" style={{ marginBottom: '12px' }}>
              <label>Plan Type</label>
              <select name="planType" className="input-field" value={formData.planType} onChange={handlePlanChange}>
                <option value="basic">Basic (₹1000/mo) - Up to 5 Users</option>
                <option value="pro">Pro (₹3000/mo) - Up to 20 Users</option>
                <option value="enterprise">Enterprise (₹10000/mo) - Unlimited Users</option>
              </select>
            </div>
            <div className="form-group">
              <label>Max Users Allowed</label>
              <input 
                type="number" 
                name="maxUsers" 
                className="input-field"
                required 
                value={formData.maxUsers}
                onChange={handleChange}
              />
            </div>
          </div>

          <div style={{ marginBottom: '24px' }}>
            <h4 style={{ marginBottom: '12px', color: 'var(--text-primary)', fontSize: '15px' }}>3. Primary Admin Account</h4>
            <div className="form-group" style={{ marginBottom: '12px' }}>
              <label>Admin Full Name</label>
              <input 
                type="text" 
                name="adminName"
                className="input-field" 
                required 
                placeholder="e.g. Jane Doe"
                value={formData.adminName}
                onChange={handleChange}
              />
            </div>
            <div className="form-group" style={{ marginBottom: '12px' }}>
              <label>Admin Email</label>
              <input 
                type="email" 
                name="adminEmail" 
                className="input-field"
                required 
                placeholder="admin@acmecorp.com"
                value={formData.adminEmail}
                onChange={handleChange}
              />
            </div>
            <div className="form-group" style={{ marginBottom: '12px' }}>
              <label>Admin Phone (Optional)</label>
              <input 
                type="tel" 
                name="adminPhone" 
                className="input-field"
                placeholder="+91 9876543210"
                value={formData.adminPhone}
                onChange={handleChange}
              />
            </div>
            <div className="form-group" style={{ marginBottom: '12px' }}>
              <label>Temporary Password</label>
              <input 
                type="password" 
                name="adminPassword" 
                className="input-field"
                required 
                placeholder="Minimum 6 characters"
                minLength="6"
                value={formData.adminPassword}
                onChange={handleChange}
              />
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '16px' }}>
            <button type="button" className="btn-secondary" onClick={onClose} disabled={isSubmitting}>
              Cancel
            </button>
            <button type="submit" className="btn-primary" disabled={isSubmitting}>
              {isSubmitting ? 'Creating...' : 'Provision Tenant'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
