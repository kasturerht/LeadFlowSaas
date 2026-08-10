import React, { useState, useEffect } from 'react';
import { X, ShieldAlert, LogIn, Save, AlertTriangle, Building2, UserCircle, MessageSquare, CreditCard, Mail, Phone, MapPin, Clock, ShieldCheck, Globe, Settings2 } from 'lucide-react';
import { doc, updateDoc, collection, query, where, getDocs } from 'firebase/firestore';
import { EmailAuthProvider, reauthenticateWithCredential } from 'firebase/auth';
import { db, auth } from '../firebase';

export default function TenantManageModal({ org, onClose, onRefresh, onImpersonate }) {
  const [status, setStatus] = useState(org.status || 'active');
  const [maxUsers, setMaxUsers] = useState(org.maxUsers || 5);
  const [isSaving, setIsSaving] = useState(false);

  // Messaging Profile State
  const [supportPhone, setSupportPhone] = useState(org.messagingProfile?.supportPhone || '');
  const [supportEmail, setSupportEmail] = useState(org.messagingProfile?.supportEmail || '');
  const [website, setWebsite] = useState(org.messagingProfile?.website || '');
  const [officeAddress, setOfficeAddress] = useState(org.messagingProfile?.officeAddress || '');
  const [officeHours, setOfficeHours] = useState(org.messagingProfile?.officeHours || '');

  // Danger Zone State
  const [showDangerZone, setShowDangerZone] = useState(false);
  const [confirmId, setConfirmId] = useState('');
  const [adminPassword, setAdminPassword] = useState('');
  const [deleteError, setDeleteError] = useState('');

  // Super Admin Edit State
  const [adminDocId, setAdminDocId] = useState(null);
  const [adminName, setAdminName] = useState('');
  const [adminContact, setAdminContact] = useState('');
  const [adminEmail, setAdminEmail] = useState(''); // Read-only

  useEffect(() => {
    const fetchAdmin = async () => {
      try {
        const q = query(collection(db, 'organizations', org.id, 'users'), where('role', '==', 'admin'));
        const snap = await getDocs(q);
        if (!snap.empty) {
          const docSnap = snap.docs[0];
          setAdminDocId(docSnap.id);
          const data = docSnap.data();
          setAdminName(data.name || '');
          setAdminContact(data.contactNumber || '');
          setAdminEmail(data.email || '');
        }
      } catch (err) {
        console.error("Error fetching admin", err);
      }
    };
    fetchAdmin();
  }, [org.id]);

  const handleSave = async () => {
    setIsSaving(true);
    try {
      const orgRef = doc(db, 'organizations', org.id);
      await updateDoc(orgRef, {
        status,
        maxUsers: parseInt(maxUsers),
        messagingProfile: {
          supportPhone,
          supportEmail,
          website,
          officeAddress,
          officeHours
        }
      });
      
      // Update Admin User Document
      if (adminDocId) {
        const adminRef = doc(db, 'organizations', org.id, 'users', adminDocId);
        await updateDoc(adminRef, {
          name: adminName,
          contactNumber: adminContact
        });
      }
      
      alert("Tenant & Admin profile updated successfully!");
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
    <div className="modal-overlay" style={{ backdropFilter: 'blur(20px)', background: 'rgba(0,0,0,0.8)' }}>
      <div 
        className="modal-content" 
        style={{ 
          maxWidth: '850px', 
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
              <span style={{ background: 'rgba(99,102,241,0.2)', color: '#818cf8', padding: '8px', borderRadius: '12px' }}><Settings2 size={24} /></span>
              {org.name}
            </h3>
            <p style={{ color: 'var(--text-muted)', fontSize: '12px', marginTop: '6px', marginLeft: '48px', fontFamily: 'monospace' }}>
              {org.id}
            </p>
          </div>
          <button className="icon-btn" onClick={onClose} style={{ alignSelf: 'flex-start', background: 'rgba(255,255,255,0.05)', borderRadius: '12px', padding: '8px' }}><X size={20} /></button>
        </div>

        <div style={{ padding: '32px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '32px' }}>
            
            {/* LEFT COLUMN */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              
              {/* Account Settings */}
              <div className="glass-panel" style={{ padding: '20px', background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.05)' }}>
                <h4 style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#e2e8f0', fontSize: '14px', marginBottom: '16px', fontWeight: 600 }}>
                  <ShieldCheck size={16} color="#34d399" /> System Access & Limits
                </h4>
                
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                  <div className="form-group" style={{ marginBottom: 0 }}>
                    <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Account Status</label>
                    <select 
                      className="input-field" 
                      value={status} 
                      onChange={(e) => setStatus(e.target.value)}
                      style={{ 
                        background: status === 'suspended' ? 'rgba(239, 68, 68, 0.1)' : 'rgba(0,0,0,0.3)', 
                        borderColor: status === 'suspended' ? 'rgba(239, 68, 68, 0.4)' : 'rgba(255,255,255,0.1)',
                        color: status === 'suspended' ? '#ef4444' : '#fff'
                      }}
                    >
                      <option value="active">Active</option>
                      <option value="suspended">Suspended (Blocked)</option>
                    </select>
                  </div>

                  <div className="form-group" style={{ marginBottom: 0 }}>
                    <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Max Users Allowed</label>
                    <input 
                      type="number" 
                      className="input-field" 
                      value={maxUsers} 
                      onChange={(e) => setMaxUsers(e.target.value)}
                      style={{ background: 'rgba(0,0,0,0.3)', border: '1px solid rgba(255,255,255,0.1)' }}
                    />
                  </div>
                </div>

                {status === 'suspended' && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#ef4444', fontSize: '12px', marginTop: '12px', padding: '8px', background: 'rgba(239,68,68,0.1)', borderRadius: '6px' }}>
                    <ShieldAlert size={14} /> <span>Users from this workspace will be blocked from logging in.</span>
                  </div>
                )}
              </div>

              {/* Super Admin Profile Edit */}
              {adminDocId && (
                <div className="glass-panel" style={{ padding: '20px', background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.05)' }}>
                  <h4 style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#e2e8f0', fontSize: '14px', marginBottom: '16px', fontWeight: 600 }}>
                    <UserCircle size={16} color="#c084fc" /> Super Admin Profile
                  </h4>
                  <p style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '16px', lineHeight: '1.4' }}>
                    Email is fixed to maintain auth integrity. You can update the name and contact.
                  </p>
                  
                  <div className="form-group" style={{ marginBottom: '16px' }}>
                    <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Email Address (Read Only)</label>
                    <input type="email" className="input-field" value={adminEmail} disabled style={{ background: 'rgba(0,0,0,0.5)', opacity: 0.6 }} />
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    <div className="form-group" style={{ marginBottom: 0 }}>
                      <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Full Name</label>
                      <input type="text" className="input-field" value={adminName} onChange={(e) => setAdminName(e.target.value)} style={{ background: 'rgba(0,0,0,0.3)', border: '1px solid rgba(255,255,255,0.1)' }} />
                    </div>
                    <div className="form-group" style={{ marginBottom: 0 }}>
                      <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Phone Number</label>
                      <input type="tel" className="input-field" value={adminContact} onChange={(e) => setAdminContact(e.target.value)} style={{ background: 'rgba(0,0,0,0.3)', border: '1px solid rgba(255,255,255,0.1)' }} />
                    </div>
                  </div>
                </div>
              )}

              {/* Impersonation */}
              <div className="glass-panel" style={{ padding: '20px', background: 'rgba(234, 179, 8, 0.05)', border: '1px solid rgba(234, 179, 8, 0.2)' }}>
                <h4 style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#fcd34d', fontSize: '14px', marginBottom: '12px', fontWeight: 600 }}>
                  <LogIn size={16} /> God Mode (Impersonation)
                </h4>
                <p style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '16px', lineHeight: '1.4' }}>
                  Login as a super admin for this specific tenant to view or modify their data directly without requiring their password.
                </p>
                <button 
                  type="button"
                  onClick={() => onImpersonate(org.id)}
                  style={{ width: '100%', background: 'rgba(234, 179, 8, 0.1)', color: '#fcd34d', border: '1px solid rgba(234, 179, 8, 0.3)', padding: '10px', borderRadius: '8px', fontSize: '13px', fontWeight: 600, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', transition: 'all 0.2s' }}
                  onMouseOver={(e) => e.currentTarget.style.background = 'rgba(234, 179, 8, 0.2)'}
                  onMouseOut={(e) => e.currentTarget.style.background = 'rgba(234, 179, 8, 0.1)'}
                >
                  <LogIn size={16} /> Login to {org.name} Workspace
                </button>
              </div>

              {/* Danger Zone */}
              {!showDangerZone ? (
                <div style={{ marginTop: 'auto' }}>
                  <button 
                    className="link-destructive"
                    onClick={() => setShowDangerZone(true)}
                    disabled={isSaving}
                    style={{ fontSize: '13px', display: 'flex', alignItems: 'center', gap: '6px', color: '#ef4444', background: 'transparent', border: 'none', cursor: 'pointer' }}
                  >
                    <AlertTriangle size={14} /> Delete this Tenant Permanently
                  </button>
                </div>
              ) : (
                <div style={{ padding: '20px', background: 'rgba(239, 68, 68, 0.05)', border: '1px solid rgba(239, 68, 68, 0.2)', borderRadius: '12px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#ef4444', marginBottom: '12px', fontWeight: 'bold' }}>
                    <AlertTriangle size={18} /> Danger Zone
                  </div>
                  <p style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '16px', lineHeight: '1.4' }}>
                    This action is irreversible. All data (leads, users, settings) will be destroyed. Type <strong>{org.id}</strong> to confirm.
                  </p>
                  
                  <input 
                    type="text" 
                    className="input-field" 
                    value={confirmId} 
                    onChange={(e) => setConfirmId(e.target.value)}
                    placeholder={org.id}
                    style={{ marginBottom: '12px', background: 'rgba(0,0,0,0.5)', borderColor: confirmId && confirmId !== org.id ? '#ef4444' : 'rgba(255,255,255,0.1)' }}
                  />

                  <input 
                    type="password" 
                    className="input-field" 
                    value={adminPassword} 
                    onChange={(e) => setAdminPassword(e.target.value)}
                    placeholder="Enter your Super Admin Password"
                    style={{ marginBottom: '12px', background: 'rgba(0,0,0,0.5)' }}
                  />

                  {deleteError && (
                    <div style={{ color: '#ef4444', fontSize: '12px', marginBottom: '12px', fontWeight: 'bold' }}>
                      {deleteError}
                    </div>
                  )}

                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button 
                      type="button"
                      onClick={() => setShowDangerZone(false)}
                      style={{ flex: 1, background: 'transparent', color: '#94a3b8', border: '1px solid rgba(255,255,255,0.1)', padding: '8px', borderRadius: '6px', cursor: 'pointer' }}
                    >
                      Cancel
                    </button>
                    <button 
                      type="button"
                      onClick={handleDeleteConfirm}
                      disabled={confirmId !== org.id || !adminPassword || isSaving}
                      style={{ flex: 2, background: '#ef4444', color: '#fff', border: 'none', padding: '8px', borderRadius: '6px', cursor: (confirmId !== org.id || !adminPassword) ? 'not-allowed' : 'pointer', opacity: (confirmId !== org.id || !adminPassword) ? 0.5 : 1, fontWeight: 600 }}
                    >
                      {isSaving ? 'Deleting...' : 'Destroy Tenant'}
                    </button>
                  </div>
                </div>
              )}

            </div>

            {/* RIGHT COLUMN: Messaging Profile */}
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <div className="glass-panel" style={{ padding: '24px', background: 'rgba(99, 102, 241, 0.05)', border: '1px solid rgba(99, 102, 241, 0.15)', height: '100%' }}>
                <h4 style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#e2e8f0', fontSize: '14px', marginBottom: '8px', fontWeight: 600 }}>
                  <MessageSquare size={16} color="#60a5fa" /> Messaging Profile (WhatsApp)
                </h4>
                <p style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '24px', lineHeight: '1.4' }}>
                  Dynamic placeholders used for automated WhatsApp templates sent by this organization.
                </p>

                <div className="form-group" style={{ marginBottom: '16px' }}>
                  <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Customer Support Phone</label>
                  <div style={{ position: 'relative' }}>
                    <Phone size={14} style={{ position: 'absolute', left: '12px', top: '12px', color: '#64748b' }} />
                    <input type="tel" className="input-field" placeholder="e.g. +91 1800 123 456" value={supportPhone} onChange={(e) => setSupportPhone(e.target.value)} style={{ paddingLeft: '36px', background: 'rgba(0,0,0,0.4)', borderColor: 'rgba(255,255,255,0.05)' }} />
                  </div>
                </div>

                <div className="form-group" style={{ marginBottom: '16px' }}>
                  <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Support Email</label>
                  <div style={{ position: 'relative' }}>
                    <Mail size={14} style={{ position: 'absolute', left: '12px', top: '12px', color: '#64748b' }} />
                    <input type="email" className="input-field" placeholder="support@company.com" value={supportEmail} onChange={(e) => setSupportEmail(e.target.value)} style={{ paddingLeft: '36px', background: 'rgba(0,0,0,0.4)', borderColor: 'rgba(255,255,255,0.05)' }} />
                  </div>
                </div>

                <div className="form-group" style={{ marginBottom: '16px' }}>
                  <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Website</label>
                  <div style={{ position: 'relative' }}>
                    <Globe size={14} style={{ position: 'absolute', left: '12px', top: '12px', color: '#64748b' }} />
                    <input type="text" className="input-field" placeholder="www.company.com" value={website} onChange={(e) => setWebsite(e.target.value)} style={{ paddingLeft: '36px', background: 'rgba(0,0,0,0.4)', borderColor: 'rgba(255,255,255,0.05)' }} />
                  </div>
                </div>

                <div className="form-group" style={{ marginBottom: '16px' }}>
                  <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Office Address</label>
                  <div style={{ position: 'relative' }}>
                    <MapPin size={14} style={{ position: 'absolute', left: '12px', top: '12px', color: '#64748b' }} />
                    <textarea className="input-field" placeholder="Building, Street, City, PIN" rows="3" value={officeAddress} onChange={(e) => setOfficeAddress(e.target.value)} style={{ paddingLeft: '36px', background: 'rgba(0,0,0,0.4)', borderColor: 'rgba(255,255,255,0.05)', resize: 'vertical' }} />
                  </div>
                </div>

                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px', display: 'block' }}>Operating Hours</label>
                  <div style={{ position: 'relative' }}>
                    <Clock size={14} style={{ position: 'absolute', left: '12px', top: '12px', color: '#64748b' }} />
                    <input type="text" className="input-field" placeholder="Mon-Sat: 10AM - 6PM" value={officeHours} onChange={(e) => setOfficeHours(e.target.value)} style={{ paddingLeft: '36px', background: 'rgba(0,0,0,0.4)', borderColor: 'rgba(255,255,255,0.05)' }} />
                  </div>
                </div>

              </div>
            </div>

          </div>

          {/* Action Bar */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '16px', marginTop: '32px', paddingTop: '24px', borderTop: '1px solid rgba(255,255,255,0.05)' }}>
            <button type="button" onClick={onClose} disabled={isSaving || showDangerZone} style={{ background: 'transparent', border: 'none', color: '#94a3b8', fontSize: '14px', fontWeight: 500, cursor: 'pointer' }}>
              Cancel
            </button>
            <button type="button" onClick={handleSave} disabled={isSaving || showDangerZone} style={{ background: 'linear-gradient(to right, #6366f1, #8b5cf6)', color: '#fff', border: 'none', padding: '10px 24px', borderRadius: '12px', fontSize: '14px', fontWeight: 600, cursor: 'pointer', boxShadow: '0 4px 15px rgba(99, 102, 241, 0.4)', transition: 'all 0.2s', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Save size={16} /> {isSaving ? 'Saving Changes...' : 'Save Configuration'}
            </button>
          </div>

        </div>
      </div>
    </div>
  );
}
