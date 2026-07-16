import React, { useState, useEffect } from 'react';
import { db, auth } from '../firebase';
import { collection, query, where, orderBy, limit, startAfter, onSnapshot, runTransaction, doc, updateDoc, arrayUnion } from 'firebase/firestore';
import { Package, Truck, CheckCircle, XCircle, Search, AlertOctagon, Lock, Unlock, ChevronRight, ChevronLeft, QrCode, Settings, RotateCcw } from 'lucide-react';

const TABS = [
  { id: 'pending', label: 'Pending Pack', icon: Package, statusQuery: ['Order Placed'] },
  { id: 'dispatched', label: 'Dispatched', icon: Truck, statusQuery: ['Dispatched'] },
  { id: 'delivered', label: 'Delivered', icon: CheckCircle, statusQuery: ['Delivered'] },
  { id: 'returned', label: 'RTO / Returned', icon: XCircle, statusQuery: ['RTO'] },
];

const COURIERS = [
  { name: 'Delhivery', regex: /^\d{13,14}$/, example: '13 or 14 digits' },
  { name: 'Bluedart', regex: /^\d{11}$/, example: '11 digits' },
  { name: 'Shiprocket', regex: /^SR\d{7,10}$/, example: 'SR followed by 7-10 digits' },
  { name: 'EcomExpress', regex: /^\d{10}$/, example: '10 digits' },
  { name: 'Other', regex: /.+/, example: 'Any format' }
];

export default function DispatchCenter() {
  const [activeTab, setActiveTab] = useState('pending');
  const [leads, setLeads] = useState([]);
  const [loading, setLoading] = useState(true);
  const [lastVisible, setLastVisible] = useState(null);
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

  useEffect(() => {
    fetchLeads(activeTab, null);
  }, [activeTab]);

  const fetchLeads = (tabId, afterDoc = null) => {
    setLoading(true);
    const tabConfig = TABS.find(t => t.id === tabId);
    
    let q = query(
      collection(db, 'leads'),
      where('status', 'in', tabConfig.statusQuery),
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
      fetchLeads(activeTab, lastVisible);
      setPageNumber(prev => prev + 1);
    }
  };

  const handlePrevPage = () => {
    // Basic back navigation (for production, maintain an array of cursors)
    fetchLeads(activeTab, null);
    setPageNumber(1);
  };

  const handleLockOrder = async (lead) => {
    const leadRef = doc(db, 'leads', lead.id);
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
    if (lead.lockedByUid !== auth.currentUser?.uid) {
      return alert("You can only unlock orders you locked.");
    }
    const leadRef = doc(db, 'leads', lead.id);
    await updateDoc(leadRef, {
      dispatchStatus: 'Pending',
      lockedByUid: null,
      dispatchHistory: arrayUnion({
        action: 'Unlocked',
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
    const partner = COURIERS.find(c => c.name === partnerName);
    if (!partner) return true;
    if (!partner.regex.test(number)) {
      setAwbError(`Invalid format for ${partnerName}. Expected: ${partner.example}`);
      return false;
    }
    setAwbError('');
    return true;
  };

  const handleConfirmDispatch = async () => {
    if (!validateAwb(courier, awb)) return;

    try {
      setIsProcessingAction(true);
      const leadRef = doc(db, 'leads', selectedLead.id);
      
      // Update the main status to 'Dispatched' so it leaves the pending queue
      await updateDoc(leadRef, {
        status: 'Dispatched',
        dispatchStatus: 'Dispatched',
        courierPartner: courier,
        trackingNumber: awb,
        dispatchedAt: new Date().toISOString(),
        packedBy: auth.currentUser?.uid,
        lockedByUid: null, // release lock
        dispatchHistory: arrayUnion({
          action: 'Dispatched',
          courier: courier,
          awb: awb,
          by: auth.currentUser?.uid,
          timestamp: new Date().toISOString()
        })
      });
      
      setSelectedLead(null);
    } catch (error) {
      console.error("Error dispatching:", error);
      alert("Failed to dispatch order.");
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

    const leadRef = doc(db, 'leads', selectedManageLead.id);
    
    try {
      setIsProcessingAction(true);

      if (manageAction === 'undo') {
        const previousAwb = selectedManageLead.trackingNumber || 'Unknown';
        const previousCourier = selectedManageLead.courierPartner || 'Unknown';

        await updateDoc(leadRef, {
          status: 'Order Placed',
          dispatchStatus: 'Pending',
          courierPartner: null,
          trackingNumber: null,
          dispatchedAt: null,
          packedBy: null,
          dispatchHistory: arrayUnion({
            action: 'Reverted Dispatch to Pending',
            previousAwb: previousAwb,
            previousCourier: previousCourier,
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
            transaction.update(leadRef, {
              status: 'Delivered',
              dispatchStatus: 'Delivered',
              deliveredAt: new Date().toISOString(),
              dispatchHistory: arrayUnion({
                action: 'Marked Delivered',
                by: auth.currentUser?.uid,
                timestamp: new Date().toISOString()
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
      <div className="reports-header" style={{ marginBottom: '20px' }}>
        <div>
          <h1 className="page-title">Dispatch Center</h1>
          <p className="page-subtitle">Fulfillment & Logistics Engine (God-Level)</p>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <div className="search-bar">
            <Search size={16} />
            <input 
              type="text" 
              placeholder="Search Name or Phone..." 
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
          <button className="btn-secondary" onClick={() => alert('Barcode scanner ready to listen...')}>
            <QrCode size={16} /> Scan
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="tabs" style={{ display: 'flex', gap: '10px', marginBottom: '20px', borderBottom: '1px solid var(--border)', paddingBottom: '10px' }}>
        {TABS.map(tab => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => { setActiveTab(tab.id); setPageNumber(1); }}
              className={`tab-button ${isActive ? 'active' : ''}`}
              style={{
                display: 'flex', alignItems: 'center', gap: '8px',
                padding: '8px 16px', borderRadius: '8px',
                background: isActive ? 'var(--primary)' : 'transparent',
                color: isActive ? '#fff' : 'var(--text-muted)',
                border: 'none', cursor: 'pointer',
                fontWeight: isActive ? 600 : 400
              }}
            >
              <Icon size={16} />
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
                  <th>Client</th>
                  <th>Product</th>
                  <th>Address & Pincode</th>
                  <th>Status & QC</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {leads.filter(l => l.name?.toLowerCase().includes(searchQuery.toLowerCase()) || l.phone?.includes(searchQuery)).map(lead => {
                  const isLocked = !!lead.lockedByUid;
                  const lockedByMe = lead.lockedByUid === auth.currentUser?.uid;
                  const isUrgentCancel = lead.urgentCancelRequest;
                  const hasAddressIssue = !lead.address || (lead.pincode && lead.pincode.length < 6);

                  return (
                    <tr key={lead.id} style={{ background: isUrgentCancel ? 'rgba(239, 68, 68, 0.1)' : 'transparent' }}>
                      <td>
                        <div style={{ fontWeight: 600 }}>{lead.name}</div>
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{lead.phone}</div>
                      </td>
                      <td>
                        <div style={{ display: 'inline-block', background: 'var(--primary-light)', color: 'var(--primary)', padding: '2px 8px', borderRadius: '12px', fontSize: '12px', fontWeight: 600 }}>
                          {lead.product || 'Standard Kit'}
                        </div>
                      </td>
                      <td>
                        <div style={{ fontSize: '13px' }}>{lead.address || 'No Address Provided'}</div>
                        <div style={{ fontSize: '12px', fontWeight: 600, color: hasAddressIssue ? '#ef4444' : 'var(--text-muted)' }}>
                          {lead.pincode ? `PIN: ${lead.pincode}` : 'NO PINCODE'}
                        </div>
                      </td>
                      <td>
                        {isUrgentCancel && (
                          <div style={{ color: '#ef4444', fontSize: '12px', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '4px' }}>
                            <AlertOctagon size={14} /> CANCEL REQUEST
                          </div>
                        )}
                        {!isUrgentCancel && isLocked && (
                          <div style={{ color: lockedByMe ? '#10b981' : '#f59e0b', fontSize: '12px', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '4px' }}>
                            <Lock size={14} /> {lockedByMe ? 'Locked by You' : 'Locked by Another Admin'}
                          </div>
                        )}
                        {!isUrgentCancel && !isLocked && activeTab === 'pending' && (
                          <div style={{ color: 'var(--text-muted)', fontSize: '12px' }}>Waiting to be packed</div>
                        )}
                        {activeTab === 'dispatched' && (
                          <div style={{ fontSize: '12px' }}>
                            <strong>{lead.courierPartner}</strong><br />
                            AWB: {lead.trackingNumber}
                          </div>
                        )}
                      </td>
                      <td>
                        {activeTab === 'pending' && (
                          <>
                            {!isLocked && (
                              <button 
                                className="btn-primary" 
                                style={{ padding: '6px 12px', fontSize: '12px' }}
                                onClick={() => handleLockOrder(lead)}
                                disabled={isProcessingAction || isUrgentCancel}
                              >
                                Claim & Pack
                              </button>
                            )}
                            {isLocked && lockedByMe && (
                              <div style={{ display: 'flex', gap: '8px' }}>
                                <button 
                                  className="btn-primary" 
                                  style={{ padding: '6px 12px', fontSize: '12px', background: '#10b981' }}
                                  onClick={() => openDispatchModal(lead)}
                                >
                                  Ship Item
                                </button>
                                <button 
                                  className="btn-secondary" 
                                  style={{ padding: '6px 8px' }}
                                  onClick={() => handleUnlockOrder(lead)}
                                  title="Release Lock"
                                >
                                  <Unlock size={14} />
                                </button>
                              </div>
                            )}
                          </>
                        )}
                        {activeTab === 'dispatched' && (
                          <div style={{ display: 'flex', gap: '8px' }}>
                            <button className="btn-secondary" style={{ padding: '6px 12px', fontSize: '12px' }} onClick={() => alert("Tracking API Hook triggered")}>
                              Track
                            </button>
                            <button 
                              className="btn-secondary" 
                              style={{ padding: '6px 12px', fontSize: '12px', display: 'flex', alignItems: 'center', gap: '4px' }} 
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
                          <div style={{ color: '#10b981', fontSize: '12px', fontWeight: 600 }}>
                            Delivered
                          </div>
                        )}
                        {activeTab === 'returned' && (
                          <div style={{ fontSize: '12px' }}>
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
                style={{ width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid var(--border)', background: 'var(--background)' }}
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
                style={{ width: '100%', padding: '10px', borderRadius: '8px', border: `1px solid ${awbError ? '#ef4444' : 'var(--border)'}`, background: 'var(--background)' }}
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
