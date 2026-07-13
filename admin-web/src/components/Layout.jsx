import React from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { signOut } from 'firebase/auth';
import { auth } from '../firebase';
import { Users, PhoneCall, TrendingUp, LogOut, Tag, BarChart3 } from 'lucide-react';

export default function Layout() {
  const navigate = useNavigate();

  const handleLogout = () => {
    signOut(auth);
    navigate('/login');
  };

  const navLinkStyle = ({ isActive }) => ({
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    padding: '8px 10px',
    borderRadius: '6px',
    textDecoration: 'none',
    color: isActive ? 'var(--primary)' : 'var(--text-muted)',
    background: isActive ? 'rgba(79, 70, 229, 0.1)' : 'transparent',
    fontWeight: isActive ? 500 : 400,
    fontSize: '13px',
  });

  return (
    <div className="app-container">
      {/* Sidebar */}
      <div className="sidebar glass-panel" style={{ borderRadius: 0, borderTop: 0, borderBottom: 0, borderLeft: 0 }}>
        <h2 className="auth-title" style={{ textAlign: 'left', marginBottom: '20px', fontSize: '18px' }}>NexaLeads</h2>
        
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <NavLink to="/dashboard" style={navLinkStyle}>
            <TrendingUp size={16} />
            <span>Dashboard</span>
          </NavLink>
          
          <NavLink to="/telecallers" style={navLinkStyle}>
            <Users size={16} />
            <span>Telecallers</span>
          </NavLink>
          
          <NavLink to="/history" style={navLinkStyle}>
            <PhoneCall size={16} />
            <span>Call History</span>
          </NavLink>
          
          <NavLink to="/products" style={navLinkStyle}>
            <Tag size={16} />
            <span>Products</span>
          </NavLink>

          <NavLink to="/reports" style={navLinkStyle}>
            <BarChart3 size={16} />
            <span>Reports</span>
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

