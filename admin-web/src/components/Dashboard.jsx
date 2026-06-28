import React, { useEffect, useState } from 'react';
import { db } from '../firebase';
import { collection, query, onSnapshot, orderBy, doc, updateDoc, getDocs } from 'firebase/firestore';

export default function Dashboard() {
  const [leads, setLeads] = useState([]);
  const [telecallers, setTelecallers] = useState([]);
  
  useEffect(() => {
    // Fetch users (telecallers) for assignment dropdown
    const fetchTelecallers = async () => {
      const q = query(collection(db, 'users'));
      const snapshot = await getDocs(q);
      const t = [];
      snapshot.forEach(doc => t.push({ id: doc.id, ...doc.data() }));
      setTelecallers(t);
    };
    fetchTelecallers();

    // Listen to the leads collection
    const qLeads = query(collection(db, 'leads'), orderBy('lastUpdated', 'desc'));
    const unsubscribe = onSnapshot(qLeads, (snapshot) => {
      const leadsData = [];
      snapshot.forEach((doc) => {
        leadsData.push({ id: doc.id, ...doc.data() });
      });
      setLeads(leadsData);
    }, (error) => {
      console.error("Error fetching leads:", error);
    });

    return () => unsubscribe();
  }, []);

  const handleAssignLead = async (leadId, telecallerId) => {
    if(!telecallerId) return;
    const selectedTelecaller = telecallers.find(t => t.id === telecallerId);
    
    try {
      const leadRef = doc(db, 'leads', leadId);
      await updateDoc(leadRef, {
        assignedTo: telecallerId,
        assignedToName: selectedTelecaller?.name || selectedTelecaller?.email || 'Unknown'
      });
    } catch (err) {
      console.error("Error assigning lead", err);
    }
  };

  const getStatusBadge = (status) => {
    if(status === 'INTERESTED') return <span className="badge badge-success">{status}</span>;
    if(status === 'FOLLOW_UP') return <span className="badge badge-warning">{status}</span>;
    return <span className="badge" style={{ background: 'rgba(255,255,255,0.1)' }}>{status || 'NEW'}</span>;
  };

  return (
    <>
      <h2 style={{ fontSize: '28px', marginBottom: '8px', fontWeight: 600 }}>Welcome back, Admin</h2>
      <p style={{ color: 'var(--text-muted)', marginBottom: '32px' }}>Here's what's happening with your leads today.</p>

      <div className="stats-grid">
        <div className="stat-card glass-panel">
          <span className="stat-title">Total Leads</span>
          <span className="stat-value">{leads.length}</span>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: 'var(--secondary)' }}>
          <span className="stat-title">Interested Leads</span>
          <span className="stat-value">{leads.filter(l => l.status === 'INTERESTED').length}</span>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: '#f59e0b' }}>
          <span className="stat-title">Follow Ups</span>
          <span className="stat-value">{leads.filter(l => l.status === 'FOLLOW_UP').length}</span>
        </div>
      </div>

      <div className="glass-panel" style={{ padding: '24px' }}>
        <h3 style={{ marginBottom: '20px', fontSize: '18px' }}>Recent Leads & Assignment</h3>
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Phone Number</th>
                <th>Status</th>
                <th>Assignee</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {leads.length === 0 ? (
                <tr>
                  <td colSpan="5" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '32px' }}>
                    No leads found. When telecallers add leads from the app, they will appear here in real-time.
                  </td>
                </tr>
              ) : (
                leads.map((lead) => (
                  <tr key={lead.id}>
                    <td style={{ fontWeight: 500 }}>{lead.name}</td>
                    <td>{lead.phoneNumber || lead.phone}</td>
                    <td>{getStatusBadge(lead.status)}</td>
                    <td style={{ color: 'var(--text-muted)' }}>{lead.assignedToName || 'Unassigned'}</td>
                    <td>
                      <select 
                        className="input-field" 
                        style={{ padding: '6px 10px', fontSize: '13px', width: 'auto' }}
                        value={lead.assignedTo || ''}
                        onChange={(e) => handleAssignLead(lead.id, e.target.value)}
                      >
                        <option value="">Assign to...</option>
                        {telecallers.map(t => (
                          <option key={t.id} value={t.id}>{t.name || t.email}</option>
                        ))}
                      </select>
                    </td>
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
