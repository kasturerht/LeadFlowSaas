import React from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { signOut } from 'firebase/auth';
import { auth } from '../firebase';
import { Users, PhoneCall, TrendingUp, LogOut, Tag, BarChart3, Package } from 'lucide-react';
export default function Layout() {
  const navigate = useNavigate();

  const handleLogout = () => {
    signOut(auth);
    navigate('/login');
  };

  const navLinkStyle = ({ isActive }) => ({
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between', // Changed to space-between for badge
    padding: '8px 10px',
    borderRadius: '6px',
    textDecoration: 'none',
    color: isActive ? 'var(--primary)' : 'var(--text-muted)',
    background: isActive ? 'rgba(79, 70, 229, 0.1)' : 'transparent',
    fontWeight: isActive ? 500 : 400,
    fontSize: '13px',
  });

  const innerContentStyle = {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  };

  const [pendingDispatchCount, setPendingDispatchCount] = React.useState(0);

  React.useEffect(() => {
    // Polling getCountFromServer to save Firestore read costs at scale (God-Level Plan)
    const fetchDispatchCount = async () => {
      try {
        const { collection, query, where, getCountFromServer } = await import('firebase/firestore');
        const { db } = await import('../firebase');
        const leadsRef = collection(db, 'leads');
        const q = query(leadsRef, where('status', '==', 'Order Placed'));
        const snapshot = await getCountFromServer(q);
        setPendingDispatchCount(snapshot.data().count);
      } catch (err) {
        console.error("Error fetching dispatch count:", err);
      }
    };

    fetchDispatchCount();
    const interval = setInterval(fetchDispatchCount, 60000); // Check every 1 minute
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="app-container">
      {/* Sidebar */}
      <div className="sidebar glass-panel" style={{ borderRadius: 0, borderTop: 0, borderBottom: 0, borderLeft: 0 }}>
        <h2 className="auth-title" style={{ textAlign: 'left', marginBottom: '20px', fontSize: '18px' }}>NexaLeads</h2>
        
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <NavLink to="/dashboard" style={navLinkStyle}>
            <div style={innerContentStyle}>
              <TrendingUp size={16} />
              <span>Dashboard</span>
            </div>
          </NavLink>
          
          <NavLink to="/dispatch" style={navLinkStyle}>
            <div style={innerContentStyle}>
              <Package size={16} />
              <span>Dispatch Center</span>
            </div>
            {pendingDispatchCount > 0 && (
              <span style={{
                background: '#ef4444',
                color: 'white',
                fontSize: '11px',
                fontWeight: 'bold',
                padding: '2px 6px',
                borderRadius: '10px'
              }}>
                {pendingDispatchCount}
              </span>
            )}
          </NavLink>

          <NavLink to="/telecallers" style={navLinkStyle}>
            <div style={innerContentStyle}>
              <Users size={16} />
              <span>Telecallers</span>
            </div>
          </NavLink>
          
          <NavLink to="/history" style={navLinkStyle}>
            <div style={innerContentStyle}>
              <PhoneCall size={16} />
              <span>Call History</span>
            </div>
          </NavLink>
          
          <NavLink to="/products" style={navLinkStyle}>
            <div style={innerContentStyle}>
              <Tag size={16} />
              <span>Products</span>
            </div>
          </NavLink>

          <NavLink to="/reports" style={navLinkStyle}>
            <div style={innerContentStyle}>
              <BarChart3 size={16} />
              <span>Reports</span>
            </div>
          </NavLink>
        </div>

        <button onClick={handleLogout} className="btn-secondary" style={{ width: '100%', justifyContent: 'center', fontSize: '13px', padding: '6px 12px' }}>
          <LogOut size={14} /> Logout
        </button>
      </div>

      {/* Main Content Area */}
      <div className="main-content">
        <Outlet />
      </div>
    </div>
  );
}

