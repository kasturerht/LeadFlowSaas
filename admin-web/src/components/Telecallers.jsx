import React, { useEffect, useState } from 'react';
import { db, secondaryAuth } from '../firebase';
import { collection, query, getDocs, doc, setDoc, updateDoc } from 'firebase/firestore';
import { createUserWithEmailAndPassword, signOut } from 'firebase/auth';

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
      const newStatus = currentStatus === false ? true : false; // Handle undefined as true
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
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
        <div>
          <h2 style={{ fontSize: '28px', marginBottom: '8px', fontWeight: 600 }}>Telecallers Team</h2>
          <p style={{ color: 'var(--text-muted)' }}>Manage your telecallers and view their performance.</p>
        </div>
        <button className="btn-primary" onClick={() => setShowModal(true)}>
          + Add New Telecaller
        </button>
      </div>

      <div className="glass-panel" style={{ padding: '24px' }}>
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Phone</th>
                <th>Status</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="5" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '32px' }}>Loading...</td>
                </tr>
              ) : telecallers.length === 0 ? (
                <tr>
                  <td colSpan="5" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '32px' }}>
                    No telecallers registered yet. Click "Add New Telecaller" above.
                  </td>
                </tr>
              ) : (
                telecallers.map((t) => {
                  const active = t.isActive !== false; // default to true if undefined
                  return (
                    <tr key={t.id} style={{ opacity: active ? 1 : 0.5 }}>
                      <td style={{ fontWeight: 500 }}>{t.name || 'N/A'}</td>
                      <td>{t.email}</td>
                      <td>{t.phoneNumber || 'N/A'}</td>
                      <td>
                        {active 
                          ? <span className="badge badge-success">Active</span>
                          : <span className="badge badge-warning" style={{ background: 'rgba(239,68,68,0.1)', color: 'var(--danger)', borderColor: 'var(--danger)' }}>Disabled</span>
                        }
                      </td>
                      <td>
                        <button 
                          onClick={() => toggleUserStatus(t.id, t.isActive)}
                          style={{
                            background: 'transparent',
                            border: '1px solid var(--surface-border)',
                            color: active ? 'var(--danger)' : 'var(--secondary)',
                            padding: '6px 12px',
                            borderRadius: '6px',
                            cursor: 'pointer'
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
          background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(4px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50
        }}>
          <div className="glass-panel auth-box" style={{ width: '400px' }}>
            <h3 style={{ fontSize: '20px', marginBottom: '16px', fontWeight: 600 }}>Register Telecaller</h3>
            
            {error && <div style={{ color: 'var(--danger)', marginBottom: '16px', fontSize: '13px' }}>{error}</div>}
            
            <form onSubmit={handleCreateTelecaller}>
              <div className="form-group" style={{ marginBottom: '12px' }}>
                <label className="form-label">Full Name</label>
                <input type="text" className="input-field" required value={name} onChange={e => setName(e.target.value)} placeholder="John Doe" />
              </div>
              <div className="form-group" style={{ marginBottom: '12px' }}>
                <label className="form-label">Email Address</label>
                <input type="email" className="input-field" required value={email} onChange={e => setEmail(e.target.value)} placeholder="john@nexaleads.com" />
              </div>
              <div className="form-group" style={{ marginBottom: '12px' }}>
                <label className="form-label">Phone Number</label>
                <input type="tel" className="input-field" required value={phone} onChange={e => setPhone(e.target.value)} placeholder="+91 9876543210" />
              </div>
              <div className="form-group" style={{ marginBottom: '24px' }}>
                <label className="form-label">Temporary Password</label>
                <input type="password" className="input-field" required value={password} onChange={e => setPassword(e.target.value)} placeholder="••••••••" />
              </div>

              <div style={{ display: 'flex', gap: '12px' }}>
                <button type="button" className="btn-primary" style={{ flex: 1, background: 'transparent', border: '1px solid var(--surface-border)', color: 'var(--text-main)' }} onClick={() => setShowModal(false)}>Cancel</button>
                <button type="submit" className="btn-primary" style={{ flex: 1 }} disabled={isCreating}>
                  {isCreating ? 'Creating...' : 'Register User'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
