import React, { useState, useEffect } from 'react';
import { collection, getDocs, doc, setDoc } from 'firebase/firestore';
import { db, secondaryAuth } from '../firebase';
import { createUserWithEmailAndPassword } from 'firebase/auth';
import { Building2, Users, AlertCircle, Plus } from 'lucide-react';
import CreateTenantModal from './CreateTenantModal';

export default function SaasAdminDashboard() {
  const [organizations, setOrganizations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const fetchOrganizations = async () => {
    setLoading(true);
    try {
      const snapshot = await getDocs(collection(db, 'organizations'));
      const orgs = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      setOrganizations(orgs);
    } catch (err) {
      console.error("Error fetching organizations:", err);
      alert("Permission denied or error fetching organizations.");
    }
    setLoading(false);
  };

  useEffect(() => {
    fetchOrganizations();
  }, []);

  const handleCreateTenant = async (tenantData) => {
    try {
      const { orgName, adminEmail, adminPassword, adminName, adminPhone } = tenantData;
      
      // 1. Generate new Org ID
      const newOrgId = `ORG_${orgName.toUpperCase().replace(/[^A-Z0-9]/g, '_')}_${Date.now().toString().slice(-4)}`;
      
      // 2. Create the Organization Document
      const orgRef = doc(db, 'organizations', newOrgId);
      await setDoc(orgRef, {
        name: orgName,
        createdAt: new Date(),
        status: 'active'
      });

      // 3. Create the Admin User in Firebase Auth (using secondaryAuth to not log out superadmin)
      const userCredential = await createUserWithEmailAndPassword(secondaryAuth, adminEmail, adminPassword);
      const newUid = userCredential.user.uid;

      // 4. Create user doc inside the organization
      const userRef = doc(db, 'organizations', newOrgId, 'users', newUid);
      await setDoc(userRef, {
        name: adminName,
        email: adminEmail,
        contactNumber: adminPhone || '',
        role: 'admin',
        isActive: true,
        createdAt: new Date()
      });

      // 5. Create the global user mapping
      const mappingRef = doc(db, 'user_mappings', newUid);
      await setDoc(mappingRef, {
        orgId: newOrgId,
        role: 'admin'
      });

      // Refresh list
      await fetchOrganizations();
      setIsModalOpen(false);
      alert("Tenant created successfully!");

    } catch (err) {
      console.error("Error creating tenant:", err);
      alert(err.message);
    }
  };

  return (
    <div style={{ padding: '24px', maxWidth: '1200px', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 600, color: 'var(--text-primary)' }}>SaaS Super Admin</h1>
          <p style={{ color: 'var(--text-muted)' }}>Manage your clients and organizations across the platform.</p>
        </div>
        <button className="btn-primary" onClick={() => setIsModalOpen(true)} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Plus size={18} /> New Client Organization
        </button>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>Loading organizations...</div>
      ) : (
        <div className="glass-panel">
          <table className="data-table">
            <thead>
              <tr>
                <th>Organization ID</th>
                <th>Company Name</th>
                <th>Status</th>
                <th>Created At</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {organizations.length === 0 ? (
                <tr>
                  <td colSpan="5" style={{ textAlign: 'center', padding: '32px' }}>
                    <Building2 size={32} style={{ color: 'var(--text-muted)', marginBottom: '8px' }} />
                    <p>No organizations found. Create your first client!</p>
                  </td>
                </tr>
              ) : (
                organizations.map(org => (
                  <tr key={org.id}>
                    <td style={{ fontFamily: 'monospace', fontWeight: 500 }}>{org.id}</td>
                    <td style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{org.name}</td>
                    <td>
                      <span style={{
                        padding: '4px 8px', 
                        borderRadius: '12px', 
                        fontSize: '12px', 
                        fontWeight: 600,
                        background: org.status === 'active' ? 'rgba(34, 197, 94, 0.1)' : 'rgba(239, 68, 68, 0.1)',
                        color: org.status === 'active' ? '#22c55e' : '#ef4444'
                      }}>
                        {org.status?.toUpperCase() || 'ACTIVE'}
                      </span>
                    </td>
                    <td>{org.createdAt?.toDate ? org.createdAt.toDate().toLocaleDateString() : 'N/A'}</td>
                    <td>
                      <button className="btn-secondary" style={{ padding: '4px 10px', fontSize: '12px' }}>Manage</button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {isModalOpen && (
        <CreateTenantModal 
          onClose={() => setIsModalOpen(false)} 
          onSubmit={handleCreateTenant} 
        />
      )}
    </div>
  );
}
