import React from 'react';
import { Lock, LogOut } from 'lucide-react';
import { auth } from '../firebase';
import { signOut } from 'firebase/auth';
import { useNavigate } from 'react-router-dom';

export default function Suspended() {
  const navigate = useNavigate();

  const handleLogout = async () => {
    await signOut(auth);
    navigate('/login');
  };

  return (
    <div style={{
      display: 'flex', 
      justifyContent: 'center', 
      alignItems: 'center', 
      height: '100vh', 
      background: 'var(--background)'
    }}>
      <div style={{
        background: 'var(--card-bg)',
        padding: '40px',
        borderRadius: '16px',
        textAlign: 'center',
        maxWidth: '400px',
        border: '1px solid var(--border-color)',
        boxShadow: '0 10px 25px rgba(0,0,0,0.1)'
      }}>
        <div style={{
          background: 'rgba(239, 68, 68, 0.1)',
          color: '#ef4444',
          width: '64px',
          height: '64px',
          borderRadius: '50%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          margin: '0 auto 24px auto'
        }}>
          <Lock size={32} />
        </div>
        
        <h1 style={{ color: 'var(--text-primary)', marginBottom: '12px', fontSize: '24px' }}>Account Suspended</h1>
        
        <p style={{ color: 'var(--text-secondary)', marginBottom: '32px', fontSize: '14px', lineHeight: '1.6' }}>
          Your organization's account has been paused by the administrator. 
          Please contact support or your account manager for further assistance.
        </p>
        
        <button 
          className="btn-secondary" 
          onClick={handleLogout}
          style={{ width: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px' }}
        >
          <LogOut size={16} /> Logout
        </button>
      </div>
    </div>
  );
}
