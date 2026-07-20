import React, { useEffect, useState } from 'react';
import { db } from '../firebase';
import { useAuth } from '../AuthContext';
import { 
  collection, 
  query, 
  onSnapshot, 
  orderBy, 
  limit, 
  startAfter, 
  getDocs, 
  getCountFromServer, 
  getAggregateFromServer, 
  sum, 
  where,
  updateDoc,
  doc,
  arrayUnion
} from 'firebase/firestore';
import { RefreshCw, Search } from 'lucide-react';

export default function Dashboard() {
  const { orgId } = useAuth();
  const [leads, setLeads] = useState([]);
  const [lastDoc, setLastDoc] = useState(null);
  const [hasMore, setHasMore] = useState(true);
  const [loadingLeads, setLoadingLeads] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeSearch, setActiveSearch] = useState('');
  const [productsList, setProductsList] = useState([]);
  const [pendingPayments, setPendingPayments] = useState([]);

  // Stats State
  const [stats, setStats] = useState({
    total: 0,
    orders: 0,
    revenue: 0,
    ringing: 0,
    busy: 0,
    off: 0,
    morning: 0,
    afternoon: 0,
    evening: 0
  });
  const [statsLoading, setStatsLoading] = useState(false);

  // Fetch aggregate statistics from server (scalable for 100k+ records)
  const fetchStats = async () => {
    if (!orgId) return;
    setStatsLoading(true);
    try {
      const [
        totalSnap,
        ordersSnap,
        revenueSnap,
        ringingSnap,
        busySnap,
        offSnap,
        morningSnap,
        afternoonSnap,
        eveningSnap
      ] = await Promise.all([
        getCountFromServer(collection(db, 'organizations', orgId, 'leads')),
        getCountFromServer(query(collection(db, 'organizations', orgId, 'leads'), where('status', 'in', ['Order Placed', 'Converted', 'Visited']))),
        getAggregateFromServer(query(collection(db, 'organizations', orgId, 'leads'), where('status', 'in', ['Order Placed', 'Converted', 'Visited'])), {
          totalRevenue: sum('orderAmount')
        }),
        getCountFromServer(query(collection(db, 'organizations', orgId, 'leads'), where('subStatus', '==', 'Ringing'))),
        getCountFromServer(query(collection(db, 'organizations', orgId, 'leads'), where('subStatus', '==', 'Busy'))),
        getCountFromServer(query(collection(db, 'organizations', orgId, 'leads'), where('subStatus', '==', 'Switched Off'))),
        getCountFromServer(query(collection(db, 'organizations', orgId, 'leads'), where('followUpTimeSlot', '==', 'Morning (10-1)'))),
        getCountFromServer(query(collection(db, 'organizations', orgId, 'leads'), where('followUpTimeSlot', '==', 'Afternoon (2-5)'))),
        getCountFromServer(query(collection(db, 'organizations', orgId, 'leads'), where('followUpTimeSlot', '==', 'Evening (5-8)')))
      ]);

      setStats({
        total: totalSnap.data().count,
        orders: ordersSnap.data().count,
        revenue: revenueSnap.data().totalRevenue || 0,
        ringing: ringingSnap.data().count,
        busy: busySnap.data().count,
        off: offSnap.data().count,
        morning: morningSnap.data().count,
        afternoon: afternoonSnap.data().count,
        evening: eveningSnap.data().count
      });
    } catch (err) {
      console.error("Error fetching stats:", err);
    } finally {
      setStatsLoading(false);
    }
  };

  useEffect(() => {
    fetchStats();
  }, [orgId]);

  useEffect(() => {
    if (!orgId || activeSearch) return;

    setLoadingLeads(true);
    const q = query(
      collection(db, 'organizations', orgId, 'leads'),
      limit(50)
    );

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const leadsData = [];
      snapshot.forEach((doc) => {
        leadsData.push({ id: doc.id, ...doc.data() });
      });
      setLeads(leadsData);
      setLastDoc(snapshot.docs[snapshot.docs.length - 1] || null);
      setHasMore(snapshot.docs.length === 50);
      setLoadingLeads(false);
    }, (error) => {
      console.error("Error listening to leads:", error);
      setLoadingLeads(false);
    });

    return () => unsubscribe();
  }, [activeSearch, orgId]);

  // Real-time listener for Pending Payments
  useEffect(() => {
    if (!orgId) return;
    const q = query(
      collection(db, 'organizations', orgId, 'leads'),
      where('status', '==', 'Pending Payment'),
      orderBy('lastUpdated', 'desc')
    );
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const pending = [];
      snapshot.forEach((docSnap) => {
        pending.push({ id: docSnap.id, ...docSnap.data() });
      });
      setPendingPayments(pending);
    });
    return () => unsubscribe();
  }, [orgId]);

  // Load products catalog list for combo lookup mapping
  useEffect(() => {
    if (!orgId) return;
    const q = query(collection(db, "organizations", orgId, "products"));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const prods = [];
      snapshot.forEach(docSnap => prods.push({ id: docSnap.id, ...docSnap.data() }));
      setProductsList(prods);
    }, (err) => {
      console.error("Error loading products for lookup:", err);
    });
    return () => unsubscribe();
  }, []);

  // Execute database-level search (prefix search for names, exact search for phones)
  const executeSearch = async (searchVal) => {
    const term = searchVal.trim();
    if (!term) {
      setActiveSearch('');
      return;
    }

    setLoadingLeads(true);
    let q;
    // Check if input is a phone number
    const isPhone = /^\+?[0-9\s-]+$/.test(term);

    if (isPhone) {
      q = query(
        collection(db, 'organizations', orgId, 'leads'),
        where('phoneNumber', '==', term),
        limit(50)
      );
    } else {
      q = query(
        collection(db, 'organizations', orgId, 'leads'),
        where('name', '>=', term),
        where('name', '<=', term + '\uf8ff'),
        limit(50)
      );
    }

    try {
      const snapshot = await getDocs(q);
      const leadsData = [];
      snapshot.forEach((doc) => {
        leadsData.push({ id: doc.id, ...doc.data() });
      });
      setLeads(leadsData);
      setLastDoc(snapshot.docs[snapshot.docs.length - 1] || null);
      setHasMore(snapshot.docs.length === 50);
    } catch (err) {
      console.error("Search failed:", err);
    } finally {
      setLoadingLeads(false);
    }
  };

  const handleSearchClick = () => {
    setActiveSearch(searchQuery);
    executeSearch(searchQuery);
  };

  const handleClearClick = () => {
    setSearchQuery('');
    setActiveSearch('');
  };

  // Pagination: fetch next 50 leads (works for both default feed and search query)
  const loadMoreLeads = async () => {
    if (!orgId || !lastDoc || loadingLeads) return;
    setLoadingLeads(true);

    let q;
    const term = activeSearch.trim();

    if (term) {
      const isPhone = /^\+?[0-9\s-]+$/.test(term);
      if (isPhone) {
        q = query(
          collection(db, 'organizations', orgId, 'leads'),
          where('phoneNumber', '==', term),
          startAfter(lastDoc),
          limit(50)
        );
      } else {
        q = query(
          collection(db, 'organizations', orgId, 'leads'),
          where('name', '>=', term),
          where('name', '<=', term + '\uf8ff'),
          startAfter(lastDoc),
          limit(50)
        );
      }
    } else {
      q = query(
        collection(db, 'organizations', orgId, 'leads'),
        startAfter(lastDoc),
        limit(50)
      );
    }

    try {
      const snapshot = await getDocs(q);
      const leadsData = [];
      snapshot.forEach((doc) => {
        leadsData.push({ id: doc.id, ...doc.data() });
      });
      setLeads((prev) => [...prev, ...leadsData]);
      setLastDoc(snapshot.docs[snapshot.docs.length - 1] || null);
      setHasMore(snapshot.docs.length === 50);
    } catch (err) {
      console.error("Failed to load more leads:", err);
    } finally {
      setLoadingLeads(false);
    }
  };

  const handleVerifyPayment = async (lead) => {
    const confirm = window.confirm(`Verify payment of ₹${lead.orderAmount || 0} for ${lead.name}?`);
    if (!confirm) return;

    try {
      const leadRef = doc(db, 'organizations', orgId, 'leads', lead.id);
      await updateDoc(leadRef, {
        status: 'Order Placed',
        paymentStatus: 'Paid',
        lastUpdated: new Date().toISOString(),
        paymentVerifiedAt: new Date().toISOString()
      });
      // The local snapshot listener for pendingPayments will automatically remove it
    } catch (err) {
      console.error("Error verifying payment:", err);
      alert("Failed to verify payment.");
    }
  };

  const getStatusBadge = (status, item = {}) => {
    let badgeHtml = null;
    if (status === 'Order Placed' || status === 'Converted' || status === 'Visited') {
      badgeHtml = <span className="badge badge-success">{status}</span>;
    } else if (status === 'Follow-up' || status === 'Visit Scheduled') {
      badgeHtml = <span className="badge badge-warning">{status}</span>;
    } else if (status === 'Product Inquiry Only' || status === 'Warm Lead') {
      badgeHtml = <span className="badge badge-info">{status}</span>;
    } else if (status === 'Call Not Answered' || status === 'No Answer' || status === 'Busy') {
      badgeHtml = <span className="badge badge-danger">{status}</span>;
    } else if (status === 'Not Interested' || status === 'Invalid' || status === 'Invalid/Wrong Number' || status === 'Rejected') {
      badgeHtml = <span className="badge badge-neutral">{status}</span>;
    } else {
      badgeHtml = <span className="badge" style={{ background: 'rgba(255,255,255,0.06)', color: 'var(--text-muted)' }}>{status || 'NEW'}</span>;
    }

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '3px', alignItems: 'flex-start' }}>
        <div style={{ display: 'flex', gap: '4px', alignItems: 'center', flexWrap: 'wrap' }}>
          {badgeHtml}
          {item.subStatus && (
            <span style={{ fontSize: '10px', color: '#fb7185', background: 'rgba(244, 63, 94, 0.1)', padding: '1px 4px', borderRadius: '3px', fontWeight: 600 }}>
              {item.subStatus}
            </span>
          )}
          {item.followUpTimeSlot && (
            <span style={{ fontSize: '10px', color: '#f59e0b', background: 'rgba(245, 158, 11, 0.1)', padding: '1px 4px', borderRadius: '3px', fontWeight: 600 }}>
              {item.followUpTimeSlot}
            </span>
          )}
        </div>
        {item.isSuspiciousShortCall && (
          <span style={{ fontSize: '10px', color: '#ffffff', background: '#ef4444', padding: '1px 6px', borderRadius: '3px', fontWeight: 'bold', marginTop: '2px' }}>
            ⚠️ Short Call Alert (&lt;5s)
          </span>
        )}
      </div>
    );
  };

  const handleFixOldPaymentStatuses = async () => {
    if (!orgId) return;
    if (!window.confirm("Are you sure you want to fix all old Payment Statuses (Emojis)?")) return;
    try {
      const leadsSnap = await getDocs(collection(db, 'organizations', orgId, 'leads'));
      let updatedCount = 0;
      const promises = [];
      leadsSnap.forEach((docSnap) => {
        const data = docSnap.data();
        let newStatus = null;
        if (data.paymentStatus === "✅ Payment Verified") newStatus = "Paid";
        else if (data.paymentStatus === "⏳ UPI Link Sent") newStatus = "Link Sent";
        
        if (newStatus) {
          promises.push(updateDoc(doc(db, 'organizations', orgId, 'leads', docSnap.id), { paymentStatus: newStatus }));
          updatedCount++;
        }
      });
      await Promise.all(promises);
      alert(`Successfully fixed ${updatedCount} old leads!`);
      fetchStats();
    } catch (err) {
      console.error("Error fixing old statuses:", err);
      alert("Failed to fix old statuses.");
    }
  };

  return (
    <>
      <div className="page-header">
        <div className="page-title-group">
          <h2 className="page-title">Welcome back, Admin</h2>
          <p className="page-subtitle">Here's what's happening with your leads today.</p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button 
            onClick={handleFixOldPaymentStatuses} 
            className="btn-secondary"
            style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', padding: '5px 10px', borderColor: '#ef4444', color: '#ef4444' }}
          >
            Fix Emoji Bug
          </button>
          <button 
            onClick={fetchStats} 
            className="btn-secondary" 
            disabled={statsLoading}
            style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', padding: '5px 10px' }}
          >
            <RefreshCw size={12} style={{ animation: statsLoading ? 'spin 1s linear infinite' : 'none' }} />
            {statsLoading ? "Refreshing..." : "Refresh Stats"}
          </button>
        </div>
      </div>

      {/* Injecting CSS animation inline for spin */}
      <style>{`
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
      `}</style>

      <div className="stats-grid">
        <div className="stat-card glass-panel">
          <span className="stat-title">Total Leads</span>
          <span className="stat-value">{statsLoading ? "..." : stats.total.toLocaleString()}</span>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: 'var(--secondary)' }}>
          <span className="stat-title">Orders Placed</span>
          <span className="stat-value">{statsLoading ? "..." : stats.orders.toLocaleString()}</span>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: '#10b981' }}>
          <span className="stat-title">Total Revenue</span>
          <span className="stat-value">{statsLoading ? "..." : `₹${stats.revenue.toLocaleString()}`}</span>
        </div>
      </div>

      <div className="stats-grid" style={{ marginBottom: '20px' }}>
        <div className="stat-card glass-panel" style={{ borderTopColor: '#fb7185' }}>
          <span className="stat-title">Unanswered Breakdown</span>
          <div style={{ display: 'flex', gap: '10px', marginTop: '4px', fontSize: '12px', fontWeight: 500 }}>
            <span style={{ color: '#fb7185' }}>🔔 Ringing: <strong>{statsLoading ? "..." : stats.ringing}</strong></span>
            <span style={{ color: '#f43f5e' }}>🔴 Busy: <strong>{statsLoading ? "..." : stats.busy}</strong></span>
            <span style={{ color: '#94a3b8' }}>📵 Off: <strong>{statsLoading ? "..." : stats.off}</strong></span>
          </div>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: '#f59e0b', gridColumn: 'span 2' }}>
          <span className="stat-title">Follow-up Time Slot Distribution</span>
          <div style={{ display: 'flex', gap: '14px', marginTop: '4px', fontSize: '12px', fontWeight: 500 }}>
            <span style={{ color: '#f59e0b' }}>🌅 Morning: <strong>{statsLoading ? "..." : stats.morning}</strong></span>
            <span style={{ color: '#fbbf24' }}>☀️ Afternoon: <strong>{statsLoading ? "..." : stats.afternoon}</strong></span>
            <span style={{ color: '#818cf8' }}>🌙 Evening: <strong>{statsLoading ? "..." : stats.evening}</strong></span>
          </div>
        </div>
      </div>

      {/* Pending Payments Section */}
      {pendingPayments.length > 0 && (
        <div className="glass-panel" style={{ padding: '16px', marginBottom: '20px', border: '1px solid rgba(245, 158, 11, 0.3)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
            <h3 className="section-title" style={{ margin: 0, color: '#f59e0b', display: 'flex', alignItems: 'center', gap: '8px' }}>
              ⏳ Pending Payments ({pendingPayments.length})
            </h3>
            <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Awaiting UPI Confirmation</span>
          </div>
          <div className="table-container">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Phone Number</th>
                  <th>Amount</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {pendingPayments.map(lead => {
                  const isTelecallerVerified = lead.paymentStatus === 'Payment Verified' || lead.paymentStatus === 'Verified' || lead.paymentStatus === 'Paid' || lead.paymentStatus === 'Payment Received';
                  return (
                  <tr key={lead.id} style={{ background: isTelecallerVerified ? 'rgba(16, 185, 129, 0.1)' : 'rgba(245, 158, 11, 0.05)' }}>
                    <td style={{ fontWeight: 500 }}>{lead.name}</td>
                    <td>{lead.phoneNumber || lead.phone}</td>
                    <td>
                      <div style={{ fontWeight: 600, color: isTelecallerVerified ? '#10b981' : '#f59e0b' }}>₹{lead.orderAmount || 0}</div>
                      {isTelecallerVerified && (
                        <div style={{ fontSize: '10px', color: '#10b981', fontWeight: 600, marginTop: '2px' }}>
                          ✅ Telecaller Verified
                        </div>
                      )}
                      <div style={{ fontSize: '10px', color: 'var(--text-muted)' }}>{lead.product}</div>
                    </td>
                    <td>
                      <button 
                        className="btn-primary" 
                        style={{ background: isTelecallerVerified ? '#3b82f6' : '#10b981', padding: '6px 12px', fontSize: '11px', border: 'none' }}
                        onClick={() => handleVerifyPayment(lead)}
                      >
                        {isTelecallerVerified ? 'Approve & Push to Dispatch' : '✓ Verify & Push to Dispatch'}
                      </button>
                    </td>
                  </tr>
                )})}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <div className="glass-panel" style={{ padding: '16px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', marginBottom: '14px' }}>
          <h3 className="section-title" style={{ margin: 0 }}>
            {activeSearch ? `Search Results for "${activeSearch}"` : "Recent Leads & Dispatches"}
          </h3>
          
          {/* Database Level Search Bar */}
          <div style={{ display: 'flex', gap: '6px', alignItems: 'center', width: '100%', maxWidth: '320px' }}>
            <div style={{ position: 'relative', flex: 1 }}>
              <Search style={{ position: 'absolute', top: '8px', left: '8px', color: 'var(--text-muted)' }} size={14} />
              <input
                type="text"
                className="input-field"
                style={{ paddingLeft: '28px', height: '30px', fontSize: '12px' }}
                placeholder="Search name prefix or exact phone..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearchClick()}
              />
            </div>
            <button 
              className="btn-primary" 
              style={{ height: '30px', fontSize: '12px', padding: '0 10px' }} 
              onClick={handleSearchClick}
              disabled={loadingLeads}
            >
              Search
            </button>
            {activeSearch && (
              <button 
                className="btn-secondary" 
                style={{ height: '30px', fontSize: '12px', padding: '0 10px' }} 
                onClick={handleClearClick}
              >
                Clear
              </button>
            )}
          </div>
        </div>

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
                  <td colSpan="5" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>
                    {loadingLeads ? "Loading leads..." : "No leads found."}
                  </td>
                </tr>
              ) : (
                leads.map((lead) => {
                  // Look up product in catalog to check if it is a combo
                  const matchingProduct = productsList.find(p => p.name === lead.product);
                  const isCombo = matchingProduct && matchingProduct.type === 'combo';
                  
                  const subProductNames = isCombo && matchingProduct.bundledProducts
                    ? matchingProduct.bundledProducts.map(item => {
                        const pr = productsList.find(prod => prod.id === item.productId);
                        return pr ? `${item.quantity} x ${pr.emojiIcon} ${pr.name}` : null;
                      }).filter(Boolean).join(" + ")
                    : null;

                  return (
                    <tr key={lead.id}>
                      <td style={{ fontWeight: 500 }}>{lead.name}</td>
                      <td>{lead.phoneNumber || lead.phone}</td>
                      <td>
                        <div style={{ display: 'flex', flexDirection: 'column' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                            <span>{lead.product || '-'}</span>
                            {isCombo && (
                              <span className="badge" style={{ background: 'rgba(129, 140, 248, 0.15)', color: '#818cf8', border: '1px solid rgba(129, 140, 248, 0.25)', padding: '0px 4px', fontSize: '9px', lineHeight: '1.2' }}>
                                Combo
                              </span>
                            )}
                          </div>
                          {isCombo && subProductNames && (
                            <div style={{ fontSize: '10px', color: 'var(--primary)', fontWeight: 500, marginTop: '2px' }}>
                              🎁 Pack: {subProductNames}
                            </div>
                          )}
                        </div>
                      </td>
                      <td>{getStatusBadge(lead.status, lead)}</td>
                      <td>
                        {lead.status === 'Order Placed' || lead.status === 'Converted' || lead.status === 'Visited' ? (
                          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                            <div><strong>Amt:</strong> ₹{lead.orderAmount || 0} ({lead.paymentMethod || 'Unknown'})</div>
                            <div><strong>City:</strong> {lead.city || '-'}</div>
                            {lead.paymentStatus === 'Paid' || lead.paymentStatus === 'Payment Received' ? (
                              <div style={{ color: '#10b981', fontWeight: 600 }}><strong>Prepaid:</strong> {lead.paymentStatus}</div>
                            ) : lead.paymentMethod === 'Prepaid' ? (
                              <button 
                                onClick={() => handleVerifyPayment(lead)}
                                style={{ marginTop: '4px', background: '#f59e0b', color: '#fff', border: 'none', padding: '2px 6px', borderRadius: '4px', fontSize: '10px', cursor: 'pointer' }}
                              >
                                Verify Payment
                              </button>
                            ) : null}
                          </div>
                        ) : (
                          <span style={{ color: 'var(--text-muted)' }}>-</span>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Cursor Pagination Load More Trigger */}
        {hasMore && leads.length >= 50 && (
          <div className="load-more-container">
            <button 
              className="btn-secondary" 
              onClick={loadMoreLeads} 
              disabled={loadingLeads}
              style={{ fontSize: '12px', padding: '4px 12px' }}
            >
              {loadingLeads ? "Loading..." : "Load More"}
            </button>
          </div>
        )}
      </div>
    </>
  );
}
