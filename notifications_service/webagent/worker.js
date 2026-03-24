self.addEventListener('push', function(event) {
    // 1. Safe Default Fallbacks
    let data = {
        title: 'New Notification',
        body: 'You have a new message',
        icon: '/favicon.ico',
        url: '/'
    };

    if (event.data) {
        try {
            // 2. Parse the payload
            const parsed = event.data.json();
            data = { ...data, ...parsed };

            // Map 'message' to 'body' to support existing API structure
            if (parsed.message && !parsed.body) {
                data.body = parsed.message;
            }
        } catch (e) {
            data.body = event.data.text();
        }
    }

    // 3. Prepare the underlying data payload (where we store URLs)
    const notificationData = {
        url: data.url || '/',
        ...data.data // Merge any extra custom data the composer sent
    };

    // 4. Handle Action Buttons (e.g., "Accept", "Decline")
    // If the composer attached specific URLs to specific buttons, we store them in `data`
    // so the click handler can find them later.
    if (data.actions && Array.isArray(data.actions)) {
        data.actions.forEach(action => {
            if (action.action_url) {
                notificationData[`action_url_${action.action}`] = action.action_url;
            }
        });
    }

    // 5. Construct the full Options object with EVERY possible Web API field
    const options = {
        body: data.body,
        icon: data.icon,                 // URL to standard icon
        badge: data.badge,               // URL to small monochrome icon (Android status bar)
        image: data.image,               // URL to large hero image

        dir: data.dir,                   // Text direction: 'auto', 'ltr', 'rtl'
        lang: data.lang,                 // BCP 47 language tag (e.g., 'en-US')

        tag: data.tag,                   // Grouping ID. New pushes with the same tag REPLACE the old one.
        renotify: data.renotify,         // Boolean. If true, replacing a tagged push alerts the user again.

        requireInteraction: data.requireInteraction, // Boolean. If true, push stays on screen until clicked/dismissed.
        silent: data.silent,             // Boolean. If true, no sound/vibration.
        vibrate: data.vibrate,           // Array of ints (e.g., [200, 100, 200] for vibrate-pause-vibrate)

        actions: data.actions,           // Array of objects: {action: 'id', title: 'Text', icon: 'url'}

        timestamp: data.timestamp || Date.now(), // Epoch time

        data: notificationData           // The hidden data payload used by the click listener
    };

    // Clean up undefined properties so we don't pass literal 'undefined' to the browser API
    Object.keys(options).forEach(key => options[key] === undefined && delete options[key]);

    event.waitUntil(
        self.registration.showNotification(data.title || 'New Notification', options)
    );
});

self.addEventListener('notificationclick', function(event) {
    // ALWAYS close the notification on click
    event.notification.close();

    let targetUrl = event.notification.data.url;

    // Determine if the user clicked a specific action button or the main notification body
    if (event.action) {
        const actionUrlKey = `action_url_${event.action}`;

        // If the composer assigned a specific URL to this button, override the main URL
        if (event.notification.data[actionUrlKey]) {
            targetUrl = event.notification.data[actionUrlKey];
        } else if (event.action === 'close' || event.action === 'dismiss') {
            // If the action is just to close/dismiss, do nothing more.
            return;
        }
    }

    // Advanced window routing: Check if the site is already open in a tab
    if (targetUrl) {
        event.waitUntil(
            clients.matchAll({ type: 'window', includeUncontrolled: true }).then(windowClients => {
                // Check if there is already a window/tab open with the target URL
                for (let i = 0; i < windowClients.length; i++) {
                    const client = windowClients[i];
                    // If it is open, just focus the tab instead of opening a duplicate
                    if (client.url === targetUrl && 'focus' in client) {
                        return client.focus();
                    }
                }
                // If the site isn't open, spawn a new tab
                if (clients.openWindow) {
                    return clients.openWindow(targetUrl);
                }
            })
        );
    }
});

// Health Check Listener
self.addEventListener('message', (event) => {
    if (event.data && event.data.type === 'PING') {
        event.source.postMessage({ type: 'PONG', status: 'active' });
    }
});
