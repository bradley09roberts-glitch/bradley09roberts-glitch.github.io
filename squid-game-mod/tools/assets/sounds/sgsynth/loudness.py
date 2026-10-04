"""sgsynth.loudness - perceptual loudness estimates used to match the doll syllables.

Plain RMS ignores that the ear hears 2-4 kHz much better than 300 Hz, so dark syllables ("mu",
"gung") would sound quieter than bright ones ("chi", "pi").  Two standard weightings bracket the
truth: K-weighting (ITU-R BS.1770 style, broadcast loudness) and A-weighting (low-level hearing).
``perceptual_level_db`` blends them 70 % K / 30 % A (equal-loudness contours at ~60 phon sit close to
K-weighting but still penalise 300 Hz relative to 3 kHz by ~6 dB), measured over the active part.
"""
from __future__ import annotations

import numpy as np

from .core import SR, lin2db, ns
from .dsp import apply_biquads, biquad_coefs


def k_weight(x: np.ndarray) -> np.ndarray:
    """Approximate BS.1770 K-weighting: 38 Hz high-pass + 1.68 kHz high shelf (+4 dB)."""
    return apply_biquads(x, [biquad_coefs("hs", 1681.97, 0.7071, 3.9998), biquad_coefs("hp", 38.13, 0.5003)])


def a_weight(x: np.ndarray) -> np.ndarray:
    """A-weighting (IEC 61672 magnitude, zero phase) applied in the frequency domain."""
    X = np.fft.rfft(x)
    f2 = np.fft.rfftfreq(len(x), 1.0 / SR) ** 2
    ra = (12194.0 ** 2 * f2 ** 2) / ((f2 + 20.6 ** 2) * np.sqrt((f2 + 107.7 ** 2) * (f2 + 737.9 ** 2)) *
                                     (f2 + 12194.0 ** 2) + 1e-30)
    return np.fft.irfft(X * ra, len(x))


def active_mask(x: np.ndarray, floor_db: float = -35.0) -> np.ndarray:
    """Samples whose 5 ms RMS envelope is within ``floor_db`` of the peak."""
    k = ns(0.005)
    env = np.sqrt(np.convolve(x * x, np.ones(k) / k, mode="same"))
    return env > np.max(np.abs(x)) * 10.0 ** (floor_db / 20.0)


def _active_db(y: np.ndarray, m: np.ndarray) -> float:
    return float(lin2db(np.sqrt(np.mean(y[m] ** 2))))


def loudness_db(x: np.ndarray):
    """(plain, K-weighted, A-weighted) active-part levels in dB."""
    m = active_mask(x)
    return _active_db(x, m), _active_db(k_weight(x), m), _active_db(a_weight(x), m)


A_WEIGHT_SHARE = 0.3


def perceptual_level_db(x: np.ndarray) -> float:
    """70/30 blend of the K- and A-weighted active levels (dB) - what the syllables are matched on."""
    _, k, a = loudness_db(x)
    return (1.0 - A_WEIGHT_SHARE) * k + A_WEIGHT_SHARE * a
