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

  const getDispositionBadge = (disp) => {
    if(disp === 'INTERESTED') return <span className="badge badge-success">{disp}</span>;
    if(disp === 'NOT_INTERESTED') return <span className="badge badge-danger" style={{background: 'rgba(239,68,68,0.1)', color: 'var(--danger)', border: '1px solid rgba(239,68,68,0.2)'}}>{disp}</span>;
    return <span className="badge badge-warning">{disp || 'UNKNOWN'}</span>;
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
        <div>
          <h2 style={{ fontSize: '28px', marginBottom: '8px', fontWeight: 600 }}>Call History logs</h2>
          <p style={{ color: 'var(--text-muted)' }}>Real-time logs of all calls made by your telecallers.</p>
        </div>
      </div>

      <div className="glass-panel" style={{ padding: '24px' }}>
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Date & Time</th>
                <th>Caller ID</th>
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
                    <td style={{ fontWeight: 500 }}>{interaction.telecallerId || 'Unknown'}</td>
                    <td>{interaction.type}</td>
                    <td>{interaction.duration || 0}</td>
                    <td>{getDispositionBadge(interaction.disposition)}</td>
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
