import React, { useEffect, useState } from 'react';
import { db } from '../firebase';
import { useAuth } from '../AuthContext';
import { TableVirtuoso } from 'react-virtuoso';
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
  arrayUnion,
  Timestamp
} from 'firebase/firestore';
import { RefreshCw, Search, Calendar, Users, ShoppingBag, IndianRupee, PhoneMissed, Clock } from 'lucide-react';

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
  const [dateFilter, setDateFilter] = useState('Today'); // 'Today', 'Yesterday', 'This Week', 'This Month', 'All Time'
  const [indexErrors, setIndexErrors] = useState([]);
  const [fixingDb, setFixingDb] = useState(false);

  // Fetch aggregate statistics from server (scalable for 100k+ records)
  const fetchStats = async () => {
    if (!orgId) return;
    setStatsLoading(true);
    try {
      let qTotal = collection(db, 'organizations', orgId, 'leads');
      let qOrders = query(collection(db, 'organizations', orgId, 'leads'), where('status', 'in', ['Order Placed', 'Converted', 'Visited']));
      let qRevenue = query(collection(db, 'organizations', orgId, 'leads'), where('status', 'in', ['Order Placed', 'Converted', 'Visited']));
      let qRinging = query(collection(db, 'organizations', orgId, 'leads'), where('subStatus', '==', '🔔 Ringing'));
      let qBusy = query(collection(db, 'organizations', orgId, 'leads'), where('subStatus', '==', '🔴 Busy'));
      let qOff = query(collection(db, 'organizations', orgId, 'leads'), where('subStatus', '==', '📵 Switched Off'));
      let qMorning = query(collection(db, 'organizations', orgId, 'leads'), where('followUpTimeSlot', '==', '🌅 Morning'));
      let qAfternoon = query(collection(db, 'organizations', orgId, 'leads'), where('followUpTimeSlot', '==', '☀️ Afternoon'));
      let qEvening = query(collection(db, 'organizations', orgId, 'leads'), where('followUpTimeSlot', '==', '🌙 Evening'));

      if (dateFilter !== 'All Time') {
        const now = new Date();
        let start = new Date();
        start.setHours(0, 0, 0, 0);

        if (dateFilter === 'Yesterday') {
          start.setDate(start.getDate() - 1);
          now.setHours(0, 0, 0, 0);
        } else if (dateFilter === 'This Week') {
          start.setDate(start.getDate() - start.getDay());
        } else if (dateFilter === 'This Month') {
          start.setDate(1);
        }

        const startTimestamp = Timestamp.fromDate(start);
        const endTimestamp = Timestamp.fromDate(now);

        const addDateFilter = (baseQuery) => {
          return query(baseQuery, where('updatedAt', '>=', startTimestamp), where('updatedAt', '<=', endTimestamp));
        };

        qTotal = addDateFilter(qTotal);
        qOrders = addDateFilter(qOrders);
        qRevenue = addDateFilter(qRevenue);
        qRinging = addDateFilter(qRinging);
        qBusy = addDateFilter(qBusy);
        qOff = addDateFilter(qOff);
        qMorning = addDateFilter(qMorning);
        qAfternoon = addDateFilter(qAfternoon);
        qEvening = addDateFilter(qEvening);
      }

      const results = await Promise.allSettled([
        getCountFromServer(qTotal),
        getCountFromServer(qOrders),
        getAggregateFromServer(qRevenue, { totalRevenue: sum('orderAmountNum') }),
        getCountFromServer(qRinging),
        getCountFromServer(qBusy),
        getCountFromServer(qOff),
        getCountFromServer(qMorning),
        getCountFromServer(qAfternoon),
        getCountFromServer(qEvening)
      ]);

      const errors = [];
      results.forEach(res => {
        if (res.status === 'rejected' && res.reason && res.reason.message && res.reason.message.includes('https://console.firebase.google.com')) {
          const match = res.reason.message.match(/(https:\/\/console\.firebase\.google\.com[^\s]+)/);
          if (match && !errors.includes(match[1])) {
            errors.push(match[1]);
          }
        }
      });
      setIndexErrors(errors);

      const getVal = (res, key = 'count') => res.status === 'fulfilled' ? (res.value.data()[key] || 0) : 0;

      setStats({
        total: getVal(results[0]),
        orders: getVal(results[1]),
        revenue: getVal(results[2], 'totalRevenue'),
        ringing: getVal(results[3]),
        busy: getVal(results[4]),
        off: getVal(results[5]),
        morning: getVal(results[6]),
        afternoon: getVal(results[7]),
        evening: getVal(results[8])
      });
    } catch (err) {
      console.error("Error fetching stats:", err);
    } finally {
      setStatsLoading(false);
    }
  };

  useEffect(() => {
    fetchStats();
  }, [orgId, dateFilter]);

  const fetchLeads = async () => {
    if (!orgId || activeSearch) return;
    setLoadingLeads(true);
    try {
      const q = query(
        collection(db, 'organizations', orgId, 'leads'),
        orderBy('updatedAt', 'desc'),
        limit(50)
      );
      const snapshot = await getDocs(q);
      const leadsData = [];
      snapshot.forEach((doc) => {
        leadsData.push({ id: doc.id, ...doc.data() });
      });
      setLeads(leadsData);
      setLastDoc(snapshot.docs[snapshot.docs.length - 1] || null);
      setHasMore(snapshot.docs.length === 50);
    } catch (error) {
      console.error("Error fetching leads:", error);
    } finally {
      setLoadingLeads(false);
    }
  };

  useEffect(() => {
    fetchLeads();
  }, [activeSearch, orgId]);

  // Temporary Data Normalization Tool
  const fixDatabaseTimestamps = async () => {
    if (!orgId) return;
    setFixingDb(true);
    try {
      const q = query(collection(db, 'organizations', orgId, 'leads'));
      const snapshot = await getDocs(q);
      let fixedCount = 0;
      
      const promises = [];
      snapshot.forEach((docSnap) => {
        const data = docSnap.data();
        let dateObj = null;

        if (!data.updatedAt) {
          // If missing, fallback to createdAt or current time
          if (data.createdAt) {
            dateObj = typeof data.createdAt === 'string' ? new Date(data.createdAt) 
                    : (typeof data.createdAt === 'number' ? new Date(data.createdAt) 
                    : (data.createdAt.toDate ? data.createdAt.toDate() : new Date()));
          } else {
            dateObj = new Date();
          }
        } else if (typeof data.updatedAt === 'string') {
          dateObj = new Date(data.updatedAt);
        } else if (typeof data.updatedAt === 'number') {
          dateObj = new Date(data.updatedAt);
        }

        if (dateObj && !isNaN(dateObj.getTime())) {
          promises.push(updateDoc(doc(db, 'organizations', orgId, 'leads', docSnap.id), {
            updatedAt: Timestamp.fromDate(dateObj)
          }));
          fixedCount++;
        }
      });
      
      await Promise.all(promises);
      alert(`Successfully fixed ${fixedCount} leads with invalid/missing timestamps!`);
      fetchStats();
      fetchLeads();
    } catch (error) {
      console.error("Error fixing database:", error);
      alert("Error fixing database. Check console.");
    } finally {
      setFixingDb(false);
    }
  };

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
        orderBy('updatedAt', 'desc'),
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

  return (
    <>
      <div className="page-header" style={{ marginBottom: '24px' }}>
        <div className="page-title-group">
          <h2 className="page-title" style={{ fontSize: '24px', letterSpacing: '-0.5px' }}>Welcome back, Admin</h2>
          <p className="page-subtitle" style={{ color: 'var(--text-muted)' }}>Here's what's happening with your leads today.</p>
        </div>
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
          
          <button 
            onClick={fixDatabaseTimestamps} 
            disabled={fixingDb}
            style={{ fontSize: '11px', padding: '4px 8px', background: 'rgba(245, 158, 11, 0.1)', color: '#f59e0b', border: '1px solid rgba(245, 158, 11, 0.2)', borderRadius: '6px', cursor: 'pointer' }}
          >
            {fixingDb ? 'Fixing...' : 'Fix DB Timestamps'}
          </button>

          <div style={{ position: 'relative', display: 'flex', alignItems: 'center', background: 'var(--surface)', border: '1px solid var(--surface-border)', borderRadius: '8px', padding: '0 12px', height: '36px' }}>
            <Calendar size={14} style={{ color: 'var(--text-muted)', marginRight: '8px' }} />
            <select 
              value={dateFilter}
              onChange={(e) => setDateFilter(e.target.value)}
              style={{ background: 'transparent', border: 'none', color: 'var(--text-main)', fontSize: '13px', outline: 'none', cursor: 'pointer' }}
            >
              <option value="Today">Today</option>
              <option value="Yesterday">Yesterday</option>
              <option value="This Week">This Week</option>
              <option value="This Month">This Month</option>
              <option value="All Time">All Time</option>
            </select>
          </div>

          <button 
            onClick={fetchStats} 
            className="btn-primary" 
            disabled={statsLoading}
            style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', height: '36px', padding: '0 16px', background: 'rgba(79, 70, 229, 0.1)', color: 'var(--primary)', border: '1px solid rgba(79, 70, 229, 0.2)' }}
          >
            <RefreshCw size={14} style={{ animation: statsLoading ? 'spin 1s linear infinite' : 'none' }} />
            {statsLoading ? "Refreshing..." : "Refresh Stats"}
          </button>
        </div>
      </div>

      {indexErrors.length > 0 && (
        <div style={{ background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.2)', padding: '16px', borderRadius: '12px', marginBottom: '24px' }}>
          <h3 style={{ color: '#ef4444', margin: '0 0 12px 0', fontSize: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            ⚠️ Firebase Indexes Required
          </h3>
          <p style={{ color: 'var(--text-main)', fontSize: '14px', marginBottom: '12px' }}>
            To filter stats by date, you need to create "Composite Indexes" in Firebase. Click the links below to auto-create them. (The stats will be 0 until they finish building).
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {indexErrors.map((link, i) => (
              <a key={i} href={link} target="_blank" rel="noopener noreferrer" style={{ color: '#3b82f6', textDecoration: 'underline', fontSize: '13px', wordBreak: 'break-all' }}>
                👉 Create Index {i + 1}
              </a>
            ))}
          </div>
        </div>
      )}

      {/* Injecting CSS animation inline for spin */}
      <style>{`
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
      `}</style>

      {/* Bento Grid layout */}
      <div className="bento-grid">
        <div className="bento-card bento-span-2">
          <div className="bento-header"><Users size={14} /> Total Leads</div>
          {statsLoading ? <div className="skeleton-text"></div> : <div className="bento-value">{stats.total.toLocaleString()}</div>}
        </div>
        
        <div className="bento-card bento-span-2">
          <div className="bento-header"><ShoppingBag size={14} /> Orders Placed</div>
          {statsLoading ? <div className="skeleton-text"></div> : <div className="bento-value">{stats.orders.toLocaleString()}</div>}
        </div>

        <div className="bento-card bento-span-2">
          <div className="bento-header"><IndianRupee size={14} /> Total Revenue</div>
          {statsLoading ? <div className="skeleton-text"></div> : <div className="bento-value">₹{stats.revenue.toLocaleString()}</div>}
        </div>

        <div className="bento-card bento-span-3">
          <div className="bento-header"><PhoneMissed size={14} /> Unanswered Breakdown</div>
          {statsLoading ? <div className="skeleton-text"></div> : (
            <div style={{ marginTop: '8px' }}>
              <div style={{ display: 'flex', height: '6px', borderRadius: '4px', overflow: 'hidden', background: 'rgba(255,255,255,0.05)', marginBottom: '12px' }}>
                <div style={{ width: `${(stats.ringing / (stats.ringing + stats.busy + stats.off || 1)) * 100}%`, background: '#fb7185' }}></div>
                <div style={{ width: `${(stats.busy / (stats.ringing + stats.busy + stats.off || 1)) * 100}%`, background: '#f43f5e' }}></div>
                <div style={{ width: `${(stats.off / (stats.ringing + stats.busy + stats.off || 1)) * 100}%`, background: '#94a3b8' }}></div>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}><div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#fb7185' }}></div> <span style={{ color: 'var(--text-muted)' }}>Ringing: {stats.ringing}</span></div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}><div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#f43f5e' }}></div> <span style={{ color: 'var(--text-muted)' }}>Busy: {stats.busy}</span></div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}><div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#94a3b8' }}></div> <span style={{ color: 'var(--text-muted)' }}>Off: {stats.off}</span></div>
              </div>
            </div>
          )}
        </div>

        <div className="bento-card bento-span-3">
          <div className="bento-header"><Clock size={14} /> Follow-up Slots</div>
          {statsLoading ? <div className="skeleton-text"></div> : (
            <div style={{ marginTop: '8px' }}>
              <div style={{ display: 'flex', height: '6px', borderRadius: '4px', overflow: 'hidden', background: 'rgba(255,255,255,0.05)', marginBottom: '12px' }}>
                <div style={{ width: `${(stats.morning / (stats.morning + stats.afternoon + stats.evening || 1)) * 100}%`, background: '#f59e0b' }}></div>
                <div style={{ width: `${(stats.afternoon / (stats.morning + stats.afternoon + stats.evening || 1)) * 100}%`, background: '#fbbf24' }}></div>
                <div style={{ width: `${(stats.evening / (stats.morning + stats.afternoon + stats.evening || 1)) * 100}%`, background: '#818cf8' }}></div>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}><div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#f59e0b' }}></div> <span style={{ color: 'var(--text-muted)' }}>Morning: {stats.morning}</span></div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}><div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#fbbf24' }}></div> <span style={{ color: 'var(--text-muted)' }}>Afternoon: {stats.afternoon}</span></div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}><div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#818cf8' }}></div> <span style={{ color: 'var(--text-muted)' }}>Evening: {stats.evening}</span></div>
              </div>
            </div>
          )}
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
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <h2 style={{ fontSize: '15px', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '8px' }}>
            Recent Leads & Dispatches
            <button onClick={fetchLeads} className="btn-secondary" style={{ padding: '4px 8px', fontSize: '11px' }} disabled={loadingLeads}>
              <RefreshCw size={12} className={loadingLeads ? "spin" : ""} /> {loadingLeads ? "Refreshing" : "Refresh"}
            </button>
          </h2>
          <div style={{ display: 'flex', background: 'rgba(255, 255, 255, 0.05)', borderRadius: '24px', padding: '4px', border: '1px solid rgba(255,255,255,0.08)' }}>
            <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
              <Search size={14} style={{ position: 'absolute', left: '12px', color: 'var(--text-muted)' }} />
              <input 
                type="text" 
                placeholder="Search precise prefix..." 
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearchClick()}
                style={{ 
                  background: 'transparent', 
                  border: 'none', 
                  color: 'white', 
                  padding: '8px 12px 8px 32px',
                  fontSize: '13px',
                  width: '240px',
                  outline: 'none'
                }}
              />
            </div>
            <button 
              onClick={handleSearchClick} 
              className="btn-primary" 
              style={{ padding: '6px 16px', fontSize: '12px', borderRadius: '20px' }}
              disabled={loadingLeads}
            >
              Search
            </button>
            {activeSearch && (
              <button 
                onClick={handleClearClick} 
                className="btn-secondary" 
                style={{ padding: '6px 12px', fontSize: '12px', borderRadius: '20px' }}
              >
                Clear
              </button>
            )}
          </div>
        </div>

        <div className="table-container" style={{ height: '600px', display: 'flex', flexDirection: 'column' }}>
          {leads.length === 0 && !loadingLeads ? (
            <div style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '40px' }}>
              No leads found.
            </div>
          ) : (
            <TableVirtuoso
              style={{ flex: 1, width: '100%', borderRadius: '12px' }}
              data={leads}
              endReached={loadMoreLeads}
              fixedHeaderContent={() => (
                <tr>
                  <th style={{ padding: '12px 16px', textAlign: 'left', fontWeight: '600', fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: '#e4e4e7', background: 'var(--surface)', borderBottom: '1px solid var(--surface-border)' }}>NAME & DATE</th>
                  <th style={{ padding: '12px 16px', textAlign: 'left', fontWeight: '600', fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: '#e4e4e7', background: 'var(--surface)', borderBottom: '1px solid var(--surface-border)' }}>PHONE NUMBER</th>
                  <th style={{ padding: '12px 16px', textAlign: 'left', fontWeight: '600', fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: '#e4e4e7', background: 'var(--surface)', borderBottom: '1px solid var(--surface-border)' }}>PRODUCT</th>
                  <th style={{ padding: '12px 16px', textAlign: 'left', fontWeight: '600', fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: '#e4e4e7', background: 'var(--surface)', borderBottom: '1px solid var(--surface-border)' }}>STATUS</th>
                  <th style={{ padding: '12px 16px', textAlign: 'left', fontWeight: '600', fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: '#e4e4e7', background: 'var(--surface)', borderBottom: '1px solid var(--surface-border)' }}>DISPATCH INFO</th>
                </tr>
              )}
              itemContent={(index, lead) => {
                const matchingProduct = productsList.find(p => p.name === lead.product);
                const isCombo = matchingProduct && matchingProduct.type === 'combo';
                const subProductNames = isCombo && matchingProduct.bundledProducts
                  ? matchingProduct.bundledProducts.map(item => {
                      const pr = productsList.find(prod => prod.id === item.productId);
                      return pr ? `${item.quantity} x ${pr.emojiIcon} ${pr.name}` : null;
                    }).filter(Boolean).join(" + ")
                  : null;

                return (
                  <>
                    <td style={{ padding: '16px', borderBottom: '1px solid var(--surface-border)' }}>
                      <div style={{ fontWeight: '500', color: 'var(--text-main)' }}>{lead.name}</div>
                      <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>
                        {lead.updatedAt ? (
                          typeof lead.updatedAt.toDate === 'function' 
                            ? lead.updatedAt.toDate().toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
                            : new Date(lead.updatedAt).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
                        ) : 'No Date'}
                      </div>
                    </td>
                    <td style={{ padding: '16px', borderBottom: '1px solid var(--surface-border)', color: 'var(--text-muted)' }}>{lead.phoneNumber || lead.phone}</td>
                    <td style={{ padding: '16px', borderBottom: '1px solid var(--surface-border)' }}>
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
                    <td style={{ padding: '16px', borderBottom: '1px solid var(--surface-border)' }}>{getStatusBadge(lead.status, lead)}</td>
                    <td style={{ padding: '16px', borderBottom: '1px solid var(--surface-border)' }}>
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
                        <span style={{ color: 'var(--text-muted)' }}>No dispatch info yet</span>
                      )}
                    </td>
                  </>
                );
              }}
              components={{
                Footer: () => (
                  <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)' }}>
                    {loadingLeads ? 'Loading more leads...' : !hasMore ? 'End of list' : ''}
                  </div>
                )
              }}
            />
          )}
        </div>

      </div>
    </>
  );
}
