// Remote-control navigation for frameXTV, injected by MainActivity after each page load.
// The D-pad moves a focus ring between clickable things; OK clicks the one under the ring.
// The video player is a same-origin iframe: OK on it steps inside, onto its own controls.
// MainActivity calls window.__tv.move/select/back/media/focusAt/scrollAt and reads the results.
(function () {
  'use strict';

  // Lay the page out at this CSS width so it reads like the desktop site instead of the
  // 960px tablet layout a 1080p TV gets by default. 0 keeps the site's own viewport.
  var VIEWPORT = __VIEWPORT__;
  // The site centers its content in a 1280px column (max-w-7xl). That's the whole TV screen at
  // VIEWPORT, so narrow the column to get the boxed, margined look of a big desktop window.
  var CONTENT = __CONTENT__;
  var vp = document.querySelector('meta[name=viewport]');
  if (VIEWPORT) {
    if (!vp) {
      vp = document.createElement('meta');
      vp.name = 'viewport';
      (document.head || document.documentElement).appendChild(vp);
    }
    if (vp.content !== 'width=' + VIEWPORT) vp.content = 'width=' + VIEWPORT;
  }
  if (!document.getElementById('tv-style')) {
    var style = document.createElement('style');
    style.id = 'tv-style';
    style.textContent =
      (CONTENT ? '.container,.max-w-6xl,.max-w-7xl,.max-w-screen-xl,.max-w-screen-2xl{max-width:' + CONTENT + 'px!important}' : '') +
      // The header is 95% opaque black, so its backdrop blur can't be seen but costs the GPU
      // a full re-blur on every scroll frame.
      'header[class*="backdrop-blur"],nav[class*="backdrop-blur"]{-webkit-backdrop-filter:none!important;backdrop-filter:none!important}' +
      // No mouse on a TV: hide scrollbars.
      'html{scrollbar-width:none}::-webkit-scrollbar{display:none}' +
      // The "Welcome to frameXTV" popup. The site only remembers it for the browser session,
      // and every app launch is a new session, so it would greet you every time.
      '[data-testid="welcome-modal"]{display:none!important}';
    (document.head || document.documentElement).appendChild(style);
  }
  try { sessionStorage.setItem('framextv_welcome_seen', 'true'); } catch (e) { /* storage off */ }

  if (window.__tv) return;

  var SELECTOR = 'a[href],button,input:not([type=hidden]),select,textarea,iframe,video,summary,' +
    '[role=button],[role=link],[role=tab],[role=menuitem],[role=option],[role=checkbox],' +
    '[role=switch],[role=slider],[tabindex]:not([tabindex="-1"]),[onclick]';
  var TEXT_TYPES = /^(text|search|email|url|tel|password|number)$/;
  var PRIMARY = /^\s*(watch|play|resume|continue)/i;
  var SEEK_SECONDS = 10;
  var EASE = ' .16s cubic-bezier(.2,.8,.2,1)';
  var RING_TRANSITION = 'transform' + EASE + ',width' + EASE + ',height' + EASE + ',border-radius' + EASE;
  var RING_CSS = 'position:absolute;left:0;top:0;width:0;height:0;pointer-events:none;' +
    'z-index:2147483647;box-sizing:border-box;border:3px solid #fff;border-radius:10px;' +
    'box-shadow:0 0 0 3px rgba(0,0,0,.55),0 0 22px 4px rgba(255,255,255,.45);' +
    'will-change:transform;transition:' + RING_TRANSITION + ';';

  var cur = null;        // focused element, in the page or inside the player
  var curFixed = false;  // cur sits in a fixed/sticky bar, so the ring can't scroll with the page
  var frame = null;      // the player iframe while its controls have focus
  var opener = null;     // the player button last pressed, to come back to when its menu closes
  var lastPos = null;    // where cur was, to pick it back up after React re-renders it
  var lastHref = location.href;
  var memory = {};       // href -> where focus was when we left, for coming back
  var allowJumpUntil = 0;
  var touched = false;   // a key was pressed since this page arrived
  var dirty = true;
  var cache = [];
  var pointerish = new WeakMap(); // element -> outermost pointer-cursor element? (cursor rarely changes)
  var clippers = new WeakMap();   // element -> nearest ancestor that clips it sideways (a row), or null
  var placeQueued = false;

  new MutationObserver(function () { dirty = true; })
    .observe(document.documentElement, { childList: true, subtree: true });

  // ---- Geometry ----

  // Device pixels per CSS pixel, so MainActivity can line its pointer up with page elements.
  function scale() {
    return window.devicePixelRatio * (window.visualViewport ? window.visualViewport.scale : 1);
  }

  function frameDoc(f) {
    try { return f.contentDocument && f.contentDocument.body ? f.contentDocument : null; } catch (e) { return null; }
  }

  // Bounding rect in the main page's viewport, also for elements inside the player iframe.
  function rectOf(el) {
    var r = el.getBoundingClientRect();
    var f = el.ownerDocument === document ? null : el.ownerDocument.defaultView.frameElement;
    if (!f) return r;
    var o = f.getBoundingClientRect();
    var x = o.left + f.clientLeft, y = o.top + f.clientTop;
    return { left: r.left + x, top: r.top + y, right: r.right + x, bottom: r.bottom + y, width: r.width, height: r.height };
  }

  function valid(el) {
    if (!el || !el.isConnected) return false;
    if (el.ownerDocument !== document && (!frame || frameDoc(frame) !== el.ownerDocument)) return false;
    var r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  }

  // ---- What can be focused ----

  function isText(el) {
    if (!el || !el.tagName) return false;
    if (el.tagName === 'TEXTAREA' || el.isContentEditable) return true;
    return el.tagName === 'INPUT' && TEXT_TYPES.test(el.type || 'text');
  }

  function isRange(el) {
    return el.tagName === 'INPUT' && el.type === 'range' || el.getAttribute('role') === 'slider';
  }

  // Clickable: matches SELECTOR, or is the outermost element showing a pointer cursor (React
  // cards are often plain divs with onClick) and has something to show. That last part skips
  // the player's invisible tap zones.
  function collect(doc) {
    var main = doc === document;
    if (main && !dirty) return cache;
    var css = doc.defaultView.getComputedStyle;
    var out = [];
    var all = doc.body.getElementsByTagName('*');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      // Rows' "Scroll left/right" arrows are for mice; the D-pad scrolls rows by itself.
      if (/^scroll\b/i.test(el.getAttribute('aria-label') || '')) continue;
      if (el.matches(SELECTOR)) { out.push(el); continue; }
      if (el.ownerSVGElement) continue; // shapes inside an icon
      // Reading computed styles is the slow part of a key press, so remember the answer.
      var ptr = pointerish.get(el);
      if (ptr === undefined) {
        var p = el.parentElement;
        ptr = css(el).cursor === 'pointer' && !(p && css(p).cursor === 'pointer');
        pointerish.set(el, ptr);
      }
      if (!ptr) continue;
      if (!el.textContent.trim() && !el.querySelector('img,svg,picture,video') &&
          css(el).backgroundImage === 'none') continue;
      out.push(el);
    }
    if (main) { cache = out; dirty = false; }
    return out;
  }

  // Shown right now, counting fades. The player's range inputs are transparent by design,
  // so judge those by their track.
  function shown(el) {
    if (!el.checkVisibility) return true;
    var probe = isRange(el) && el.parentElement ? el.parentElement : el;
    return probe.checkVisibility({ opacityProperty: true, visibilityProperty: true });
  }

  function visible(el, r, inPlayer) {
    if (r.width < 4 || r.height < 4 || el.disabled) return false;
    // Player controls fade out when idle but stay usable: any key wakes them back up.
    if (inPlayer) return !el.checkVisibility || el.checkVisibility({ visibilityProperty: true });
    if (!shown(el)) return false;
    // Skip things hidden behind a dialog or overlay. Only checkable while on screen.
    var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    if (cx >= 0 && cy >= 0 && cx < innerWidth && cy < innerHeight) {
      var top = document.elementFromPoint(cx, cy);
      if (top && top !== el && !el.contains(top) && !top.contains(el)) return false;
    }
    return true;
  }

  // The open popup in doc: a dialog, or inside the player also a menu (Settings, Subtitles).
  function openPanel(doc) {
    var sel = '[role=dialog],[aria-modal=true]' + (doc === document ? '' : ',[role=menu]');
    var panels = doc.querySelectorAll(sel);
    for (var i = panels.length - 1; i >= 0; i--) {
      if (panels[i].getBoundingClientRect().width > 0) return panels[i];
    }
    return null;
  }

  // While a popup is open, only its contents are reachable.
  function scope(doc, list) {
    var p = openPanel(doc);
    return p ? list.filter(function (el) { return p.contains(el); }) : list;
  }

  // Popups in the player scroll inside themselves (16 subtitle tracks in a 6-row box).
  // Scroll the box so the focused row is fully inside it, clear of a sticky header.
  function revealInPanel(el) {
    var w = el.ownerDocument.defaultView;
    for (var p = el.parentElement; p && p !== el.ownerDocument.body; p = p.parentElement) {
      if (!/(auto|scroll)/.test(w.getComputedStyle(p).overflowY) || p.scrollHeight <= p.clientHeight + 2) continue;
      var e = el.getBoundingClientRect(), b = p.getBoundingClientRect();
      var head = p.firstElementChild && w.getComputedStyle(p.firstElementChild).position === 'sticky' &&
        !p.firstElementChild.contains(el) ? p.firstElementChild.getBoundingClientRect().height : 0;
      var dy = e.top < b.top + head + 6 ? e.top - b.top - head - 6 : e.bottom > b.bottom - 6 ? e.bottom - b.bottom + 6 : 0;
      if (dy) p.scrollBy({ top: dy, behavior: 'smooth' });
      return;
    }
  }

  // Inside the player after cur went away (a menu opened over it, switched pages, or closed):
  // land on the open menu's selected row, else back on the button that opened it.
  function recoverInPlayer() {
    var d = frame && frameDoc(frame);
    if (!d) return;
    var panel = openPanel(d);
    var list = candidates();
    if (panel) {
      // The current choice (a checked track, the "Connected" server), else the first option;
      // never the menu's X.
      var options = list.filter(function (c) { return !/^close/i.test(c.el.getAttribute('aria-label') || ''); });
      var pick = null;
      for (var i = 0; i < options.length && !pick; i++) {
        var el = options[i].el;
        // Pick-one rows only: an on/off toggle (Autoplay Next) being "checked" isn't the choice.
        var radio = /^(menuitemradio|radio|option)$/.test(el.getAttribute('role') || '');
        if (radio && (el.getAttribute('aria-checked') === 'true' || el.getAttribute('aria-selected') === 'true') ||
            !el.hasAttribute('role') && /\bconnected\b|✓/i.test(el.textContent)) pick = el;
      }
      pick = pick || first(options) || first(list);
      if (pick) setCur(pick);
    } else if (opener && opener.isConnected) {
      setCur(opener);
    }
  }

  function dialogOpen() {
    return !!openPanel(document);
  }

  function candidates() {
    var doc = document;
    if (frame) {
      doc = frame.isConnected && frameDoc(frame);
      if (!doc) { frame = null; doc = document; }
    }
    var out = [];
    var list = scope(doc, collect(doc));
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      var local = el.getBoundingClientRect();
      if (frame && deadInPlayer(el, local, doc)) continue;
      if (visible(el, local, !!frame)) out.push({ el: el, r: frame ? rectOf(el) : local });
    }
    return out;
  }

  // Player parts the D-pad shouldn't stop on: the video and other layers covering the whole
  // player (they swallow every Up/Down), the volume slider (Left/Right would change volume and
  // trap you before Subtitles/Settings/Fullscreen; the remote has volume keys), the centre
  // play button (same as the one in the bar) and the Cast button that can't work here.
  function deadInPlayer(el, r, doc) {
    if (el.tagName === 'VIDEO') return true;
    var w = doc.defaultView;
    if (r.width * r.height > w.innerWidth * w.innerHeight * 0.4) return true;
    var label = el.getAttribute('aria-label') || '';
    return isRange(el) && /volume/i.test(label) || /^(play|pause) stream$/i.test(label) ||
      /^cast unavailable/i.test(label);
  }

  // Where a real finger tap on el would land, in device pixels, when nothing covers it.
  // Fullscreen and some menus only open for a real press, not a scripted click.
  function tapPoint(el) {
    var d = el.ownerDocument, w = d.defaultView;
    var r = el.getBoundingClientRect();
    var x = r.left + r.width / 2, y = r.top + r.height / 2;
    if (x < 0 || y < 0 || x >= w.innerWidth || y >= w.innerHeight) return null;
    var hit = d.elementFromPoint(x, y);
    if (!hit || (hit !== el && !el.contains(hit))) return null;
    var m = rectOf(el), k = scale();
    if (d !== document && !d.fullscreenElement) {
      var top = document.elementFromPoint(m.left + m.width / 2, m.top + m.height / 2);
      if (top !== frame) return null;
    }
    return { t: 'tap', x: (m.left + m.width / 2) * k, y: (m.top + m.height / 2) * k };
  }

  // The horizontal span of the screen el can actually show in: the screen, narrowed by the row
  // that scrolls it sideways. Cards past a row's edge are cut off even though they're on screen.
  function lane(el) {
    var c = clippers.get(el);
    if (c === undefined) {
      c = null;
      var css = el.ownerDocument.defaultView.getComputedStyle;
      for (var p = el.parentElement; p && p !== el.ownerDocument.body; p = p.parentElement) {
        if (css(p).overflowX !== 'visible') { c = p; break; }
      }
      clippers.set(el, c);
    }
    var left = 0, right = innerWidth;
    if (c && c.isConnected) {
      var r = c.getBoundingClientRect();
      left = Math.max(left, r.left);
      right = Math.min(right, r.right);
    }
    return { left: left, right: right };
  }

  function isFixed(el) {
    for (var e = el; e && e !== document.body; e = e.parentElement) {
      var p = getComputedStyle(e).position;
      if (p === 'fixed' || p === 'sticky') return true;
    }
    return false;
  }

  // frameXTV serves its player from its own origin (/embed/...), so its controls are reachable.
  // Don't wait for the <video>: it shows up a while after the page does.
  function playerFrame(el) {
    return el.tagName === 'IFRAME' ? frameDoc(el) : null;
  }

  // Where focus lands on a new page: the player, else a Watch/Play button, else the first
  // thing on screen in reading order, skipping the fixed header bar when there's anything else.
  // The page's video player, if one is on screen. Found directly rather than through
  // candidates(): a loading overlay covers it for its first seconds, which would hide it.
  function player() {
    if (frame || dialogOpen()) return null;
    var frames = document.querySelectorAll('iframe');
    for (var i = 0; i < frames.length; i++) {
      var r = frames[i].getBoundingClientRect();
      if (r.width > innerWidth * 0.4 && r.bottom > 0 && r.top < innerHeight && playerFrame(frames[i])) return frames[i];
    }
    return null;
  }

  function first(list) {
    var p = player();
    if (p) return p;
    var onScreen = list.filter(function (c) {
      var r = c.r, l = frame ? { left: 0, right: innerWidth } : lane(c.el);
      return r.bottom > 0 && r.top < innerHeight && r.right > l.left && r.left < l.right;
    });
    for (var i = 0; i < onScreen.length; i++) {
      if (PRIMARY.test(onScreen[i].el.textContent || '')) return onScreen[i].el;
    }
    var best = null, bestScore = Infinity, bestFixed = null, bestFixedScore = Infinity;
    for (i = 0; i < onScreen.length; i++) {
      var r = onScreen[i].r;
      var s = Math.max(r.top, 0) * 3 + Math.max(r.left, 0);
      if (frame || !isFixed(onScreen[i].el)) {
        if (s < bestScore) { bestScore = s; best = onScreen[i].el; }
      } else if (s < bestFixedScore) { bestFixedScore = s; bestFixed = onScreen[i].el; }
    }
    return best || bestFixed;
  }

  // ---- The ring ----

  function ringIn(doc) {
    if (!doc.__tvRing) {
      var r = doc.createElement('div');
      r.setAttribute('data-tv-ring', '');
      r.style.cssText = RING_CSS;
      doc.__tvRing = r;
    }
    return doc.__tvRing;
  }

  function place() {
    if (cur && !cur.isConnected && cur.ownerDocument === document && lastPos) {
      cur = null;
      reacquire();
    } else if (frame && cur && !cur.isConnected) {
      cur = null;
      recoverInPlayer();
    }
    var ring = null, r = null, parent = null, fixed = true;
    if (valid(cur) && (!frame || shown(cur))) {
      var d = cur.ownerDocument;
      if (d !== document && d.fullscreenElement) {
        // A fullscreen player hides everything else, so the ring has to live inside it.
        ring = ringIn(d); r = cur.getBoundingClientRect(); parent = d.fullscreenElement;
      } else {
        ring = ringIn(document); r = rectOf(cur); parent = document.fullscreenElement || document.documentElement;
        // Pinned to the page, the ring scrolls with it on the compositor instead of chasing
        // the page from script. Only fixed bars and fullscreen need viewport coordinates.
        fixed = curFixed || !!document.fullscreenElement;
      }
    }
    var docs = [document];
    var fd = frame && frameDoc(frame);
    if (fd) docs.push(fd);
    docs.forEach(function (d) { if (d.__tvRing && d.__tvRing !== ring) d.__tvRing.remove(); });
    if (!ring) return;
    var position = fixed ? 'fixed' : 'absolute';
    var jump = ring.parentNode !== parent || ring.style.position !== position;
    if (ring.parentNode !== parent) parent.appendChild(ring);
    // Switching coordinate systems: snap instead of sliding across the screen.
    if (jump) { ring.style.transition = 'none'; ring.style.position = position; }
    var x = r.left - 5 + (fixed ? 0 : scrollX), y = r.top - 5 + (fixed ? 0 : scrollY);
    var radius = parseFloat(cur.ownerDocument.defaultView.getComputedStyle(cur).borderTopLeftRadius) || 6;
    ring.style.transform = 'translate3d(' + x + 'px,' + y + 'px,0)';
    ring.style.width = (r.width + 10) + 'px';
    ring.style.height = (r.height + 10) + 'px';
    ring.style.borderRadius = (radius + 5) + 'px';
    if (jump) { void ring.offsetWidth; ring.style.transition = RING_TRANSITION; }
  }

  // Many scroll events per frame while scrolling: re-place at most once per frame.
  function placeSoon() {
    if (placeQueued) return;
    placeQueued = true;
    requestAnimationFrame(function () { placeQueued = false; place(); });
  }

  // ---- Moving ----

  function fire(el, type, related, x, y) {
    var w = el.ownerDocument.defaultView;
    var Ctor = type.indexOf('pointer') === 0 && w.PointerEvent ? w.PointerEvent : w.MouseEvent;
    el.dispatchEvent(new Ctor(type, {
      bubbles: true, cancelable: true, view: w, relatedTarget: related || null,
      clientX: x || 0, clientY: y || 0, pointerType: 'mouse'
    }));
  }

  // Player controls hide after a few idle seconds; a mouse move brings them back.
  function wake() {
    var d = frame && frameDoc(frame);
    if (!d) return;
    var t = valid(cur) && cur.ownerDocument === d ? cur : d.querySelector('video');
    if (!t) return;
    var r = t.getBoundingClientRect();
    var x = r.left + r.width / 2, y = r.top + r.height / 2;
    fire(t, 'pointermove', null, x, y);
    fire(t, 'mousemove', null, x, y);
  }

  function setCur(el) {
    var old = cur;
    if (old === el) return;
    if (old && old.isConnected) {
      fire(old, 'pointerout', el); fire(old, 'mouseout', el); fire(old, 'mouseleave', el);
      var ae = old.ownerDocument.activeElement;
      if (isText(ae)) ae.blur();
    }
    cur = el;
    fire(el, 'pointerover', old); fire(el, 'mouseover', old); fire(el, 'mouseenter', old);

    if (el.ownerDocument !== document) revealInPanel(el);
    // Inside the player only the player itself needs to be on screen.
    var target = el.ownerDocument === document ? el : frame;
    if (target) {
      var r = target.getBoundingClientRect();
      var l = lane(target);
      var outV = r.top < 72 && r.height < innerHeight - 72 || r.bottom > innerHeight - 24 && r.top > 72;
      // Sideways, scroll the row just enough to show the next card, one card at a time.
      var outH = r.left < l.left - 1 || r.right > l.right + 1;
      if (outV || outH) {
        target.scrollIntoView({ behavior: 'smooth', block: outV ? 'center' : 'nearest', inline: 'nearest' });
      }
    }
    var p = rectOf(el);
    lastPos = { x: p.left + p.width / 2, y: p.top + p.height / 2 };
    curFixed = el.ownerDocument === document && isFixed(el);
    place();
    // Catch up with the site's hover animations (cards grow a little when focused).
    setTimeout(place, 220);
    setTimeout(place, 480);
  }

  function shownSideways(el, x) {
    if (frame) return x >= 0 && x <= innerWidth;
    var l = lane(el);
    return x >= l.left && x <= l.right;
  }

  function pageScroll(dir) {
    if (!frame && (dir === 'up' || dir === 'down')) {
      window.scrollBy({ top: (dir === 'down' ? 1 : -1) * innerHeight * 0.6, behavior: 'smooth' });
    }
  }

  function videoNear() {
    var docs = [frame && frameDoc(frame)];
    var frames = document.querySelectorAll('iframe');
    for (var i = 0; i < frames.length; i++) docs.push(frameDoc(frames[i]));
    docs.push(document);
    for (i = 0; i < docs.length; i++) {
      var v = docs[i] && docs[i].querySelector('video');
      if (v) return v;
    }
    return null;
  }

  // ---- Seeking ----
  // TV-style scrubbing: presses add up to a target shown in a bubble, and the video jumps
  // once when you stop, instead of re-buffering on every press. Steps grow while you keep going.

  var seek = null;    // { video, start, target, count, last, timer } while presses are adding up
  var osd = null;     // the bubble, and the timer that hides it
  var osdTimer = 0;

  function clock(s) {
    s = Math.max(0, Math.round(s));
    var h = Math.floor(s / 3600), m = Math.floor(s / 60) % 60, sec = s % 60;
    return (h ? h + ':' + (m < 10 ? '0' : '') : '') + m + ':' + (sec < 10 ? '0' : '') + sec;
  }

  // Watching rather than working the controls: the ring on the player itself, the player's
  // controls faded out (as in fullscreen), or the ring on its seek bar.
  function watching() {
    if (!valid(cur)) return false;
    if (!frame) return !!playerFrame(cur) && cur === player();
    return !shown(cur) || isRange(cur);
  }

  function seekVideo() {
    var p = frame || player();
    var d = p && frameDoc(p);
    return d && d.querySelector('video') || videoNear();
  }

  function seekBy(sign) {
    var v = seekVideo();
    if (!v || !isFinite(v.duration)) return false;
    var now = Date.now();
    if (!seek || seek.video !== v) seek = { video: v, start: v.currentTime, target: v.currentTime, count: 0, last: 0 };
    seek.count = now - seek.last < 800 ? seek.count + 1 : 1;
    seek.last = now;
    var step = seek.count > 14 ? 60 : seek.count > 5 ? 30 : SEEK_SECONDS;
    seek.target = Math.max(0, Math.min(v.duration - 1, seek.target + sign * step));
    showOsd();
    clearTimeout(seek.timer);
    seek.timer = setTimeout(commitSeek, 700);
    return true;
  }

  function commitSeek() {
    if (!seek) return;
    clearTimeout(seek.timer);
    seek.video.currentTime = seek.target;
    seek = null;
    wake();
    hideOsdSoon(900);
  }

  function cancelSeek() {
    if (!seek) return false;
    clearTimeout(seek.timer);
    seek = null;
    hideOsdSoon(0);
    return true;
  }

  function showOsd() {
    var v = seek.video, d = v.ownerDocument;
    var fs = d.fullscreenElement;
    var host = fs ? d : document;
    if (!osd || osd.ownerDocument !== host) {
      if (osd) osd.remove();
      osd = host.createElement('div');
      osd.style.cssText = 'position:fixed;z-index:2147483647;pointer-events:none;transform:translate(-50%,-50%);' +
        'min-width:340px;padding:14px 22px 16px;border-radius:14px;background:rgba(9,9,11,.86);' +
        'border:1px solid rgba(255,255,255,.14);color:#fff;text-align:center;' +
        'font:600 24px/1.25 system-ui,sans-serif;font-variant-numeric:tabular-nums;';
      osd.appendChild(host.createElement('div'));
      var track = host.createElement('div');
      track.style.cssText = 'height:4px;margin-top:12px;border-radius:2px;background:rgba(255,255,255,.22);overflow:hidden';
      var fill = host.createElement('div');
      fill.style.cssText = 'height:100%;background:#e50914';
      track.appendChild(fill);
      osd.appendChild(track);
    }
    // Over the lower part of the player, wherever it is on screen.
    var r = fs ? { left: 0, top: 0, width: d.defaultView.innerWidth, height: d.defaultView.innerHeight }
      : (d === document ? v : d.defaultView.frameElement).getBoundingClientRect();
    osd.style.left = (r.left + r.width / 2) + 'px';
    osd.style.top = (r.top + r.height * 0.72) + 'px';
    var delta = seek.target - seek.start;
    osd.firstChild.textContent = (delta < 0 ? '⏪ -' : '⏩ +') + clock(Math.abs(delta)) +
      '  ·  ' + clock(seek.target) + ' / ' + clock(v.duration);
    osd.firstChild.style.whiteSpace = 'pre';
    osd.lastChild.firstChild.style.width = (seek.target / v.duration * 100) + '%';
    var parent = fs || document.documentElement;
    if (osd.parentNode !== parent) parent.appendChild(osd);
    osd.style.opacity = '1';
    clearTimeout(osdTimer);
  }

  function hideOsdSoon(ms) {
    clearTimeout(osdTimer);
    osdTimer = setTimeout(function () { if (osd && !seek) osd.remove(); }, ms);
  }

  function move(dir) {
    touched = true;
    // Left/Right while watching (or mid-seek) scrubs. Decide before waking the controls,
    // since waking is what makes them count as "in use".
    if ((dir === 'left' || dir === 'right') && (seek || watching()) && seekBy(dir === 'right' ? 1 : -1)) return;
    if (seek) commitSeek();
    wake();
    var list = candidates();
    if (!valid(cur)) {
      var f = first(list);
      if (f) setCur(f); else pageScroll(dir);
      return;
    }
    var a = rectOf(cur);
    var ax = a.left + a.width / 2, ay = a.top + a.height / 2;
    var horiz = dir === 'left' || dir === 'right';
    var best = null, bestScore = Infinity;

    for (var i = 0; i < list.length; i++) {
      var el = list[i].el, b = list[i].r;
      if (el === cur || el.contains(cur)) continue;
      var inside = cur.contains(el);
      var bx = b.left + b.width / 2, by = b.top + b.height / 2;
      var main;
      // A target has to start beyond where cur starts, so something wide (the player's
      // seek bar) doesn't count as "to the right" of every button under it.
      if (dir === 'right') {
        if (bx <= ax + 2 || (!inside && (b.right <= a.right || b.left <= a.left))) continue;
        main = Math.max(0, b.left - a.right);
      } else if (dir === 'left') {
        if (bx >= ax - 2 || (!inside && (b.left >= a.left || b.right >= a.right))) continue;
        main = Math.max(0, a.left - b.right);
      } else if (dir === 'down') {
        if (by <= ay + 2 || (!inside && (b.bottom <= a.bottom || b.top <= a.top))) continue;
        if (!shownSideways(el, bx)) continue; // scrolled-away cards in other rows
        main = Math.max(0, b.top - a.bottom);
      } else {
        if (by >= ay - 2 || (!inside && (b.top >= a.top || b.bottom >= a.bottom))) continue;
        if (!shownSideways(el, bx)) continue;
        main = Math.max(0, a.top - b.bottom);
      }
      // Gap across the movement axis: 0 when the two overlap (same row / same column).
      var gap = horiz
        ? Math.max(0, Math.max(a.top, b.top) - Math.min(a.bottom, b.bottom))
        : Math.max(0, Math.max(a.left, b.left) - Math.min(a.right, b.right));
      var drift = horiz ? Math.abs(by - ay) : Math.abs(bx - ax);
      var score = main + gap * (horiz ? 5 : 2) + drift * 0.1;
      // Small buttons inside a card (Add to My List) only when nothing else is that way,
      // so Right goes card to card.
      if (inside) score += 600;
      if (score < bestScore) { bestScore = score; best = el; }
    }

    if (best) setCur(best); else pageScroll(dir);
  }

  // ---- Selecting ----

  function remember() {
    if (valid(cur) && cur.ownerDocument === document) {
      var r = cur.getBoundingClientRect();
      memory[location.href] = { scroll: scrollY, x: r.left + r.width / 2, y: r.top + r.height / 2 };
    }
  }

  function enterPlayer(f) {
    frame = f;
    opener = null;
    var d = frameDoc(f);
    // Menus scroll inside the player's own page; those scrolls never reach this window.
    if (d && !d.__tvScroll) { d.__tvScroll = true; d.addEventListener('scroll', placeSoon, true); }
    wake();
    // Give the controls a moment to fade in, then start on play/pause.
    setTimeout(function () {
      if (frame !== f) return;
      var list = candidates();
      var start = null;
      for (var i = 0; i < list.length && !start; i++) {
        if (/^(play|pause)$/i.test(list[i].el.getAttribute('aria-label') || '')) start = list[i].el;
      }
      start = start || first(list);
      if (start) setCur(start);
    }, 200);
  }

  function select() {
    touched = true;
    allowJumps();
    // OK mid-seek: jump there now.
    if (seek) { commitSeek(); return { t: 'seek' }; }
    if (!valid(cur)) {
      var f = first(candidates());
      if (f) setCur(f);
      return null;
    }
    var el = cur;
    if (frame) {
      // First press only brings hidden controls back, so nothing gets clicked blind.
      var hidden = !shown(el);
      wake();
      if (hidden) return { t: 'wake' };
      if (isRange(el)) {
        var v = el.ownerDocument.querySelector('video');
        if (v) { if (v.paused) v.play(); else v.pause(); }
        return { t: 'video' };
      }
    } else if (el.tagName === 'IFRAME') {
      if (playerFrame(el)) { enterPlayer(el); return { t: 'player' }; }
      // Another site's page: MainActivity switches to the pointer.
      var r = el.getBoundingClientRect(), k = scale();
      return { t: 'frame', x: (r.left + r.width / 2) * k, y: (r.top + r.height / 2) * k };
    }
    if (isText(el)) {
      el.focus();
      el.click();
      return { t: 'input' };
    }
    if (el.tagName === 'VIDEO') {
      if (el.paused) el.play(); else el.pause();
      return { t: 'video' };
    }
    if (frame) {
      var open = openPanel(el.ownerDocument);
      if (!(open && open.contains(el))) opener = el;
    }
    remember();
    // Buttons (and everything in the player) get a real tap from MainActivity. Cards keep the
    // scripted click: a tap on their centre would hit the play icon drawn over the poster.
    if (frame || el.tagName === 'BUTTON' || el.getAttribute('role') === 'button') {
      var tap = tapPoint(el);
      if (tap) return tap;
    }
    var c = el.getBoundingClientRect(), x = c.left + c.width / 2, y = c.top + c.height / 2;
    fire(el, 'pointerdown', null, x, y); fire(el, 'mousedown', null, x, y);
    fire(el, 'pointerup', null, x, y); fire(el, 'mouseup', null, x, y);
    el.click();
    return { t: 'click' };
  }

  // Back: leave fullscreen, the player controls, the keyboard, or a dialog. False when there
  // is nothing to close, so MainActivity goes back in history instead.
  function back() {
    if (cancelSeek()) return true;
    if (frame) {
      var d = frameDoc(frame);
      // An open player menu closes first, through its own X (Escape doesn't close them).
      var panel = d && openPanel(d);
      if (panel) {
        var x = panel.querySelector('button[aria-label^="close" i]');
        if (x) x.click(); else if (opener && opener.isConnected) opener.click();
        if (opener && opener.isConnected) setCur(opener);
        // Keep the controls up, so the next Left/Right moves along them instead of seeking.
        wake();
        return true;
      }
      if (d && d.fullscreenElement) { d.exitFullscreen(); return true; }
      var f = frame;
      frame = null;
      if (f.isConnected) setCur(f);
      return true;
    }
    if (document.fullscreenElement) { document.exitFullscreen(); return true; }
    var ae = document.activeElement;
    if (isText(ae)) { ae.blur(); return true; }
    var dialogs = document.querySelectorAll('[role=dialog],[aria-modal=true]');
    for (var i = 0; i < dialogs.length; i++) {
      if (dialogs[i].getBoundingClientRect().width > 0) {
        var t = ae || document.body;
        t.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, bubbles: true }));
        return true;
      }
    }
    allowJumps();
    return false;
  }

  // Remote media keys.
  function media(cmd) {
    var v = videoNear();
    if (!v) return;
    wake();
    if (cmd === 'toggle') { if (v.paused) v.play(); else v.pause(); }
    else if (cmd === 'play') v.play();
    else if (cmd === 'pause') v.pause();
    else seekBy(cmd === 'forward' ? 1 : -1);
  }

  // ---- Pointer mode hand-offs (device pixels) ----

  function candidateAt(doc, x, y) {
    var el = doc.elementFromPoint(x, y);
    var list = collect(doc);
    while (el && list.indexOf(el) < 0) el = el.parentElement;
    return el;
  }

  // Leaving pointer mode: put the focus ring on whatever the pointer was over.
  function focusAt(x, y) {
    var k = scale();
    x /= k; y /= k;
    var el = candidateAt(document, x, y);
    var d = el && playerFrame(el);
    if (d) {
      var o = el.getBoundingClientRect();
      var inner = candidateAt(d, x - o.left - el.clientLeft, y - o.top - el.clientTop);
      if (inner) { frame = el; setCur(inner); return; }
    }
    frame = null;
    if (el) setCur(el);
  }

  // Pointer pushed against a screen edge: scroll whatever is under it.
  function scrollAt(x, y, dx, dy) {
    var k = scale();
    dx /= k; dy /= k;
    var el = document.elementFromPoint(x / k, y / k);
    for (; el && el !== document.body; el = el.parentElement) {
      var cs = getComputedStyle(el);
      var canY = dy && /(auto|scroll)/.test(cs.overflowY) && el.scrollHeight > el.clientHeight;
      var canX = dx && /(auto|scroll)/.test(cs.overflowX) && el.scrollWidth > el.clientWidth;
      if (canY || canX) { el.scrollBy(dx, dy); return; }
    }
    window.scrollBy(dx, dy);
  }

  // ---- Page changes ----

  // Ads inside players call history.back() to bounce you off the page. Only let history
  // jumps through right after you pressed something.
  function allowJumps() { allowJumpUntil = Date.now() + 1500; }
  ['pointerdown', 'keydown'].forEach(function (t) { addEventListener(t, allowJumps, true); });
  if (window.navigation) {
    navigation.addEventListener('navigate', function (e) {
      if (e.navigationType === 'traverse' && e.cancelable && Date.now() > allowJumpUntil) e.preventDefault();
    });
  }

  function reacquire() {
    var el = lastPos && candidateAt(document, lastPos.x, lastPos.y);
    if (el) setCur(el);
  }

  function autofocus() {
    if (valid(cur)) return;
    var f = first(candidates());
    if (f) setCur(f);
  }

  // New page: start on its main action, or where you were if you've come back to it.
  function arrived() {
    cur = null;
    frame = null;
    lastPos = null;
    touched = false;
    place();
    var m = memory[location.href];
    setTimeout(function () {
      if (valid(cur)) return;
      if (!m) { autofocus(); return; }
      if (Math.abs(scrollY - m.scroll) > 40) window.scrollTo(0, m.scroll);
      setTimeout(function () {
        if (valid(cur)) return;
        var el = candidateAt(document, m.x, m.y);
        if (el) setCur(el); else autofocus();
      }, 250);
    }, 700);
  }

  // Page scrolls move the ring by themselves; carousels and other inner scrollers don't.
  window.addEventListener('scroll', function (e) {
    if (curFixed || e.target !== document) placeSoon();
  }, true);
  window.addEventListener('resize', placeSoon);
  setInterval(function () {
    // If the welcome popup opened before we could mark it seen, close it properly (hidden,
    // it would still lock the page behind it).
    var welcome = document.querySelector('[data-testid="welcome-continue-btn"]');
    if (welcome) welcome.click();
    if (location.href !== lastHref) { lastHref = location.href; arrived(); return; }
    // A player menu just opened (they open above their buttons): step into it.
    if (frame) {
      var pd = frameDoc(frame), pp = pd && openPanel(pd);
      if (pp && !(cur && pp.contains(cur))) recoverInPlayer();
    }
    // The player loads after the page; if you haven't moved yet, start on it once it's there.
    if (!touched) {
      var p = player();
      if (p && cur !== p) setCur(p);
    }
    if (cur) place();
  }, 300);

  window.__tv = { move: move, select: select, back: back, media: media, focusAt: focusAt, scrollAt: scrollAt };

  // Show the ring right away so it's clear the remote works.
  setTimeout(autofocus, 1500);
})();
