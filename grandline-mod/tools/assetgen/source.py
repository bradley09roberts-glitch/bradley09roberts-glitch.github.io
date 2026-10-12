"""Reads the ids the Java code refers to, so the generator and the checker stay in sync with the mod."""
import os
import re

TOOLS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.dirname(TOOLS)
JAVA = os.path.join(ROOT, 'src/main/java/com/grandline')
RES = os.path.join(ROOT, 'src/main/resources')


def _read(rel):
    with open(os.path.join(JAVA, rel), encoding='utf-8') as f:
        return f.read()


def item_ids():
    # feature registers that add their items to ModItems.ITEMS from their own class
    src = _read('registry/ModItems.java') + _read('ship/NavalItems.java')
    plain = re.findall(r'ITEMS\.register\("([a-z0-9_]+)"', src)
    weapons = re.findall(r'\bweapon\("([a-z0-9_]+)"', src)
    return plain, weapons


def block_ids():
    return re.findall(r'BLOCKS\.register\("([a-z0-9_]+)"', _read('registry/ModBlocks.java'))


def skin_ids():
    src = _read('npc/Archetypes.java')
    out = []
    for block in re.findall(r'List\.of\(((?:"[a-z0-9_]+"\s*,?\s*)+)\)', src):
        for s in re.findall(r'"([a-z0-9_]+)"', block):
            if re.search(r'_\d+$', s) and s not in out:
                out.append(s)
    default = re.search(r'define\(SKIN,\s*"([a-z0-9_]+)"\)', _read('npc/GLNpc.java'))
    if default and default.group(1) not in out:
        out.append(default.group(1))
    return out


def projectile_kinds():
    src = _read('client/render/ProjectileRenderer.java')
    m = re.search(r'KINDS\s*=\s*Set\.of\(([^;]*)\);', src, re.S)
    kinds = re.findall(r'"([a-z0-9_]+)"', m.group(1)) if m else []
    return kinds + ['energy']


def loot_table_ids():
    ids = set()
    for rel in ('world/settlement/Buildings.java', 'world/landmark/LandmarkBuilders.java'):
        ids.update(re.findall(r'"grandline:(chests/[a-z0-9_/]+)"', _read(rel)))
    return sorted(ids)


def mob_effect_ids():
    return re.findall(r'EFFECTS\.register\("([a-z0-9_]+)"', _read('registry/ModEffects.java'))
