import React from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { signOut } from 'firebase/auth';
import { auth } from '../firebase';
import { Users, PhoneCall, TrendingUp, LogOut } from 'lucide-react';

export default function Layout() {
  const navigate = useNavigate();

  const handleLogout = () => {
    signOut(auth);
    navigate('/login');
  };

  const navLinkStyle = ({ isActive }) => ({
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    padding: '12px',
    borderRadius: '8px',
    textDecoration: 'none',
    color: isActive ? 'var(--primary)' : 'var(--text-muted)',
    background: isActive ? 'rgba(79, 70, 229, 0.1)' : 'transparent',
    fontWeight: isActive ? 500 : 400,
  });

  return (
    <div className="app-container">
      {/* Sidebar */}
      <div className="sidebar glass-panel" style={{ borderRadius: 0, borderTop: 0, borderBottom: 0, borderLeft: 0 }}>
        <h2 className="auth-title" style={{ textAlign: 'left', marginBottom: '40px', fontSize: '24px' }}>NexaLeads</h2>
        
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <NavLink to="/dashboard" style={navLinkStyle}>
            <TrendingUp size={20} />
            <span>Dashboard</span>
          </NavLink>
          
          <NavLink to="/telecallers" style={navLinkStyle}>
            <Users size={20} />
            <span>Telecallers</span>
          </NavLink>
          
          <NavLink to="/history" style={navLinkStyle}>
            <PhoneCall size={20} />
            <span>Call History</span>
          </NavLink>
        </div>

        <button onClick={handleLogout} className="btn-primary" style={{ background: 'transparent', border: '1px solid var(--surface-border)', color: 'var(--text-muted)' }}>
          <LogOut size={18} /> Logout
        </button>
      </div>

      {/* Main Content Area */}
      <div className="main-content">
        <Outlet />
      </div>
    </div>
  );
}
