import React, { useEffect, useState } from 'react';
import { db } from '../firebase';
import { 
  collection, 
  query, 
  getDocs, 
  where,
  limit
} from 'firebase/firestore';
import { BarChart3, Clock, DollarSign, Award, AlertTriangle, FileText, ChevronRight, User } from 'lucide-react';

export default function Reports() {
  const [telecallers, setTelecallers] = useState([]);
  const [presetRange, setPresetRange] = useState('month'); // 'today', 'yesterday', 'week', 'month', 'last30', 'ytd', 'all'
  
  // Aggregated Leaderboard and Analytics state
  const [leaderboardData, setLeaderboardData] = useState([]);
  const [revenueStats, setRevenueStats] = useState({ total: 0, conversionRate: 0, talkTime: 0, complianceScore: 100 });
  const [productSalesData, setProductSalesData] = useState([]);
  const [leadLeakageData, setLeadLeakageData] = useState({ ringing: 0, busy: 0, off: 0, notInterested: 0, other: 0 });
  const [loading, setLoading] = useState(false);

  // Missing values alert flag
  const [missingAmountsCount, setMissingAmountsCount] = useState(0);

  // Interactive Call Audit Inspector State
  const [selectedCallerId, setSelectedCallerId] = useState(null);
  const [auditInteractions, setAuditInteractions] = useState([]);
  const [auditLeadsCache, setAuditLeadsCache] = useState({});
  const [auditLoading, setAuditLoading] = useState(false);

  // Fetch telecallers once on mount
  useEffect(() => {
    const fetchMetadata = async () => {
      try {
        const uQ = query(collection(db, 'users'), where('role', '==', 'telecaller'));
        const uSnap = await getDocs(uQ);
        const callers = [];
        uSnap.forEach(docSnap => callers.push({ id: docSnap.id, ...docSnap.data() }));
        setTelecallers(callers);
        
        // Auto-select Manjushree or the first telecaller on load to answer diagnostic questions instantly
        const manjushree = callers.find(c => c.name?.toLowerCase().includes('manjushree') || c.email?.toLowerCase().includes('manjushree'));
        if (manjushree) {
          setSelectedCallerId(manjushree.id);
        } else if (callers.length > 0) {
          setSelectedCallerId(callers[0].id);
        }
      } catch (err) {
        console.error("Error loading reports metadata:", err);
      }
    };
    fetchMetadata();
  }, []);

  // Compute timezone-compliant boundaries for selected preset
  const getDateBounds = (preset) => {
    const start = new Date();
    const end = new Date();
    end.setHours(23, 59, 59, 999);

    switch (preset) {
      case 'today':
        start.setHours(0, 0, 0, 0);
        break;
      case 'yesterday':
        start.setDate(start.getDate() - 1);
        start.setHours(0, 0, 0, 0);
        end.setDate(end.getDate() - 1);
        end.setHours(23, 59, 59, 999);
        break;
      case 'week':
        const day = start.getDay();
        start.setDate(start.getDate() - day);
        start.setHours(0, 0, 0, 0);
        break;
      case 'month':
        start.setDate(1);
        start.setHours(0, 0, 0, 0);
        break;
      case 'last30':
        start.setDate(start.getDate() - 30);
        start.setHours(0, 0, 0, 0);
        break;
      case 'ytd':
        start.setMonth(0, 1);
        start.setHours(0, 0, 0, 0);
        break;
      case 'all':
        start.setFullYear(2020, 0, 1);
        start.setHours(0, 0, 0, 0);
        break;
      default:
        start.setDate(1);
        start.setHours(0, 0, 0, 0);
    }
    return { start, end };
  };

  // Safe numerical parser for pricing fields containing formatting or spaces
  const parseAmount = (val) => {
    if (val === undefined || val === null) return 0;
    const cleanStr = String(val).replace(/[^0-9.]/g, '');
    return parseFloat(cleanStr) || 0;
  };

  // Query Firestore and group everything in memory
  // This requires ZERO composite index builds on Firestore, preventing runtime crash warnings
  useEffect(() => {
    const loadReportsData = async () => {
      setLoading(true);
      const { start, end } = getDateBounds(presetRange);

      try {
        // 1. Fetch all interaction logs in the date range (using ISO string comparisons matching Android DB schema)
        const intQuery = query(
          collection(db, 'interactions'),
          where('timestamp', '>=', start.toISOString()),
          where('timestamp', '<=', end.toISOString())
        );
        const intSnap = await getDocs(intQuery);
        const interactions = [];
        intSnap.forEach(docSnap => interactions.push({ id: docSnap.id, ...docSnap.data() }));

        // 2. Query converted leads by status.
        let leadsQuery;
        if (presetRange === 'all') {
          leadsQuery = query(
            collection(db, 'leads'),
            where('status', 'in', ['Order Placed', 'Converted', 'Visited']),
            limit(1000)
          );
        } else {
          leadsQuery = query(
            collection(db, 'leads'),
            where('status', 'in', ['Order Placed', 'Converted', 'Visited'])
          );
        }

        const leadsSnap = await getDocs(leadsQuery);
        const convertedLeads = [];
        let zeroAmountsCount = 0;

        leadsSnap.forEach(docSnap => {
          const lead = { id: docSnap.id, ...docSnap.data() };
          
          // Get conversion date using robust fallback
          let leadDate = null;
          if (lead.convertedAt) {
            leadDate = new Date(lead.convertedAt);
          } else if (lead.lastUpdated) {
            leadDate = lead.lastUpdated.toDate ? lead.lastUpdated.toDate() : new Date(lead.lastUpdated);
          }

          const parsedAmt = parseAmount(lead.orderAmount);
          const isConverted = ['Order Placed', 'Converted', 'Visited'].includes(lead.status);

          if (isConverted) {
            if (presetRange === 'all') {
              convertedLeads.push(lead);
              if (parsedAmt === 0) zeroAmountsCount++;
            } else if (leadDate && leadDate >= start && leadDate <= end) {
              convertedLeads.push(lead);
              if (parsedAmt === 0) zeroAmountsCount++;
            }
          }
        });

        setMissingAmountsCount(zeroAmountsCount);

        // 3. Initialize mapping stats for all telecallers
        const callerStatsMap = {};
        telecallers.forEach(tc => {
          callerStatsMap[tc.id] = {
            id: tc.id,
            name: tc.name || tc.email,
            totalCalls: 0,
            connectedCalls: 0,
            suspiciousCalls: 0,
            totalTalkTime: 0,
            orderCount: 0,
            revenue: 0
          };
        });

        // 4. Map interactions data (supporting both callerId and telecallerId keys)
        interactions.forEach(item => {
          const callerId = item.callerId || item.telecallerId;
          if (!callerId) return;

          // If caller is deleted but has historical records, preserve audit integrity
          if (!callerStatsMap[callerId]) {
            callerStatsMap[callerId] = {
              id: callerId,
              name: item.callerName || 'Deleted Telecaller',
              totalCalls: 0,
              connectedCalls: 0,
              suspiciousCalls: 0,
              totalTalkTime: 0,
              orderCount: 0,
              revenue: 0
            };
          }

          callerStatsMap[callerId].totalCalls += 1;
          const duration = Number(item.duration) || 0;
          if (duration > 0) {
            callerStatsMap[callerId].connectedCalls += 1;
          }
          if (item.isSuspiciousShortCall || duration < 5) {
            callerStatsMap[callerId].suspiciousCalls += 1;
          }
          callerStatsMap[callerId].totalTalkTime += duration;
        });

        // 5. Map converted leads order amounts (supporting both assignedTo and telecallerId keys)
        convertedLeads.forEach(lead => {
          const callerId = lead.assignedTo || lead.telecallerId;
          if (!callerId) return;

          if (!callerStatsMap[callerId]) {
            callerStatsMap[callerId] = {
              id: callerId,
              name: 'Deleted Telecaller',
              totalCalls: 0,
              connectedCalls: 0,
              suspiciousCalls: 0,
              totalTalkTime: 0,
              orderCount: 0,
              revenue: 0
            };
          }

          callerStatsMap[callerId].orderCount += 1;
          callerStatsMap[callerId].revenue += parseAmount(lead.orderAmount);
        });

        setLeaderboardData(Object.values(callerStatsMap));

        // 6. Calculate Overall Summary Metrics
        const totalRevenue = convertedLeads.reduce((sum, l) => sum + parseAmount(l.orderAmount), 0);
        const totalCalls = interactions.length;
        const totalTalkTime = interactions.reduce((sum, item) => sum + (Number(item.duration) || 0), 0);
        
        const conversionRate = totalCalls > 0 ? Math.round((convertedLeads.length / totalCalls) * 100) : 0;

        const suspiciousCalls = interactions.filter(item => 
          item.isSuspiciousShortCall || (Number(item.duration) || 0) < 5
        ).length;
        const complianceScore = totalCalls > 0 ? Math.round(((totalCalls - suspiciousCalls) / totalCalls) * 100) : 100;

        setRevenueStats({
          total: totalRevenue,
          conversionRate,
          talkTime: totalTalkTime,
          complianceScore
        });

        // 7. Calculate Top Products Mix
        const prodSalesMap = {};
        convertedLeads.forEach(lead => {
          const prodName = lead.product || 'Unknown Product';
          if (!prodSalesMap[prodName]) {
            prodSalesMap[prodName] = { name: prodName, units: 0, revenue: 0 };
          }
          prodSalesMap[prodName].units += 1;
          prodSalesMap[prodName].revenue += parseAmount(lead.orderAmount);
        });
        
        const sortedProducts = Object.values(prodSalesMap).sort((a, b) => b.revenue - a.revenue);
        setProductSalesData(sortedProducts);

        // 8. Calculate Lead Funnel Leakage Breakdown directly from Call Dispositions
        const leakage = { ringing: 0, busy: 0, off: 0, notInterested: 0, other: 0 };
        interactions.forEach(item => {
          const status = item.statusAfter || item.disposition || '';
          const subStatus = item.subStatus || '';

          if (status === 'Call Not Answered' || subStatus === 'Ringing') {
            leakage.ringing += 1;
          } else if (status === 'Busy' || subStatus === 'Busy') {
            leakage.busy += 1;
          } else if (subStatus === 'Switched Off') {
            leakage.off += 1;
          } else if (['Not Interested', 'Invalid', 'Invalid/Wrong Number', 'Rejected', 'NOT_INTERESTED'].includes(status)) {
            leakage.notInterested += 1;
          } else if (!['Order Placed', 'Converted', 'Visited', 'INTERESTED'].includes(status)) {
            leakage.other += 1;
          }
        });
        setLeadLeakageData(leakage);

      } catch (err) {
        console.error("Error loading analytics reports:", err);
      } finally {
        setLoading(false);
      }
    };

    if (telecallers.length > 0) {
      loadReportsData();
    }
  }, [presetRange, telecallers]);

  // Dynamic Caller Audit Loader (triggers when selectedCallerId changes or range preset updates)
  useEffect(() => {
    const fetchAuditData = async () => {
      if (!selectedCallerId) return;
      setAuditLoading(true);
      const { start, end } = getDateBounds(presetRange);

      try {
        // Query interactions specifically belonging to the selected telecaller within range
        const auditQ = query(
          collection(db, 'interactions'),
          where('timestamp', '>=', start.toISOString()),
          where('timestamp', '<=', end.toISOString())
        );
        const snap = await getDocs(auditQ);
        
        const rawLogs = [];
        const requiredLeadIds = [];
        snap.forEach(docSnap => {
          const data = docSnap.data();
          const callerId = data.callerId || data.telecallerId;
          if (callerId === selectedCallerId) {
            rawLogs.push({ id: docSnap.id, ...data });
            const leadId = data.leadId || data.lead_id;
            if (leadId) requiredLeadIds.push(leadId);
          }
        });

        setAuditInteractions(rawLogs);

        // Batch resolve client details (names & phone numbers) for these interactions
        const uniqueIds = Array.from(new Set(requiredLeadIds)).filter(id => !auditLeadsCache[id]);
        if (uniqueIds.length > 0) {
          const chunks = [];
          for (let i = 0; i < uniqueIds.length; i += 30) {
            chunks.push(uniqueIds.slice(i, i + 30));
          }

          const resolvedCache = { ...auditLeadsCache };
          for (const chunk of chunks) {
            const leadsQ = query(collection(db, 'leads'), where('__name__', 'in', chunk));
            const leadsSnap = await getDocs(leadsQ);
            leadsSnap.forEach(d => {
              resolvedCache[d.id] = d.data();
            });
          }
          setAuditLeadsCache(resolvedCache);
        }

      } catch (err) {
        console.error("Error fetching call audit log details:", err);
      } finally {
        setAuditLoading(false);
      }
    };

    if (telecallers.length > 0) {
      fetchAuditData();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedCallerId, presetRange, telecallers]);

  // Export Leaderboard stats to CSV
  const handleExportCSV = () => {
    const headers = ['Telecaller Name', 'Total Calls', 'Connected Calls', 'Connection Rate %', 'Total Talk Time', 'Orders Converted', 'Revenue Contribution', 'AOV', 'Suspicious Calls'];
    const rows = leaderboardData.map(tc => {
      const connRate = tc.totalCalls > 0 ? Math.round((tc.connectedCalls / tc.totalCalls) * 100) : 0;
      const aov = tc.orderCount > 0 ? Math.round(tc.revenue / tc.orderCount) : 0;
      return [
        tc.name,
        tc.totalCalls,
        tc.connectedCalls,
        `${connRate}%`,
        formatDuration(tc.totalTalkTime),
        tc.orderCount,
        `₹${tc.revenue}`,
        `₹${aov}`,
        tc.suspiciousCalls
      ];
    });

    const csvContent = [headers.join(','), ...rows.map(e => e.map(val => `"${val}"`).join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", `Telecaller_Leaderboard_${presetRange}_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const formatDuration = (seconds) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return `${h > 0 ? h + 'h ' : ''}${m}m ${s}s`;
  };

  const getSelectedCallerName = () => {
    const caller = telecallers.find(c => c.id === selectedCallerId);
    return caller ? (caller.name || caller.email) : 'Select a Telecaller';
  };

  return (
    <>
      <div className="page-header">
        <div className="page-title-group">
          <h2 className="page-title">Executive BI & Analytics</h2>
          <p className="page-subtitle">Real-time revenue attribution and operational efficiency report.</p>
        </div>

        {/* Date Ranges & CSV Export Controls */}
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
          <select 
            className="input-field" 
            style={{ width: '150px', height: '30px', padding: '0 8px', fontSize: '12px' }}
            value={presetRange}
            onChange={(e) => setPresetRange(e.target.value)}
          >
            <option value="today">Today</option>
            <option value="yesterday">Yesterday</option>
            <option value="week">This Week</option>
            <option value="month">This Month</option>
            <option value="last30">Last 30 Days</option>
            <option value="ytd">Year to Date (YTD)</option>
            <option value="all">All Time</option>
          </select>

          <button 
            className="btn-secondary" 
            style={{ height: '30px', fontSize: '12px', padding: '0 12px', display: 'flex', alignItems: 'center', gap: '6px' }}
            onClick={handleExportCSV}
            disabled={leaderboardData.length === 0}
          >
            <FileText size={12} />
            Export CSV
          </button>
        </div>
      </div>

      {/* Database Integrity Alert Banner */}
      {missingAmountsCount > 0 && (
        <div className="glass-panel" style={{ 
          marginBottom: '20px', 
          padding: '12px 16px', 
          borderLeft: '4px solid var(--danger)', 
          background: 'rgba(239, 68, 68, 0.08)',
          color: '#fca5a5',
          fontSize: '12px',
          display: 'flex',
          alignItems: 'center',
          gap: '8px'
        }}>
          <AlertTriangle size={14} style={{ color: 'var(--danger)' }} />
          <span>
            <strong>Attention Required:</strong> {missingAmountsCount} converted orders in this range are missing prices/order amounts in the database. Their revenue displays as ₹0.
          </span>
        </div>
      )}

      {/* Top Level Summary Cards */}
      <div className="stats-grid" style={{ marginBottom: '20px' }}>
        <div className="stat-card glass-panel" style={{ borderTopColor: '#10b981' }}>
          <span className="stat-title" style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <DollarSign size={12} style={{ color: '#10b981' }} /> Total Revenue
          </span>
          <span className="stat-value" style={{ fontSize: '22px' }}>
            {loading ? "..." : `₹${revenueStats.total.toLocaleString()}`}
          </span>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: 'var(--primary)' }}>
          <span className="stat-title" style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Award size={12} style={{ color: 'var(--primary)' }} /> Sales Funnel Conversion
          </span>
          <span className="stat-value" style={{ fontSize: '22px' }}>
            {loading ? "..." : `${revenueStats.conversionRate}%`}
          </span>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: '#6366f1' }}>
          <span className="stat-title" style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Clock size={12} style={{ color: '#6366f1' }} /> Total Talk Time
          </span>
          <span className="stat-value" style={{ fontSize: '22px' }}>
            {loading ? "..." : formatDuration(revenueStats.talkTime)}
          </span>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: revenueStats.complianceScore < 85 ? 'var(--danger)' : '#10b981' }}>
          <span className="stat-title" style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <AlertTriangle size={12} style={{ color: revenueStats.complianceScore < 85 ? 'var(--danger)' : '#10b981' }} /> Compliance Score
          </span>
          <span className="stat-value" style={{ fontSize: '22px' }}>
            {loading ? "..." : `${revenueStats.complianceScore}%`}
          </span>
        </div>
      </div>

      {/* Row 2: Telecaller Leaderboard Matrix */}
      <div className="glass-panel" style={{ padding: '16px', marginBottom: '20px' }}>
        <h3 className="section-title" style={{ marginTop: 0, marginBottom: '12px' }}>Telecaller Performance Matrix</h3>
        <div style={{ marginBottom: '8px', fontSize: '11px', color: 'var(--text-muted)' }}>
          💡 Click on any telecaller's row below to audit their call logs and lead details in real-time.
        </div>
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Telecaller Name</th>
                <th>Total Calls</th>
                <th>Connected %</th>
                <th>Total Talk Time</th>
                <th>Orders</th>
                <th>Revenue Generated</th>
                <th>AOV</th>
                <th>Suspicious Calls</th>
                <th style={{ width: '40px' }}></th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="9" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>
                    Loading Performance Matrix...
                  </td>
                </tr>
              ) : leaderboardData.length === 0 ? (
                <tr>
                  <td colSpan="9" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>
                    No stats logged for this date range.
                  </td>
                </tr>
              ) : (
                leaderboardData.map(tc => {
                  const connectionRate = tc.totalCalls > 0 ? Math.round((tc.connectedCalls / tc.totalCalls) * 100) : 0;
                  const aov = tc.orderCount > 0 ? Math.round(tc.revenue / tc.orderCount) : 0;
                  const isSelected = selectedCallerId === tc.id;
                  
                  return (
                    <tr 
                      key={tc.id} 
                      onClick={() => setSelectedCallerId(tc.id)}
                      style={{ 
                        cursor: 'pointer', 
                        background: isSelected ? 'rgba(79, 70, 229, 0.08)' : 'transparent',
                        borderLeft: isSelected ? '3px solid var(--primary)' : 'none'
                      }}
                    >
                      <td style={{ fontWeight: 600 }}>{tc.name}</td>
                      <td>{tc.totalCalls}</td>
                      <td>
                        <span style={{ color: connectionRate > 60 ? 'var(--secondary)' : 'var(--text-muted)', fontWeight: 500 }}>
                          {connectionRate}%
                        </span>
                      </td>
                      <td>{formatDuration(tc.totalTalkTime)}</td>
                      <td style={{ fontWeight: 600 }}>{tc.orderCount}</td>
                      <td style={{ color: '#10b981', fontWeight: 600 }}>₹{tc.revenue.toLocaleString()}</td>
                      <td>₹{aov.toLocaleString()}</td>
                      <td>
                        {tc.suspiciousCalls > 0 ? (
                          <span style={{ color: 'var(--danger)', fontWeight: 'bold' }}>
                            ⚠️ {tc.suspiciousCalls} Short Calls
                          </span>
                        ) : (
                          <span style={{ color: 'var(--text-muted)' }}>0</span>
                        )}
                      </td>
                      <td style={{ textAlign: 'center' }}>
                        <ChevronRight size={14} style={{ color: isSelected ? 'var(--primary)' : 'var(--text-muted)' }} />
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Row 3: Call Details Audit Inspector (Interactive Workspace) */}
      {selectedCallerId && (
        <div className="glass-panel" style={{ padding: '16px', marginBottom: '20px', borderTop: '2px solid var(--primary)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px', flexWrap: 'wrap', gap: '8px' }}>
            <h3 className="section-title" style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '8px' }}>
              <User size={16} style={{ color: 'var(--primary)' }} /> 
              Detailed Call logs Audit: {getSelectedCallerName()}
            </h3>
            <span className="badge" style={{ background: 'rgba(79, 70, 229, 0.1)', color: 'var(--primary)', fontSize: '11px', padding: '4px 8px' }}>
              {auditInteractions.length} Calls in Range
            </span>
          </div>

          <div className="table-container" style={{ maxHeight: '350px', overflowY: 'auto' }}>
            <table>
              <thead>
                <tr>
                  <th>Client / Customer Name</th>
                  <th>Contact Number</th>
                  <th>Call Outcome / Status</th>
                  <th>Sub-Status / Disposition</th>
                  <th>Call Duration</th>
                  <th>Date & Time</th>
                  <th>Order Value</th>
                </tr>
              </thead>
              <tbody>
                {auditLoading ? (
                  <tr>
                    <td colSpan="7" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>
                      Resolving client details and loading logs...
                    </td>
                  </tr>
                ) : auditInteractions.length === 0 ? (
                  <tr>
                    <td colSpan="7" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>
                      No calls logged by this telecaller in the selected range.
                    </td>
                  </tr>
                ) : (
                  auditInteractions.map(item => {
                    const leadId = item.leadId || item.lead_id;
                    const lead = auditLeadsCache[leadId] || {};
                    const clientName = lead.name || 'Unknown Lead';
                    const clientPhone = lead.phone || lead.phoneNumber || '-';
                    const formattedDate = new Date(item.timestamp).toLocaleString();
                    const durationStr = formatDuration(Number(item.duration) || 0);
                    const statusText = item.statusAfter || item.disposition || 'Pending';
                    const subStatusText = item.subStatus || '-';
                    const amt = parseAmount(item.orderAmount);
                    
                    return (
                      <tr key={item.id}>
                        <td style={{ fontWeight: 600 }}>{clientName}</td>
                        <td>{clientPhone}</td>
                        <td>
                          <span className="badge" style={{ 
                            background: statusText === 'Order Placed' ? 'rgba(16, 185, 129, 0.1)' : 'rgba(255,255,255,0.05)',
                            color: statusText === 'Order Placed' ? '#10b981' : 'var(--text)'
                          }}>
                            {statusText}
                          </span>
                        </td>
                        <td style={{ color: 'var(--text-muted)' }}>{subStatusText}</td>
                        <td>{durationStr}</td>
                        <td style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{formattedDate}</td>
                        <td style={{ fontWeight: 600, color: amt > 0 ? '#10b981' : 'var(--text-muted)' }}>
                          {amt > 0 ? `₹${amt}` : '-'}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Row 4: Product sales and Leakage analysis */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px', marginBottom: '20px' }}>
        
        {/* Top selling products bar matrix */}
        <div className="glass-panel" style={{ padding: '16px' }}>
          <h3 className="section-title" style={{ marginTop: 0, marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <BarChart3 size={14} style={{ color: 'var(--primary)' }} /> Top Selling Catalog Mix
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {loading ? (
              <span style={{ color: 'var(--text-muted)', fontSize: '12px' }}>Loading catalog metrics...</span>
            ) : productSalesData.length === 0 ? (
              <span style={{ color: 'var(--text-muted)', fontSize: '12px' }}>No sales data.</span>
            ) : (
              productSalesData.map((prod, idx) => {
                const totalRevAcrossAll = productSalesData.reduce((sum, p) => sum + p.revenue, 0) || 1;
                const percentage = Math.round((prod.revenue / totalRevAcrossAll) * 100);

                return (
                  <div key={idx} style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', fontWeight: 500 }}>
                      <span>{prod.name}</span>
                      <span style={{ color: 'var(--text-muted)' }}>
                        {prod.units} Sold (₹{prod.revenue.toLocaleString()})
                      </span>
                    </div>
                    <div style={{ background: 'rgba(255,255,255,0.06)', borderRadius: '4px', height: '6px', overflow: 'hidden' }}>
                      <div style={{ background: 'var(--primary)', width: `${percentage}%`, height: '100%' }} />
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Lead Funnel Leakage status analysis */}
        <div className="glass-panel" style={{ padding: '16px' }}>
          <h3 className="section-title" style={{ marginTop: 0, marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <AlertTriangle size={14} style={{ color: 'var(--danger)' }} /> Lead Leakage & Dispositions
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {loading ? (
              <span style={{ color: 'var(--text-muted)', fontSize: '12px' }}>Loading leakage analysis...</span>
            ) : (
              Object.entries(leadLeakageData).map(([key, count], idx) => {
                const label = key === 'ringing' ? 'Ringing / Missed Call' :
                              key === 'busy' ? 'Busy / Unreachable' :
                              key === 'off' ? 'Switched Off' :
                              key === 'notInterested' ? 'Not Interested / Rejected' :
                              'Other Failures';
                
                const totalLeakage = Object.values(leadLeakageData).reduce((sum, v) => sum + v, 0) || 1;
                const percentage = Math.round((count / totalLeakage) * 100);

                return (
                  <div key={idx} style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', fontWeight: 500 }}>
                      <span>{label}</span>
                      <span style={{ color: 'var(--text-muted)' }}>{count} Attempts ({percentage}%)</span>
                    </div>
                    <div style={{ background: 'rgba(255,255,255,0.06)', borderRadius: '4px', height: '6px', overflow: 'hidden' }}>
                      <div style={{ background: 'var(--danger)', width: `${percentage}%`, height: '100%' }} />
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

      </div>
    </>
  );
}
