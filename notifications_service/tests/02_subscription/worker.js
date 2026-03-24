self.addEventListener('push', function(event) {
    let data = { title: 'Notification', body: 'You have a new message' };
    if (event.data) {
        try {
            data = event.data.json();
        } catch (e) {
            data.body = event.data.text();
        }
    }

    const options = {
        body: data.message || data.body,
        icon: '/icon.png', // Replace with your icon path
        badge: '/badge.png', // Replace with your badge path
        data: {
            url: data.url || '/'
        }
    };

    event.waitUntil(
        self.registration.showNotification(data.title || 'New Alert', options)
    );
});

self.addEventListener('notificationclick', function(event) {
    event.notification.close();
    event.waitUntil(
        clients.openWindow(event.notification.data.url)
    );
});

// Health Check Listener
self.addEventListener('message', (event) => {
    if (event.data && event.data.type === 'PING') {
        event.source.postMessage({ type: 'PONG', status: 'active' });
    }
});
