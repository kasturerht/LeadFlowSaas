import React, { useState } from 'react';
import { auth } from '../firebase';
import { signInWithEmailAndPassword } from 'firebase/auth';
import { Lock, Mail, ArrowRight } from 'lucide-react';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await signInWithEmailAndPassword(auth, email, password);
    } catch (err) {
      setError(err.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="glass-panel auth-box">
        <h1 className="auth-title">NexaLeads</h1>
        <p className="auth-subtitle">Admin Workspace Login</p>
        
        {error && <div style={{ color: 'var(--danger)', marginBottom: '16px', fontSize: '14px' }}>{error}</div>}
        
        <form onSubmit={handleLogin}>
          <div className="form-group">
            <label className="form-label">Email Address</label>
            <div style={{ position: 'relative' }}>
              <Mail style={{ position: 'absolute', top: '12px', left: '12px', color: 'var(--text-muted)' }} size={20} />
              <input 
                type="email" 
                className="input-field" 
                style={{ paddingLeft: '40px' }}
                placeholder="admin@nexaleads.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
          </div>
          
          <div className="form-group">
            <label className="form-label">Password</label>
            <div style={{ position: 'relative' }}>
              <Lock style={{ position: 'absolute', top: '12px', left: '12px', color: 'var(--text-muted)' }} size={20} />
              <input 
                type="password" 
                className="input-field" 
                style={{ paddingLeft: '40px' }}
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
          </div>
          
          <button type="submit" className="btn-primary" style={{ width: '100%', marginTop: '24px' }} disabled={loading}>
            {loading ? 'Authenticating...' : 'Sign In'} <ArrowRight size={18} />
          </button>
        </form>
      </div>
    </div>
  );
}
