import React, { createContext, useContext, useEffect, useState } from 'react';
import { onAuthStateChanged } from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import { auth, db } from './firebase';

const AuthContext = createContext();

export function useAuth() {
  return useContext(AuthContext);
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [orgId, setOrgId] = useState(null);
  const [role, setRole] = useState(null);
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
            setOrgId(data.orgId);
            
            if (currentUser.email === 'kasturerht@gmail.com' || currentUser.email === 'admin@nexaleads.com') {
              setRole('superadmin');
            } else {
              setRole(data.role);
            }
          } else {
            if (currentUser.email === 'kasturerht@gmail.com' || currentUser.email === 'admin@nexaleads.com') {
              setRole('superadmin');
              setOrgId('SYSTEM');
            } else {
              console.error("No user mapping found for this UID!");
              setOrgId(null);
              setRole(null);
            }
          }
        } catch (error) {
          console.error("Error fetching user mapping:", error);
        }
      } else {
        setUser(null);
        setOrgId(null);
        setRole(null);
      }
      setLoading(false);
    });

    return unsubscribe;
  }, []);

  const value = {
    user,
    orgId,
    role,
    loading
  };

  return (
    <AuthContext.Provider value={value}>
      {!loading && children}
    </AuthContext.Provider>
  );
}
