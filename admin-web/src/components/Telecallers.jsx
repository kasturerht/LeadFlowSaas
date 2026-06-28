import React, { useEffect, useState } from 'react';
import { db } from '../firebase';
import { collection, query, getDocs } from 'firebase/firestore';

export default function Telecallers() {
  const [telecallers, setTelecallers] = useState([]);
  const [loading, setLoading] = useState(true);
  
  useEffect(() => {
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
    fetchTelecallers();
  }, []);

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
        <div>
          <h2 style={{ fontSize: '28px', marginBottom: '8px', fontWeight: 600 }}>Telecallers Team</h2>
          <p style={{ color: 'var(--text-muted)' }}>Manage your telecallers and view their performance.</p>
        </div>
      </div>

      <div className="glass-panel" style={{ padding: '24px' }}>
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Phone</th>
                <th>Role</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="4" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '32px' }}>Loading...</td>
                </tr>
              ) : telecallers.length === 0 ? (
                <tr>
                  <td colSpan="4" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '32px' }}>
                    No telecallers registered yet. They need to sign up via the Android app.
                  </td>
                </tr>
              ) : (
                telecallers.map((t) => (
                  <tr key={t.id}>
                    <td style={{ fontWeight: 500 }}>{t.name || 'N/A'}</td>
                    <td>{t.email}</td>
                    <td>{t.phoneNumber || 'N/A'}</td>
                    <td><span className="badge badge-success">Telecaller</span></td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}
