"""Soft night crickets loop for the Istighfar night mode.
Run:  python3 tools/gen_cricket.py   (needs numpy)"""
import wave, numpy as np
OUT = 'app/src/main/res/raw/cricket.wav'
SR = 22050
DUR = 8.0                      # all periods divide 8 s -> seamless loop
n = int(SR * DUR)
t = np.arange(n) / SR
mix = np.zeros(n)

def cricket(freq, period, offset, pulses, amp):
    ph = ((t - offset) % period)
    env = np.zeros(n)
    for k in range(pulses):
        s = 0.02 + k * 0.075
        m = (ph >= s) & (ph < s + 0.05)
        x = (ph[m] - s) / 0.05
        env[m] = np.sin(np.pi * x) ** 2
    return amp * env * np.sin(2 * np.pi * freq * t)

mix += cricket(4200, 1.0, 0.00, 3, 1.0)
mix += cricket(4600, 1.6, 0.35, 4, 0.8)
mix += cricket(3900, 0.8, 0.50, 2, 0.7)
mix += cricket(5100, 2.0, 1.10, 5, 0.55)
mix = mix / (np.abs(mix).max() + 1e-9) * 0.8
w = wave.open(OUT, 'wb')
w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
w.writeframes((mix * 32767).astype('<i2').tobytes()); w.close()
print('ok', OUT)
