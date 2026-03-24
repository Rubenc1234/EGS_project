import { useEffect, useState } from 'react';

export const useAuth = () => {
  const [token, setToken] = useState<string | null>(null);
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  useEffect(() => {
    const storedToken = localStorage.getItem('egs_token');
    setToken(storedToken);
    setIsLoggedIn(!!storedToken);
  }, []);

  return { token, isLoggedIn };
};
