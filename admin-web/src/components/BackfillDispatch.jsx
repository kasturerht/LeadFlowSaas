import React, { useState } from 'react';
import { collection, getDocs, doc, writeBatch } from 'firebase/firestore';
import { db } from '../firebase';
import { useAuth } from '../AuthContext';

export default function BackfillDispatch() {
  const { orgId } = useAuth();
  const [logs, setLogs] = useState([]);
  const [status, setStatus] = useState('idle');

  const addLog = (msg) => {
    setLogs(prev => [...prev, `${new Date().toLocaleTimeString()} - ${msg}`]);
  };

  const startBackfill = async () => {
    if (!orgId) return alert("No orgId");
    const confirm = window.confirm("This will update old leads with missing dispatchStatus. Proceed?");
    if (!confirm) return;

    setStatus('running');
    addLog("Starting backfill for org: " + orgId);

    try {
      // 1. Fetch all users to create a mapping for assignedToName
      const usersSnap = await getDocs(collection(db, 'organizations', orgId, 'users'));
      const usersMap = {};
      usersSnap.docs.forEach(d => {
        usersMap[d.id] = d.data().name || "Unknown Agent";
      });
      addLog(`Loaded ${usersSnap.docs.length} telecallers for mapping.`);

      // 2. Fetch all leads
      const leadsSnap = await getDocs(collection(db, 'organizations', orgId, 'leads'));
      addLog(`Found ${leadsSnap.docs.length} total leads. Analyzing...`);

      let updatesToMake = [];

      leadsSnap.docs.forEach(d => {
        const data = d.data();
        let updateObj = {};
        let needsUpdate = false;

        // Check missing dispatchStatus
        if (data.status === 'Order Placed' && !data.dispatchStatus) {
          updateObj.dispatchStatus = 'Pending';
          needsUpdate = true;
        } else if (data.status === 'Delivered' && !data.dispatchStatus) {
          updateObj.dispatchStatus = 'Delivered';
          needsUpdate = true;
        } else if (data.status === 'Dispatched' && !data.dispatchStatus) {
          updateObj.dispatchStatus = 'Dispatched';
          needsUpdate = true;
        } else if ((data.status === 'RTO' || data.status === 'Returned') && !data.dispatchStatus) {
          updateObj.dispatchStatus = 'Returned';
          needsUpdate = true;
        }

        // Check missing assignedToName (The new Silicon Valley Architecture step)
        if (data.assignedTo && !data.assignedToName) {
            updateObj.assignedToName = usersMap[data.assignedTo] || "Deleted/Unknown Agent";
            needsUpdate = true;
        }

        if (needsUpdate) {
            updatesToMake.push({ ref: d.ref, ...updateObj });
        }
      });

      addLog(`Found ${updatesToMake.length} leads that need backfilling.`);

      // Firestore batches have a limit of 500 writes per batch
      const BATCH_SIZE = 400;
      for (let i = 0; i < updatesToMake.length; i += BATCH_SIZE) {
        const chunk = updatesToMake.slice(i, i + BATCH_SIZE);
        const batch = writeBatch(db);
        chunk.forEach(update => {
          const { ref, ...fieldsToUpdate } = update;
          batch.update(ref, fieldsToUpdate);
        });
        await batch.commit();
        addLog(`Committed batch ${Math.floor(i/BATCH_SIZE) + 1} (${chunk.length} records)`);
      }

      addLog("Backfill completed successfully!");
      setStatus('done');

    } catch (e) {
      addLog("ERROR: " + e.message);
      setStatus('error');
    }
  };

  return (
    <div style={{ padding: '20px', background: '#1a1a1a', color: 'white', borderRadius: '8px', margin: '20px 0' }}>
      <h2>Database Backfill Tool (Dispatch Status)</h2>
      <p>This tool runs a one-time script to fix old data so it matches the new Cloud Functions rules.</p>
      
      {status === 'idle' && (
        <button onClick={startBackfill} style={{ padding: '10px 20px', background: '#3b82f6', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>
          Start Backfill
        </button>
      )}

      {status === 'running' && <p>Running... please do not close this window.</p>}

      <div style={{ marginTop: '20px', background: 'black', padding: '10px', height: '200px', overflowY: 'auto', fontFamily: 'monospace' }}>
        {logs.map((log, i) => <div key={i}>{log}</div>)}
      </div>
    </div>
  );
}
