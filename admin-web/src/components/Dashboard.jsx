import React, { useEffect, useState } from 'react';
import { db } from '../firebase';
import { collection, query, onSnapshot, orderBy } from 'firebase/firestore';

export default function Dashboard() {
  const [leads, setLeads] = useState([]);
  
  useEffect(() => {
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

  const getStatusBadge = (status) => {
    if(status === 'Order Placed') return <span className="badge badge-success">{status}</span>;
    if(status === 'Follow-up' || status === 'Visit Scheduled') return <span className="badge badge-warning">{status}</span>;
    if(status === 'Rejected' || status === 'Not Interested' || status === 'Invalid') return <span className="badge" style={{ background: 'rgba(239, 68, 68, 0.2)', color: '#fca5a5' }}>{status}</span>;
    return <span className="badge" style={{ background: 'rgba(255,255,255,0.1)' }}>{status || 'NEW'}</span>;
  };

  const totalOrders = leads.filter(l => l.status === 'Order Placed');
  const totalRevenue = totalOrders.reduce((sum, lead) => {
    const amount = parseFloat(lead.orderAmount) || 0;
    return sum + amount;
  }, 0);

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
          <span className="stat-title">Orders Placed</span>
          <span className="stat-value">{totalOrders.length}</span>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: '#10b981' }}>
          <span className="stat-title">Total Revenue</span>
          <span className="stat-value">₹{totalRevenue.toLocaleString()}</span>
        </div>
      </div>

      <div className="glass-panel" style={{ padding: '24px' }}>
        <h3 style={{ marginBottom: '20px', fontSize: '18px' }}>Recent Leads & Dispatches</h3>
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Phone Number</th>
                <th>Product</th>
                <th>Status</th>
                <th>Dispatch Info</th>
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
                    <td>{lead.product || '-'}</td>
                    <td>{getStatusBadge(lead.status)}</td>
                    <td>
                      {lead.status === 'Order Placed' ? (
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                          <div><strong>Amt:</strong> ₹{lead.orderAmount || 0} ({lead.paymentMethod || 'Unknown'})</div>
                          <div><strong>City:</strong> {lead.city || '-'}</div>
                        </div>
                      ) : (
                        <span style={{ color: 'var(--text-muted)' }}>-</span>
                      )}
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
