// A avatar da L.I.L.Y em Canvas: o mesmo desenho original do app (holograma em busto, cabelo em
// fitas de vidro, halo, fone de luz e o traço de circuito), animado conforme a presença.

import { blink, breathing, mouthOpen, target } from '../core/presence.js';

const SUCCESS = '#5BE49B';
const ERROR = '#FF6B7A';
const LISTEN = '#63D8FF';

function hsl(h, s, l, a = 1) {
  return `hsla(${((h % 360) + 360) % 360}, ${s}%, ${l}%, ${a})`;
}

/** Paleta a partir de uma matiz (equivalente à do app, em HSL). */
export function palette(hue) {
  return {
    hair: hsl(hue, 45, 32),
    hairHi: hsl(hue + 10, 85, 78),
    skin: hsl(hue + 20, 60, 96),
    skinShade: hsl(hue + 10, 30, 80),
    iris: hsl(hue - 75, 100, 70),
    irisDeep: hsl(hue - 60, 75, 30),
    outfit: hsl(hue - 25, 35, 16),
    outfitHi: hsl(hue - 60, 60, 70),
    glow: hsl(hue - 75, 100, 76),
    ink: hsl(hue + 10, 50, 10),
  };
}

export class AvatarView {
  constructor(canvas, { hue = 258, style = 'HOLO', presentation = 'BUST', motion = 'FULL' } = {}) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.look = { hue, style, presentation, motion };
    this.state = { mode: 'IDLE', level: 0, progress: null };
    this.pose = target('IDLE');
    this.level = 0;
    this.start = performance.now();
    this.reduce = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    this.frame = this.frame.bind(this);
    this.running = false;
  }

  setLook(look) {
    this.look = { ...this.look, ...look };
    this.draw(this.time());
  }

  setState(s) {
    this.state = s;
    if (!this.running) this.draw(this.time());
  }

  time() {
    return (performance.now() - this.start) * (this.look.motion === 'REDUCED' ? 0.55 : 1) + 1000;
  }

  run() {
    if (this.running) return;
    if (this.reduce || this.look.motion === 'OFF') return this.draw(1000);
    this.running = true;
    requestAnimationFrame(this.frame);
  }

  stop() {
    this.running = false;
  }

  frame() {
    if (!this.running) return;
    this.draw(this.time());
    requestAnimationFrame(this.frame);
  }

  draw(t) {
    const c = this.canvas;
    const dpr = globalThis.devicePixelRatio || 1;
    const w = c.clientWidth || c.width;
    const h = c.clientHeight || c.height;
    if (c.width !== Math.round(w * dpr) || c.height !== Math.round(h * dpr)) {
      c.width = Math.round(w * dpr);
      c.height = Math.round(h * dpr);
    }
    const ctx = this.ctx;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, c.width, c.height);
    // Aproxima a expressão do alvo (mola simples).
    const tgt = target(this.state.mode);
    const k = this.running ? 0.12 : 1;
    for (const key of Object.keys(tgt)) this.pose[key] += (tgt[key] - this.pose[key]) * k;
    this.level += ((this.state.level || 0) - this.level) * (this.running ? 0.3 : 1);
    const f = { t, mode: this.state.mode, progress: this.state.progress, level: this.level, pose: this.pose, p: palette(this.look.hue), style: this.look.style, effects: this.running && this.look.motion === 'FULL' };
    f.stateColor = f.mode === 'SUCCESS' ? SUCCESS : f.mode === 'ERROR' ? ERROR : f.mode === 'LISTENING' ? LISTEN : f.p.glow;
    if (this.look.presentation === 'ORB') return this.orb(ctx, c, f);
    this.bust(ctx, c, f, this.look.presentation === 'FACE');
  }

  bust(ctx, c, f, face) {
    const [vx, vy, vw, vh] = face ? [20, 2, 60, 66] : [0, 0, 100, 100];
    const s = Math.min(c.width / vw, c.height / vh);
    ctx.setTransform(s, 0, 0, s, (c.width - vw * s) / 2 - vx * s, (c.height - vh * s) / 2 - vy * s);
    const breath = breathing(f.t);
    const sway = Math.sin((2 * Math.PI * f.t) / 3700) * 0.7;
    if (!face) {
      const strength = f.mode === 'IDLE' ? 0.28 : f.mode === 'LISTENING' ? 0.32 + 0.4 * f.level : 0.42;
      const g = ctx.createRadialGradient(50, 40, 0, 50, 40, 46);
      g.addColorStop(0, withAlpha(f.stateColor, strength));
      g.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(50, 40, 46, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.save();
    ctx.translate(0, breath * 0.35);
    this.outfit(ctx, f);
    ctx.restore();
    ctx.save();
    ctx.translate(0, breath * 0.5);
    ctx.translate(50, 62);
    ctx.rotate((f.pose.tilt * Math.PI) / 180);
    ctx.translate(-50, -62);
    this.hairBack(ctx, f, sway);
    this.neck(ctx, f);
    this.head(ctx, f);
    this.face(ctx, f);
    this.hairFront(ctx, f, sway);
    this.earpiece(ctx, f);
    this.halo(ctx, f);
    ctx.restore();
    this.extras(ctx, f);
    if (f.style === 'HOLO') {
      ctx.save();
      ctx.globalCompositeOperation = 'source-atop';
      ctx.strokeStyle = `rgba(255,255,255,${f.effects ? 0.05 + 0.02 * Math.sin(f.t / 70) : 0.06})`;
      ctx.lineWidth = 0.35;
      const off = f.effects ? (f.t / 60) % 1.4 : 0;
      ctx.beginPath();
      for (let y = 0; y < 100; y += 1.4) {
        ctx.moveTo(0, y + off);
        ctx.lineTo(100, y + off);
      }
      ctx.stroke();
      ctx.restore();
    }
  }

  outfit(ctx, f) {
    const p = f.p;
    ctx.beginPath();
    ctx.moveTo(11, 101);
    ctx.bezierCurveTo(12.5, 87, 22, 79.5, 36, 76.8);
    ctx.quadraticCurveTo(42, 75.4, 43.6, 71);
    ctx.lineTo(56.4, 71);
    ctx.quadraticCurveTo(58, 75.4, 64, 76.8);
    ctx.bezierCurveTo(78, 79.5, 87.5, 87, 89, 101);
    ctx.closePath();
    const g = ctx.createLinearGradient(0, 72, 0, 100);
    g.addColorStop(0, f.style === 'MINIMAL' ? p.outfit : mix(p.outfit, p.outfitHi, 0.18));
    g.addColorStop(1, p.outfit);
    ctx.fillStyle = g;
    ctx.fill();
    ctx.strokeStyle = withAlpha(p.outfitHi, 0.45);
    ctx.lineWidth = 0.5;
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(41.5, 64.5);
    ctx.quadraticCurveTo(50, 69.5, 58.5, 64.5);
    ctx.lineTo(60, 72.5);
    ctx.quadraticCurveTo(50, 78, 40, 72.5);
    ctx.closePath();
    ctx.fillStyle = mix(p.outfit, p.outfitHi, 0.12);
    ctx.fill();
    ctx.beginPath();
    ctx.moveTo(40.6, 71.6);
    ctx.quadraticCurveTo(50, 77, 59.4, 71.6);
    ctx.strokeStyle = withAlpha(f.stateColor, 0.9);
    ctx.lineWidth = 0.7;
    ctx.lineCap = 'round';
    ctx.stroke();
    ctx.strokeStyle = withAlpha(p.outfitHi, 0.35);
    ctx.lineWidth = 0.45;
    line(ctx, 50, 77.5, 50, 92);
    line(ctx, 27, 86, 38, 80.5);
    line(ctx, 73, 86, 62, 80.5);
    dot(ctx, 50, 84, 0.8, withAlpha(f.stateColor, 0.85));
  }

  neck(ctx, f) {
    ctx.beginPath();
    ctx.moveTo(44.6, 55);
    ctx.lineTo(55.4, 55);
    ctx.quadraticCurveTo(55.2, 63, 57, 68);
    ctx.lineTo(43, 68);
    ctx.quadraticCurveTo(44.8, 63, 44.6, 55);
    ctx.closePath();
    ctx.fillStyle = f.p.skinShade;
    ctx.fill();
  }

  headPath(ctx) {
    ctx.beginPath();
    ctx.moveTo(50, 19);
    ctx.bezierCurveTo(60.5, 19, 67, 26, 67, 36);
    ctx.bezierCurveTo(67, 47.5, 61.5, 56, 55.2, 59.6);
    ctx.quadraticCurveTo(50, 62.8, 44.8, 59.6);
    ctx.bezierCurveTo(38.5, 56, 33, 47.5, 33, 36);
    ctx.bezierCurveTo(33, 26, 39.5, 19, 50, 19);
    ctx.closePath();
  }

  head(ctx, f) {
    const p = f.p;
    if (f.style === 'HOLO') {
      ctx.lineWidth = 0.7;
      ctx.save();
      ctx.translate(0.55, 0);
      this.headPath(ctx);
      ctx.strokeStyle = withAlpha(LISTEN, 0.35);
      ctx.stroke();
      ctx.translate(-1.1, 0);
      this.headPath(ctx);
      ctx.strokeStyle = 'rgba(255,111,216,0.28)';
      ctx.stroke();
      ctx.restore();
    }
    this.headPath(ctx);
    const g = ctx.createLinearGradient(40, 22, 60, 62);
    g.addColorStop(0, p.skin);
    g.addColorStop(1, f.style === 'MINIMAL' ? p.skin : p.skinShade);
    ctx.globalAlpha = f.style === 'GLASS' ? 0.86 : 1;
    ctx.fillStyle = g;
    ctx.fill();
    ctx.globalAlpha = 1;
    ctx.strokeStyle = withAlpha(p.glow, 0.45);
    ctx.lineWidth = 0.45;
    ctx.stroke();
  }

  face(ctx, f) {
    const p = f.p;
    const pose = f.pose;
    if (f.style !== 'MINIMAL') {
      for (const cx of [40.6, 59.4]) {
        const g = ctx.createRadialGradient(cx, 49, 0, cx, 49, 4.6);
        g.addColorStop(0, withAlpha(p.glow, 0.2));
        g.addColorStop(1, 'rgba(0,0,0,0)');
        ctx.fillStyle = g;
        ctx.fillRect(cx - 5, 44, 10, 10);
      }
    }
    ctx.strokeStyle = withAlpha(f.stateColor, 0.85);
    ctx.lineWidth = 0.4;
    line(ctx, 60.5, 50.4, 63.2, 48.6);
    line(ctx, 61.2, 51.8, 63.9, 50);
    dot(ctx, 64.6, 48.2, 0.42, withAlpha(f.stateColor, 0.85));
    const b = blink(f.t);
    let gx = pose.gazeX;
    let gy = pose.gazeY;
    if (f.mode === 'IDLE') {
      gx += Math.sin((2 * Math.PI * f.t) / 9300) * 0.25;
      gy += Math.sin((2 * Math.PI * f.t) / 13700 + 1.3) * 0.12;
    }
    for (const cx of [42.8, 57.2]) this.eye(ctx, f, cx, 42.2, b, gx, gy);
    for (const left of [true, false]) {
      const cx = left ? 42.8 : 57.2;
      const inner = left ? cx + 3.4 : cx - 3.4;
      const outer = left ? cx - 3.9 : cx + 3.9;
      const base = 36.6 - pose.browRaise * 1.25;
      ctx.beginPath();
      ctx.moveTo(outer, base + 0.35);
      ctx.quadraticCurveTo(cx, base - 1.3, inner, base - pose.browTilt * 1.3 + (1 - pose.browTilt) * 0.2);
      ctx.strokeStyle = p.hair;
      ctx.lineWidth = 0.85;
      ctx.lineCap = 'round';
      ctx.stroke();
    }
    ctx.beginPath();
    ctx.moveTo(50.7, 46.4);
    ctx.quadraticCurveTo(51.3, 48.7, 49.6, 49.3);
    ctx.strokeStyle = p.skinShade;
    ctx.lineWidth = 0.45;
    ctx.stroke();
    const open = mouthOpen(f.mode, f.level, f.t);
    const smile = pose.smile;
    const y = 53.8;
    const half = 3.1 * (1 + 0.08 * smile);
    const cornerY = y - smile * 0.9;
    const midY = y + smile * 1.5;
    const lip = '#c8829e';
    ctx.beginPath();
    ctx.moveTo(50 - half, cornerY);
    if (open > 0.04) {
      ctx.quadraticCurveTo(50, midY - 0.6, 50 + half, cornerY);
      ctx.quadraticCurveTo(50, midY + 0.6 + open * 3.4, 50 - half, cornerY);
      ctx.closePath();
      ctx.fillStyle = withAlpha(p.ink, 0.92);
      ctx.fill();
      ctx.strokeStyle = lip;
      ctx.lineWidth = 0.45;
      ctx.stroke();
    } else {
      ctx.quadraticCurveTo(50, midY - 0.2, 50 + half, cornerY);
      ctx.strokeStyle = lip;
      ctx.lineWidth = 0.65;
      ctx.stroke();
    }
  }

  eye(ctx, f, cx, cy, b, gx, gy) {
    const p = f.p;
    const pose = f.pose;
    const w = 8.2;
    if (pose.happyEyes > 0.5) {
      ctx.beginPath();
      ctx.moveTo(cx - w / 2, cy + 0.7);
      ctx.quadraticCurveTo(cx, cy - 2.6, cx + w / 2, cy + 0.7);
      ctx.strokeStyle = p.hair;
      ctx.lineWidth = 1.05;
      ctx.lineCap = 'round';
      ctx.stroke();
      return;
    }
    const open = Math.max(0.06, Math.min(1.25, pose.eyeOpen * (1 - b) * (1 - 0.42 * pose.squint)));
    const h = 4.9;
    const almond = () => {
      ctx.beginPath();
      ctx.moveTo(cx - w / 2, cy);
      ctx.quadraticCurveTo(cx, cy - h * open, cx + w / 2, cy);
      ctx.quadraticCurveTo(cx, cy + h * 0.6 * open, cx - w / 2, cy);
      ctx.closePath();
    };
    almond();
    ctx.fillStyle = '#f6f2ff';
    ctx.fill();
    ctx.save();
    almond();
    ctx.clip();
    const ix = cx + Math.max(-1, Math.min(1, gx)) * 1.4;
    const iy = cy - 0.3 + Math.max(-1, Math.min(1, gy)) * 0.8;
    const g = ctx.createRadialGradient(ix, iy, 0, ix, iy, 2.7);
    g.addColorStop(0, p.iris);
    g.addColorStop(1, p.irisDeep);
    dot(ctx, ix, iy, 2.6, g);
    dot(ctx, ix, iy, 1.05, p.ink);
    dot(ctx, ix - 0.65, iy - 0.75, 0.52, 'rgba(255,255,255,0.92)');
    dot(ctx, ix + 0.7, iy + 0.5, 0.26, 'rgba(255,255,255,0.5)');
    ctx.restore();
    ctx.beginPath();
    ctx.moveTo(cx - w / 2 - 0.2, cy + 0.1);
    ctx.quadraticCurveTo(cx, cy - h * open - 0.25, cx + w / 2 + 0.2, cy + 0.1);
    ctx.strokeStyle = p.hair;
    ctx.lineWidth = 0.85;
    ctx.lineCap = 'round';
    ctx.stroke();
    const outer = cx < 50 ? cx - w / 2 : cx + w / 2;
    const dir = cx < 50 ? -1 : 1;
    ctx.lineWidth = 0.7;
    line(ctx, outer, cy, outer + dir * 1.1, cy - 1);
  }

  hairBack(ctx, f, sway) {
    const p = f.p;
    ctx.beginPath();
    ctx.moveTo(50, 12.5);
    ctx.bezierCurveTo(36, 12.5, 27.5, 22, 28, 35);
    ctx.bezierCurveTo(28.3, 45, 27, 53, 29.5 + sway, 60.5);
    ctx.quadraticCurveTo(33, 63, 36.5, 59.6);
    ctx.lineTo(63.5, 59.6);
    ctx.quadraticCurveTo(67, 63, 70.5 + sway, 60.5);
    ctx.bezierCurveTo(73, 53, 71.7, 45, 72, 35);
    ctx.bezierCurveTo(72.5, 22, 64, 12.5, 50, 12.5);
    ctx.closePath();
    if (f.style === 'MINIMAL') ctx.fillStyle = p.hair;
    else {
      const g = ctx.createLinearGradient(0, 12, 0, 62);
      g.addColorStop(0, mix(p.hair, p.hairHi, 0.35));
      g.addColorStop(0.5, p.hair);
      g.addColorStop(1, mix(p.hair, p.ink, 0.25));
      ctx.fillStyle = g;
    }
    ctx.fill();
  }

  hairFront(ctx, f, sway) {
    const p = f.p;
    ctx.beginPath();
    ctx.moveTo(66.6, 31);
    ctx.bezierCurveTo(66.2, 20.5, 58.5, 15.6, 49.5, 16);
    ctx.bezierCurveTo(40.5, 16.4, 33.6, 22, 32.6, 31.5);
    ctx.bezierCurveTo(32.2, 39, 32.6, 46.5, 31.4 + sway * 0.8, 53.5);
    ctx.quadraticCurveTo(34.8, 47, 35.7, 38.5);
    ctx.bezierCurveTo(36.8, 31.6, 41, 28.6, 45.6, 29.4);
    ctx.quadraticCurveTo(50.4, 27.2, 52.8, 24.4);
    ctx.quadraticCurveTo(56.4, 28.6, 61.2, 29.6);
    ctx.quadraticCurveTo(64.6, 30.6, 66.6, 37);
    ctx.closePath();
    if (f.style === 'MINIMAL') ctx.fillStyle = p.hair;
    else {
      const g = ctx.createLinearGradient(52, 15, 38, 48);
      g.addColorStop(0, p.hairHi);
      g.addColorStop(1, p.hair);
      ctx.fillStyle = g;
    }
    ctx.fill();
    if (f.style !== 'MINIMAL') {
      ctx.strokeStyle = withAlpha(p.hairHi, 0.55);
      ctx.lineWidth = 0.32;
      curve(ctx, 52.6, 18, 44, 20, 37.5, 30);
      curve(ctx, 58, 19.5, 53, 22, 51.5, 25.5);
      curve(ctx, 35.2, 33, 33.8, 42, 33, 49);
    }
  }

  earpiece(ctx, f) {
    roundRect(ctx, 65.6, 38.6, 2.6, 6.2, 1.3, f.p.outfit);
    const pulse = f.mode === 'LISTENING' ? 0.5 + 0.5 * f.level : 0.55 + 0.25 * Math.sin((2 * Math.PI * f.t) / 2200);
    dot(ctx, 66.9, 41.7, 0.8, withAlpha(f.stateColor, Math.max(0.2, Math.min(1, pulse))));
  }

  halo(ctx, f) {
    const cx = 50;
    const cy = 9.6;
    const rx = 19;
    const ry = 3.7;
    const base = f.mode === 'LISTENING' ? 0.35 + 0.55 * f.level : f.mode === 'SUCCESS' || f.mode === 'ERROR' ? 0.9 : 0.4;
    ctx.lineCap = 'round';
    ctx.strokeStyle = withAlpha(f.stateColor, base * 0.6);
    ctx.lineWidth = 0.55 + (f.mode === 'LISTENING' ? f.level * 1.2 : 0);
    ctx.beginPath();
    ctx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
    ctx.stroke();
    const spin = ((f.t / 18) % 360) * (Math.PI / 180);
    const arc = (start, sweep, width, color) => {
      ctx.strokeStyle = color;
      ctx.lineWidth = width;
      ctx.beginPath();
      ctx.ellipse(cx, cy, rx, ry, 0, start, start + sweep);
      ctx.stroke();
    };
    if (f.mode === 'WORKING') {
      if (f.progress != null) arc(-Math.PI / 2, Math.PI * 2 * f.progress, 1.3, f.stateColor);
      else arc(spin * 2.2, 1.4, 1.3, f.stateColor);
    } else if (f.mode === 'THINKING') {
      for (let i = 0; i < 3; i++) {
        const a = spin * 1.6 + (i * 2 * Math.PI) / 3;
        dot(ctx, cx + rx * Math.cos(a), cy + ry * Math.sin(a), 0.95, f.stateColor);
      }
    } else for (let i = 0; i < 3; i++) arc(spin + (i * 2 * Math.PI) / 3, 0.6, 0.9, withAlpha(f.stateColor, base));
  }

  extras(ctx, f) {
    if (f.mode === 'LISTENING') {
      for (let i = 1; i <= 3; i++) {
        const r = 2.4 * i + ((f.t % 900) / 900) * 2.4;
        ctx.strokeStyle = withAlpha(LISTEN, Math.max(0, (0.55 * f.level + 0.2) * (1 - i / 4)));
        ctx.lineWidth = 0.5;
        ctx.beginPath();
        ctx.arc(67, 41.7, r, -0.7, 0.7);
        ctx.stroke();
      }
    } else if (f.mode === 'SUCCESS') {
      const n = f.effects ? 6 : 3;
      for (let i = 0; i < n; i++) {
        const a = (2 * Math.PI * i) / n + f.t / 1400;
        const r = 30 + 3 * Math.sin(f.t / 300 + i);
        sparkle(ctx, 50 + r * Math.cos(a), 40 + r * 0.75 * Math.sin(a), 1.6, SUCCESS);
      }
    } else if (f.mode === 'ERROR') {
      dot(ctx, 72, 9, 3.1, ERROR);
      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 0.75;
      line(ctx, 72, 7.4, 72, 9.4);
      dot(ctx, 72, 10.6, 0.45, '#fff');
    }
  }

  orb(ctx, c, f) {
    const s = Math.min(c.width, c.height) / 100;
    ctx.setTransform(s, 0, 0, s, (c.width - 100 * s) / 2, (c.height - 100 * s) / 2);
    const r = 36 + breathing(f.t) * 0.8;
    const aura = ctx.createRadialGradient(50, 50, 0, 50, 50, 50);
    aura.addColorStop(0, withAlpha(f.stateColor, 0.35));
    aura.addColorStop(1, 'rgba(0,0,0,0)');
    dot(ctx, 50, 50, 50, aura);
    const g = ctx.createRadialGradient(42, 38, 0, 42, 38, r * 1.5);
    g.addColorStop(0, mix(f.p.glow, '#ffffff', 0.35));
    g.addColorStop(0.35, f.p.hairHi);
    g.addColorStop(0.7, f.p.hair);
    g.addColorStop(1, f.p.ink);
    dot(ctx, 50, 50, r, g);
    ctx.strokeStyle = 'rgba(255,255,255,0.22)';
    ctx.lineWidth = 1.2;
    ctx.beginPath();
    ctx.arc(50, 50, r, 0, Math.PI * 2);
    ctx.stroke();
    const b = blink(f.t);
    const pose = f.pose;
    for (const ex of [40, 60]) {
      const gx = pose.gazeX * 2.2;
      const gy = pose.gazeY * 2;
      if (pose.happyEyes > 0.5) {
        ctx.strokeStyle = '#fff';
        ctx.lineWidth = 2.2;
        curve(ctx, ex - 4 + gx, 50, ex + gx, 44, ex + 4 + gx, 50);
      } else {
        const h = Math.max(1.4, 11 * pose.eyeOpen * (1 - b) * (1 - 0.4 * pose.squint));
        roundRect(ctx, ex - 3 + gx, 49 - h / 2 + gy, 6, h, 3, '#fff');
      }
    }
    const open = mouthOpen(f.mode, f.level, f.t);
    if (open > 0.04) {
      ctx.fillStyle = 'rgba(255,255,255,0.9)';
      ctx.beginPath();
      ctx.ellipse(50, 61 + open * 2.5, 4, 1 + open * 2.5, 0, 0, Math.PI * 2);
      ctx.fill();
    } else if (Math.abs(pose.smile) > 0.2) {
      ctx.strokeStyle = 'rgba(255,255,255,0.85)';
      ctx.lineWidth = 1.6;
      curve(ctx, 45, 61 - pose.smile * 0.8, 50, 61 + pose.smile * 3, 55, 61 - pose.smile * 0.8);
    }
    ctx.lineCap = 'round';
    const rr = r + 6;
    if (f.mode === 'WORKING') {
      ctx.strokeStyle = f.stateColor;
      ctx.lineWidth = 2.4;
      ctx.beginPath();
      if (f.progress != null) ctx.arc(50, 50, rr, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * f.progress);
      else {
        const a = ((f.t / 6) % 360) * (Math.PI / 180);
        ctx.arc(50, 50, rr, a, a + Math.PI / 2);
      }
      ctx.stroke();
    } else if (['LISTENING', 'ERROR', 'SUCCESS'].includes(f.mode)) {
      ctx.strokeStyle = f.mode === 'LISTENING' ? withAlpha(LISTEN, 0.3 + 0.6 * f.level) : f.stateColor;
      ctx.lineWidth = f.mode === 'LISTENING' ? 1.5 + 3 * f.level : 2.4;
      ctx.beginPath();
      ctx.arc(50, 50, rr, 0, Math.PI * 2);
      ctx.stroke();
    } else if (f.mode === 'THINKING') {
      for (let i = 0; i < 3; i++) {
        const a = (((f.t / 5) % 360) + i * 120) * (Math.PI / 180);
        dot(ctx, 50 + rr * Math.cos(a), 50 + rr * Math.sin(a), 2.2, f.stateColor);
      }
    }
  }
}

function line(ctx, x1, y1, x2, y2) {
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();
}

function curve(ctx, x1, y1, cx, cy, x2, y2) {
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.quadraticCurveTo(cx, cy, x2, y2);
  ctx.stroke();
}

function dot(ctx, x, y, r, fill) {
  ctx.fillStyle = fill;
  ctx.beginPath();
  ctx.arc(x, y, r, 0, Math.PI * 2);
  ctx.fill();
}

function roundRect(ctx, x, y, w, h, r, fill) {
  ctx.fillStyle = fill;
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
  ctx.fill();
}

function sparkle(ctx, x, y, r, color) {
  ctx.fillStyle = color;
  ctx.beginPath();
  ctx.moveTo(x, y - r);
  ctx.quadraticCurveTo(x, y, x + r, y);
  ctx.quadraticCurveTo(x, y, x, y + r);
  ctx.quadraticCurveTo(x, y, x - r, y);
  ctx.quadraticCurveTo(x, y, x, y - r);
  ctx.fill();
}

/** Uma cor CSS com outra transparência (hsla/hex/rgb). */
export function withAlpha(color, a) {
  if (color.startsWith('hsla(')) return color.replace(/,\s*[\d.]+\)$/, `, ${a})`);
  if (color.startsWith('#')) {
    const n = parseInt(color.slice(1), 16);
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
  }
  return color;
}

/** Mistura duas cores hsla (aproximação pelo HSL). */
export function mix(a, b, t) {
  const pa = parse(a);
  const pb = parse(b);
  if (!pa || !pb) return a;
  return hsl(pa[0] + (pb[0] - pa[0]) * t, pa[1] + (pb[1] - pa[1]) * t, pa[2] + (pb[2] - pa[2]) * t, pa[3] + (pb[3] - pa[3]) * t);
}

function parse(c) {
  if (c === '#ffffff') return [0, 0, 100, 1];
  const m = c.match(/hsla\(([-\d.]+),\s*([\d.]+)%,\s*([\d.]+)%,\s*([\d.]+)\)/);
  return m ? m.slice(1).map(Number) : null;
}
