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
import MigrationTool from './components/MigrationTool';
import Suspended from './components/Suspended';

function App() {
  const { user, loading, orgStatus, role } = useAuth();

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
        <Route element={user ? (orgStatus === 'suspended' && role !== 'superadmin' ? <Navigate to="/suspended" /> : <Layout />) : <Navigate to="/login" />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/telecallers" element={<Telecallers />} />
          <Route path="/history" element={<CallHistory />} />
          <Route path="/products" element={<Products />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="/dispatch" element={<DispatchCenter />} />
          <Route path="/saas-admin" element={<SaasAdminDashboard />} />
          <Route path="/super-migrate" element={<MigrationTool />} />
        </Route>

        <Route path="/suspended" element={user && orgStatus === 'suspended' && role !== 'superadmin' ? <Suspended /> : <Navigate to="/dashboard" />} />

        <Route 
          path="/" 
          element={<Navigate to={user ? (orgStatus === 'suspended' && role !== 'superadmin' ? "/suspended" : "/dashboard") : "/login"} />} 
        />
      </Routes>
    </Router>
  );
}

export default App;
