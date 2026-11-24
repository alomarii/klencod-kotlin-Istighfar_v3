"""Physically-inspired water drop / ambience synthesis for the Istighfar app.
Run:  python3 tools/gen_sounds.py   (needs numpy)"""
import wave, numpy as np
OUT = 'app/src/main/res/raw/'
SR = 44100
rng = np.random.default_rng(11)

def save(name, x, sr=SR, gain=0.9):
    x = np.asarray(x, dtype=np.float64)
    x = x / (np.abs(x).max() + 1e-12) * gain
    w = wave.open(OUT + name + '.wav', 'wb')
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
    w.writeframes((x * 32767).astype('<i2').tobytes()); w.close()

def band(x, lo, hi, sr=SR):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / sr)
    g = np.ones_like(f)
    if lo: g *= 1 / (1 + (lo / np.maximum(f, 1e-3)) ** 4)
    if hi: g *= 1 / (1 + (f / hi) ** 4)
    return np.fft.irfft(X * g, len(x))

def comb(x, delay, g):
    d = int(delay * SR); y = x.copy()
    for i in range(d, len(y)):
        y[i] += g * y[i - d]
    return y

def plink(r_mm, amp=1.0, dur=0.7, thump=1.0, seed=0):
    """Minnaert bubble resonance: f = 3.26 / r. Pitch glides upward while the
    bubble rings down, plus impact click and a low body thump."""
    n = int(SR * dur); t = np.arange(n) / SR
    f0 = 3.26 / (r_mm * 1e-3)
    f = f0 * (1 + 0.40 * (1 - np.exp(-t / 0.045)))
    ph = 2 * np.pi * np.cumsum(f) / SR
    env = np.minimum(t / 0.0007, 1) * np.exp(-t / (0.026 + 0.004 * r_mm))
    tone = (np.sin(ph) + 0.07 * np.sin(2 * ph + 0.6)) * env
    r = np.random.default_rng(seed)
    click = band(r.standard_normal(n), 2500, 9000) * np.exp(-t / 0.0022)
    click /= np.abs(click).max() + 1e-9
    body = np.sin(2 * np.pi * (90 + 80 * np.exp(-t / 0.018)) * t) * np.exp(-t / 0.04)
    return amp * (0.95 * tone + 0.10 * click + 0.22 * thump * body)

def drop(r_mm, seed):
    n = int(SR * 0.95)
    out = np.zeros(n)
    main = plink(r_mm, 1.0, 0.7, 1.0, seed)
    out[:len(main)] += main
    # returning jet droplet: a smaller, higher, quieter plink 0.20 s later
    sec = plink(r_mm * 0.55, 0.18, 0.4, 0.15, seed + 50)
    s0 = int(0.20 * SR)
    out[s0:s0 + len(sec)] += sec
    # small room tail
    wet = sum(comb(out[:n // 2 + 8000], d, 0.55) for d in (0.0297, 0.0371, 0.0411, 0.0437))
    wet = band(wet, 250, 5500)
    res = out.copy(); res[:len(wet)] += 0.10 * wet[:n]
    res = band(res, 60, 6000)
    fade = np.ones(n); k = int(0.08 * SR); fade[-k:] = np.linspace(1, 0, k)
    return res * fade

# ONE natural, soft water-drop sound (the app plays only this one)
save('drop', drop(2.1, 3), gain=0.8)

# ---------- ambient loops (FFT shaped => perfectly periodic)
def loop_noise(sec, sr, lo, hi, tilt=0.0):
    n = int(sec * sr)
    X = np.fft.rfft(rng.standard_normal(n)); f = np.fft.rfftfreq(n, 1 / sr)
    g = 1 / (1 + (lo / np.maximum(f, 1e-3)) ** 4) / (1 + (f / hi) ** 4)
    g *= (np.maximum(f, 50) / 1000.0) ** tilt
    return np.fft.irfft(X * g, n)

def add_wrapped(buf, i, snd):
    idx = (np.arange(len(snd)) + i) % len(buf)
    buf[idx] += snd

# rain: broadband patter + many tiny drop ticks
sr2 = 22050; sec = 4.0; n = int(sec * sr2)
rain = loop_noise(sec, sr2, 500, 9000, tilt=-0.1)
rain = rain / np.abs(rain).max() * 0.55
for _ in range(220):
    d = int(0.012 * sr2); tt = np.arange(d) / sr2
    f0 = rng.uniform(2200, 5200)
    tk = np.sin(2 * np.pi * f0 * tt * (1 + 3 * tt)) * np.exp(-tt / 0.003) * rng.uniform(0.15, 0.6)
    add_wrapped(rain, int(rng.uniform(0, n)), tk)
mod = 1 + 0.12 * np.sin(2 * np.pi * np.arange(n) / n * 2)
save('rain', rain * mod, sr2, 0.7)

# stream: babbling band noise + bubble blips
sec = 4.0; n = int(sec * sr2)
st = loop_noise(sec, sr2, 350, 3200, tilt=0.0)
am = loop_noise(sec, sr2, 3, 14); am = 1 + 0.55 * am / np.abs(am).max()
st = st / np.abs(st).max() * am * 0.55
for _ in range(70):
    d = int(0.05 * sr2); tt = np.arange(d) / sr2
    f0 = rng.uniform(500, 1100)
    b = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 6 * tt)) / sr2) * np.exp(-tt / 0.012) * rng.uniform(0.1, 0.45)
    add_wrapped(st, int(rng.uniform(0, n)), b)
save('stream', st, sr2, 0.7)

# wind: low swelling noise
sec = 6.0; n = int(sec * sr2)
wd = loop_noise(sec, sr2, 120, 1100, tilt=-0.6)
tt = np.arange(n) / n
wd = wd / np.abs(wd).max() * (0.55 + 0.45 * np.sin(2 * np.pi * tt * 1 + 0.4) * np.sin(2 * np.pi * tt * 2 + 1.1))
save('wind', wd, sr2, 0.6)

# ---------- birds
def chirp(f_a, f_b, d, vib=0, am=0):
    n = int(d * SR); t = np.arange(n) / SR
    f = f_a + (f_b - f_a) * (t / d) ** 0.8 + vib * np.sin(2 * np.pi * 28 * t)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.2 * np.sin(4 * np.pi * np.cumsum(f) / SR)
    e = np.sin(np.pi * t / d) ** 1.5
    if am: e = e * (0.6 + 0.4 * np.sin(2 * np.pi * am * t))
    return x * e

def seq(items, total):
    out = np.zeros(int(total * SR))
    for start, snd in items:
        i = int(start * SR); out[i:i + len(snd)] += snd[:len(out) - i]
    return out

b1 = seq([(0.00, chirp(3000, 4600, 0.07)), (0.11, chirp(3200, 4800, 0.07)), (0.22, chirp(3400, 5000, 0.07)),
          (0.40, chirp(4800, 3200, 0.16, 120)), (0.62, chirp(4600, 3000, 0.18, 150))], 0.9)
b2 = seq([(0.00, chirp(4200, 4600, 0.20, 250, 0)), (0.28, chirp(4600, 3300, 0.16)),
          (0.50, chirp(3800, 4000, 0.30, 0, 45)), (0.88, chirp(3600, 4300, 0.10)), (1.02, chirp(3800, 4500, 0.10))], 1.3)
for nm, b in (('bird1', b1), ('bird2', b2)):
    wet = band(comb(np.concatenate([b, np.zeros(8000)]), 0.043, 0.35), 1500, 9000)
    save(nm, np.concatenate([b, np.zeros(8000)]) + 0.2 * wet, SR, 0.8)

# ---------- chimes
def bell(notes, dur, decay, spacing):
    n = int(SR * dur); out = np.zeros(n); t = np.arange(n) / SR
    for k, fq in enumerate(notes):
        st = int(SR * spacing * k)
        tt = t[:n - st]
        v = (np.sin(2 * np.pi * fq * tt) * 0.6 + np.sin(2 * np.pi * fq * 2.76 * tt) * 0.18 * np.exp(-tt * 3)
             + np.sin(2 * np.pi * fq * 5.4 * tt) * 0.06 * np.exp(-tt * 6)) * np.exp(-tt * decay)
        v[:1800] *= np.linspace(0, 1, 1800) ** 1.5
        out[st:] += v
    return band(out, 0, 4500)
save('chime', bell([1046.5, 1318.5, 1568.0], 1.2, 4.0, 0.11), gain=0.8)
save('bell', bell([523.25, 659.25, 783.99, 1046.5, 1318.5], 2.6, 2.2, 0.16), gain=0.8)
print('ok')
