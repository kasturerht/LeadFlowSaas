import React, { createContext, useContext, useEffect, useState } from 'react';
import { onAuthStateChanged } from 'firebase/auth';
import { doc, getDoc, onSnapshot } from 'firebase/firestore';
import { auth, db } from './firebase';

const AuthContext = createContext();

export function useAuth() {
  return useContext(AuthContext);
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [realOrgId, setRealOrgId] = useState(null); // The actual Org ID
  const [impersonatingOrgId, setImpersonatingOrgId] = useState(null); // The spoofed Org ID
  const [role, setRole] = useState(null);
  const [orgStatus, setOrgStatus] = useState('active'); // active or suspended
  const [orgName, setOrgName] = useState('Organization');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (currentUser) => {
      if (currentUser) {
        setUser(currentUser);
        try {
          // Fetch the user's SaaS mapping to discover their Organization ID
          const mappingRef = doc(db, 'user_mappings', currentUser.uid);
          const mappingSnap = await getDoc(mappingRef);
          
          if (mappingSnap.exists()) {
            const data = mappingSnap.data();
            setRealOrgId(data.orgId);
            
            if (currentUser.email === 'kasturerht@gmail.com' || currentUser.email === 'admin@nexaleads.com') {
              setRole('superadmin');
            } else {
              setRole(data.role);
            }
          } else {
            if (currentUser.email === 'kasturerht@gmail.com' || currentUser.email === 'admin@nexaleads.com') {
              setRole('superadmin');
              setRealOrgId('SYSTEM');
            } else {
              console.error("No user mapping found for this UID!");
              setRealOrgId(null);
              setRole(null);
            }
          }
        } catch (error) {
          console.error("Error fetching user mapping:", error);
        }
      } else {
        setUser(null);
        setRealOrgId(null);
        setRole(null);
        setImpersonatingOrgId(null);
      }
      setLoading(false);
    });

    return unsubscribe;
  }, []);

  const orgId = impersonatingOrgId || realOrgId;

  // Real-time listener for the user's organization status and name
  useEffect(() => {
    let unsubOrg = () => {};
    
    if (orgId && orgId !== 'SYSTEM') {
      const orgRef = doc(db, 'organizations', orgId);
      unsubOrg = onSnapshot(orgRef, (docSnap) => {
        if (docSnap.exists()) {
          const data = docSnap.data();
          setOrgStatus(data.status || 'active');
          setOrgName(data.name || 'Organization');
        } else {
          setOrgStatus('active'); // fallback
          setOrgName('Organization');
        }
      });
    } else {
      setOrgStatus('active');
      setOrgName('SYSTEM ADMIN');
    }

    return () => {
      unsubOrg();
    };
  }, [orgId]);

  const impersonateTenant = (orgId) => {
    if (role === 'superadmin') {
      setImpersonatingOrgId(orgId);
    } else {
      console.error("Only superadmin can impersonate.");
    }
  };

  const exitImpersonation = () => {
    setImpersonatingOrgId(null);
  };

  // const orgId is already defined above

  const value = {
    user,
    orgId, // Exposes either the real one, or the impersonated one
    realOrgId,
    impersonatingOrgId,
    role,
    loading,
    orgStatus, // Expose status
    orgName, // Expose name
    impersonateTenant,
    exitImpersonation
  };

  return (
    <AuthContext.Provider value={value}>
      {impersonatingOrgId && (
        <div style={{ background: '#ef4444', color: 'white', padding: '8px', textAlign: 'center', fontWeight: 'bold', zIndex: 9999, position: 'relative' }}>
          ⚠️ You are currently impersonating {impersonatingOrgId}. 
          <button onClick={exitImpersonation} style={{ marginLeft: '12px', background: 'white', color: '#ef4444', border: 'none', padding: '4px 8px', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}>
            Exit Impersonation
          </button>
        </div>
      )}
      {!loading && children}
    </AuthContext.Provider>
  );
}
