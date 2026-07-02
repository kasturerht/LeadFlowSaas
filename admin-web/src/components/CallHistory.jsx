import React, { useEffect, useState } from 'react';
import { db } from '../firebase';
import { collection, query, onSnapshot, orderBy } from 'firebase/firestore';

export default function CallHistory() {
  const [interactions, setInteractions] = useState([]);
  
  useEffect(() => {
    const q = query(collection(db, 'interactions'), orderBy('timestamp', 'desc'));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const data = [];
      snapshot.forEach(doc => data.push({ id: doc.id, ...doc.data() }));
      setInteractions(data);
    }, (error) => {
      console.error("Error fetching interactions", error);
    });

    return () => unsubscribe();
  }, []);

  const formatDate = (timestamp) => {
    if (!timestamp) return '-';
    // Handle both Firestore Timestamp and Unix ms
    const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
    return date.toLocaleString();
  };

  const getDispositionBadge = (rawDisp, item = {}) => {
    const disp = rawDisp || 'UNKNOWN';
    let badgeHtml = null;
    if(disp === 'Order Placed' || disp === 'Converted' || disp === 'Visited' || disp === 'INTERESTED') badgeHtml = <span className="badge badge-success">{disp}</span>;
    else if(disp === 'Follow-up' || disp === 'Visit Scheduled') badgeHtml = <span className="badge badge-warning">{disp}</span>;
    else if(disp === 'Product Inquiry Only' || disp === 'Warm Lead') badgeHtml = <span className="badge" style={{ background: 'rgba(14, 165, 233, 0.2)', color: '#38bdf8' }}>{disp}</span>;
    else if(disp === 'Call Not Answered' || disp === 'No Answer' || disp === 'Busy') badgeHtml = <span className="badge" style={{ background: 'rgba(244, 63, 94, 0.2)', color: '#fb7185' }}>{disp}</span>;
    else if(disp === 'Not Interested' || disp === 'Invalid' || disp === 'Invalid/Wrong Number' || disp === 'NOT_INTERESTED' || disp === 'Rejected') badgeHtml = <span className="badge" style={{ background: 'rgba(148, 163, 184, 0.2)', color: '#cbd5e1' }}>{disp}</span>;
    else badgeHtml = <span className="badge" style={{ background: 'rgba(255,255,255,0.1)' }}>{disp}</span>;

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', alignItems: 'flex-start' }}>
        <div style={{ display: 'flex', gap: '4px', alignItems: 'center', flexWrap: 'wrap' }}>
          {badgeHtml}
          {item.subStatus && <span style={{ fontSize: '11px', color: '#fb7185', background: 'rgba(244, 63, 94, 0.1)', padding: '2px 6px', borderRadius: '4px', fontWeight: 600 }}>{item.subStatus}</span>}
          {item.followUpTimeSlot && <span style={{ fontSize: '11px', color: '#f59e0b', background: 'rgba(245, 158, 11, 0.1)', padding: '2px 6px', borderRadius: '4px', fontWeight: 600 }}>{item.followUpTimeSlot}</span>}
          {item.paymentStatus && <span style={{ fontSize: '11px', color: '#10b981', background: 'rgba(16, 185, 129, 0.1)', padding: '2px 6px', borderRadius: '4px', fontWeight: 600 }}>Prepaid: {item.paymentStatus}</span>}
        </div>
        {item.isSuspiciousShortCall && (
          <span style={{ fontSize: '11px', color: '#ffffff', background: '#ef4444', padding: '2px 8px', borderRadius: '4px', fontWeight: 'bold' }}>
            ⚠️ Short Call Alert (&lt;5s)
          </span>
        )}
      </div>
    );
  }

  return (
    <>
      <h2 style={{ fontSize: '28px', marginBottom: '8px', fontWeight: 600 }}>Call & Interaction History</h2>
      <p style={{ color: 'var(--text-muted)', marginBottom: '32px' }}>Real-time audit log of all telecaller calls and visits.</p>

      <div className="card">
        <div className="table-responsive">
          <table>
            <thead>
              <tr>
                <th>Time</th>
                <th>Telecaller</th>
                <th>Type</th>
                <th>Duration (s)</th>
                <th>Disposition</th>
                <th>Notes</th>
              </tr>
            </thead>
            <tbody>
              {interactions.length === 0 ? (
                <tr>
                  <td colSpan="6" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '32px' }}>
                    No call logs found.
                  </td>
                </tr>
              ) : (
                interactions.map((interaction) => (
                  <tr key={interaction.id}>
                    <td style={{ color: 'var(--text-muted)' }}>{formatDate(interaction.timestamp)}</td>
                    <td style={{ fontWeight: 500 }}>{interaction.callerName || interaction.callerId || interaction.telecallerId || 'Unknown'}</td>
                    <td>{interaction.isVisitLog ? 'Visit' : (interaction.type || 'Call')}</td>
                    <td>{interaction.duration || 0}</td>
                    <td>{getDispositionBadge(interaction.statusAfter || interaction.disposition, interaction)}</td>
                    <td style={{ maxWidth: '200px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {interaction.notes || '-'}
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
