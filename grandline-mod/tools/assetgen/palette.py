"""Shared material library for item sprites."""
from .sprite import Mat, Fixed, Force, OD, OL, DK, MD, LT, HL

# ------------------------------------------------------------------------------------------- metals
STEEL = ['#15181d', '#3c434c', '#7c8794', '#adb7c1', '#d6dde3', '#ffffff']
BRIGHT_STEEL = ['#14171c', '#3a434e', '#8693a2', '#bcc6d0', '#e2e8ee', '#ffffff']
IRON = ['#181818', '#444444', '#6b6b6b', '#969696', '#c4c4c4', '#e8e8e8']
DARK_IRON = ['#0d0e11', '#25272c', '#383b42', '#4f535c', '#6c717c', '#9097a3']
BLACK_BLADE = ['#050507', '#16141c', '#24212d', '#353142', '#4f4a63', '#8a83a6']
GOLD = ['#3d2405', '#6e4a0c', '#a87414', '#dcaa2a', '#f5d55a', '#fff3b0']
BRASS = ['#33220a', '#5e4416', '#8f6a26', '#bf9440', '#dcbc6a', '#f6e4a8']
BRONZE = ['#2b1608', '#55301a', '#80502a', '#a8703a', '#c8955a', '#e8c490']
COPPER = ['#2e1208', '#5a2614', '#8a4024', '#b8623a', '#d88a5e', '#f4c09a']
SEASTONE = ['#0b1a1e', '#21393f', '#3c5d64', '#5b8189', '#86aab0', '#c4e0e2']

# ------------------------------------------------------------------------------------------- woods / cloth
WOOD = ['#241808', '#3f2b12', '#5e421d', '#83602c', '#a47b3c', '#c8a060']
DARK_WOOD = ['#160d06', '#2b1a0c', '#422814', '#5c3a1e', '#7a502c', '#9a6c40']
LIGHT_WOOD = ['#3a2a14', '#6a4e28', '#987240', '#bf955a', '#d8b47a', '#f0d8a8']
LEATHER = ['#1e0f07', '#3d2010', '#5a321a', '#7a4826', '#9a6236', '#bc8250']
RED_LEATHER = ['#200606', '#460d0d', '#6e1616', '#9a2222', '#c03a32', '#e2685a']
CLOTH_RED = ['#2a0508', '#55101a', '#8a1a24', '#bf2a30', '#e04a40', '#ff8a70']
CLOTH_BLUE = ['#070d24', '#122048', '#1f3878', '#3058aa', '#4f7fd0', '#90b8f0']
CLOTH_WHITE = ['#2a2c34', '#6a6e7a', '#a8acb6', '#d4d8de', '#eef0f2', '#ffffff']
PAPER = ['#3e3220', '#7c6a48', '#bfae84', '#dccb9e', '#ece0bc', '#fbf4dc']
WHITE_PAPER = ['#3a3c44', '#8a8c94', '#c8cad0', '#e4e6ea', '#f4f5f7', '#ffffff']
BONE = ['#3a3428', '#7a705c', '#bdb196', '#ddd3b8', '#f0e9d4', '#fffcf0']
GLASS = ['#16324a', '#3a6a8a', '#6aa8c8', '#9ad0e8', '#c8ecf8', '#ffffff']
ROPE = ['#2a1e0c', '#5a4420', '#8a6c38', '#b09050', '#cdb070', '#e8d8a0']


def M(r, **kw):
    """Material from a ramp list."""
    return Mat(None, ramp=r, **kw)


def wrap_pattern(a_idx=MD, b_color='#e8e0cc', period=2, phase=0):
    """Katana handle: alternating diamonds of ray-skin showing through the silk wrap."""
    def fn(x, y, idx):
        if idx in (OD, OL):
            return idx
        if (x - y + phase) % (2 * period) < period:
            return b_color
        return idx
    return fn
