import React, { useState } from 'react';
import { X } from 'lucide-react';

export default function CreateTenantModal({ onClose, onSubmit }) {
  const [formData, setFormData] = useState({
    orgName: '',
    adminName: '',
    adminEmail: '',
    adminPhone: '',
    adminPassword: ''
  });
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsSubmitting(true);
    await onSubmit(formData);
    setIsSubmitting(false);
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: '500px' }}>
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
            <h4 style={{ marginBottom: '12px', color: 'var(--text-primary)', fontSize: '15px' }}>2. Primary Admin Account</h4>
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
