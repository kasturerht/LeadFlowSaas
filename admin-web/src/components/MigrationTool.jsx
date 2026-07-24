import React, { useState, useEffect } from 'react';
import { collection, getDocs, doc, setDoc, deleteDoc, query, limit, writeBatch } from 'firebase/firestore';
import { db } from '../firebase';
import { useAuth } from '../AuthContext';
import { Database, CheckCircle, Loader, ArrowRight } from 'lucide-react';
import BackfillDispatch from './BackfillDispatch';

export default function MigrationTool() {
  const { user } = useAuth();
  const [status, setStatus] = useState('idle');
  const [logs, setLogs] = useState([]);
  const [oldOrgId, setOldOrgId] = useState('');
  const [newOrgId] = useState('ORG_SUJATA_NUTRILIVE');

  const addLog = (msg) => {
    setLogs(prev => [...prev, `${new Date().toLocaleTimeString()} - ${msg}`]);
  };

  const checkState = async () => {
    setStatus('checking');
    try {
      addLog("Checking user_mappings for Ghost Org ID...");
      const mappingSnap = await getDocs(query(collection(db, 'user_mappings'), limit(10)));
      
      let foundOrg = null;
      for (const d of mappingSnap.docs) {
        if (d.data().orgId && d.data().orgId !== newOrgId) {
          foundOrg = d.data().orgId;
          break;
        }
      }

      if (!foundOrg) {
        addLog("No legacy orgId found. Either already migrated or empty.");
        setStatus('error');
        return;
      }

      setOldOrgId(foundOrg);
      addLog(`Found legacy orgId: ${foundOrg}`);
      setStatus('ready');

    } catch (err) {
      addLog("Error checking state: " + err.message);
      setStatus('error');
    }
  };

  const migrateCollection = async (collectionName) => {
    let count = 0;
    addLog(`Starting migration for ${collectionName}...`);
    
    while (true) {
      const q = query(collection(db, 'organizations', oldOrgId, collectionName), limit(400));
      const snap = await getDocs(q);
      
      if (snap.empty) {
        addLog(`Finished ${collectionName}. Total migrated: ${count}`);
        break;
      }

      const batch = writeBatch(db);
      
      snap.docs.forEach(document => {
        const data = document.data();
        const newRef = doc(db, 'organizations', newOrgId, collectionName, document.id);
        batch.set(newRef, data);
        
        const oldRef = doc(db, 'organizations', oldOrgId, collectionName, document.id);
        batch.delete(oldRef);
      });

      await batch.commit();
      count += snap.size;
      addLog(`Migrated chunk of ${snap.size} ${collectionName}... (Total: ${count})`);
    }
  };

  const runMigration = async () => {
    if (!oldOrgId) return;
    setStatus('migrating');
    addLog("=== MIGRATION STARTED ===");
    
    try {
      addLog("Provisioning new Tenant root document: " + newOrgId);
      await setDoc(doc(db, 'organizations', newOrgId), {
        name: "Sujata Nutrilive",
        status: "active",
        planType: "enterprise",
        maxUsers: 1000,
        mrr: 0,
        createdAt: new Date().toISOString()
      });

      await migrateCollection('leads');
      await migrateCollection('interactions');
      await migrateCollection('users');
      await migrateCollection('products');
      await migrateCollection('categories');

      addLog("Updating user_mappings to point to new Tenant...");
      let mappingCount = 0;
      const mappingsSnap = await getDocs(collection(db, 'user_mappings'));
      
      let batch = writeBatch(db);
      let opsCount = 0;
      
      for (let i = 0; i < mappingsSnap.docs.length; i++) {
        const d = mappingsSnap.docs[i];
        if (d.data().orgId === oldOrgId) {
          batch.update(d.ref, { orgId: newOrgId });
          opsCount++;
          mappingCount++;
        }
        
        if (opsCount === 450 || i === mappingsSnap.docs.length - 1) {
          if (opsCount > 0) await batch.commit();
          batch = writeBatch(db);
          opsCount = 0;
        }
      }
      addLog(`Updated ${mappingCount} user mappings.`);

      setStatus('done');
      addLog("=== MIGRATION COMPLETELY SUCCESSFUL ===");

    } catch (err) {
      addLog("FATAL ERROR: " + err.message);
      setStatus('error');
    }
  };

  const nukeLegacyDoc = async () => {
    try {
      addLog("Permanently deleting legacy root document: " + oldOrgId);
      await deleteDoc(doc(db, 'organizations', oldOrgId));
      addLog("Successfully nuked " + oldOrgId + " from the registry!");
    } catch (err) {
      addLog("Failed to delete legacy doc: " + err.message);
    }
  };

  return (
    <div style={{ padding: '40px', maxWidth: '800px', margin: '0 auto', fontFamily: 'system-ui' }}>
      <div className="glass-panel" style={{ padding: '30px', borderRadius: '12px' }}>
        <h1 style={{ display: 'flex', alignItems: 'center', gap: '12px', margin: '0 0 20px 0', color: 'var(--primary)' }}>
          <Database size={28} /> Silicon Valley Tenant Migration
        </h1>
        
        <p style={{ color: 'var(--text-muted)', lineHeight: '1.6' }}>
          This tool will safely transfer all data from your legacy Ghost ID to the official Enterprise Tenant Registry structure. 
          It uses batched chunking to safely process 100,000+ documents without crashing.
        </p>

        <div style={{ background: 'rgba(0,0,0,0.2)', padding: '20px', borderRadius: '8px', margin: '20px 0' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
            <strong>Legacy Source:</strong>
            <span style={{ color: '#f59e0b' }}>{oldOrgId || 'Unknown'}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'center', margin: '10px 0', color: 'var(--text-muted)' }}>
            <ArrowRight size={24} />
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <strong>Official Target:</strong>
            <span style={{ color: '#22c55e' }}>{newOrgId}</span>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '16px', marginTop: '24px' }}>
          {status === 'idle' && (
            <button className="btn-primary" onClick={checkState} style={{ padding: '12px 24px', fontSize: '16px' }}>
              Analyze Database
            </button>
          )}
          
          {status === 'ready' && (
            <button className="btn-primary" onClick={runMigration} style={{ padding: '12px 24px', fontSize: '16px', background: 'var(--danger)' }}>
              Execute Full Migration (Batched)
            </button>
          )}

          {status === 'migrating' && (
            <button className="btn-primary" disabled style={{ padding: '12px 24px', fontSize: '16px', opacity: 0.7, display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Loader size={18} className="spin" /> Migrating Data...
            </button>
          )}

          {status === 'done' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#22c55e', fontWeight: 'bold' }}>
                <CheckCircle size={24} /> Migration Complete! 
              </div>
              <button className="btn-secondary" onClick={nukeLegacyDoc} style={{ padding: '8px 16px', borderColor: 'var(--danger)', color: 'var(--danger)' }}>
                Nuke Legacy Org from Dashboard
              </button>
            </div>
          )}
        </div>
      </div>

      <div style={{ background: '#0f172a', padding: '20px', borderRadius: '8px', marginTop: '24px', border: '1px solid #1e293b' }}>
        <h3 style={{ margin: '0 0 12px 0', fontSize: '14px', color: '#94a3b8' }}>Migration Logs</h3>
        <div style={{ height: '300px', overflowY: 'auto', fontFamily: 'monospace', fontSize: '12px', color: '#cbd5e1' }}>
          {logs.length === 0 ? <span style={{ opacity: 0.5 }}>Waiting to start...</span> : null}
          {logs.map((log, i) => (
            <div key={i} style={{ marginBottom: '4px', borderBottom: '1px solid rgba(255,255,255,0.05)', paddingBottom: '4px' }}>
              {log}
            </div>
          ))}
        </div>
      </div>

      <hr style={{ borderColor: '#334155', margin: '40px 0' }} />
      
      {/* Backfill Dispatch Status Tool for old leads */}
      <BackfillDispatch />
    </div>
  );
}
