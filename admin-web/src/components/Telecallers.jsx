import React, { useEffect, useState } from 'react';
import { db, secondaryAuth } from '../firebase';
import { collection, query, getDocs, doc, setDoc, updateDoc } from 'firebase/firestore';
import { createUserWithEmailAndPassword, signOut } from 'firebase/auth';
import { UserPlus, X } from 'lucide-react';

export default function Telecallers() {
  const [telecallers, setTelecallers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);

  // Form State
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [isCreating, setIsCreating] = useState(false);
  const [error, setError] = useState('');

  const fetchTelecallers = async () => {
    try {
      const q = query(collection(db, 'users'));
      const snapshot = await getDocs(q);
      const t = [];
      snapshot.forEach(doc => t.push({ id: doc.id, ...doc.data() }));
      setTelecallers(t);
    } catch (error) {
      console.error("Error fetching telecallers", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTelecallers();
  }, []);

  const handleCreateTelecaller = async (e) => {
    e.preventDefault();
    setIsCreating(true);
    setError('');

    try {
      // 1. Create User in Auth using Secondary App to prevent Admin logout
      const userCredential = await createUserWithEmailAndPassword(secondaryAuth, email, password);
      const newUserId = userCredential.user.uid;

      // 2. Write User details to Firestore
      await setDoc(doc(db, 'users', newUserId), {
        name,
        email,
        phoneNumber: phone,
        role: 'telecaller',
        isActive: true, // Crucial flag for disabling later
        createdAt: new Date().toISOString()
      });

      // 3. Clean up secondary auth session
      await signOut(secondaryAuth);

      // 4. Update UI
      setShowModal(false);
      setName(''); setEmail(''); setPassword(''); setPhone('');
      fetchTelecallers(); // Refresh list

    } catch (err) {
      console.error(err);
      setError(err.message || "Failed to create user.");
    } finally {
      setIsCreating(false);
    }
  };

  const toggleUserStatus = async (userId, currentStatus) => {
    try {
      const newStatus = currentStatus === false ? true : false;
      await updateDoc(doc(db, 'users', userId), {
        isActive: newStatus
      });
      // Update local state to feel snappy
      setTelecallers(prev => prev.map(t => t.id === userId ? { ...t, isActive: newStatus } : t));
    } catch (err) {
      console.error("Failed to update status", err);
    }
  };

  return (
    <>
      <div className="page-header">
        <div className="page-title-group">
          <h2 className="page-title">Telecallers Team</h2>
          <p className="page-subtitle">Manage your telecallers and view their performance.</p>
        </div>
        <button className="btn-primary" onClick={() => setShowModal(true)} style={{ padding: '6px 12px', fontSize: '13px' }}>
          <UserPlus size={14} /> Add Telecaller
        </button>
      </div>

      <div className="glass-panel" style={{ padding: '16px' }}>
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Phone</th>
                <th>Status</th>
                <th style={{ textAlign: 'right' }}>Action</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="5" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>Loading telecallers...</td>
                </tr>
              ) : telecallers.length === 0 ? (
                <tr>
                  <td colSpan="5" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>
                    No telecallers registered yet. Click "Add Telecaller" above.
                  </td>
                </tr>
              ) : (
                telecallers.map((t) => {
                  const active = t.isActive !== false;
                  return (
                    <tr key={t.id} style={{ opacity: active ? 1 : 0.5 }}>
                      <td style={{ fontWeight: 500 }}>{t.name || 'N/A'}</td>
                      <td>{t.email}</td>
                      <td>{t.phoneNumber || 'N/A'}</td>
                      <td>
                        {active 
                          ? <span className="badge badge-success">Active</span>
                          : <span className="badge badge-danger">Disabled</span>
                        }
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <button 
                          onClick={() => toggleUserStatus(t.id, t.isActive)}
                          className="btn-secondary"
                          style={{
                            padding: '4px 8px',
                            fontSize: '11px',
                            borderColor: active ? 'rgba(239, 68, 68, 0.2)' : 'rgba(16, 185, 129, 0.2)',
                            color: active ? 'var(--danger)' : 'var(--secondary)'
                          }}
                        >
                          {active ? 'Disable' : 'Enable'}
                        </button>
                      </td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Add Telecaller Modal */}
      {showModal && (
        <div style={{
          position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
          background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(3px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100
        }}>
          <div className="glass-panel auth-box" style={{ width: '100%', maxWidth: '340px', padding: '20px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
              <h3 style={{ fontSize: '15px', fontWeight: 600 }}>Register Telecaller</h3>
              <button 
                onClick={() => setShowModal(false)} 
                style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
              >
                <X size={16} />
              </button>
            </div>
            
            {error && <div style={{ color: 'var(--danger)', marginBottom: '10px', fontSize: '11px' }}>{error}</div>}
            
            <form onSubmit={handleCreateTelecaller}>
              <div className="form-group" style={{ marginBottom: '10px' }}>
                <label className="form-label">Full Name</label>
                <input 
                  type="text" 
                  className="input-field" 
                  style={{ padding: '6px 10px', fontSize: '12px' }}
                  required 
                  value={name} 
                  onChange={e => setName(e.target.value)} 
                  placeholder="John Doe" 
                />
              </div>
              <div className="form-group" style={{ marginBottom: '10px' }}>
                <label className="form-label">Email Address</label>
                <input 
                  type="email" 
                  className="input-field" 
                  style={{ padding: '6px 10px', fontSize: '12px' }}
                  required 
                  value={email} 
                  onChange={e => setEmail(e.target.value)} 
                  placeholder="john@nexaleads.com" 
                />
              </div>
              <div className="form-group" style={{ marginBottom: '10px' }}>
                <label className="form-label">Phone Number</label>
                <input 
                  type="tel" 
                  className="input-field" 
                  style={{ padding: '6px 10px', fontSize: '12px' }}
                  required 
                  value={phone} 
                  onChange={e => setPhone(e.target.value)} 
                  placeholder="+91 9876543210" 
                />
              </div>
              <div className="form-group" style={{ marginBottom: '18px' }}>
                <label className="form-label">Temporary Password</label>
                <input 
                  type="password" 
                  className="input-field" 
                  style={{ padding: '6px 10px', fontSize: '12px' }}
                  required 
                  value={password} 
                  onChange={e => setPassword(e.target.value)} 
                  placeholder="••••••••" 
                />
              </div>

              <div style={{ display: 'flex', gap: '8px' }}>
                <button 
                  type="button" 
                  className="btn-secondary" 
                  style={{ flex: 1, fontSize: '12px', padding: '6px 10px' }} 
                  onClick={() => setShowModal(false)}
                >
                  Cancel
                </button>
                <button 
                  type="submit" 
                  className="btn-primary" 
                  style={{ flex: 1, fontSize: '12px', padding: '6px 10px' }} 
                  disabled={isCreating}
                >
                  {isCreating ? 'Creating...' : 'Register'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
