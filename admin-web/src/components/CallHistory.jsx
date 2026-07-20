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
import { AlertTriangle, CheckSquare, Search, Award, Clock, Activity, Flag } from 'lucide-react';

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
  const [selectedType, setSelectedType] = useState('all'); // 'all', 'call', 'visit'
  const [auditStatusFilter, setAuditStatusFilter] = useState('all'); // 'all', 'flagged', 'reviewed', 'unreviewed'
  const [searchQuery, setSearchQuery] = useState('');

  // QA & Coaching Row Expansion
  const [expandedRowId, setExpandedRowId] = useState(null);
  const [auditStateMap, setAuditStateMap] = useState({}); // interactionId -> { reviewStatus, isFlagged, flagReason, coachingNotes }
  const [savingAuditId, setSavingAuditId] = useState(null);

  // Dynamic Lead Details Resolution Cache (avoids SKU bloat/unnecessary document reads)
  const [leadsCache, setLeadsCache] = useState({}); // leadId -> { name, phoneNumber }
  const fetchedIdsRef = useRef(new Set());

  // Fetch telecallers to populate dropdown filter
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
        console.error("Error fetching telecallers list:", err);
      }
    };
    fetchCallers();
  }, [orgId]);

  // Fetch products catalog list for combo lookup mapping
  useEffect(() => {
    if (!orgId) return;
    const q = query(collection(db, "organizations", orgId, "products"));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const prods = [];
      snapshot.forEach(docSnap => prods.push({ id: docSnap.id, ...docSnap.data() }));
      setProductsList(prods);
    }, (err) => {
      console.error("Error loading products for lookup in history:", err);
    });
    return () => unsubscribe();
  }, [orgId]);

  // Listen to filtered interactions (with limit(50) for high performance)
  useEffect(() => {
    if (!orgId) return;
    setLoading(true);
    let q = query(
      collection(db, 'organizations', orgId, 'interactions'),
      orderBy('timestamp', 'desc'),
      limit(50)
    );

    const constraints = [];

    if (selectedCaller) {
      constraints.push(where('telecallerId', '==', selectedCaller));
    }

    if (selectedType === 'visit') {
      constraints.push(where('isVisitLog', '==', true));
    } else if (selectedType === 'call') {
      constraints.push(where('isVisitLog', '==', false));
    }

    // Apply audit status filter directly to query
    if (auditStatusFilter === 'flagged') {
      constraints.push(where('isFlagged', '==', true));
    } else if (auditStatusFilter === 'reviewed') {
      constraints.push(where('reviewStatus', '==', 'reviewed'));
    } else if (auditStatusFilter === 'unreviewed') {
      constraints.push(where('reviewStatus', '==', 'unreviewed'));
    }

    q = query(
      collection(db, 'organizations', orgId, 'interactions'),
      ...constraints,
      orderBy('timestamp', 'desc'),
      limit(50)
    );

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const data = [];
      snapshot.forEach(doc => data.push({ id: doc.id, ...doc.data() }));
      setInteractions(data);
      setLastDoc(snapshot.docs[snapshot.docs.length - 1] || null);
      setHasMore(snapshot.docs.length === 50);
      setLoading(false);
    }, (error) => {
      console.error("Error listening to interactions:", error);
      setLoading(false);
    });

    return () => unsubscribe();
  }, [selectedCaller, selectedType, auditStatusFilter, orgId]);

  // Dynamically resolve lead details for loaded interactions (batch fetch chunks of 30)
  useEffect(() => {
    if (!orgId) return;
    const fetchLeadsForInteractions = async () => {
      const missingIds = interactions
        .map(item => item.leadId || item.lead_id)
        .filter(id => id && !fetchedIdsRef.current.has(id));

      if (missingIds.length === 0) return;

      const uniqueIds = Array.from(new Set(missingIds));
      // Mark as currently fetching/fetched immediately to prevent concurrent duplicate calls
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

          // Handle cases where lead documents do not exist in database
          chunk.forEach(id => {
            if (!newCache[id]) {
              newCache[id] = { name: 'Unknown Lead', phoneNumber: '-' };
            }
          });
        } catch (err) {
          console.error("Error fetching leads batch:", err);
        }
      }

      setLeadsCache(prev => ({ ...prev, ...newCache }));
    };

    if (interactions.length > 0) {
      fetchLeadsForInteractions();
    }
  }, [interactions, orgId]);

  // Load more interactions using startAfter cursor
  const loadMoreInteractions = async () => {
    if (!lastDoc || loading || !orgId) return;
    setLoading(true);

    let q = query(
      collection(db, 'organizations', orgId, 'interactions'),
      orderBy('timestamp', 'desc'),
      startAfter(lastDoc),
      limit(50)
    );

    const constraints = [];

    if (selectedCaller) {
      constraints.push(where('telecallerId', '==', selectedCaller));
    }

    if (selectedType === 'visit') {
      constraints.push(where('isVisitLog', '==', true));
    } else if (selectedType === 'call') {
      constraints.push(where('isVisitLog', '==', false));
    }

    if (auditStatusFilter === 'flagged') {
      constraints.push(where('isFlagged', '==', true));
    } else if (auditStatusFilter === 'reviewed') {
      constraints.push(where('reviewStatus', '==', 'reviewed'));
    } else if (auditStatusFilter === 'unreviewed') {
      constraints.push(where('reviewStatus', '==', 'unreviewed'));
    }

    q = query(
      collection(db, 'organizations', orgId, 'interactions'),
      ...constraints,
      orderBy('timestamp', 'desc'),
      startAfter(lastDoc),
      limit(50)
    );

    try {
      const snapshot = await getDocs(q);
      const data = [];
      snapshot.forEach(doc => data.push({ id: doc.id, ...doc.data() }));
      setInteractions(prev => [...prev, ...data]);
      setLastDoc(snapshot.docs[snapshot.docs.length - 1] || null);
      setHasMore(snapshot.docs.length === 50);
    } catch (err) {
      console.error("Error loading more interactions:", err);
    } finally {
      setLoading(false);
    }
  };

  // Local filtering for Search Query to avoid building duplicate composite indexes
  const filteredInteractions = interactions.filter(item => {
    if (!searchQuery.trim()) return true;
    const queryStr = searchQuery.toLowerCase();
    
    const leadId = item.leadId || item.lead_id;
    const leadInfo = leadId ? leadsCache[leadId] : null;
    const leadName = (leadInfo ? leadInfo.name : (item.leadName || item.name || '')).toLowerCase();
    const leadPhone = (leadInfo ? leadInfo.phoneNumber : (item.leadPhone || item.phoneNumber || item.phone || ''));
    const callerNameMatch = (item.callerName || '').toLowerCase().includes(queryStr);
    const notesMatch = (item.notes || '').toLowerCase().includes(queryStr);
    
    return leadName.includes(queryStr) || leadPhone.includes(queryStr) || callerNameMatch || notesMatch;
  });

  // Calculate coaching metrics from filtered items
  const totalCallsCount = filteredInteractions.length;
  const totalTalkSeconds = filteredInteractions.reduce((sum, item) => sum + (Number(item.duration) || 0), 0);
  
  const avgTalkDuration = totalCallsCount > 0 ? Math.round(totalTalkSeconds / totalCallsCount) : 0;
  
  const suspiciousCallsCount = filteredInteractions.filter(item => 
    item.isSuspiciousShortCall || (Number(item.duration) || 0) < 5
  ).length;
  const suspiciousRate = totalCallsCount > 0 ? Math.round((suspiciousCallsCount / totalCallsCount) * 100) : 0;

  const flaggedCallsCount = filteredInteractions.filter(item => item.isFlagged).length;

  const formatDuration = (seconds) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return `${h > 0 ? h + 'h ' : ''}${m}m ${s}s`;
  };

  const formatDate = (timestamp) => {
    if (!timestamp) return '-';
    const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
    return date.toLocaleString();
  };

  // Manage expandable row audit inputs
  const getAuditData = (id, originalItem) => {
    if (auditStateMap[id]) return auditStateMap[id];
    return {
      reviewStatus: originalItem.reviewStatus || 'unreviewed',
      isFlagged: originalItem.isFlagged || false,
      flagReason: originalItem.flagReason || 'Fake Call',
      coachingNotes: originalItem.coachingNotes || ''
    };
  };

  const updateAuditField = (id, field, value, originalItem) => {
    const current = getAuditData(id, originalItem);
    setAuditStateMap(prev => ({
      ...prev,
      [id]: {
        ...current,
        [field]: value
      }
    }));
  };

  const handleSaveAudit = async (id, originalItem) => {
    setSavingAuditId(id);
    const data = getAuditData(id, originalItem);
    
    try {
      const docRef = doc(db, 'organizations', orgId, 'interactions', id);
      await updateDoc(docRef, {
        reviewStatus: data.reviewStatus,
        isFlagged: data.isFlagged,
        flagReason: data.isFlagged ? data.flagReason : '',
        coachingNotes: data.coachingNotes,
        reviewedAt: new Date().toISOString()
      });
      
      // Close row on successful save
      setExpandedRowId(null);
    } catch (err) {
      console.error("Error saving QA audit:", err);
      alert("Failed to save audit: " + err.message);
    } finally {
      setSavingAuditId(null);
    }
  };

  const handleSpeedChange = (e, audioId) => {
    const player = document.getElementById(audioId);
    if (player) {
      player.playbackRate = parseFloat(e.target.value);
    }
  };

  const getDispositionBadge = (rawDisp, item = {}) => {
    const disp = rawDisp || 'UNKNOWN';
    let badgeHtml = null;
    if (disp === 'Order Placed' || disp === 'Converted' || disp === 'Visited' || disp === 'INTERESTED') {
      badgeHtml = <span className="badge badge-success">{disp}</span>;
    } else if (disp === 'Follow-up' || disp === 'Visit Scheduled') {
      badgeHtml = <span className="badge badge-warning">{disp}</span>;
    } else if (disp === 'Product Inquiry Only' || disp === 'Warm Lead') {
      badgeHtml = <span className="badge badge-info">{disp}</span>;
    } else if (disp === 'Call Not Answered' || disp === 'No Answer' || disp === 'Busy') {
      badgeHtml = <span className="badge badge-danger">{disp}</span>;
    } else if (disp === 'Not Interested' || disp === 'Invalid' || disp === 'Invalid/Wrong Number' || disp === 'NOT_INTERESTED' || disp === 'Rejected') {
      badgeHtml = <span className="badge badge-neutral">{disp}</span>;
    } else {
      badgeHtml = <span className="badge" style={{ background: 'rgba(255,255,255,0.06)', color: 'var(--text-muted)' }}>{disp}</span>;
    }

    const matchingProduct = productsList.find(p => p.name === item.product);
    const isCombo = matchingProduct && matchingProduct.type === 'combo';
    
    const subProductNames = isCombo && matchingProduct.bundledProducts
      ? matchingProduct.bundledProducts.map(bundledItem => {
          const pr = productsList.find(prod => prod.id === bundledItem.productId);
          return pr ? `${bundledItem.quantity} x ${pr.emojiIcon} ${pr.name}` : null;
        }).filter(Boolean).join(" + ")
      : null;

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
        {item.isFlagged && (
          <span style={{ fontSize: '10px', color: '#ffffff', background: 'var(--danger)', padding: '1px 6px', borderRadius: '3px', fontWeight: 'bold', marginTop: '2px', display: 'flex', alignItems: 'center', gap: '3px' }}>
            <Flag size={10} /> Flagged: {item.flagReason || 'Unprofessional'}
          </span>
        )}
        {isCombo && subProductNames && (
          <div style={{ fontSize: '10px', color: 'var(--primary)', fontWeight: 500, marginTop: '2px' }}>
            🎁 Pack: {subProductNames}
          </div>
        )}
      </div>
    );
  };

  const getDurationLabel = (sec) => {
    const seconds = Number(sec) || 0;
    if (seconds === 0) return <span style={{ color: 'var(--text-muted)' }}>0s</span>;
    if (seconds < 10) return <span style={{ color: 'var(--danger)', fontWeight: 500 }}>🔴 {seconds}s (Short)</span>;
    if (seconds < 90) return <span style={{ color: '#f59e0b' }}>🟡 {seconds}s (Intro)</span>;
    
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return (
      <span style={{ color: 'var(--secondary)', fontWeight: 500 }}>
        🟢 {minutes}m {remainingSeconds}s (Productive)
      </span>
    );
  };

  return (
    <>
      <div className="page-header">
        <div className="page-title-group">
          <h2 className="page-title">QA & Call Audit Center</h2>
          <p className="page-subtitle">Coaching and compliance workspace for telecaller performance.</p>
        </div>
        
        {/* Dynamic Filters Bar */}
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ position: 'relative', width: '180px' }}>
            <Search style={{ position: 'absolute', top: '8px', left: '8px', color: 'var(--text-muted)' }} size={13} />
            <input 
              type="text" 
              className="input-field" 
              style={{ paddingLeft: '26px', height: '30px', fontSize: '12px' }}
              placeholder="Search Lead or Phone..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>

          <select 
            className="input-field" 
            style={{ width: '130px', height: '30px', padding: '0 8px', fontSize: '12px' }}
            value={selectedCaller}
            onChange={(e) => setSelectedCaller(e.target.value)}
          >
            <option value="">All Callers</option>
            {telecallers.map(tc => (
              <option key={tc.id} value={tc.id}>{tc.name}</option>
            ))}
          </select>

          <select 
            className="input-field" 
            style={{ width: '110px', height: '30px', padding: '0 8px', fontSize: '12px' }}
            value={selectedType}
            onChange={(e) => setSelectedType(e.target.value)}
          >
            <option value="all">All Types</option>
            <option value="call">Calls Only</option>
            <option value="visit">Visits Only</option>
          </select>

          <select 
            className="input-field" 
            style={{ width: '130px', height: '30px', padding: '0 8px', fontSize: '12px' }}
            value={auditStatusFilter}
            onChange={(e) => setAuditStatusFilter(e.target.value)}
          >
            <option value="all">All Audit Status</option>
            <option value="flagged">Flagged Only</option>
            <option value="reviewed">Reviewed Only</option>
            <option value="unreviewed">Unreviewed Only</option>
          </select>
        </div>
      </div>

      {/* Audit QA KPIs Header Grid */}
      <div className="stats-grid" style={{ marginBottom: '20px' }}>
        <div className="stat-card glass-panel" style={{ borderTopColor: '#6366f1' }}>
          <span className="stat-title" style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Activity size={12} /> Total Talk Time
          </span>
          <span className="stat-value" style={{ fontSize: '20px' }}>{formatDuration(totalTalkSeconds)}</span>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: 'var(--secondary)' }}>
          <span className="stat-title" style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Clock size={12} /> Avg Call Duration
          </span>
          <span className="stat-value" style={{ fontSize: '20px' }}>{avgTalkDuration}s</span>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: '#ef4444' }}>
          <span className="stat-title" style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <AlertTriangle size={12} style={{ color: 'var(--danger)' }} /> Suspicious Rate
          </span>
          <span className="stat-value" style={{ fontSize: '20px', color: suspiciousRate > 20 ? 'var(--danger)' : 'var(--text-main)' }}>
            {suspiciousRate}%
          </span>
        </div>
        <div className="stat-card glass-panel" style={{ borderTopColor: 'var(--danger)' }}>
          <span className="stat-title" style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Flag size={12} style={{ color: 'var(--danger)' }} /> Flagged Logs
          </span>
          <span className="stat-value" style={{ fontSize: '20px', color: flaggedCallsCount > 0 ? 'var(--danger)' : 'var(--text-main)' }}>
            {flaggedCallsCount}
          </span>
        </div>
      </div>

      <div className="glass-panel" style={{ padding: '16px' }}>
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th style={{ width: '40px' }}></th>
                <th>Time</th>
                <th>Lead / Phone</th>
                <th>Telecaller</th>
                <th>Duration</th>
                <th>Disposition</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {filteredInteractions.length === 0 ? (
                <tr>
                  <td colSpan="7" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>
                    {loading ? "Loading history..." : "No call logs found."}
                  </td>
                </tr>
              ) : (
                filteredInteractions.map((interaction) => {
                  const isExpanded = expandedRowId === interaction.id;
                  const auditData = getAuditData(interaction.id, interaction);

                  // Extract lead details dynamically resolved in the cache
                  const leadId = interaction.leadId || interaction.lead_id;
                  const leadInfo = leadId ? leadsCache[leadId] : null;
                  
                  const leadName = leadInfo ? leadInfo.name : (interaction.leadName || interaction.name || 'Unknown Lead');
                  const leadPhone = leadInfo ? leadInfo.phoneNumber : (interaction.leadPhone || interaction.phoneNumber || interaction.phone || '-');
                  
                  return (
                    <React.Fragment key={interaction.id}>
                      <tr 
                        style={{ cursor: 'pointer', background: isExpanded ? 'rgba(255,255,255,0.02)' : 'transparent' }}
                        onClick={() => setExpandedRowId(isExpanded ? null : interaction.id)}
                      >
                        <td style={{ textAlign: 'center' }}>
                          <span style={{ fontSize: '10px', color: 'var(--text-muted)' }}>
                            {isExpanded ? '▼' : '▶'}
                          </span>
                        </td>
                        <td style={{ color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>{formatDate(interaction.timestamp)}</td>
                        <td>
                          <div style={{ display: 'flex', flexDirection: 'column' }}>
                            <span style={{ fontWeight: 500 }}>{leadName}</span>
                            <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{leadPhone}</span>
                          </div>
                        </td>
                        <td style={{ fontWeight: 500 }}>{interaction.callerName || interaction.callerId || interaction.telecallerId || 'Unknown'}</td>
                        <td>{getDurationLabel(interaction.duration)}</td>
                        <td>{getDispositionBadge(interaction.statusAfter || interaction.disposition, interaction)}</td>
                        <td>
                          {interaction.reviewStatus === 'reviewed' ? (
                            <span className="badge badge-success" style={{ padding: '2px 6px', fontSize: '10px' }}>Reviewed</span>
                          ) : (
                            <span className="badge badge-neutral" style={{ padding: '2px 6px', fontSize: '10px' }}>Pending QA</span>
                          )}
                        </td>
                      </tr>

                      {/* Expandable QA detail panel row */}
                      {isExpanded && (
                        <tr>
                          <td colSpan="7" style={{ background: 'rgba(15, 23, 42, 0.4)', padding: '14px', borderBottom: '1px solid var(--surface-border)' }}>
                            <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
                              
                              {/* Left Panel: Audio recording audit */}
                              <div style={{ flex: '1 1 300px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                <div style={{ fontWeight: 600, fontSize: '12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                                  <Award size={14} style={{ color: 'var(--primary)' }} /> Call Audio Log
                                </div>
                                <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                                  Auditing recorded conversation session file:
                                </div>
                                
                                {/* Audio Player renders conditionally only if recording exists */}
                                {interaction.recordingUrl || interaction.audioUrl ? (
                                  <div style={{ 
                                    background: 'rgba(15, 23, 42, 0.6)', 
                                    padding: '10px', 
                                    borderRadius: '6px', 
                                    border: '1px solid var(--surface-border)',
                                    marginTop: '4px'
                                  }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                      <audio 
                                        id={`audio-${interaction.id}`} 
                                        src={interaction.recordingUrl || interaction.audioUrl} 
                                        controls 
                                        style={{ height: '30px', flex: 1 }} 
                                      />
                                      <select 
                                        className="input-field" 
                                        style={{ width: '80px', height: '30px', padding: '0 4px', fontSize: '11px' }}
                                        onChange={(e) => handleSpeedChange(e, `audio-${interaction.id}`)}
                                        defaultValue="1.0"
                                      >
                                        <option value="1.0">1.0x Speed</option>
                                        <option value="1.25">1.25x Speed</option>
                                        <option value="1.5">1.5x Speed</option>
                                        <option value="2.0">2.0x Speed</option>
                                      </select>
                                    </div>
                                  </div>
                                ) : (
                                  <div style={{ 
                                    padding: '8px', 
                                    background: 'rgba(255, 255, 255, 0.02)', 
                                    borderRadius: '6px', 
                                    border: '1px solid var(--surface-border)', 
                                    fontSize: '11px', 
                                    color: 'var(--text-muted)', 
                                    textAlign: 'center',
                                    marginTop: '4px'
                                  }}>
                                    🔇 Call recording not available for this session
                                  </div>
                                )}

                                <div style={{ marginTop: '8px' }}>
                                  <div style={{ fontWeight: 600, fontSize: '11px', color: 'var(--text-muted)', marginBottom: '4px' }}>
                                    Telecaller Interaction Notes:
                                  </div>
                                  <div style={{ 
                                    background: 'rgba(15, 23, 42, 0.4)', 
                                    padding: '8px', 
                                    borderRadius: '6px', 
                                    border: '1px solid var(--surface-border)',
                                    fontSize: '12px',
                                    color: 'var(--text-main)',
                                    whiteSpace: 'pre-wrap',
                                    lineHeight: '1.4'
                                  }}>
                                    {interaction.notes || 'No caller notes submitted.'}
                                  </div>
                                </div>
                              </div>

                              {/* Right Panel: Compliance flagging and coaching feedback */}
                              <div style={{ flex: '1 1 300px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                <div style={{ fontWeight: 600, fontSize: '12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                                  <CheckSquare size={14} style={{ color: 'var(--primary)' }} /> Audit Feedback & Compliance
                                </div>

                                <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
                                  {/* Review Status Toggles */}
                                  <div style={{ flex: 1, minWidth: '130px' }}>
                                    <label className="form-label" style={{ fontSize: '11px' }}>QA Audit Status</label>
                                    <select 
                                      className="input-field" 
                                      style={{ height: '30px', fontSize: '12px', padding: '0 8px' }}
                                      value={auditData.reviewStatus}
                                      onChange={(e) => updateAuditField(interaction.id, 'reviewStatus', e.target.value, interaction)}
                                    >
                                      <option value="unreviewed">Pending QA</option>
                                      <option value="reviewed">Reviewed & Passed</option>
                                    </select>
                                  </div>

                                  {/* Fraud Flagging toggles */}
                                  <div style={{ flex: 1, minWidth: '130px' }}>
                                    <label className="form-label" style={{ fontSize: '11px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                                      <Flag size={11} style={{ color: 'var(--danger)' }} /> Flag Compliance Issue?
                                    </label>
                                    <div style={{ display: 'flex', gap: '6px', alignItems: 'center', height: '30px' }}>
                                      <input 
                                        type="checkbox" 
                                        checked={auditData.isFlagged}
                                        onChange={(e) => updateAuditField(interaction.id, 'isFlagged', e.target.checked, interaction)}
                                        style={{ height: '16px', width: '16px', cursor: 'pointer' }}
                                      />
                                      <span style={{ fontSize: '12px' }}>Flag for Review</span>
                                    </div>
                                  </div>

                                  {auditData.isFlagged && (
                                    <div style={{ width: '100%' }}>
                                      <label className="form-label" style={{ fontSize: '11px' }}>Compliance Flag Reason</label>
                                      <select 
                                        className="input-field" 
                                        style={{ height: '30px', fontSize: '12px', padding: '0 8px' }}
                                        value={auditData.flagReason}
                                        onChange={(e) => updateAuditField(interaction.id, 'flagReason', e.target.value, interaction)}
                                      >
                                        <option value="Fake Call">Fake Call (Dummy Log)</option>
                                        <option value="Bad Pitch">Bad Pitch (Wrong Details)</option>
                                        <option value="Unprofessional Language">Unprofessional Language</option>
                                        <option value="Speed Dial Fraud">Speed Dial / No Conversation</option>
                                        <option value="Other">Other / Policy Violation</option>
                                      </select>
                                    </div>
                                  )}
                                </div>

                                {/* Coaching Feedback Notes */}
                                <div>
                                  <label className="form-label" style={{ fontSize: '11px' }}>Admin Coaching Feedback</label>
                                  <textarea 
                                    className="input-field" 
                                    rows="2" 
                                    style={{ fontSize: '12px', padding: '6px 10px' }}
                                    placeholder="Write feedback for the telecaller..."
                                    value={auditData.coachingNotes}
                                    onChange={(e) => updateAuditField(interaction.id, 'coachingNotes', e.target.value, interaction)}
                                  />
                                </div>

                                {/* Action Buttons */}
                                <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '4px' }}>
                                  <button 
                                    className="btn-primary" 
                                    style={{ height: '30px', fontSize: '12px', padding: '0 14px' }}
                                    onClick={() => handleSaveAudit(interaction.id, interaction)}
                                    disabled={savingAuditId === interaction.id}
                                  >
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

        {/* Cursor Pagination Trigger */}
        {hasMore && filteredInteractions.length >= 50 && (
          <div className="load-more-container">
            <button 
              className="btn-secondary" 
              onClick={loadMoreInteractions} 
              disabled={loading}
              style={{ fontSize: '12px', padding: '4px 12px' }}
            >
              {loading ? "Loading..." : "Load More"}
            </button>
          </div>
        )}
      </div>
    </>
  );
}
