import React, { useState } from 'react';
import { X, ShieldAlert, LogIn, Save, AlertTriangle } from 'lucide-react';
import { doc, updateDoc } from 'firebase/firestore';
import { EmailAuthProvider, reauthenticateWithCredential } from 'firebase/auth';
import { db, auth } from '../firebase';

export default function TenantManageModal({ org, onClose, onRefresh, onImpersonate }) {
  const [status, setStatus] = useState(org.status || 'active');
  const [maxUsers, setMaxUsers] = useState(org.maxUsers || 5);
  const [isSaving, setIsSaving] = useState(false);

  // Danger Zone State
  const [showDangerZone, setShowDangerZone] = useState(false);
  const [confirmId, setConfirmId] = useState('');
  const [adminPassword, setAdminPassword] = useState('');
  const [deleteError, setDeleteError] = useState('');

  const handleSave = async () => {
    setIsSaving(true);
    try {
      const orgRef = doc(db, 'organizations', org.id);
      await updateDoc(orgRef, {
        status,
        maxUsers: parseInt(maxUsers)
      });
      alert("Tenant updated successfully!");
      onRefresh();
      onClose();
    } catch (e) {
      console.error(e);
      alert("Error updating tenant: " + e.message);
    }
    setIsSaving(false);
  };

  const handleDeleteConfirm = async () => {
    if (confirmId !== org.id) return;
    
    setIsSaving(true);
    setDeleteError('');
    try {
      if (auth.currentUser && adminPassword) {
        const credential = EmailAuthProvider.credential(auth.currentUser.email, adminPassword);
        await reauthenticateWithCredential(auth.currentUser, credential);
      } else if (!auth.currentUser) {
        throw new Error("No authenticated user found.");
      } else {
        throw new Error("Super Admin password is required.");
      }

      await import('firebase/firestore').then(({ deleteDoc }) => 
        deleteDoc(doc(db, 'organizations', org.id))
      );
      alert("Tenant deleted successfully!");
      onRefresh();
      onClose();
    } catch (e) {
      console.error(e);
      let errorMsg = e.message;
      if (e.code === 'auth/wrong-password' || e.code === 'auth/invalid-credential') {
        errorMsg = "Incorrect super admin password.";
      }
      setDeleteError(errorMsg);
    }
    setIsSaving(false);
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: '400px' }}>
        <div className="modal-header">
          <div>
            <h3>Manage Tenant</h3>
            <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{org.id}</p>
          </div>
          <button className="icon-btn" onClick={onClose}><X size={20} /></button>
        </div>

        <div style={{ padding: '20px' }}>
          <h2 style={{ marginBottom: '16px', color: 'var(--text-primary)' }}>{org.name}</h2>
          
          <div className="form-group" style={{ marginBottom: '16px' }}>
            <label>Account Status</label>
            <select 
              className="input-field" 
              value={status} 
              onChange={(e) => setStatus(e.target.value)}
              style={{ borderColor: status === 'suspended' ? '#ef4444' : '' }}
            >
              <option value="active">Active</option>
              <option value="suspended">Suspended (Blocked)</option>
            </select>
            {status === 'suspended' && (
              <p style={{ color: '#ef4444', fontSize: '12px', marginTop: '4px' }}>
                <ShieldAlert size={12} style={{ display: 'inline', marginRight: '4px' }} />
                Users will not be able to log in.
              </p>
            )}
          </div>

          <div className="form-group" style={{ marginBottom: '24px' }}>
            <label>Max Users Allowed</label>
            <input 
              type="number" 
              className="input-field" 
              value={maxUsers} 
              onChange={(e) => setMaxUsers(e.target.value)}
            />
          </div>

          <hr style={{ borderColor: 'var(--border-color)', margin: '16px 0' }} />

          <div style={{ marginBottom: '24px' }}>
            <label style={{ display: 'block', marginBottom: '8px', fontSize: '13px', fontWeight: 600 }}>Impersonation (God Mode)</label>
            <button 
              className="btn-secondary" 
              style={{ width: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px', borderColor: '#eab308', color: '#ca8a04' }}
              onClick={() => onImpersonate(org.id)}
            >
              <LogIn size={16} /> Login as {org.name}
            </button>
            <p style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '6px', textAlign: 'center' }}>
              Allows you to view and edit this tenant's data directly.
            </p>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '30px' }}>
            <button 
              className="btn-secondary" 
              onClick={() => setShowDangerZone(true)} 
              disabled={isSaving || showDangerZone} 
              style={{ borderColor: 'var(--danger)', color: 'var(--danger)', fontSize: '13px' }}
            >
              Delete Tenant
            </button>
            
            <div style={{ display: 'flex', gap: '12px' }}>
              <button className="btn-secondary" onClick={onClose} disabled={isSaving}>Cancel</button>
              <button className="btn-primary" onClick={handleSave} disabled={isSaving} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Save size={16} /> {isSaving ? 'Saving...' : 'Save Changes'}
              </button>
            </div>
          </div>

          {showDangerZone && (
            <div style={{ marginTop: '24px', padding: '16px', border: '1px solid var(--danger)', borderRadius: '8px', background: 'rgba(239, 68, 68, 0.05)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--danger)', marginBottom: '12px', fontWeight: 'bold' }}>
                <AlertTriangle size={20} /> Danger Zone
              </div>
              <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '16px' }}>
                This action is irreversible. All data for this tenant will be permanently destroyed.
              </p>
              
              <div style={{ marginBottom: '12px' }}>
                <label style={{ display: 'block', fontSize: '12px', marginBottom: '4px' }}>
                  To confirm, type <strong>{org.id}</strong> below:
                </label>
                <input 
                  type="text" 
                  className="input-field" 
                  value={confirmId} 
                  onChange={(e) => setConfirmId(e.target.value)}
                  placeholder={org.id}
                  style={{ borderColor: confirmId && confirmId !== org.id ? 'var(--danger)' : '' }}
                />
              </div>

              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', fontSize: '12px', marginBottom: '4px' }}>
                  Super Admin Password:
                </label>
                <input 
                  type="password" 
                  className="input-field" 
                  value={adminPassword} 
                  onChange={(e) => setAdminPassword(e.target.value)}
                  placeholder="Enter your password to verify"
                />
              </div>

              {deleteError && (
                <div style={{ color: 'var(--danger)', fontSize: '12px', marginBottom: '12px', fontWeight: 'bold' }}>
                  Error: {deleteError}
                </div>
              )}

              <button 
                className="btn-primary" 
                onClick={handleDeleteConfirm} 
                disabled={confirmId !== org.id || !adminPassword || isSaving} 
                style={{ 
                  width: '100%', 
                  background: 'var(--danger)', 
                  opacity: (confirmId !== org.id || !adminPassword) ? 0.5 : 1,
                  cursor: (confirmId !== org.id || !adminPassword) ? 'not-allowed' : 'pointer'
                }}
              >
                {isSaving ? 'Deleting...' : 'I understand the consequences, delete this tenant'}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
