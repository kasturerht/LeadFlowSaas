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

  const getStatusBadge = (status, item = {}) => {
    let badgeHtml = null;
    if(status === 'Order Placed' || status === 'Converted' || status === 'Visited') badgeHtml = <span className="badge badge-success">{status}</span>;
    else if(status === 'Follow-up' || status === 'Visit Scheduled') badgeHtml = <span className="badge badge-warning">{status}</span>;
    else if(status === 'Product Inquiry Only' || status === 'Warm Lead') badgeHtml = <span className="badge" style={{ background: 'rgba(14, 165, 233, 0.2)', color: '#38bdf8' }}>{status}</span>;
    else if(status === 'Call Not Answered' || status === 'No Answer' || status === 'Busy') badgeHtml = <span className="badge" style={{ background: 'rgba(244, 63, 94, 0.2)', color: '#fb7185' }}>{status}</span>;
    else if(status === 'Not Interested' || status === 'Invalid' || status === 'Invalid/Wrong Number' || status === 'Rejected') badgeHtml = <span className="badge" style={{ background: 'rgba(148, 163, 184, 0.2)', color: '#cbd5e1' }}>{status}</span>;
    else badgeHtml = <span className="badge" style={{ background: 'rgba(255,255,255,0.1)' }}>{status || 'NEW'}</span>;

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', alignItems: 'flex-start' }}>
        <div style={{ display: 'flex', gap: '4px', alignItems: 'center', flexWrap: 'wrap' }}>
          {badgeHtml}
          {item.subStatus && <span style={{ fontSize: '11px', color: '#fb7185', background: 'rgba(244, 63, 94, 0.1)', padding: '2px 6px', borderRadius: '4px', fontWeight: 600 }}>{item.subStatus}</span>}
          {item.followUpTimeSlot && <span style={{ fontSize: '11px', color: '#f59e0b', background: 'rgba(245, 158, 11, 0.1)', padding: '2px 6px', borderRadius: '4px', fontWeight: 600 }}>{item.followUpTimeSlot}</span>}
        </div>
        {item.isSuspiciousShortCall && (
          <span style={{ fontSize: '11px', color: '#ffffff', background: '#ef4444', padding: '2px 8px', borderRadius: '4px', fontWeight: 'bold' }}>
            ⚠️ Short Call Alert (&lt;5s)
          </span>
        )}
      </div>
    );
  };

  const totalOrders = leads.filter(l => l.status === 'Order Placed' || l.status === 'Converted' || l.status === 'Visited');
  const totalRevenue = totalOrders.reduce((sum, lead) => {
    const amount = parseFloat(lead.orderAmount) || 0;
    return sum + amount;
  }, 0);

  const unansweredLeads = leads.filter(l => l.status === 'Call Not Answered' || l.status === 'No Answer' || l.status === 'Busy');
  const ringingCount = unansweredLeads.filter(l => l.subStatus && l.subStatus.includes('Ringing')).length;
  const busyCount = unansweredLeads.filter(l => l.subStatus && l.subStatus.includes('Busy')).length;
  const offCount = unansweredLeads.filter(l => l.subStatus && l.subStatus.includes('Switched')).length;

  const followUpLeads = leads.filter(l => l.status === 'Follow-up' || l.status === 'Visit Scheduled');
  const morningCount = followUpLeads.filter(l => l.followUpTimeSlot && l.followUpTimeSlot.includes('Morning')).length;
  const afterCount = followUpLeads.filter(l => l.followUpTimeSlot && l.followUpTimeSlot.includes('Afternoon')).length;
  const eveCount = followUpLeads.filter(l => l.followUpTimeSlot && l.followUpTimeSlot.includes('Evening')).length;

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

      <div className="stats-grid" style={{ marginTop: '16px', marginBottom: '32px' }}>
        <div className="stat-card glass-panel" style={{ borderTopColor: '#fb7185' }}>
          <span className="stat-title">Unanswered Breakdown</span>
          <div style={{ display: 'flex', gap: '12px', marginTop: '8px', fontSize: '13px', fontWeight: 500 }}>
            <span style={{ color: '#fb7185' }}>🔔 Ringing: <strong>{ringingCount}</strong></span>
            <span style={{ color: '#f43f5e' }}>🔴 Busy: <strong>{busyCount}</strong></span>
            <span style={{ color: '#94a3b8' }}>📵 Off: <strong>{offCount}</strong></span>
          </div>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: '#f59e0b', gridColumn: 'span 2' }}>
          <span className="stat-title">Follow-up Time Slot Distribution</span>
          <div style={{ display: 'flex', gap: '16px', marginTop: '8px', fontSize: '13px', fontWeight: 500 }}>
            <span style={{ color: '#f59e0b' }}>🌅 Morning (10-1): <strong>{morningCount}</strong></span>
            <span style={{ color: '#fbbf24' }}>☀️ Afternoon (2-5): <strong>{afterCount}</strong></span>
            <span style={{ color: '#818cf8' }}>🌙 Evening (5-8): <strong>{eveCount}</strong></span>
          </div>
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
                    <td>{getStatusBadge(lead.status, lead)}</td>
                    <td>
                      {lead.status === 'Order Placed' ? (
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                          <div><strong>Amt:</strong> ₹{lead.orderAmount || 0} ({lead.paymentMethod || 'Unknown'})</div>
                          <div><strong>City:</strong> {lead.city || '-'}</div>
                          {lead.paymentStatus && <div style={{ color: '#10b981', fontWeight: 600 }}><strong>Prepaid:</strong> {lead.paymentStatus}</div>}
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
