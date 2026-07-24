import React, { useState, useEffect } from 'react';
import { db, auth } from '../firebase';
import { useAuth } from '../AuthContext';
import { collection, query, where, orderBy, limit, startAfter, onSnapshot, runTransaction, doc, updateDoc, arrayUnion } from 'firebase/firestore';
import { Package, Truck, CheckCircle, XCircle, Search, AlertOctagon, Lock, Unlock, ChevronRight, ChevronLeft, QrCode, Settings, RotateCcw } from 'lucide-react';

const TABS = [
  { id: 'pending', label: 'Pending Pack', icon: Package, statusQuery: ['Order Placed'] },
  { id: 'awaiting_payment', label: 'Upcoming (Unpaid)', icon: AlertOctagon, statusQuery: ['Order Placed'] },
  { id: 'dispatched', label: 'Dispatched', icon: Truck, statusQuery: ['Dispatched'] },
  { id: 'delivered', label: 'Delivered', icon: CheckCircle, statusQuery: ['Delivered'] },
  { id: 'returned', label: 'RTO / Returned', icon: XCircle, statusQuery: ['RTO'] },
];

const COURIERS = [
  { name: 'Delhivery', regex: /^\d{13,14}$/, example: '13 or 14 digits' },
  { name: 'Bluedart', regex: /^\d{11}$/, example: '11 digits' },
  { name: 'Shiprocket', regex: /^SR\d{7,10}$/, example: 'SR followed by 7-10 digits' },
  { name: 'EcomExpress', regex: /^\d{10}$/, example: '10 digits' },
  { name: 'India Post', regex: /^[A-Za-z]{2}\d{9}[A-Za-z]{2}$/, example: 'e.g. EB123456789IN' },
  { name: 'Other', regex: /.+/, example: 'Any format' }
];

export default function DispatchCenter() {
  const { orgId } = useAuth();
  const [activeTab, setActiveTab] = useState('pending');
  const [leads, setLeads] = useState([]);
  const [loading, setLoading] = useState(true);
  const [lastVisible, setLastVisible] = useState(null);
  const [pageCursors, setPageCursors] = useState([]);
  const [pageNumber, setPageNumber] = useState(1);
  const [searchQuery, setSearchQuery] = useState('');

  // Modal State
  const [selectedLead, setSelectedLead] = useState(null);
  const [selectedManageLead, setSelectedManageLead] = useState(null);
  const [manageAction, setManageAction] = useState(null); // 'delivered' | 'rto' | 'undo'
  const [rtoReason, setRtoReason] = useState('');
  const [courier, setCourier] = useState(COURIERS[0].name);
  const [awb, setAwb] = useState('');
  const [awbError, setAwbError] = useState('');
  const [isProcessingAction, setIsProcessingAction] = useState(false);
  const [products, setProducts] = useState([]);

  useEffect(() => {
    if (!orgId) return;
    const unsub = onSnapshot(collection(db, "organizations", orgId, "products"), (snap) => {
      const p = [];
      snap.forEach(doc => p.push({ id: doc.id, ...doc.data() }));
      setProducts(p);
    });
    return () => unsub();
  }, [orgId]);

  useEffect(() => {
    if (!orgId) return;
    setPageCursors([]);
    fetchLeads(activeTab, null);
  }, [activeTab, orgId]);

  const fetchLeads = (tabId, afterDoc = null) => {
    if (!orgId) return;
    setLoading(true);
    const tabConfig = TABS.find(t => t.id === tabId);
    
    let q = query(
      collection(db, 'organizations', orgId, 'leads'),
      where('status', 'in', tabConfig.statusQuery),
      orderBy('status'),
      limit(50)
    );

    if (afterDoc) {
      q = query(q, startAfter(afterDoc));
    }

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const fetchedLeads = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      setLeads(fetchedLeads);
      if (snapshot.docs.length > 0) {
        setLastVisible(snapshot.docs[snapshot.docs.length - 1]);
      } else {
        setLastVisible(null);
      }
      setLoading(false);
    }, (error) => {
      console.error("Error fetching dispatch leads:", error);
      setLoading(false);
    });

    return unsubscribe;
  };

  const handleNextPage = () => {
    if (lastVisible) {
      setPageCursors(prev => [...prev, lastVisible]);
      fetchLeads(activeTab, lastVisible);
      setPageNumber(prev => prev + 1);
    }
  };

  const handlePrevPage = () => {
    if (pageCursors.length > 0) {
      const newCursors = [...pageCursors];
      newCursors.pop(); // remove current page cursor
      const prevCursor = newCursors.length > 0 ? newCursors[newCursors.length - 1] : null;
      setPageCursors(newCursors);
      fetchLeads(activeTab, prevCursor);
      setPageNumber(prev => prev - 1);
    }
  };

  const handleLockOrder = async (lead) => {
    if (!orgId) return;
    const leadRef = doc(db, 'organizations', orgId, 'leads', lead.id);
    const currentUserUid = auth.currentUser?.uid;

    if (!currentUserUid) return alert("You must be logged in.");

    try {
      setIsProcessingAction(true);
      await runTransaction(db, async (transaction) => {
        const leadDoc = await transaction.get(leadRef);
        if (!leadDoc.exists()) throw "Document does not exist!";

        const data = leadDoc.data();
        if (data.lockedByUid && data.lockedByUid !== currentUserUid) {
          throw `Already claimed by another admin.`;
        }

        transaction.update(leadRef, {
          dispatchStatus: 'Processing',
          lockedByUid: currentUserUid,
          dispatchHistory: arrayUnion({
            action: 'Locked',
            by: currentUserUid,
            timestamp: new Date().toISOString()
          })
        });
      });
      alert("Order Locked for Packing!");
    } catch (e) {
      alert(`Lock failed: ${e}`);
    } finally {
      setIsProcessingAction(false);
    }
  };

  const handleUnlockOrder = async (lead) => {
    const isOwner = lead.lockedByUid === auth.currentUser?.uid;
    if (!isOwner) {
      const confirm = window.confirm("⚠️ This order is locked by another admin. Do you want to Force Unlock it?");
      if (!confirm) return;
    }
    if (!orgId) return;
    const leadRef = doc(db, 'organizations', orgId, 'leads', lead.id);
    await updateDoc(leadRef, {
      dispatchStatus: 'Pending',
      lockedByUid: null,
      dispatchHistory: arrayUnion({
        action: isOwner ? 'Unlocked' : 'Force Unlocked',
        by: auth.currentUser?.uid,
        timestamp: new Date().toISOString()
      })
    });
  };

  const openDispatchModal = (lead) => {
    if (lead.urgentCancelRequest) {
      alert("HALT: Telecaller requested cancellation. Cannot dispatch.");
      return;
    }
    setSelectedLead(lead);
    setAwb('');
    setAwbError('');
  };

  const validateAwb = (partnerName, number) => {
    const cleanNumber = number.trim();
    const partner = COURIERS.find(c => c.name === partnerName);
    if (!partner) return true;
    if (!partner.regex.test(cleanNumber)) {
      setAwbError(`Invalid format for ${partnerName}. Expected: ${partner.example}`);
      return false;
    }
    setAwbError('');
    return true;
  };

  const handleConfirmDispatch = async () => {
    const cleanAwb = awb.trim();
    if (!validateAwb(courier, cleanAwb)) return;

    try {
      setIsProcessingAction(true);
      const leadRef = doc(db, 'organizations', orgId, 'leads', selectedLead.id);
      
      await runTransaction(db, async (transaction) => {
        const leadDoc = await transaction.get(leadRef);
        if (!leadDoc.exists()) throw "Document does not exist!";
        const data = leadDoc.data();

        if (data.urgentCancelRequest) {
          throw "Halt: Telecaller requested cancellation. Cannot dispatch.";
        }

        if (data.paymentMethod === 'Prepaid' && data.paymentStatus !== 'Paid' && data.paymentStatus !== 'Payment Received') {
          throw "SECURITY HALT: Order is Prepaid but Payment is not verified! Cannot dispatch.";
        }
        
        const preDispatchStatus = data.status || 'Order Placed';

        transaction.update(leadRef, {
          status: 'Dispatched',
          dispatchStatus: 'Dispatched',
          preDispatchStatus: preDispatchStatus,
          courierPartner: courier,
          trackingNumber: cleanAwb,
          dispatchedAt: new Date().toISOString(),
          packedBy: auth.currentUser?.uid,
          lockedByUid: null, // release lock
          dispatchHistory: arrayUnion({
            action: 'Dispatched',
            courier: courier,
            awb: cleanAwb,
            by: auth.currentUser?.uid,
            timestamp: new Date().toISOString()
          })
        });
      });
      
      setSelectedLead(null);
    } catch (error) {
      console.error("Error dispatching:", error);
      alert(`Failed to dispatch order: ${error}`);
    } finally {
      setIsProcessingAction(false);
    }
  };

  const handleUpdateFinalStatus = async () => {
    if (!selectedManageLead || !manageAction) return;
    if (manageAction === 'rto' && !rtoReason) return alert("Please select an RTO Reason.");
    
    // Double confirmation for safety
    if (manageAction === 'delivered') {
      const confirm = window.confirm("Are you sure this order is safely DELIVERED? This action is final.");
      if (!confirm) return;
    }
    if (manageAction === 'rto') {
      const confirm = window.confirm("Are you sure you want to mark this as RTO? This action is final.");
      if (!confirm) return;
    }

    if (!orgId) return;
    const leadRef = doc(db, 'organizations', orgId, 'leads', selectedManageLead.id);
    
    try {
      setIsProcessingAction(true);

      if (manageAction === 'undo') {
        const previousAwb = selectedManageLead.trackingNumber || 'Unknown';
        const previousCourier = selectedManageLead.courierPartner || 'Unknown';
        const restoreStatus = selectedManageLead.preDispatchStatus || 'Order Placed';

        await updateDoc(leadRef, {
          status: restoreStatus,
          dispatchStatus: 'Pending',
          courierPartner: null,
          trackingNumber: null,
          dispatchedAt: null,
          packedBy: null,
          dispatchHistory: arrayUnion({
            action: 'Reverted Dispatch to Pending',
            previousAwb: previousAwb,
            previousCourier: previousCourier,
            restoredStatus: restoreStatus,
            by: auth.currentUser?.uid,
            timestamp: new Date().toISOString()
          })
        });
      } else {
        // Use Transaction for Delivered/RTO to prevent concurrency bugs
        await runTransaction(db, async (transaction) => {
          const leadDoc = await transaction.get(leadRef);
          if (!leadDoc.exists()) throw "Document does not exist!";
          const data = leadDoc.data();
          
          if (data.status === 'Delivered' || data.status === 'RTO') {
            throw `Order is already marked as ${data.status} by another admin.`;
          }

          if (manageAction === 'delivered') {
            // Retention Engine: Calculate exhaustion date
            let shortestConsumptionDays = 30;
            try {
              if (data.baseProductsBreakdown) {
                // E.g. "sku_1:2,sku_2:1"
                const parts = data.baseProductsBreakdown.split(',').filter(Boolean);
                let minDays = Infinity;
                for (const part of parts) {
                  const [sku, qty] = part.split(':');
                  const prod = products.find(p => p.id === sku);
                  const pDays = prod?.consumptionDays ? Number(prod.consumptionDays) : 30;
                  const q = Number(qty) || 1;
                  const totalDays = pDays * q;
                  if (totalDays < minDays) minDays = totalDays;
                }
                if (minDays !== Infinity) shortestConsumptionDays = minDays;
              }
            } catch (e) {
              console.error("Error calculating exhaustion date", e);
            }

            const today = new Date();
            const deliveredIso = today.toISOString();
            today.setDate(today.getDate() + shortestConsumptionDays);
            const exhaustionIso = today.toISOString();

            transaction.update(leadRef, {
              status: 'Delivered',
              dispatchStatus: 'Delivered',
              deliveredAt: deliveredIso,
              exhaustionDate: exhaustionIso,
              dispatchHistory: arrayUnion({
                action: 'Marked Delivered',
                by: auth.currentUser?.uid,
                timestamp: deliveredIso
              })
            });
          } else if (manageAction === 'rto') {
            transaction.update(leadRef, {
              status: 'RTO',
              dispatchStatus: 'Returned',
              rtoReason: rtoReason,
              returnedAt: new Date().toISOString(),
              dispatchHistory: arrayUnion({
                action: 'Marked RTO',
                reason: rtoReason,
                by: auth.currentUser?.uid,
                timestamp: new Date().toISOString()
              })
            });
          }
        });
      }

      // Reset Modal State
      setSelectedManageLead(null);
      setManageAction(null);
      setRtoReason('');
    } catch (error) {
      console.error("Error updating final status:", error);
      alert(`Failed to update: ${error}`);
    } finally {
      setIsProcessingAction(false);
    }
  };

  return (
    <div className="reports-container fade-in">
      <div className="reports-header" style={{ marginBottom: '24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 className="page-title">Dispatch Center</h1>
          <p className="page-subtitle">Fulfillment & Logistics Engine (God-Level)</p>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <div style={{ display: 'flex', background: 'rgba(255, 255, 255, 0.05)', borderRadius: '24px', padding: '4px', border: '1px solid rgba(255,255,255,0.08)' }}>
            <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
              <Search size={14} style={{ position: 'absolute', left: '12px', color: 'var(--text-muted)' }} />
              <input 
                type="text" 
                placeholder="Search Name or Phone..." 
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
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
          </div>
          <button className="btn-secondary" style={{ borderRadius: '24px', padding: '0 16px', fontSize: '13px', height: '40px', background: 'rgba(255,255,255,0.05)' }} onClick={() => alert('Barcode scanner ready to listen...')}>
            <QrCode size={14} /> Scan
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="tabs" style={{ display: 'flex', gap: '8px', marginBottom: '20px' }}>
        {TABS.map(tab => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => { setActiveTab(tab.id); setPageNumber(1); }}
              className={`tab-btn ${isActive ? 'active' : ''}`}
              style={{
                display: 'flex', alignItems: 'center', gap: '6px',
                padding: '8px 20px', borderRadius: '100px', fontSize: '13px', fontWeight: 600,
                background: isActive ? 'var(--primary)' : 'rgba(255,255,255,0.05)',
                color: isActive ? '#000000' : 'var(--text-main)',
                border: '1px solid', borderColor: isActive ? 'var(--primary)' : 'rgba(255,255,255,0.1)',
                cursor: 'pointer', transition: 'all 0.2s'
              }}
            >
              <Icon size={14} />
              {tab.label}
            </button>
          )
        })}
      </div>

      {/* Data Table */}
      <div className="glass-panel" style={{ padding: '0', overflow: 'hidden' }}>
        {loading ? (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>Loading records...</div>
        ) : leads.length === 0 ? (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>No records found in this queue.</div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="leads-table">
              <thead>
                <tr>
                  <th style={{ padding: '12px 16px', textAlign: 'left', fontWeight: '600', fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: '#e4e4e7', background: 'var(--surface)', borderBottom: '1px solid var(--surface-border)' }}>CLIENT</th>
                  <th style={{ padding: '12px 16px', textAlign: 'left', fontWeight: '600', fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: '#e4e4e7', background: 'var(--surface)', borderBottom: '1px solid var(--surface-border)' }}>PRODUCT</th>
                  <th style={{ padding: '12px 16px', textAlign: 'left', fontWeight: '600', fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: '#e4e4e7', background: 'var(--surface)', borderBottom: '1px solid var(--surface-border)' }}>ADDRESS & PINCODE</th>
                  <th style={{ padding: '12px 16px', textAlign: 'left', fontWeight: '600', fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: '#e4e4e7', background: 'var(--surface)', borderBottom: '1px solid var(--surface-border)' }}>STATUS & QC</th>
                  <th style={{ padding: '12px 16px', textAlign: 'right', fontWeight: '600', fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: '#e4e4e7', background: 'var(--surface)', borderBottom: '1px solid var(--surface-border)' }}>ACTION</th>
                </tr>
              </thead>
              <tbody>
                {leads.filter(l => l.name?.toLowerCase().includes(searchQuery.toLowerCase()) || l.phone?.includes(searchQuery))
                 .filter(l => {
                   const isPrepaidUnpaid = l.paymentMethod === 'Prepaid' && l.paymentStatus !== 'Paid' && l.paymentStatus !== 'Payment Received';
                   if (activeTab === 'pending') return !isPrepaidUnpaid;
                   if (activeTab === 'awaiting_payment') return isPrepaidUnpaid;
                   return true;
                 })
                 .map(lead => {
                  const isLocked = !!lead.lockedByUid;
                  const lockedByMe = lead.lockedByUid === auth.currentUser?.uid;
                  const isUrgentCancel = lead.urgentCancelRequest;
                  const hasAddressIssue = !lead.address || (lead.pincode && lead.pincode.length < 6);

                  return (
                    <tr key={lead.id} style={{ background: isUrgentCancel ? 'rgba(239, 68, 68, 0.1)' : 'transparent' }}>
                      <td>
                        <div style={{ fontWeight: 500, fontSize: '13px', color: 'var(--text-main)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '180px' }}>{lead.name}</div>
                        <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>{lead.phone}</div>
                        <div style={{ marginTop: '6px' }}>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', padding: '2px 6px', background: 'rgba(59, 130, 246, 0.1)', border: '1px solid rgba(59, 130, 246, 0.2)', borderRadius: '4px', fontSize: '9px', color: '#60a5fa', fontWeight: 600, letterSpacing: '0.04em', textTransform: 'uppercase', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '170px' }}>
                            👤 {lead.assignedToName || 'UNKNOWN'}
                          </span>
                        </div>
                      </td>
                      <td>
                        <div style={{ display: 'inline-block', background: 'rgba(255, 255, 255, 0.04)', border: '1px solid rgba(255, 255, 255, 0.1)', color: 'var(--text-main)', padding: '2px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 500, whiteSpace: 'nowrap' }}>
                          {lead.product || 'Standard Kit'}
                        </div>
                      </td>
                      <td>
                        <div style={{ fontSize: '11px', color: 'var(--text-muted)', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', textOverflow: 'ellipsis', lineHeight: '1.4' }}>{lead.address || 'No Address Provided'}</div>
                        <div style={{ fontSize: '11px', fontWeight: 500, color: hasAddressIssue ? '#ef4444' : 'var(--text-main)', margin: '4px 0' }}>
                          {lead.pincode ? `PIN: ${lead.pincode}` : 'NO PINCODE'}
                        </div>
                        {/* Payment Gateway Visual Badges */}
                        <div>
                          {lead.paymentMethod === 'Prepaid' ? (
                            <span style={{ 
                              background: (lead.paymentStatus === 'Paid' || lead.paymentStatus === 'Payment Received') ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)', 
                              color: (lead.paymentStatus === 'Paid' || lead.paymentStatus === 'Payment Received') ? '#34d399' : '#f87171', 
                              border: (lead.paymentStatus === 'Paid' || lead.paymentStatus === 'Payment Received') ? '1px solid rgba(16,185,129,0.2)' : '1px solid rgba(239,68,68,0.2)',
                              padding: '2px 6px', borderRadius: '4px', fontSize: '10px', fontWeight: 600, letterSpacing: '0.02em' 
                            }}>
                              PREPAID {(lead.paymentStatus === 'Paid' || lead.paymentStatus === 'Payment Received') ? '✓ PAID' : '❌ UNPAID'}
                            </span>
                          ) : (
                            <span style={{ 
                              background: 'transparent', 
                              color: '#f59e0b', 
                              border: '1px solid rgba(245,158,11,0.2)',
                              padding: '2px 6px', borderRadius: '4px', fontSize: '10px', fontWeight: 600, letterSpacing: '0.02em' 
                            }}>
                              COD • COLLECT ₹{lead.orderAmount || 0}
                            </span>
                          )}
                        </div>
                      </td>
                      <td>
                        {isUrgentCancel && (
                          <div style={{ color: '#ef4444', fontSize: '11px', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '4px', whiteSpace: 'nowrap' }}>
                            <AlertOctagon size={14} /> CANCEL REQUEST
                          </div>
                        )}
                        {!isUrgentCancel && isLocked && (
                          <div style={{ color: lockedByMe ? '#10b981' : '#f59e0b', fontSize: '11px', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '4px', whiteSpace: 'nowrap' }}>
                            <Lock size={14} /> {lockedByMe ? 'Locked by You' : 'Locked by Another Admin'}
                          </div>
                        )}
                        {!isUrgentCancel && !isLocked && activeTab === 'pending' && (
                          <div style={{ color: 'var(--text-muted)', fontSize: '11px', whiteSpace: 'nowrap' }}>Waiting to be packed</div>
                        )}
                        {!isUrgentCancel && !isLocked && activeTab === 'awaiting_payment' && (
                          <div style={{ color: '#f59e0b', fontSize: '11px', fontWeight: 600, whiteSpace: 'nowrap' }}>Awaiting Payment</div>
                        )}
                        {activeTab === 'dispatched' && (
                          <div style={{ fontSize: '12px' }}>
                            <strong>{lead.courierPartner}</strong><br />
                            AWB: {lead.trackingNumber}
                          </div>
                        )}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        {activeTab === 'awaiting_payment' && (
                          <button 
                            className="btn-secondary" 
                            style={{ height: '26px', padding: '0 12px', fontSize: '11px', opacity: 0.6, cursor: 'not-allowed', marginLeft: 'auto', whiteSpace: 'nowrap', display: 'inline-flex', alignItems: 'center' }}
                            disabled={true}
                          >
                            Cannot Pack Yet
                          </button>
                        )}
                        {activeTab === 'pending' && (
                          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                            {!isLocked && (
                              <button 
                                className="btn-primary" 
                                style={{ height: '26px', padding: '0 12px', fontSize: '11px', whiteSpace: 'nowrap', display: 'inline-flex', alignItems: 'center', borderRadius: '6px' }}
                                onClick={() => handleLockOrder(lead)}
                                disabled={isProcessingAction || isUrgentCancel}
                              >
                                Claim & Pack
                              </button>
                            )}
                            {isLocked && lockedByMe && (
                              <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
                                <button 
                                  className="btn-secondary" 
                                  style={{ height: '26px', width: '26px', padding: '0', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', borderRadius: '6px', background: 'transparent', border: '1px solid rgba(255,255,255,0.05)', color: 'var(--text-muted)' }}
                                  onClick={() => handleUnlockOrder(lead)}
                                  title="Release Lock"
                                >
                                  <Unlock size={14} />
                                </button>
                                <button 
                                  className="btn-primary" 
                                  style={{ height: '26px', padding: '0 12px', fontSize: '11px', background: 'var(--secondary)', color: 'white', whiteSpace: 'nowrap', display: 'inline-flex', alignItems: 'center', borderRadius: '6px' }}
                                  onClick={() => openDispatchModal(lead)}
                                >
                                  Ship Item
                                </button>
                              </div>
                            )}
                          </div>
                        )}
                        {activeTab === 'dispatched' && (
                          <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
                            <button className="btn-secondary" style={{ height: '26px', padding: '0 12px', fontSize: '11px', whiteSpace: 'nowrap', display: 'inline-flex', alignItems: 'center', borderRadius: '6px' }} onClick={() => alert("Tracking API Hook triggered")}>
                              Track
                            </button>
                            <button 
                              className="btn-secondary" 
                              style={{ height: '26px', padding: '0 12px', fontSize: '11px', whiteSpace: 'nowrap', display: 'inline-flex', alignItems: 'center', gap: '4px', borderRadius: '6px' }} 
                              onClick={() => {
                                setSelectedManageLead(lead);
                                setManageAction(null);
                                setRtoReason('');
                              }}
                            >
                              <Settings size={14} /> Manage
                            </button>
                          </div>
                        )}
                        {activeTab === 'delivered' && (
                          <div style={{ color: '#10b981', fontSize: '11px', fontWeight: 600, textAlign: 'right' }}>
                            Delivered
                          </div>
                        )}
                        {activeTab === 'returned' && (
                          <div style={{ fontSize: '11px', textAlign: 'right' }}>
                            <strong style={{ color: '#ef4444' }}>RTO</strong>
                            <div style={{ color: 'var(--text-muted)', marginTop: '2px' }}>Reason: {lead.rtoReason || 'N/A'}</div>
                          </div>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        
        {/* Pagination Controls */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '15px 20px', borderTop: '1px solid var(--border)' }}>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Page {pageNumber}</div>
          <div style={{ display: 'flex', gap: '10px' }}>
            <button className="btn-secondary" disabled={pageNumber === 1} onClick={handlePrevPage}>
              <ChevronLeft size={16} /> Prev
            </button>
            <button className="btn-secondary" disabled={leads.length < 50} onClick={handleNextPage}>
              Next <ChevronRight size={16} />
            </button>
          </div>
        </div>
      </div>

      {/* Courier Assignment Modal */}
      {selectedLead && (
        <div className="modal-overlay" style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)',
          display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000
        }}>
          <div className="glass-panel fade-in" style={{ width: '400px', padding: '30px' }}>
            <h2 style={{ marginTop: 0, fontSize: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Truck size={20} className="text-primary" /> Assign Courier
            </h2>
            <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '20px' }}>
              Enter shipping details for {selectedLead.name}.
            </p>

            <div style={{ marginBottom: '15px' }}>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, marginBottom: '6px' }}>Courier Partner</label>
              <select 
                style={{ width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid var(--surface-border)', background: 'var(--background)', color: 'var(--text-main)' }}
                value={courier}
                onChange={(e) => { setCourier(e.target.value); setAwbError(''); }}
              >
                {COURIERS.map(c => <option key={c.name} value={c.name}>{c.name}</option>)}
              </select>
            </div>

            <div style={{ marginBottom: '20px' }}>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, marginBottom: '6px' }}>AWB / Tracking Number</label>
              <input 
                type="text" 
                style={{ width: '100%', padding: '10px', borderRadius: '8px', border: `1px solid ${awbError ? 'var(--danger)' : 'var(--surface-border)'}`, background: 'var(--background)', color: 'var(--text-main)' }}
                placeholder="Scan or type AWB..."
                value={awb}
                onChange={(e) => { setAwb(e.target.value); validateAwb(courier, e.target.value); }}
              />
              {awbError && <div style={{ color: '#ef4444', fontSize: '11px', marginTop: '6px' }}>{awbError}</div>}
            </div>

            <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end' }}>
              <button className="btn-secondary" onClick={() => setSelectedLead(null)} disabled={isProcessingAction}>Cancel</button>
              <button className="btn-primary" onClick={handleConfirmDispatch} disabled={isProcessingAction || !!awbError || !awb}>
                {isProcessingAction ? 'Processing...' : 'Confirm Dispatch'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Cyber-Minimalist Manage Shipment Modal (Delivered / RTO / Undo) */}
      {selectedManageLead && (
        <div className="modal-overlay fade-in" style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(8px)',
          display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000
        }}>
          <div className="cyber-modal fade-in" style={{ width: '420px', padding: '32px' }}>
            
            {/* Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
              <h2 style={{ margin: 0, fontSize: '18px', fontWeight: 600, color: '#ffffff' }}>
                Manage Shipment
              </h2>
              {/* Top Right Undo Link (Clean placement) */}
              {(() => {
                const dispatchedTime = selectedManageLead.dispatchedAt ? new Date(selectedManageLead.dispatchedAt).getTime() : 0;
                const now = new Date().getTime();
                const canUndo = (now - dispatchedTime) < (24 * 60 * 60 * 1000);
                if (canUndo && manageAction !== 'undo') {
                  return (
                    <button 
                      className="link-destructive"
                      onClick={() => { setManageAction('undo'); setRtoReason(''); }}
                    >
                      <RotateCcw size={14} /> Undo Dispatch
                    </button>
                  );
                }
                return null;
              })()}
            </div>
            
            {/* Inset Details Block */}
            <div className="inset-details" style={{ marginBottom: '24px' }}>
              <div className="inset-row">
                <span className="inset-label">Client</span>
                <span className="inset-value" style={{ fontFamily: 'Inter, sans-serif' }}>{selectedManageLead.name}</span>
              </div>
              <div className="inset-row">
                <span className="inset-label">Courier</span>
                <span className="inset-value" style={{ color: '#a3a3a3' }}>{selectedManageLead.courierPartner}</span>
              </div>
              <div className="inset-row">
                <span className="inset-label">Tracking AWB</span>
                <span className="inset-value" style={{ color: '#ffffff' }}>{selectedManageLead.trackingNumber}</span>
              </div>
            </div>

            {/* Status Selection (Segmented Control) */}
            <div style={{ display: 'flex', gap: '12px', marginBottom: '24px' }}>
              <button 
                className={`segment-btn ${manageAction === 'delivered' ? 'active-success' : ''}`}
                onClick={() => setManageAction('delivered')}
              >
                <CheckCircle size={16} className="icon" /> Delivered
              </button>
              <button 
                className={`segment-btn ${manageAction === 'rto' ? 'active-danger' : ''}`}
                onClick={() => setManageAction('rto')}
              >
                <XCircle size={16} className="icon" /> RTO
              </button>
            </div>

            {/* RTO Reason Dropdown */}
            {manageAction === 'rto' && (
              <div className="fade-in" style={{ marginBottom: '24px' }}>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 500, marginBottom: '8px', color: '#a3a3a3' }}>Reason for RTO</label>
                <select 
                  className={`cyber-select ${!rtoReason ? 'invalid' : ''}`}
                  value={rtoReason}
                  onChange={(e) => setRtoReason(e.target.value)}
                >
                  <option value="" disabled>Select reason...</option>
                  <option value="Customer Refused">Customer Refused Delivery</option>
                  <option value="Address Not Found">Address Not Found / Incorrect</option>
                  <option value="Contact Unreachable">Contact Unreachable / Switched Off</option>
                  <option value="Fake Order">Fake Order (Customer Denied Ordering)</option>
                  <option value="Other">Other</option>
                </select>
              </div>
            )}

            {/* Undo Warning Area */}
            {manageAction === 'undo' && (
              <div className="fade-in" style={{ background: 'rgba(239, 68, 68, 0.05)', padding: '16px', borderRadius: '8px', marginBottom: '24px', border: '1px solid rgba(239, 68, 68, 0.2)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#ef4444', fontWeight: 600, marginBottom: '8px', fontSize: '13px' }}>
                  <AlertOctagon size={16} /> Destructive Action
                </div>
                <div style={{ fontSize: '13px', color: '#a3a3a3', lineHeight: '1.5' }}>
                  Deleting tracking <strong style={{ color: '#ededed' }}>{selectedManageLead.trackingNumber || 'N/A'}</strong>. The order will be moved back to Pending.
                </div>
              </div>
            )}

            {/* Action Footer */}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '12px' }}>
              <button 
                className="btn-secondary" 
                style={{ border: 'none', background: 'transparent' }} 
                onClick={() => { setSelectedManageLead(null); setManageAction(null); }} 
                disabled={isProcessingAction}
              >
                Cancel
              </button>
              
              <button 
                className={manageAction === 'undo' || manageAction === 'rto' ? "btn-solid-danger" : "btn-high-contrast"}
                onClick={handleUpdateFinalStatus} 
                disabled={isProcessingAction || !manageAction || (manageAction === 'rto' && !rtoReason)}
              >
                {isProcessingAction ? 'Processing...' : (manageAction === 'undo' ? 'Confirm Delete' : 'Save Status')}
              </button>
            </div>

          </div>
        </div>
      )}
    </div>
  );
}
