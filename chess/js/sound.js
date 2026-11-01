/* Mosswood Chess: tiny synthesized sound effects (no audio files needed). */
(function (global) {
  'use strict';
  let ctx = null, enabled = true, master = null;
  function ac() {
    if (!ctx) {
      const AC = global.AudioContext || global.webkitAudioContext;
      if (!AC) return null;
      ctx = new AC();
      master = ctx.createGain(); master.gain.value = 0.55; master.connect(ctx.destination);
    }
    if (ctx.state === 'suspended') ctx.resume();
    return ctx;
  }
  function tone(freq, dur, opts) {
    const c = ac(); if (!c) return;
    opts = opts || {};
    const t0 = c.currentTime + (opts.delay || 0);
    const o = c.createOscillator(), g = c.createGain();
    o.type = opts.type || 'sine';
    o.frequency.setValueAtTime(freq, t0);
    if (opts.slide) o.frequency.exponentialRampToValueAtTime(opts.slide, t0 + dur);
    g.gain.setValueAtTime(0.0001, t0);
    g.gain.exponentialRampToValueAtTime(opts.vol || 0.3, t0 + (opts.attack || 0.008));
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    o.connect(g); g.connect(master);
    o.start(t0); o.stop(t0 + dur + 0.05);
  }
  function noise(dur, opts) {
    const c = ac(); if (!c) return;
    opts = opts || {};
    const t0 = c.currentTime + (opts.delay || 0);
    const len = Math.floor(c.sampleRate * dur);
    const buf = c.createBuffer(1, len, c.sampleRate), d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, opts.decay || 2);
    const src = c.createBufferSource(); src.buffer = buf;
    const f = c.createBiquadFilter(); f.type = opts.filter || 'bandpass';
    f.frequency.setValueAtTime(opts.freq || 1200, t0);
    if (opts.sweep) f.frequency.exponentialRampToValueAtTime(opts.sweep, t0 + dur);
    f.Q.value = opts.q || 1.2;
    const g = c.createGain(); g.gain.value = opts.vol || 0.5;
    src.connect(f); f.connect(g); g.connect(master);
    src.start(t0);
  }
  const FX = {
    move() { noise(0.08, { freq: 900, q: 2, vol: 0.55, decay: 3 }); tone(220, 0.09, { vol: 0.18, type: 'triangle' }); },
    hop() { tone(380, 0.12, { slide: 620, vol: 0.12, type: 'sine' }); noise(0.07, { freq: 900, q: 2, vol: 0.45, decay: 3, delay: 0.1 }); },
    capture() { noise(0.28, { freq: 2400, sweep: 300, q: 0.8, vol: 0.6, decay: 1.6 }); tone(140, 0.18, { vol: 0.25, type: 'triangle', slide: 70 }); },
    castle() { FX.move(); setTimeout(FX.move, 110); },
    check() { tone(880, 0.35, { vol: 0.16 }); tone(1320, 0.4, { vol: 0.1, delay: 0.06 }); },
    promote() { [523, 659, 784, 1047].forEach((f, i) => tone(f, 0.22, { vol: 0.13, delay: i * 0.07, type: 'triangle' })); },
    win() { [523, 659, 784, 1047, 784, 1047].forEach((f, i) => tone(f, 0.3, { vol: 0.14, delay: i * 0.12, type: 'triangle' })); },
    lose() { [392, 349, 311, 262].forEach((f, i) => tone(f, 0.4, { vol: 0.12, delay: i * 0.16, type: 'sine' })); },
    draw() { [440, 440, 392].forEach((f, i) => tone(f, 0.25, { vol: 0.12, delay: i * 0.14, type: 'triangle' })); },
    coach() { tone(660, 0.08, { vol: 0.08, type: 'sine' }); tone(990, 0.1, { vol: 0.06, delay: 0.06 }); },
    warn() { tone(330, 0.16, { vol: 0.14, type: 'square' }); tone(262, 0.22, { vol: 0.12, type: 'square', delay: 0.12 }); },
    good() { tone(784, 0.12, { vol: 0.1, type: 'triangle' }); tone(1175, 0.18, { vol: 0.08, type: 'triangle', delay: 0.08 }); },
    illegal() { tone(150, 0.12, { vol: 0.12, type: 'square' }); },
    click() { tone(520, 0.04, { vol: 0.06, type: 'triangle' }); },
    sparkle() { [1568, 2093, 2637].forEach((f, i) => tone(f, 0.12, { vol: 0.05, delay: i * 0.05 })); }
  };
  global.Sound = {
    play(name) { if (!enabled) return; try { FX[name] && FX[name](); } catch (e) { /* audio is optional */ } },
    setEnabled(v) { enabled = !!v; },
    unlock() { try { ac(); } catch (e) { /* ignore */ } }
  };
})(window);
