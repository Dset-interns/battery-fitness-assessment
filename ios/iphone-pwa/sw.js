/*
 * Service worker: keeps the app and the pose-AI files on the phone so it works with no internet.
 *
 * - The app shell is network-first, so a new version appears as soon as the phone is online, and the
 *   cached copy opens it when it is not.
 * - Everything else from this origin (logos, the MediaPipe runtime and models in vendor/) is cache-first.
 * - Cross-origin requests are left alone.
 *
 * Browsers only run service workers on HTTPS (or localhost) with a certificate they trust.
 * Raise CACHE when the list of files below changes.
 */
const CACHE = "bfa-shell-v2";
const SHELL = ["./", "index.html", "manifest.json", "sai-logo.png", "dset_logo.png", "apple-touch-icon.png", "a4_marker.html"];
const VENDOR = [
  "vendor/mediapipe/vision_bundle.mjs",
  "vendor/mediapipe/wasm/vision_wasm_internal.js",
  "vendor/mediapipe/wasm/vision_wasm_internal.wasm",
  "vendor/mediapipe/wasm/vision_wasm_nosimd_internal.js",
  "vendor/mediapipe/wasm/vision_wasm_nosimd_internal.wasm",
  "vendor/mediapipe/pose_landmarker_full.task",
  "vendor/mediapipe/pose_landmarker_lite.task"
];

self.addEventListener("install", e => {
  e.waitUntil((async () => {
    const c = await caches.open(CACHE);
    // A missing vendor file (fetch_vendor.py not run yet) must not stop the app itself from being cached.
    await Promise.all([...SHELL, ...VENDOR].map(u => c.add(new Request(u, { cache: "reload" })).catch(() => null)));
    await self.skipWaiting();
  })());
});

self.addEventListener("activate", e => {
  e.waitUntil((async () => {
    for (const k of await caches.keys()) if (k !== CACHE) await caches.delete(k);
    await self.clients.claim();
  })());
});

self.addEventListener("fetch", e => {
  const req = e.request, url = new URL(req.url);
  if (url.origin !== self.location.origin) return;
  if (req.method === "HEAD") { e.respondWith(caches.match(req, { ignoreMethod: true, ignoreSearch: true }).then(r => r || fetch(req))); return; }
  if (req.method !== "GET") return;
  const shell = req.mode === "navigate" || url.pathname.endsWith("/index.html") || url.pathname.endsWith("/");
  if (shell) {
    e.respondWith(fetch(req).then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
      return res;
    }).catch(async () => (await caches.match(req, { ignoreSearch: true })) || (await caches.match("index.html")) || (await caches.match("./"))));
    return;
  }
  e.respondWith(caches.match(req, { ignoreSearch: true }).then(hit => hit || fetch(req).then(res => {
    if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
    return res;
  })));
});
