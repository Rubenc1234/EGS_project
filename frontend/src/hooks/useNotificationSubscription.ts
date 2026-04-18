import { useEffect, useState } from 'react';
import { useAuth } from './useAuth';

// NOTA: Substitui isto com a vapid_public_key obtida do POST /v1/admin/clients
const VAPID_PUBLIC_KEY = 'BMMJUFw8nHie7nAvefWySNtLVkkSy9eAMr8N08kv1pu-I6v_BnEVienA45hKRpiUDdsZ9vxXZuujsS_hqwwQL0I';

export const useNotificationSubscription = () => {
  const { token } = useAuth();
  const [subscriptionStatus, setSubscriptionStatus] = useState<'idle' | 'loading' | 'subscribed' | 'error'>('idle');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      console.log('[useNotificationSubscription] Waiting for token...');
      return;
    }
    
    if (subscriptionStatus !== 'idle') return;

    const registerSubscription = async () => {
      try {
        console.log('[useNotificationSubscription] Checking notification permission...');
        
        // Only proceed if permission is already GRANTED
        // User must click the "Enable Notifications" button first!
        if (!('Notification' in window)) {
          console.log('[useNotificationSubscription] Notifications not supported');
          return;
        }
        
        if (Notification.permission !== 'granted') {
          console.log('[useNotificationSubscription] Permission not yet granted. User should click "Enable Notifications" button.');
          setSubscriptionStatus('idle'); // Stay idle until user grants permission
          return;
        }

        console.log('[useNotificationSubscription] Permission already granted! Creating subscription...');
        setSubscriptionStatus('loading');

        // Check if service worker is available
        if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
          console.error('[useNotificationSubscription] Push notifications not supported');
          throw new Error('Push notifications not supported in this browser');
        }
        console.log('[useNotificationSubscription] Browser supports push notifications ✅');

        // Get service worker registration
        console.log('[useNotificationSubscription] Waiting for Service Worker...');
        const registration = await Promise.race([
          navigator.serviceWorker.ready,
          new Promise((_, reject) => 
            setTimeout(() => reject(new Error('Service Worker registration timeout')), 5000)
          )
        ]) as ServiceWorkerRegistration;
        console.log('[useNotificationSubscription] Service Worker is ready ✅');

        // Check if already subscribed
        console.log('[useNotificationSubscription] Checking for existing subscription...');
        let subscription = await registration.pushManager.getSubscription();
        
        if (!subscription) {
          console.log('[useNotificationSubscription] Creating new push subscription with VAPID key...');
          subscription = await registration.pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY),
          });
          console.log('[useNotificationSubscription] ✅ New subscription created');
        } else {
          console.log('[useNotificationSubscription] ✅ Already subscribed');
        }

        // Extract user ID from JWT token
        console.log('[useNotificationSubscription] Extracting user ID from JWT...');
        const userId = extractUserIdFromToken(token);
        if (!userId) {
          console.error('[useNotificationSubscription] Could not extract user ID');
          throw new Error('Could not extract user ID from token');
        }
        console.log('[useNotificationSubscription] User ID:', userId);

        // Send subscription to backend
        console.log('[useNotificationSubscription] Sending subscription to backend...');
        const response = await fetch('/v1/users/me/subscription', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
            'X-User-Id': userId,
          },
          body: JSON.stringify({
            deviceId: generateDeviceId(),
            platform: 'web',
            endpoint: subscription.endpoint,
            keys: {
              p256dh: arrayBufferToBase64(subscription.getKey('p256dh')),
              auth: arrayBufferToBase64(subscription.getKey('auth')),
            },
            metadata: {
              browser: getBrowserInfo(),
              userAgent: navigator.userAgent,
              timestamp: new Date().toISOString(),
            },
          }),
        });

        console.log('[useNotificationSubscription] Backend response:', response.status);
        if (!response.ok) {
          const errorText = await response.text();
          console.error('[useNotificationSubscription] Backend error:', errorText);
          throw new Error(`Backend returned ${response.status}`);
        }

        const result = await response.json();
        console.log('[useNotificationSubscription] ✅ Subscription registered successfully!', result);
        setSubscriptionStatus('subscribed');
      } catch (err) {
        console.error('[useNotificationSubscription] ❌ Error:', err);
        setError(err instanceof Error ? err.message : 'Unknown error');
        setSubscriptionStatus('error');
      }
    };

    registerSubscription();
  }, [token, subscriptionStatus]);

  return { subscriptionStatus, error };
};

// Utility functions
function urlBase64ToUint8Array(base64String: string): BufferSource {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding)
    .replace(/\-/g, '+')
    .replace(/_/g, '/');

  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);

  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}

function arrayBufferToBase64(buffer: ArrayBuffer | null): string {
  if (!buffer) return '';
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return window.btoa(binary);
}

function generateDeviceId(): string {
  return `${navigator.userAgent.substring(0, 20)}-${Date.now()}`;
}

function getBrowserInfo(): string {
  if (navigator.userAgent.includes('Chrome')) return 'Chrome';
  if (navigator.userAgent.includes('Firefox')) return 'Firefox';
  if (navigator.userAgent.includes('Safari')) return 'Safari';
  if (navigator.userAgent.includes('Edge')) return 'Edge';
  return 'Unknown';
}

function extractUserIdFromToken(token: string | null): string | null {
  if (!token) return null;
  try {
    // JWT format: header.payload.signature
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    
    // Decode payload (base64url)
    const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
    
    // Try 'sub' first (Keycloak), else 'user_id'
    return payload.sub || payload.user_id || null;
  } catch (err) {
    console.error('[App] Error extracting user ID from token:', err);
    return null;
  }
}
