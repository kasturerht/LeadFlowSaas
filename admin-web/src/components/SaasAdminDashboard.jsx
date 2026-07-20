import React, { useState, useEffect } from 'react';
import { collection, getDocs, doc, setDoc, query, orderBy, limit, startAfter, where, getCountFromServer } from 'firebase/firestore';
import { db, secondaryAuth } from '../firebase';
import { createUserWithEmailAndPassword } from 'firebase/auth';
import { Building2, Users, Search, Plus, DollarSign, Activity } from 'lucide-react';
import CreateTenantModal from './CreateTenantModal';
import TenantManageModal from './TenantManageModal';
import { useAuth } from '../AuthContext';
import { useNavigate } from 'react-router-dom';

export default function SaasAdminDashboard() {
  const { impersonateTenant } = useAuth();
  const navigate = useNavigate();
  const [organizations, setOrganizations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [managingOrg, setManagingOrg] = useState(null);
  
  // Pagination & Search state
  const [searchTerm, setSearchTerm] = useState('');
  const [lastVisible, setLastVisible] = useState(null);
  const [hasMore, setHasMore] = useState(true);
  const PAGE_SIZE = 20;

  // KPI state
  const [kpi, setKpi] = useState({ totalOrgs: 0, totalMrr: 0 });

  const fetchKpis = async () => {
    try {
      const orgsRef = collection(db, 'organizations');
      // Note: In a real app with 1000+ orgs, MRR and active counts would be maintained via Cloud Functions.
      const snapshot = await getDocs(orgsRef);
      let mrr = 0;
      let activeCount = 0;
      
      snapshot.forEach(doc => { 
        const data = doc.data();
        if (data.status === 'active' || !data.status) {
          activeCount++;
          mrr += (data.mrr || 0);
        }
      });
      
      setKpi({
        totalOrgs: activeCount,
        totalMrr: mrr
      });
    } catch (e) {
      console.error("Failed to fetch KPIs", e);
    }
  };

  const fetchOrganizations = async (isLoadMore = false, search = '') => {
    if (!isLoadMore) setLoading(true);
    try {
      let q = collection(db, 'organizations');
      
      if (search.trim() !== '') {
        q = query(q, where('name', '>=', search), where('name', '<=', search + '\uf8ff'), orderBy('name'), limit(PAGE_SIZE));
      } else {
        if (isLoadMore && lastVisible) {
          q = query(q, orderBy('createdAt', 'desc'), startAfter(lastVisible), limit(PAGE_SIZE));
        } else {
          q = query(q, orderBy('createdAt', 'desc'), limit(PAGE_SIZE));
        }
      }

      const snapshot = await getDocs(q);
      const orgs = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      
      if (isLoadMore) {
        setOrganizations(prev => [...prev, ...orgs]);
      } else {
        setOrganizations(orgs);
      }

      setLastVisible(snapshot.docs[snapshot.docs.length - 1]);
      setHasMore(snapshot.docs.length === PAGE_SIZE);

    } catch (err) {
      console.error("Error fetching organizations:", err);
      alert("Permission denied or error fetching organizations.");
    }
    setLoading(false);
  };

  useEffect(() => {
    fetchKpis();
    fetchOrganizations();
  }, []);

  const handleSearch = (e) => {
    e.preventDefault();
    fetchOrganizations(false, searchTerm);
  };
  
  const handleImpersonate = (orgId) => {
    setManagingOrg(null);
    impersonateTenant(orgId);
    navigate('/dashboard');
  };

  const handleCreateTenant = async (tenantData) => {
    try {
      const { orgName, adminEmail, adminPassword, adminName, adminPhone, planType, maxUsers, billingCycle, mrr } = tenantData;
      
      // 1. Generate new Org ID
      const newOrgId = `ORG_${orgName.toUpperCase().replace(/[^A-Z0-9]/g, '_')}_${Date.now().toString().slice(-4)}`;
      
      // 2. Create the Organization Document with Billing details
      const nextRenewal = new Date();
      nextRenewal.setMonth(nextRenewal.getMonth() + (billingCycle === 'yearly' ? 12 : 1));

      const orgRef = doc(db, 'organizations', newOrgId);
      await setDoc(orgRef, {
        name: orgName,
        createdAt: new Date(),
        status: 'active',
        planType: planType || 'basic',
        maxUsers: parseInt(maxUsers) || 5,
        billingCycle: billingCycle || 'monthly',
        nextRenewalDate: nextRenewal,
        mrr: mrr || 0
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
      await fetchKpis();
      await fetchOrganizations();
      setIsModalOpen(false);
      alert("Tenant provisioned successfully!");

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
          <p style={{ color: 'var(--text-muted)' }}>God Mode: Manage 1000+ tenants, billing, and access limits.</p>
        </div>
        <button className="btn-primary" onClick={() => setIsModalOpen(true)} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Plus size={18} /> Provision New Client
        </button>
      </div>

      {/* KPI CARDS */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '20px', marginBottom: '24px' }}>
        <div className="glass-panel" style={{ padding: '20px', display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ background: 'rgba(59, 130, 246, 0.1)', padding: '16px', borderRadius: '16px' }}>
            <Building2 size={24} color="#3b82f6" />
          </div>
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: '13px', fontWeight: 600 }}>ACTIVE TENANTS</p>
            <h2 style={{ fontSize: '28px', margin: '4px 0 0 0', color: 'var(--text-primary)' }}>{kpi.totalOrgs}</h2>
          </div>
        </div>
        <div className="glass-panel" style={{ padding: '20px', display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ background: 'rgba(34, 197, 94, 0.1)', padding: '16px', borderRadius: '16px' }}>
            <DollarSign size={24} color="#22c55e" />
          </div>
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: '13px', fontWeight: 600 }}>GLOBAL MRR</p>
            <h2 style={{ fontSize: '28px', margin: '4px 0 0 0', color: 'var(--text-primary)' }}>₹{kpi.totalMrr.toLocaleString()}</h2>
          </div>
        </div>
        <div className="glass-panel" style={{ padding: '20px', display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ background: 'rgba(168, 85, 247, 0.1)', padding: '16px', borderRadius: '16px' }}>
            <Activity size={24} color="#a855f7" />
          </div>
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: '13px', fontWeight: 600 }}>SYSTEM STATUS</p>
            <h2 style={{ fontSize: '28px', margin: '4px 0 0 0', color: 'var(--text-primary)' }}>Healthy</h2>
          </div>
        </div>
      </div>

      {/* SEARCH BAR */}
      <form onSubmit={handleSearch} style={{ marginBottom: '20px', display: 'flex', gap: '12px' }}>
        <div style={{ position: 'relative', flex: 1, maxWidth: '400px' }}>
          <Search size={18} style={{ position: 'absolute', left: '12px', top: '10px', color: '#94a3b8' }} />
          <input 
            type="text" 
            placeholder="Search organizations..." 
            className="input-field" 
            style={{ paddingLeft: '40px' }}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <button type="submit" className="btn-secondary">Search</button>
      </form>

      {/* DATA TABLE */}
      <div className="glass-panel">
        <table className="data-table">
          <thead>
            <tr>
              <th>Organization</th>
              <th>Plan & Limits</th>
              <th>MRR</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading && organizations.length === 0 ? (
              <tr><td colSpan="5" style={{ textAlign: 'center', padding: '32px' }}>Loading...</td></tr>
            ) : organizations.length === 0 ? (
              <tr>
                <td colSpan="5" style={{ textAlign: 'center', padding: '32px' }}>
                  <Building2 size={32} style={{ color: 'var(--text-muted)', marginBottom: '8px' }} />
                  <p>No organizations found.</p>
                </td>
              </tr>
            ) : (
              organizations.map(org => (
                <tr key={org.id}>
                  <td>
                    <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{org.name}</div>
                    <div style={{ fontSize: '12px', fontFamily: 'monospace', color: 'var(--text-muted)' }}>{org.id}</div>
                  </td>
                  <td>
                    <div style={{ textTransform: 'capitalize', fontWeight: 500 }}>{org.planType || 'Basic'}</div>
                    <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Max Users: {org.maxUsers || 5}</div>
                  </td>
                  <td style={{ fontWeight: 600 }}>₹{org.mrr?.toLocaleString() || 0}</td>
                  <td>
                    <span style={{
                      padding: '4px 8px', borderRadius: '12px', fontSize: '12px', fontWeight: 600,
                      background: org.status === 'active' ? 'rgba(34, 197, 94, 0.1)' : 'rgba(239, 68, 68, 0.1)',
                      color: org.status === 'active' ? '#22c55e' : '#ef4444'
                    }}>
                      {org.status?.toUpperCase() || 'ACTIVE'}
                    </span>
                  </td>
                  <td>
                    <button className="btn-secondary" style={{ padding: '4px 10px', fontSize: '12px' }} onClick={() => setManagingOrg(org)}>Manage</button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        
        {/* PAGINATION */}
        {hasMore && !loading && (
          <div style={{ padding: '16px', textAlign: 'center', borderTop: '1px solid var(--border-color)' }}>
            <button className="btn-secondary" onClick={() => fetchOrganizations(true, searchTerm)}>
              Load More
            </button>
          </div>
        )}
      </div>
      
      {isModalOpen && (
        <CreateTenantModal 
          onClose={() => setIsModalOpen(false)} 
          onSubmit={handleCreateTenant} 
        />
      )}
      
      {managingOrg && (
        <TenantManageModal
          org={managingOrg}
          onClose={() => setManagingOrg(null)}
          onRefresh={() => fetchOrganizations(false, searchTerm)}
          onImpersonate={handleImpersonate}
        />
      )}
    </div>
  );
}
