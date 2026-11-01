'use strict';

const PAGE_W = 210;
const PAGE_H = 297;

const state = {
  page: { marginX: 8, marginY: 8, gap: 6, rows: 2, pages: 1, cutLines: true, outline: false },
  header: {
    enabled: true,
    title: 'Take what you need',
    desc: 'Pull off a strip – it is free!',
    height: 45,
    align: 'center',
    titleSize: 22,
    descSize: 12,
    image: null,
    imagePos: 'left',
    imageSize: 30,
    imageBgOn: false,
    imageBg: '#222222',
    imageRecolor: false,
  },
  strips: {
    count: 10,
    length: 70,
    direction: 'up',
    padding: 1.5,
    font: 'system-ui, sans-serif',
    fontSize: 9,
    align: 'center',
    bold: false,
    italic: false,
    underline: false,
    mode: 'same',
    text: 'A smile for you',
    link: '',
    image: null,
    imageSize: 12,
    imageBgOn: false,
    imageBg: '#222222',
    imageRecolor: false,
    qrSize: 15,
    items: [],
  },
};

const preview = document.getElementById('preview');
const itemsBox = document.getElementById('items');

/* ---------- helpers ---------- */

function getPath(path) {
  return path.split('.').reduce((o, k) => o[k], state);
}

function setPath(path, value) {
  const keys = path.split('.');
  const last = keys.pop();
  keys.reduce((o, k) => o[k], state)[last] = value;
}

function esc(str) {
  return String(str).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// Escaped text with **bold** and *italic* markup for parts of a line
function inlineFormat(str) {
  return esc(str)
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
    .replace(/\*(.+?)\*/g, '<i>$1</i>');
}

function readImage(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

const qrCache = new Map();

function qrSvg(text, sizeMm) {
  if (typeof qrcode === 'undefined') return '';
  let body = qrCache.get(text);
  if (!body) {
    if (qrcode.stringToBytesFuncs && qrcode.stringToBytesFuncs['UTF-8']) {
      qrcode.stringToBytes = qrcode.stringToBytesFuncs['UTF-8'];
    }
    const qr = qrcode(0, 'M');
    qr.addData(text);
    qr.make();
    const n = qr.getModuleCount();
    const quiet = 1;
    let d = '';
    for (let r = 0; r < n; r++) {
      for (let c = 0; c < n; c++) {
        if (qr.isDark(r, c)) d += `M${c + quiet} ${r + quiet}h1v1h-1z`;
      }
    }
    const total = n + quiet * 2;
    body = { total, d };
    qrCache.set(text, body);
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${body.total} ${body.total}" ` +
    `style="width:${sizeMm}mm;height:${sizeMm}mm" shape-rendering="crispEdges">` +
    `<rect width="100%" height="100%" fill="#fff"/><path d="${body.d}" fill="#000"/></svg>`;
}

/* ---------- layout ---------- */

function metrics() {
  const { page, header, strips } = state;
  const innerW = PAGE_W - 2 * page.marginX;
  const innerH = PAGE_H - 2 * page.marginY;
  const zettelH = (header.enabled ? header.height : 0) + strips.length;
  const fit = Math.max(0, Math.floor((innerH + page.gap) / (zettelH + page.gap)));
  const stripW = innerW / Math.max(1, strips.count);
  return { innerW, innerH, zettelH, fit, stripW };
}

function stripContent(i) {
  const s = state.strips;
  if (s.mode !== 'individual') return { text: s.text, link: s.link, image: s.image };
  const it = s.items[i] || {};
  return {
    text: it.text || s.text,
    link: it.link || s.link,
    image: it.image || s.image,
  };
}

/*
 * White artwork on a transparent background (e.g. logo lettering) vanishes on
 * paper. Recolor only the white areas that touch transparency to black, so
 * white shapes enclosed by other colors (inside a sign, say) stay white.
 */
const recolorCache = new Map();

function recolorWhite(src) {
  const img = new Image();
  img.onload = () => {
    const c = document.createElement('canvas');
    c.width = img.naturalWidth;
    c.height = img.naturalHeight;
    const ctx = c.getContext('2d');
    ctx.drawImage(img, 0, 0);
    const data = ctx.getImageData(0, 0, c.width, c.height);
    const px = data.data;
    const w = c.width, h = c.height;
    const isClear = i => px[i * 4 + 3] < 10;
    const isLight = i => px[i * 4 + 3] >= 10 &&
      Math.min(px[i * 4], px[i * 4 + 1], px[i * 4 + 2]) > 170;

    const seen = new Uint8Array(w * h);
    const queue = [];
    for (let i = 0; i < w * h; i++) {
      if (!isClear(i)) continue;
      const x = i % w;
      for (const n of [i - w, i + w, x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1]) {
        if (n >= 0 && n < w * h && !seen[n] && isLight(n)) { seen[n] = 1; queue.push(n); }
      }
    }
    while (queue.length) {
      const i = queue.pop();
      px[i * 4] = px[i * 4 + 1] = px[i * 4 + 2] = 0;
      const x = i % w;
      for (const n of [i - w, i + w, x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1]) {
        if (n >= 0 && n < w * h && !seen[n] && isLight(n)) { seen[n] = 1; queue.push(n); }
      }
    }
    ctx.putImageData(data, 0, 0);
    recolorCache.set(src, c.toDataURL('image/png'));
    render();
  };
  img.src = src;
}

function imageSrc(src, opts) {
  if (!opts.imageRecolor) return src;
  if (!recolorCache.has(src)) {
    recolorCache.set(src, src);
    recolorWhite(src);
  }
  return recolorCache.get(src);
}

// Backdrop for images with white/transparent parts that would vanish on paper
function imageBg(opts) {
  return opts.imageBgOn ? `background:${opts.imageBg};padding:0.8mm;border-radius:0.8mm` : '';
}

function renderHeader(innerW) {
  const h = state.header;
  const textAlign = `text-align:${h.align}`;
  let img = '';
  if (h.image) {
    const size = h.imagePos === 'top'
      ? `max-height:${h.imageSize}mm;max-width:100%`
      : `width:${h.imageSize}mm`;
    img = `<img src="${esc(imageSrc(h.image, h))}" alt="" style="${size};${imageBg(h)}">`;
  }
  const title = h.title ? `<h2 style="font-size:${h.titleSize}pt">${esc(h.title)}</h2>` : '';
  const desc = h.desc ? `<p style="font-size:${h.descSize}pt">${esc(h.desc)}</p>` : '';
  return `<div class="head img-${h.imagePos}" style="height:${h.height}mm;width:${innerW}mm">` +
    `${img}<div class="head-text" style="${textAlign}">${title}${desc}</div></div>`;
}

function renderStrip(i, stripW) {
  const s = state.strips;
  const { text, link, image } = stripContent(i);
  const pad = s.padding;
  const cross = Math.max(1, stripW - 2 * pad);
  const rotation = s.direction === 'up' ? -90 : 90;

  const parts = [];
  if (image) {
    parts.push(`<img src="${esc(imageSrc(image, s))}" alt="" style="width:${s.imageSize}mm;height:${cross}mm;${imageBg(s)}">`);
  }
  const textStyle = [
    `font-family:${s.font}`,
    `font-size:${s.fontSize}pt`,
    `text-align:${s.align}`,
    `font-weight:${s.bold ? 700 : 400}`,
    `font-style:${s.italic ? 'italic' : 'normal'}`,
    `text-decoration:${s.underline ? 'underline' : 'none'}`,
    `max-height:${cross}mm`,
  ].join(';');
  parts.push(`<div class="strip-text" style="${textStyle}">${inlineFormat(text)}</div>`);
  if (link) parts.push(qrSvg(link, Math.min(s.qrSize, cross)));

  const innerStyle = `width:${s.length}mm;height:${stripW}mm;padding:${pad}mm;` +
    `transform:translate(-50%,-50%) rotate(${rotation}deg)`;
  return `<div class="strip" style="width:${stripW}mm;height:${s.length}mm">` +
    `<div class="strip-inner" style="${innerStyle}">${parts.join('')}</div></div>`;
}

function renderZettel(m) {
  const { header, strips, page } = state;
  let html = `<div class="zettel${page.outline ? ' outline' : ''}" style="width:${m.innerW}mm;height:${m.zettelH}mm">`;
  if (header.enabled) html += renderHeader(m.innerW);
  html += `<div class="strips" style="${header.enabled ? '' : 'border-top:0'}">`;
  for (let i = 0; i < strips.count; i++) html += renderStrip(i, m.stripW);
  return html + '</div></div>';
}

function render() {
  const m = metrics();
  const { page } = state;
  const zettel = renderZettel(m);

  const gap = `<div class="${page.cutLines ? 'cut' : ''}" style="height:${page.gap}mm"></div>`;
  const rows = Array.from({ length: page.rows }, () => zettel).join(gap);
  const pageHtml = `<div class="page" style="padding:${page.marginY}mm ${page.marginX}mm">${rows}</div>`;
  preview.innerHTML = pageHtml.repeat(page.pages);

  updateStatus(m);
  syncControls();
  applyZoom();
}

/* ---------- status & warnings ---------- */

function updateStatus(m) {
  const { page, strips, header } = state;
  document.getElementById('summary').textContent = t('summary', {
    rows: page.rows, count: strips.count, total: page.rows * strips.count, pages: page.pages,
  });

  document.getElementById('fitHelp').textContent = t('fitHelp', { height: fmt(m.zettelH), fit: m.fit });

  const cross = Math.max(0, m.stripW - 2 * strips.padding);
  document.getElementById('stripHelp').textContent =
    t('stripHelp', { width: fmt(m.stripW, 1), usable: fmt(cross, 1) });

  const issues = [];
  if (page.rows > m.fit) issues.push(t('issueRows', { fit: m.fit }));

  const links = new Set();
  for (let i = 0; i < strips.count; i++) {
    const { link } = stripContent(i);
    if (link) links.add(link);
  }
  const qrHelp = document.getElementById('qrHelp');
  qrHelp.textContent = '';
  qrHelp.classList.remove('warn');
  const qrMm = Math.min(strips.qrSize, cross);
  let worstModule = Infinity;
  for (const link of links) {
    if (!/^[a-z][a-z0-9+.-]*:/i.test(link)) issues.push(t('issueScheme', { link }));
    const body = qrCache.get(link);
    if (body) worstModule = Math.min(worstModule, qrMm / body.total);
  }
  if (links.size) {
    if (worstModule < Infinity) {
      qrHelp.textContent = t('qrHelp', { size: fmt(qrMm, 1), dot: fmt(worstModule, 2) });
    }
    if (strips.qrSize > cross) qrHelp.textContent += t('qrLimited', { set: fmt(strips.qrSize) });
    if (worstModule < 0.4) {
      qrHelp.classList.add('warn');
      issues.push(t('issueQrSmall'));
    }
  }

  // Overflow checks on the first rendered Zupfzettel
  const first = preview.querySelector('.zettel');
  if (first) {
    const cutText = [...first.querySelectorAll('.strip-text')]
      .some(t => t.scrollHeight > t.clientHeight + 1 || t.scrollWidth > t.clientWidth + 1);
    if (cutText) issues.push(t('issueText'));
    const head = first.querySelector('.head');
    const headText = first.querySelector('.head-text');
    if (header.enabled && head && headText && headText.offsetHeight > head.clientHeight) {
      issues.push(t('issueHead'));
    }
  }

  document.getElementById('issues').innerHTML = issues
    .map(t => `<div class="issue"><svg class="icon"><use href="#i-warn"/></svg><span>${esc(t)}</span></div>`)
    .join('');
}

/* ---------- language ---------- */

const LANGS = Object.keys(I18N);
let lang = 'de';

function t(key, vars = {}) {
  const entry = I18N[lang][key] ?? I18N.de[key] ?? key;
  if (typeof entry === 'function') return entry(vars);
  return entry.replace(/\{(\w+)\}/g, (_, k) => vars[k] ?? '');
}

// Locale number formatting (1,5 vs 1.5); without digits it shows up to one decimal
function fmt(n, digits) {
  const opts = digits === undefined
    ? { maximumFractionDigits: 1 }
    : { minimumFractionDigits: digits, maximumFractionDigits: digits };
  return Number(n).toLocaleString(lang, opts);
}

// ?lang=en wins, then the remembered choice, then German
function initialLang() {
  const fromUrl = new URLSearchParams(location.search).get('lang');
  if (LANGS.includes(fromUrl)) return fromUrl;
  try {
    const saved = localStorage.getItem('abreisser.lang');
    if (LANGS.includes(saved)) return saved;
  } catch { /* storage unavailable */ }
  return 'de';
}

const dropRefreshers = new Set();

function applyLanguage(next) {
  lang = next;
  try { localStorage.setItem('abreisser.lang', lang); } catch { /* storage unavailable */ }

  document.documentElement.lang = lang;
  document.title = t('appTitle');
  document.querySelector('meta[name=description]').content = t('metaDesc');
  document.querySelectorAll('[data-i18n]').forEach(el => { el.textContent = t(el.dataset.i18n); });
  document.querySelectorAll('[data-i18n-html]').forEach(el => { el.innerHTML = t(el.dataset.i18nHtml); });
  document.querySelectorAll('[data-i18n-ph]').forEach(el => { el.placeholder = t(el.dataset.i18nPh); });
  document.querySelectorAll('[data-i18n-title]').forEach(el => { el.title = t(el.dataset.i18nTitle); });
  document.querySelectorAll('[data-i18n-aria]').forEach(el => { el.setAttribute('aria-label', t(el.dataset.i18nAria)); });

  for (const refresh of dropRefreshers) refresh();
  renderItems();
  showStep(currentStep);
  render();
}

/* ---------- editor ---------- */

let currentStep = 1;
let zoom = 'fit';

function syncControls() {
  document.querySelectorAll('[data-set]').forEach(btn => {
    const [path, val] = btn.dataset.set.split('=');
    const on = String(getPath(path)) === val;
    btn.classList.toggle('active', on);
    btn.setAttribute('aria-pressed', on);
  });
  document.querySelectorAll('[data-show]').forEach(el => {
    const [path, val] = el.dataset.show.split('=');
    const cur = getPath(path);
    el.hidden = val === undefined ? !cur : String(cur) !== val;
  });
  document.querySelectorAll('[data-zoom]').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.zoom === String(zoom));
  });
  document.querySelectorAll('[data-lang]').forEach(btn => {
    const on = btn.dataset.lang === lang;
    btn.classList.toggle('active', on);
    btn.setAttribute('aria-pressed', on);
  });
}

function syncInputs() {
  document.querySelectorAll('[data-bind]').forEach(el => {
    const value = getPath(el.dataset.bind);
    if (el.type === 'checkbox') el.checked = !!value;
    else el.value = value;
  });
}

function showStep(n) {
  currentStep = n;
  document.querySelectorAll('[data-panel]').forEach(p => { p.hidden = Number(p.dataset.panel) !== n; });
  document.querySelectorAll('[data-step]').forEach(b => {
    const s = Number(b.dataset.step);
    b.classList.toggle('active', s === n);
    b.classList.toggle('done', s < n);
    if (s === n) b.setAttribute('aria-current', 'step');
    else b.removeAttribute('aria-current');
  });
  document.querySelector('[data-action="back"]').disabled = n === 1;
  const next = document.querySelector('[data-action="next"]');
  next.textContent = t(n === 3 ? 'print' : 'next');
  document.querySelector('.panels').scrollTop = 0;
}

function applyZoom() {
  let z = zoom;
  if (z === 'fit') {
    const scroller = document.querySelector('.stage-scroll');
    const pagePx = preview.querySelector('.page')?.offsetWidth || 794;
    z = Math.min(1, (scroller.clientWidth - 48) / pagePx);
  }
  preview.style.zoom = Math.max(0.2, Number(z));
}

// Wrap the selection in markdown-style markers, or insert an empty pair
function wrapSelection(textarea, marker) {
  const { selectionStart: a, selectionEnd: b, value } = textarea;
  const sel = value.slice(a, b);
  const before = value.slice(Math.max(0, a - marker.length), a);
  const after = value.slice(b, b + marker.length);
  if (sel && before === marker && after === marker) {
    textarea.value = value.slice(0, a - marker.length) + sel + value.slice(b + marker.length);
    textarea.setSelectionRange(a - marker.length, b - marker.length);
  } else {
    textarea.value = value.slice(0, a) + marker + sel + marker + value.slice(b);
    textarea.setSelectionRange(a + marker.length, b + marker.length);
  }
  textarea.focus();
  textarea.dispatchEvent(new Event('input', { bubbles: true }));
}

function formatShortcuts(textarea) {
  textarea.addEventListener('keydown', e => {
    if (!(e.ctrlKey || e.metaKey)) return;
    const key = e.key.toLowerCase();
    if (key === 'b' || key === 'i') {
      e.preventDefault();
      wrapSelection(textarea, key === 'b' ? '**' : '*');
    }
  });
}

// Image picker with click, keyboard and drag & drop
function mountDrop(el, get, set) {
  el.tabIndex = 0;
  el.setAttribute('role', 'button');
  el.innerHTML = `
    <span class="thumb"></span>
    <span class="drop-text"></span>
    <button type="button" class="remove"></button>
    <input type="file" accept="image/*" hidden>`;
  const thumb = el.querySelector('.thumb');
  const text = el.querySelector('.drop-text');
  const remove = el.querySelector('.remove');
  const input = el.querySelector('input');

  const refresh = () => {
    if (!el.isConnected) { dropRefreshers.delete(refresh); return; }
    const src = get();
    el.classList.toggle('has-image', !!src);
    thumb.innerHTML = src ? `<img src="${esc(src)}" alt="">` : '<svg class="icon"><use href="#i-image"/></svg>';
    text.innerHTML = src
      ? `<b>${esc(t('dropSelected'))}</b><span>${esc(t('dropReplaceHint'))}</span>`
      : `<b>${esc(t('dropAdd'))}</b><span>${esc(t('dropAddHint'))}</span>`;
    remove.textContent = t('remove');
    remove.hidden = !src;
    el.setAttribute('aria-label', t(src ? 'dropReplace' : 'dropAdd'));
  };
  dropRefreshers.add(refresh);
  const accept = async file => {
    if (!file || !file.type.startsWith('image/')) return;
    set(await readImage(file));
    refresh();
    render();
  };

  el.addEventListener('click', e => { if (e.target !== remove) input.click(); });
  el.addEventListener('keydown', e => {
    if (e.target === el && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); input.click(); }
  });
  input.addEventListener('change', () => { accept(input.files[0]); input.value = ''; });
  remove.addEventListener('click', e => {
    e.stopPropagation();
    set(null);
    refresh();
    render();
  });
  el.addEventListener('dragover', e => { e.preventDefault(); el.classList.add('over'); });
  el.addEventListener('dragleave', () => el.classList.remove('over'));
  el.addEventListener('drop', e => {
    e.preventDefault();
    el.classList.remove('over');
    accept(e.dataTransfer.files[0]);
  });
  refresh();
}

function initBindings() {
  document.querySelectorAll('[data-bind]').forEach(el => {
    const path = el.dataset.bind;
    el.addEventListener('input', () => {
      if (el.type === 'checkbox') setPath(path, el.checked);
      else if (el.type === 'number') {
        if (el.value === '' || isNaN(el.valueAsNumber)) return;
        const min = el.min === '' ? -Infinity : Number(el.min);
        const max = el.max === '' ? Infinity : Number(el.max);
        setPath(path, Math.min(max, Math.max(min, el.valueAsNumber)));
      } else setPath(path, el.value);

      if (path === 'strips.count') renderItems();
      render();
    });
    if (el.type === 'number') {
      // Show the clamped value once the user leaves the field
      el.addEventListener('change', () => { el.value = getPath(path); });
    }
  });
  syncInputs();

  document.querySelectorAll('[data-set]').forEach(btn => {
    btn.addEventListener('click', () => {
      const [path, val] = btn.dataset.set.split('=');
      setPath(path, val);
      if (path === 'strips.mode') renderItems();
      render();
    });
  });

  document.querySelectorAll('[data-image]').forEach(el => {
    const path = el.dataset.image;
    mountDrop(el, () => getPath(path), v => setPath(path, v));
  });

  document.querySelectorAll('[data-wrap]').forEach(btn => {
    btn.addEventListener('click', () => wrapSelection(document.getElementById(btn.dataset.target), btn.dataset.wrap));
  });
  formatShortcuts(document.getElementById('sText'));

  document.querySelectorAll('[data-step-input]').forEach(btn => {
    btn.addEventListener('click', () => {
      const input = document.getElementById(btn.dataset.stepInput);
      if (Number(btn.dataset.delta) > 0) input.stepUp(); else input.stepDown();
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
  });

  document.querySelectorAll('[data-step]').forEach(btn => {
    btn.addEventListener('click', () => showStep(Number(btn.dataset.step)));
  });

  document.querySelectorAll('[data-lang]').forEach(btn => {
    btn.addEventListener('click', () => applyLanguage(btn.dataset.lang));
  });

  document.querySelectorAll('[data-zoom]').forEach(btn => {
    btn.addEventListener('click', () => {
      zoom = btn.dataset.zoom === 'fit' ? 'fit' : Number(btn.dataset.zoom);
      applyZoom();
      syncControls();
    });
  });
  window.addEventListener('resize', () => { if (zoom === 'fit') applyZoom(); });

  const actions = {
    print: () => window.print(),
    back: () => showStep(Math.max(1, currentStep - 1)),
    next: () => (currentStep === 3 ? window.print() : showStep(currentStep + 1)),
    fill: () => {
      state.page.rows = Math.max(1, metrics().fit);
      syncInputs();
      render();
    },
  };
  document.querySelectorAll('[data-action]').forEach(btn => {
    btn.addEventListener('click', () => actions[btn.dataset.action]());
  });
}

function renderItems() {
  const s = state.strips;
  while (s.items.length < s.count) s.items.push({ text: '', link: '', image: null });
  if (s.mode !== 'individual') return;

  const open = new Set([...itemsBox.querySelectorAll('details[open]')].map(d => d.dataset.index));
  itemsBox.innerHTML = '';
  for (let i = 0; i < s.count; i++) {
    const it = s.items[i];
    const box = document.createElement('details');
    box.className = 'item';
    box.dataset.index = i;
    box.open = open.has(String(i));
    box.innerHTML = `
      <summary><span class="badge">${i + 1}</span><span class="preview-text"></span>
        <svg class="icon chev"><use href="#i-chevron"/></svg></summary>
      <div class="stack">
        <div class="field">
          <label for="it${i}t">${esc(t('text'))}</label>
          <textarea id="it${i}t" rows="2" maxlength="200" placeholder="${esc(t('itemTextPh'))}"></textarea>
        </div>
        <div class="field">
          <label for="it${i}l">${esc(t('qrLink'))}</label>
          <input id="it${i}l" type="url" placeholder="${esc(t('itemLinkPh'))}">
        </div>
        <div class="field">
          <span class="label">${esc(t('image'))}</span>
          <div class="drop"></div>
        </div>
      </div>`;
    const textEl = box.querySelector('textarea');
    const linkEl = box.querySelector('input[type=url]');
    const label = box.querySelector('.preview-text');
    const updateLabel = () => {
      const own = it.text.trim();
      label.textContent = own ? own.replace(/\*/g, '').split('\n')[0] : t('itemShared');
      label.classList.toggle('custom', !!(own || it.link || it.image));
    };
    textEl.value = it.text;
    linkEl.value = it.link;
    textEl.addEventListener('input', () => { it.text = textEl.value; updateLabel(); render(); });
    linkEl.addEventListener('input', () => { it.link = linkEl.value; updateLabel(); render(); });
    formatShortcuts(textEl);
    mountDrop(box.querySelector('.drop'), () => it.image, v => { it.image = v; updateLabel(); });
    updateLabel();
    itemsBox.appendChild(box);
  }
}

// Optional preset: index.html?preset=name loads presets/name.json over the defaults
async function loadPreset() {
  const name = new URLSearchParams(location.search).get('preset');
  if (!name || !/^[\w-]+$/.test(name)) return;
  try {
    const res = await fetch(`presets/${name}.json`);
    if (!res.ok) throw new Error(res.status);
    const preset = await res.json();
    for (const section of Object.keys(state)) Object.assign(state[section], preset[section]);
  } catch (err) {
    console.warn(`Could not load preset "${name}":`, err);
  }
}

loadPreset().then(() => {
  initBindings();
  applyLanguage(initialLang());
});
