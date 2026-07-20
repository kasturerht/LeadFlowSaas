import React, { useEffect, useState } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './AuthContext';
import Login from './components/Login';
import Layout from './components/Layout';
import Dashboard from './components/Dashboard';
import Telecallers from './components/Telecallers';
import CallHistory from './components/CallHistory';
import Products from './components/Products';
import Reports from './components/Reports';
import DispatchCenter from './components/DispatchCenter';
import SaasAdminDashboard from './components/SaasAdminDashboard';

function App() {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', background: 'var(--background)' }}>
        <div style={{ color: 'var(--primary)', fontSize: '18px', fontWeight: 500 }}>Loading NexaLeads...</div>
      </div>
    );
  }

  return (
    <Router>
      <Routes>
        <Route 
          path="/login" 
          element={user ? <Navigate to="/dashboard" /> : <Login />} 
        />
        
        {/* Protected Routes wrapped in Layout */}
        <Route element={user ? <Layout /> : <Navigate to="/login" />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/telecallers" element={<Telecallers />} />
          <Route path="/history" element={<CallHistory />} />
          <Route path="/products" element={<Products />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="/dispatch" element={<DispatchCenter />} />
          <Route path="/saas-admin" element={<SaasAdminDashboard />} />
        </Route>

        <Route 
          path="/" 
          element={<Navigate to={user ? "/dashboard" : "/login"} />} 
        />
      </Routes>
    </Router>
  );
}

export default App;
