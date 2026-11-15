const notificationFeed = document.querySelector('#fleet-notification-feed');
const refreshInterval = Number(notificationFeed.dataset.refreshSeconds) * 1000;
let previousNotifications = notificationFeed.innerHTML.trim();

async function refreshNotifications() {
    try {
        if (document.hidden || notificationFeed.contains(document.activeElement)) return;

        const response = await fetch(notificationFeed.dataset.url, {
            cache: 'no-store',
            redirect: 'error',
        });
        if (!response.ok || response.headers.get('X-Fleet-Notifications') !== '1') return;

        const markup = (await response.text()).trim();
        if (markup !== previousNotifications && !notificationFeed.contains(document.activeElement)) {
            notificationFeed.innerHTML = markup;
            previousNotifications = markup;
        }
    } catch {
        // A temporary connection failure should leave the last received list visible.
    } finally {
        setTimeout(refreshNotifications, refreshInterval);
    }
}

setTimeout(refreshNotifications, refreshInterval);
