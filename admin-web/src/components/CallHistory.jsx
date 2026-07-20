import React, { useEffect, useState, useRef } from 'react';
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
  where,
  doc,
  updateDoc
} from 'firebase/firestore';
import { AlertTriangle, CheckSquare, Search, Award, Clock, Activity, Flag, Calendar, PhoneCall, TrendingUp, Star } from 'lucide-react';
import { subDays, startOfDay, endOfDay } from 'date-fns';
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';

export default function CallHistory() {
  const { orgId } = useAuth();
  const [interactions, setInteractions] = useState([]);
  const [lastDoc, setLastDoc] = useState(null);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);
  const [productsList, setProductsList] = useState([]);

  // Filters State
  const [telecallers, setTelecallers] = useState([]);
  const [selectedCaller, setSelectedCaller] = useState('');
  const [selectedType, setSelectedType] = useState('all'); 
  const [auditStatusFilter, setAuditStatusFilter] = useState('all'); 
  const [selectedDisposition, setSelectedDisposition] = useState('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [dateRange, setDateRange] = useState('7d'); // 'today', 'yesterday', '7d', '30d', 'all'

  // Tabs State
  const [activeTab, setActiveTab] = useState('all'); // 'all', 'flagged', 'converted'

  // QA & Coaching Row Expansion
  const [expandedRowId, setExpandedRowId] = useState(null);
  const [auditStateMap, setAuditStateMap] = useState({});
  const [savingAuditId, setSavingAuditId] = useState(null);

  // Dynamic Lead Details Resolution Cache
  const [leadsCache, setLeadsCache] = useState({});
  const fetchedIdsRef = useRef(new Set());

  // Fetch telecallers
  useEffect(() => {
    if (!orgId) return;
    const fetchCallers = async () => {
      try {
        const q = query(collection(db, 'organizations', orgId, 'users'), where('role', '==', 'telecaller'));
        const snap = await getDocs(q);
        const callers = [];
        snap.forEach(doc => {
          const u = doc.data();
          callers.push({ id: doc.id, name: u.name || u.email });
        });
        setTelecallers(callers);
      } catch (err) {
        console.error("Error fetching telecallers:", err);
      }
    };
    fetchCallers();
  }, [orgId]);

  // Fetch products
  useEffect(() => {
    if (!orgId) return;
    const q = query(collection(db, "organizations", orgId, "products"));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const prods = [];
      snapshot.forEach(docSnap => prods.push({ id: docSnap.id, ...docSnap.data() }));
      setProductsList(prods);
    });
    return () => unsubscribe();
  }, [orgId]);

  // Main Interactions Listener with Date Range
  useEffect(() => {
    if (!orgId) return;
    setLoading(true);
    
    let constraints = [];
    if (dateRange !== 'all') {
      const now = new Date();
      let start, end;
      if (dateRange === 'today') {
        start = startOfDay(now);
        end = endOfDay(now);
      } else if (dateRange === 'yesterday') {
        const yest = subDays(now, 1);
        start = startOfDay(yest);
        end = endOfDay(yest);
      } else if (dateRange === '7d') {
        start = startOfDay(subDays(now, 7));
        end = endOfDay(now);
      } else if (dateRange === '30d') {
        start = startOfDay(subDays(now, 30));
        end = endOfDay(now);
      }
      constraints.push(where('timestamp', '>=', start.toISOString()));
      constraints.push(where('timestamp', '<=', end.toISOString()));
    }
    
    constraints.push(orderBy('timestamp', 'desc'));
    constraints.push(limit(500)); // Increased limit for robust analytics

    const q = query(collection(db, 'organizations', orgId, 'interactions'), ...constraints);

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const data = [];
      snapshot.forEach(doc => data.push({ id: doc.id, ...doc.data() }));
      setInteractions(data);
      setLastDoc(snapshot.docs[snapshot.docs.length - 1] || null);
      setHasMore(snapshot.docs.length === 500);
      setLoading(false);
    }, (error) => {
      console.error("Error listening to interactions:", error);
      setLoading(false);
    });

    return () => unsubscribe();
  }, [orgId, dateRange]);

  // Dynamic Lead Resolution
  useEffect(() => {
    if (!orgId) return;
    const fetchLeadsForInteractions = async () => {
      const missingIds = interactions
        .map(item => item.leadId || item.lead_id)
        .filter(id => id && !fetchedIdsRef.current.has(id));

      if (missingIds.length === 0) return;
      const uniqueIds = Array.from(new Set(missingIds));
      uniqueIds.forEach(id => fetchedIdsRef.current.add(id));
      const newCache = {};

      for (let i = 0; i < uniqueIds.length; i += 30) {
        const chunk = uniqueIds.slice(i, i + 30);
        try {
          const q = query(collection(db, 'organizations', orgId, 'leads'), where('__name__', 'in', chunk));
          const snap = await getDocs(q);
          snap.forEach(docSnap => {
            const data = docSnap.data();
            newCache[docSnap.id] = {
              name: data.name || 'Unknown Lead',
              phoneNumber: data.phoneNumber || data.phone || '-'
            };
          });
          chunk.forEach(id => {
            if (!newCache[id]) newCache[id] = { name: 'Unknown Lead', phoneNumber: '-' };
          });
        } catch (err) {
          console.error("Error fetching leads batch:", err);
        }
      }
      setLeadsCache(prev => ({ ...prev, ...newCache }));
    };
    if (interactions.length > 0) fetchLeadsForInteractions();
  }, [interactions, orgId]);

  // Local Filtering
  const filteredInteractions = interactions.filter(item => {
    // Top Bar Filters
    if (selectedCaller && item.callerId !== selectedCaller && item.telecallerId !== selectedCaller) return false;
    if (selectedType === 'visit' && item.isVisitLog !== true) return false;
    if (selectedType === 'call' && item.isVisitLog === true) return false;
    if (auditStatusFilter === 'flagged' && item.isFlagged !== true) return false;
    if (auditStatusFilter === 'reviewed' && item.reviewStatus !== 'reviewed') return false;
    if (auditStatusFilter === 'unreviewed' && item.reviewStatus === 'reviewed') return false;
    
    const disp = item.statusAfter || item.disposition || '';
    if (selectedDisposition !== 'all') {
      if (selectedDisposition === 'converted' && !(disp === 'Order Placed' || disp === 'Converted')) return false;
      if (selectedDisposition === 'failed' && !(disp === 'Call Not Answered' || disp === 'Not Interested' || disp === 'Rejected')) return false;
    }

    // Tabs
    if (activeTab === 'flagged' && !item.isFlagged && !(Number(item.duration) < 5)) return false;
    if (activeTab === 'converted' && !(disp === 'Order Placed' || disp === 'Converted')) return false;

    // Search
    if (searchQuery.trim()) {
      const queryStr = searchQuery.toLowerCase();
      const leadId = item.leadId || item.lead_id;
      const leadInfo = leadId ? leadsCache[leadId] : null;
      const leadName = (leadInfo ? leadInfo.name : (item.leadName || item.name || '')).toLowerCase();
      const leadPhone = (leadInfo ? leadInfo.phoneNumber : (item.leadPhone || item.phoneNumber || item.phone || ''));
      const callerNameMatch = (item.callerName || '').toLowerCase().includes(queryStr);
      if (!leadName.includes(queryStr) && !leadPhone.includes(queryStr) && !callerNameMatch) return false;
    }
    
    return true;
  });

  // Calculate SaaS Metrics
  const totalCallsCount = filteredInteractions.length;
  const connectedCalls = filteredInteractions.filter(item => (Number(item.duration) || 0) > 0 || (item.statusAfter && item.statusAfter !== 'Call Not Answered')).length;
  const convertedCalls = filteredInteractions.filter(item => (item.statusAfter === 'Order Placed' || item.disposition === 'Converted')).length;
  
  const connectRate = totalCallsCount > 0 ? Math.round((connectedCalls / totalCallsCount) * 100) : 0;
  const conversionRate = connectedCalls > 0 ? ((convertedCalls / connectedCalls) * 100).toFixed(1) : 0;
  
  const totalTalkSeconds = filteredInteractions.reduce((sum, item) => sum + (Number(item.duration) || 0), 0);
  const avgTalkDuration = totalCallsCount > 0 ? Math.round(totalTalkSeconds / totalCallsCount) : 0;

  // QA Average
  const gradedCalls = filteredInteractions.filter(item => item.reviewStatus === 'reviewed' && item.qaScore !== undefined);
  const avgQaScore = gradedCalls.length > 0 
    ? Math.round(gradedCalls.reduce((sum, item) => sum + item.qaScore, 0) / gradedCalls.length) 
    : 0;

  // Chart Data Preparation
  const chartDataMap = {};
  filteredInteractions.forEach(item => {
    const d = item.timestamp ? (item.timestamp.toDate ? item.timestamp.toDate() : new Date(item.timestamp)) : new Date();
    const dateKey = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    const displayStr = `${d.getDate()}/${d.getMonth()+1}`;
    
    if (!chartDataMap[dateKey]) {
      chartDataMap[dateKey] = { dateKey, date: displayStr, calls: 0, conversions: 0 };
    }
    chartDataMap[dateKey].calls += 1;
    const disp = item.statusAfter || item.disposition || '';
    if (disp === 'Order Placed' || disp === 'Converted') {
      chartDataMap[dateKey].conversions += 1;
    }
  });
  
  let chartData = Object.values(chartDataMap).sort((a, b) => a.dateKey.localeCompare(b.dateKey));
  
  // If only one day of data exists, pad with the previous day so the chart draws a nice trend instead of a flat block.
  if (chartData.length === 1) {
    const single = chartData[0];
    const [y, m, d] = single.dateKey.split('-');
    const prevDate = new Date(Number(y), Number(m) - 1, Number(d) - 1);
    const prevDisplay = `${prevDate.getDate()}/${prevDate.getMonth()+1}`;
    chartData = [
      { date: prevDisplay, calls: 0, conversions: 0 },
      single
    ];
  }

  const formatDuration = (seconds) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return `${h > 0 ? h + 'h ' : ''}${m}m ${s}s`;
  };

  const formatDate = (timestamp) => {
    if (!timestamp) return '-';
    const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
    return date.toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  };

  const getAuditData = (id, originalItem) => {
    if (auditStateMap[id]) return auditStateMap[id];
    return {
      reviewStatus: originalItem.reviewStatus || 'unreviewed',
      isFlagged: originalItem.isFlagged || false,
      flagReason: originalItem.flagReason || 'Fake Call',
      coachingNotes: originalItem.coachingNotes || '',
      scoreGreeting: originalItem.scoreGreeting || 0,
      scorePitch: originalItem.scorePitch || 0,
      scoreClosing: originalItem.scoreClosing || 0
    };
  };

  const updateAuditField = (id, field, value, originalItem) => {
    const current = getAuditData(id, originalItem);
    setAuditStateMap(prev => ({
      ...prev,
      [id]: { ...current, [field]: value }
    }));
  };

  const handleSaveAudit = async (id, originalItem) => {
    setSavingAuditId(id);
    const data = getAuditData(id, originalItem);
    const qaScore = Math.round(((Number(data.scoreGreeting) + Number(data.scorePitch) + Number(data.scoreClosing)) / 15) * 100);
    
    try {
      const docRef = doc(db, 'organizations', orgId, 'interactions', id);
      await updateDoc(docRef, {
        reviewStatus: data.reviewStatus,
        isFlagged: data.isFlagged,
        flagReason: data.isFlagged ? data.flagReason : '',
        coachingNotes: data.coachingNotes,
        scoreGreeting: Number(data.scoreGreeting),
        scorePitch: Number(data.scorePitch),
        scoreClosing: Number(data.scoreClosing),
        qaScore: qaScore,
        reviewedAt: new Date().toISOString()
      });
      setExpandedRowId(null);
    } catch (err) {
      console.error("Error saving QA audit:", err);
      alert("Failed to save audit: " + err.message);
    } finally {
      setSavingAuditId(null);
    }
  };

  const getDispositionBadge = (rawDisp, item = {}) => {
    const disp = rawDisp || 'UNKNOWN';
    let badgeHtml = null;
    if (disp === 'Order Placed' || disp === 'Converted') {
      badgeHtml = <span className="badge badge-success">{disp}</span>;
    } else if (disp === 'Follow-up' || disp === 'Visit Scheduled') {
      badgeHtml = <span className="badge badge-warning">{disp}</span>;
    } else if (disp === 'Call Not Answered' || disp === 'No Answer' || disp === 'Busy') {
      badgeHtml = <span className="badge badge-danger">{disp}</span>;
    } else {
      badgeHtml = <span className="badge badge-neutral">{disp}</span>;
    }
    return badgeHtml;
  };

  return (
    <div style={{ paddingBottom: '40px' }}>
      <div className="page-header" style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: '16px' }}>
        <div className="page-title-group" style={{ width: '100%' }}>
          <h2 className="page-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            QA & Call Audit Center {loading && <span style={{ fontSize: '12px', fontWeight: 'normal', color: 'var(--text-muted)' }}>Updating...</span>}
          </h2>
          <p className="page-subtitle">Silicon Valley grade coaching & compliance workspace.</p>
        </div>
        
        {/* Dynamic Filters Bar - Moved below title for full width */}
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap', width: '100%', background: 'rgba(255,255,255,0.02)', padding: '12px', borderRadius: '8px', border: '1px solid var(--surface-border)' }}>
          
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <label style={{ fontSize: '10px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>Date Range</label>
            <select 
              className="input-field" 
              style={{ width: '140px', height: '36px', padding: '0 12px', fontSize: '13px', background: 'var(--primary)', color: 'white', border: 'none', fontWeight: 500 }}
              value={dateRange}
              onChange={(e) => setDateRange(e.target.value)}
            >
              <option value="today">Today</option>
              <option value="yesterday">Yesterday</option>
              <option value="7d">Last 7 Days</option>
              <option value="30d">Last 30 Days</option>
              <option value="all">All Time</option>
            </select>
          </div>

          <div style={{ width: '1px', height: '36px', background: 'var(--surface-border)', margin: '0 4px', alignSelf: 'flex-end' }}></div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <label style={{ fontSize: '10px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>Search</label>
            <div style={{ position: 'relative', width: '180px' }}>
              <Search style={{ position: 'absolute', top: '11px', left: '12px', color: 'var(--text-muted)' }} size={14} />
              <input 
                type="text" 
                className="input-field" 
                style={{ paddingLeft: '34px', height: '36px', fontSize: '13px' }}
                placeholder="Name or Phone..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <label style={{ fontSize: '10px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>Telecaller</label>
            <select className="input-field" style={{ height: '36px', fontSize: '13px', minWidth: '140px' }} value={selectedCaller} onChange={(e) => setSelectedCaller(e.target.value)}>
              <option value="">All Callers</option>
              {telecallers.map(tc => <option key={tc.id} value={tc.id}>{tc.name}</option>)}
            </select>
          </div>
          
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <label style={{ fontSize: '10px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>Disposition</label>
            <select className="input-field" style={{ height: '36px', fontSize: '13px', minWidth: '140px' }} value={selectedDisposition} onChange={(e) => setSelectedDisposition(e.target.value)}>
              <option value="all">All Outcomes</option>
              <option value="converted">Converted Only</option>
              <option value="failed">Failed/Rejected</option>
            </select>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <label style={{ fontSize: '10px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>QA Status</label>
            <select className="input-field" style={{ height: '36px', fontSize: '13px', minWidth: '130px' }} value={auditStatusFilter} onChange={(e) => setAuditStatusFilter(e.target.value)}>
              <option value="all">All Audits</option>
              <option value="unreviewed">Pending QA</option>
              <option value="reviewed">Reviewed</option>
            </select>
          </div>
        </div>
      </div>

      {/* Audit QA KPIs Header Grid */}
      <div className="stats-grid" style={{ marginBottom: '20px' }}>
        <div className="stat-card glass-panel" style={{ borderTopColor: 'var(--primary)' }}>
          <span className="stat-title" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <PhoneCall size={14} /> Connect Rate
          </span>
          <span className="stat-value" style={{ fontSize: '24px' }}>{connectRate}%</span>
          <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{connectedCalls} / {totalCallsCount} calls connected</span>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: 'var(--success)' }}>
          <span className="stat-title" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <TrendingUp size={14} style={{ color: 'var(--success)' }} /> Conversion Rate
          </span>
          <span className="stat-value" style={{ fontSize: '24px', color: 'var(--success)' }}>{conversionRate}%</span>
          <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{convertedCalls} orders placed</span>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: '#f59e0b' }}>
          <span className="stat-title" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Clock size={14} /> Avg Handle Time
          </span>
          <span className="stat-value" style={{ fontSize: '24px' }}>{avgTalkDuration}s</span>
          <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Total: {formatDuration(totalTalkSeconds)}</span>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: '#8b5cf6' }}>
          <span className="stat-title" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Star size={14} style={{ color: '#8b5cf6' }} /> QA Avg Score
          </span>
          <span className="stat-value" style={{ fontSize: '24px', color: '#8b5cf6' }}>{avgQaScore}%</span>
          <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Based on {gradedCalls.length} reviewed calls</span>
        </div>
      </div>

      {/* Analytics Chart & Smart Views */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '20px', marginBottom: '20px' }}>
        <div className="glass-panel" style={{ padding: '20px', height: '240px', display: 'flex', flexDirection: 'column' }}>
          <h3 style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '16px' }}>Call & Conversion Trends</h3>
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
              <defs>
                <linearGradient id="colorCalls" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="var(--primary)" stopOpacity={0.3}/>
                  <stop offset="95%" stopColor="var(--primary)" stopOpacity={0}/>
                </linearGradient>
                <linearGradient id="colorConversions" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="var(--success)" stopOpacity={0.3}/>
                  <stop offset="95%" stopColor="var(--success)" stopOpacity={0}/>
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
              <XAxis dataKey="date" stroke="var(--text-muted)" fontSize={11} tickLine={false} axisLine={false} tickMargin={8} />
              <YAxis yAxisId="left" stroke="var(--text-muted)" fontSize={11} tickLine={false} axisLine={false} />
              <YAxis yAxisId="right" orientation="right" stroke="var(--text-muted)" fontSize={11} tickLine={false} axisLine={false} />
              <Tooltip 
                contentStyle={{ background: 'rgba(15, 23, 42, 0.9)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px', fontSize: '12px', boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.5)' }} 
                itemStyle={{ color: '#fff', fontWeight: 500 }}
              />
              <Area yAxisId="left" type="monotone" dataKey="calls" stroke="var(--primary)" strokeWidth={3} fillOpacity={1} fill="url(#colorCalls)" name="Total Calls" activeDot={{ r: 6, strokeWidth: 0 }} />
              <Area yAxisId="right" type="monotone" dataKey="conversions" stroke="var(--success)" strokeWidth={3} fillOpacity={1} fill="url(#colorConversions)" name="Orders" activeDot={{ r: 6, strokeWidth: 0 }} />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
        <button className={`tab-btn ${activeTab === 'all' ? 'active' : ''}`} onClick={() => setActiveTab('all')} style={{ padding: '8px 20px', borderRadius: '100px', fontSize: '13px', fontWeight: 600, background: activeTab === 'all' ? 'var(--primary)' : 'rgba(255,255,255,0.05)', color: activeTab === 'all' ? 'white' : 'var(--text-main)', border: '1px solid', borderColor: activeTab === 'all' ? 'var(--primary)' : 'rgba(255,255,255,0.1)', cursor: 'pointer', transition: 'all 0.2s' }}>All Logs</button>
        <button className={`tab-btn ${activeTab === 'flagged' ? 'active' : ''}`} onClick={() => setActiveTab('flagged')} style={{ padding: '8px 20px', borderRadius: '100px', fontSize: '13px', fontWeight: 600, background: activeTab === 'flagged' ? 'rgba(239, 68, 68, 0.15)' : 'rgba(255,255,255,0.05)', color: activeTab === 'flagged' ? '#fca5a5' : 'var(--text-main)', border: '1px solid', borderColor: activeTab === 'flagged' ? 'rgba(239, 68, 68, 0.4)' : 'rgba(255,255,255,0.1)', cursor: 'pointer', transition: 'all 0.2s' }}>Risk Inbox</button>
        <button className={`tab-btn ${activeTab === 'converted' ? 'active' : ''}`} onClick={() => setActiveTab('converted')} style={{ padding: '8px 20px', borderRadius: '100px', fontSize: '13px', fontWeight: 600, background: activeTab === 'converted' ? 'rgba(34, 197, 94, 0.15)' : 'rgba(255,255,255,0.05)', color: activeTab === 'converted' ? '#86efac' : 'var(--text-main)', border: '1px solid', borderColor: activeTab === 'converted' ? 'rgba(34, 197, 94, 0.4)' : 'rgba(255,255,255,0.1)', cursor: 'pointer', transition: 'all 0.2s' }}>High Intent (Orders)</button>
      </div>

      {/* Data Grid */}
      <div className="glass-panel" style={{ padding: '4px', overflow: 'hidden' }}>
        <div className="table-container" style={{ margin: 0, padding: 0 }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: 'rgba(0,0,0,0.2)' }}>
                <th style={{ width: '40px', padding: '12px' }}></th>
                <th style={{ width: '12%', padding: '12px' }}>Time</th>
                <th style={{ width: '22%', padding: '12px' }}>Lead / Phone</th>
                <th style={{ width: '18%', padding: '12px' }}>Telecaller</th>
                <th style={{ width: '12%', padding: '12px' }}>Duration</th>
                <th style={{ width: '22%', padding: '12px' }}>Disposition</th>
                <th style={{ width: '14%', padding: '12px' }}>QA Score</th>
              </tr>
            </thead>
            <tbody>
              {filteredInteractions.length === 0 ? (
                <tr>
                  <td colSpan="7" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>
                    {loading ? "Loading..." : "No call logs match the selected criteria."}
                  </td>
                </tr>
              ) : (
                filteredInteractions.map((interaction) => {
                  const isExpanded = expandedRowId === interaction.id;
                  const auditData = getAuditData(interaction.id, interaction);
                  
                  const leadId = interaction.leadId || interaction.lead_id;
                  const leadInfo = leadId ? leadsCache[leadId] : null;
                  const leadName = leadInfo ? leadInfo.name : (interaction.leadName || interaction.name || 'Unknown Lead');
                  const leadPhone = leadInfo ? leadInfo.phoneNumber : (interaction.leadPhone || interaction.phoneNumber || interaction.phone || '-');
                  
                  const isShortCall = (Number(interaction.duration) || 0) < 5;

                  return (
                    <React.Fragment key={interaction.id}>
                      <tr 
                        style={{ cursor: 'pointer', background: isExpanded ? 'rgba(255,255,255,0.04)' : 'transparent', borderLeft: isShortCall ? '3px solid #ef4444' : '3px solid transparent' }}
                        onClick={() => setExpandedRowId(isExpanded ? null : interaction.id)}
                      >
                        <td style={{ textAlign: 'center' }}>
                          <span style={{ fontSize: '10px', color: 'var(--text-muted)' }}>{isExpanded ? '▼' : '▶'}</span>
                        </td>
                        <td style={{ color: 'var(--text-muted)', whiteSpace: 'nowrap', fontSize: '12px' }}>{formatDate(interaction.timestamp)}</td>
                        <td>
                          <div style={{ display: 'flex', flexDirection: 'column' }}>
                            <span style={{ fontWeight: 600, fontSize: '13px' }}>{leadName}</span>
                            <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{leadPhone}</span>
                          </div>
                        </td>
                        <td style={{ fontWeight: 500, fontSize: '13px' }}>{interaction.callerName || interaction.callerId || 'Unknown'}</td>
                        <td style={{ fontSize: '13px', fontWeight: 500, color: isShortCall ? '#ef4444' : 'var(--text-main)' }}>
                          {interaction.duration || '0'}s
                          {isShortCall && <AlertTriangle size={10} style={{ marginLeft: '4px', display: 'inline' }} />}
                        </td>
                        <td>
                          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', alignItems: 'flex-start' }}>
                            {getDispositionBadge(interaction.statusAfter || interaction.disposition)}
                            {interaction.isFlagged && <span style={{ fontSize: '10px', background: 'var(--danger)', padding: '1px 4px', borderRadius: '3px', color: 'white' }}>Flagged</span>}
                          </div>
                        </td>
                        <td>
                          {interaction.reviewStatus === 'reviewed' ? (
                            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                              <span className="badge badge-success" style={{ padding: '2px 6px', fontSize: '11px', background: interaction.qaScore >= 80 ? 'var(--success)' : (interaction.qaScore >= 50 ? '#f59e0b' : '#ef4444') }}>
                                {interaction.qaScore || 0}%
                              </span>
                            </div>
                          ) : (
                            <span className="badge badge-neutral" style={{ padding: '2px 6px', fontSize: '11px' }}>Pending</span>
                          )}
                        </td>
                      </tr>

                      {/* Expandable QA Panel */}
                      {isExpanded && (
                        <tr>
                          <td colSpan="7" style={{ background: 'rgba(15, 23, 42, 0.6)', padding: '20px', borderBottom: '1px solid var(--surface-border)' }}>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.5fr', gap: '24px' }}>
                              
                              {/* Left: Call Details */}
                              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                <h4 style={{ margin: 0, fontSize: '13px', display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-main)' }}>
                                  <Clock size={14} /> Call Details & Notes
                                </h4>
                                
                                {interaction.recordingUrl ? (
                                  <div style={{ background: 'rgba(0,0,0,0.2)', padding: '12px', borderRadius: '8px', border: '1px solid var(--surface-border)' }}>
                                    <audio src={interaction.recordingUrl} controls style={{ width: '100%', height: '30px' }} />
                                  </div>
                                ) : (
                                  <div style={{ padding: '12px', background: 'rgba(255,255,255,0.02)', borderRadius: '8px', border: '1px dashed var(--surface-border)', color: 'var(--text-muted)', fontSize: '11px', textAlign: 'center' }}>
                                    🔇 No audio recording available for this call.
                                  </div>
                                )}

                                <div>
                                  <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginBottom: '4px' }}>Telecaller Notes:</div>
                                  <div style={{ background: 'rgba(0,0,0,0.2)', padding: '10px', borderRadius: '6px', fontSize: '12px', whiteSpace: 'pre-wrap' }}>
                                    {interaction.notes || <span style={{ color: 'var(--text-muted)' }}>No notes provided.</span>}
                                  </div>
                                </div>
                              </div>

                              {/* Right: QA Rubric */}
                              <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', background: 'rgba(255,255,255,0.02)', padding: '16px', borderRadius: '8px', border: '1px solid var(--surface-border)' }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                  <h4 style={{ margin: 0, fontSize: '13px', display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--primary)' }}>
                                    <CheckSquare size={14} /> QA Scorecard
                                  </h4>
                                  <span style={{ fontSize: '18px', fontWeight: 'bold', color: 'var(--text-main)' }}>
                                    {Math.round(((Number(auditData.scoreGreeting) + Number(auditData.scorePitch) + Number(auditData.scoreClosing)) / 15) * 100)}%
                                  </span>
                                </div>

                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px' }}>
                                  <div>
                                    <label style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Greeting & Tone (0-5)</label>
                                    <input type="number" min="0" max="5" className="input-field" style={{ height: '30px', fontSize: '13px' }} value={auditData.scoreGreeting} onChange={(e) => updateAuditField(interaction.id, 'scoreGreeting', e.target.value, interaction)} />
                                  </div>
                                  <div>
                                    <label style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Pitch & Product (0-5)</label>
                                    <input type="number" min="0" max="5" className="input-field" style={{ height: '30px', fontSize: '13px' }} value={auditData.scorePitch} onChange={(e) => updateAuditField(interaction.id, 'scorePitch', e.target.value, interaction)} />
                                  </div>
                                  <div>
                                    <label style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Closing / Objection (0-5)</label>
                                    <input type="number" min="0" max="5" className="input-field" style={{ height: '30px', fontSize: '13px' }} value={auditData.scoreClosing} onChange={(e) => updateAuditField(interaction.id, 'scoreClosing', e.target.value, interaction)} />
                                  </div>
                                </div>

                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                                  <div>
                                    <label style={{ fontSize: '11px', color: 'var(--text-muted)', display: 'flex', gap: '4px', alignItems: 'center' }}>
                                      <Flag size={10} color="var(--danger)" /> Flag Compliance Issue?
                                    </label>
                                    <select className="input-field" style={{ height: '30px', fontSize: '12px' }} value={auditData.isFlagged ? 'yes' : 'no'} onChange={(e) => updateAuditField(interaction.id, 'isFlagged', e.target.value === 'yes', interaction)}>
                                      <option value="no">No Issues</option>
                                      <option value="yes">Yes, Flag Call</option>
                                    </select>
                                  </div>
                                  {auditData.isFlagged && (
                                    <div>
                                      <label style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Reason</label>
                                      <select className="input-field" style={{ height: '30px', fontSize: '12px' }} value={auditData.flagReason} onChange={(e) => updateAuditField(interaction.id, 'flagReason', e.target.value, interaction)}>
                                        <option value="Fake Call">Fake Call (Dummy Log)</option>
                                        <option value="Bad Pitch">Bad Pitch (Wrong Details)</option>
                                        <option value="Unprofessional Language">Unprofessional Language</option>
                                      </select>
                                    </div>
                                  )}
                                </div>

                                <div>
                                  <label style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Admin Coaching Notes</label>
                                  <textarea className="input-field" rows="2" style={{ fontSize: '12px', padding: '8px' }} placeholder="Provide actionable feedback for the agent..." value={auditData.coachingNotes} onChange={(e) => updateAuditField(interaction.id, 'coachingNotes', e.target.value, interaction)} />
                                </div>

                                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '4px' }}>
                                  <select className="input-field" style={{ width: '130px', height: '32px', fontSize: '12px' }} value={auditData.reviewStatus} onChange={(e) => updateAuditField(interaction.id, 'reviewStatus', e.target.value, interaction)}>
                                    <option value="unreviewed">Pending QA</option>
                                    <option value="reviewed">Mark as Reviewed</option>
                                  </select>
                                  <button className="btn-primary" style={{ height: '32px', fontSize: '12px', padding: '0 20px' }} onClick={() => handleSaveAudit(interaction.id, interaction)} disabled={savingAuditId === interaction.id}>
                                    {savingAuditId === interaction.id ? "Saving..." : "Save Audit"}
                                  </button>
                                </div>

                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
