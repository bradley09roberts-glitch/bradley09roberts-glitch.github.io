#!/usr/bin/env python3
"""Builds docs/STRUCTURE_AUDIT.md: one ledger row per unique structure (every landmark, every settlement building
type in every style, island secrets, ship interiors), comparing the BEFORE and AFTER structure dumps
(GL_SELFTEST=structures) and merging the written review notes.

    python3 -I tools/gen_structure_audit.py <before dump dir> <after dump dir> [--renders docs/audit/renders]

Automatic columns (from the dumps): footprint, blocks before/after, height, kit blocks used after (grandline:
architectural kit), gameplay anchors before/after (marker kinds + NPC roles + loot tables) and whether they are
preserved. Review columns come from docs/audit/*.md (landmark batches: "### <id>" sections) and
docs/audit/settlement_notes.json (settlement types). A structure missing from the AFTER dumps is flagged.
"""
import collections
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, 'docs')


def load(d):
    out = {}
    for p in glob.glob(os.path.join(d, '*.json')):
        with open(p) as f:
            j = json.load(f)
        if j['kind'] == 'town':
            continue
        out[j['id']] = j
    return out


def stats(j):
    pal = j['palette']
    bl = j['blocks']
    n = len(bl) // 4
    kit = collections.Counter()
    ys = [bl[i + 1] for i in range(0, len(bl), 4)]
    for i in range(0, len(bl), 4):
        s = pal[bl[i + 3]]
        if s.startswith('grandline:'):
            kit[s.split('[')[0].split(':')[1]] += 1
    anchors = collections.Counter()
    for m in j['markers']:
        role = re.search(r'role:"?([a-z_]+)', m['data'])
        anchors[m['kind'] + (':' + role.group(1) if role else '')] += 1
    loot = collections.Counter(c['loot'].split('/')[-1] for c in j['chests'])
    return {'blocks': n, 'height': (max(ys) - min(ys) + 1) if ys else 0, 'kit': kit, 'anchors': anchors, 'loot': loot}


def fmt_counter(c, limit=8):
    items = sorted(c.items(), key=lambda t: (-t[1], t[0]))
    s = ', '.join(f'{k}×{v}' if v > 1 else k for k, v in items[:limit])
    if len(items) > limit:
        s += f', +{len(items) - limit} more'
    return s or '—'


def landmark_notes():
    notes = {}
    for p in sorted(glob.glob(os.path.join(DOCS, 'audit', 'landmarks_*.md'))):
        batch = os.path.basename(p)[len('landmarks_'):-3]
        text = open(p, encoding='utf-8').read()
        for part in re.split(r'\n(?=#{2,3} )', text):
            m = re.match(r'#{2,3} `?([a-z0-9_]+)`?', part)
            if m:
                notes[m.group(1)] = (batch, part.strip())
    return notes


def kit_doc(usage):
    sys.path.insert(0, os.path.join(ROOT, 'tools'))
    from assetgen import arch_blocks as A
    fams = collections.OrderedDict()
    for b in A.BLOCKS:
        fams.setdefault(b['family'], []).append(b)
    out = ['# Architectural building kit\n',
           f'There are {len(A.BLOCKS)} blocks in {len(fams)} families, all registered in `registry/ArchBlocks` (namespace `grandline`). They appear in the '
           'creative tab "Grand Line Architecture".\n',
           'One spec, `tools/assetgen/arch_blocks.py`, generates everything for them:\n',
           '- the Java registration, 16×16 textures, block and item models and block states;',
           '- loot tables (slabs drop two when double), recipes (crafting plus stonecutting for stone and tile families), '
           'mineable and shape tags, and English names.\n',
           'Textures are original pixel art painted by code (`tools/assetgen/arch_textures.py`).\n',
           '"Used in" counts the structures whose AFTER dump contains the block. It covers settlement buildings in every '
           'style, landmarks, secrets and ship interiors; streets and piers are stamped during generation and are listed separately.\n',
           '| family | block | kind | used in |', '|---|---|---|---|']
    for fam, bs in fams.items():
        for b in bs:
            u = sorted(usage.get(b['id'], []), key=lambda t: -t[0])
            where = f'{len(u)} structures (e.g. ' + ', '.join(i for _, i in u[:4]) + ')' if u else 'kit only (available to players)'
            out.append(f'| {fam} | `{b["id"]}` ({b["name"]}) | {b["kind"]} | {where} |')
    out.append('\nPier and street pieces stamped by the world generator (not in the dumps): `dock_planks`, `weathered_beam` '
               '(pilings), `bollard`, `brass_lantern`, `cargo_crate`, `cargo_sacks`, `rope_coil`, `dressed_sandstone` and '
               '`dressed_sandstone_wall` (desert paving and lamps), and the paper lanterns (Wano and jungle street lamps).')
    return '\n'.join(out) + '\n'


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(2)
    before, after = load(sys.argv[1]), load(sys.argv[2])
    renders = None
    if '--renders' in sys.argv:
        renders = sys.argv[sys.argv.index('--renders') + 1]
    settle = {}
    sp = os.path.join(DOCS, 'audit', 'settlement_notes.json')
    if os.path.exists(sp):
        settle = json.load(open(sp, encoding='utf-8'))
    lnotes = landmark_notes()
    ids = sorted(set(before) | set(after))
    rows = {'landmark': [], 'building': [], 'secret': [], 'interior': []}
    problems = []
    for i in ids:
        b, a = before.get(i), after.get(i)
        kind = (a or b)['kind']
        sb = stats(b) if b else None
        sa = stats(a) if a else None
        preserved = '—'
        if sb and sa:
            lost = (sb['anchors'] - sa['anchors']) + (sb['loot'] - sa['loot'])
            expected = settle.get(i.split('__')[0], {}).get('expected_lost', [])
            documented = [k for k in list(lost) if k in expected]
            for k in documented:
                del lost[k]
            gained = (sa['anchors'] - sb['anchors']) + (sa['loot'] - sb['loot'])
            if not lost:
                preserved = 'yes' + (f' (+{fmt_counter(gained, 4)})' if gained else '') + (f'; removed by design: {", ".join(documented)}' if documented else '')
            else:
                preserved = 'CHANGED: lost ' + fmt_counter(lost, 6) + ('; added ' + fmt_counter(gained, 4) if gained else '')
                problems.append(f'{i}: gameplay anchors lost {fmt_counter(lost, 10)}')
        if not a:
            problems.append(f'{i}: missing from the AFTER dump')
        rows[kind].append((i, kind, b, a, sb, sa, preserved))
    out = []
    out.append('# Structure audit ledger\n')
    out.append('This file is generated by `tools/gen_structure_audit.py` from the structure dumps taken before and after the building '
               'overhaul (`GL_SELFTEST=structures`), plus the written reviews in `docs/audit/`.\n')
    out.append('**What each row records:** every unique structure the mod generates, with its footprint, block counts, height and '
               'the new kit blocks it uses. "Anchors" are the gameplay hooks: NPC spawn roles, boards, cells, quest anchors, treasure '
               'and secret markers, and loot chests by table. "Preserved" checks that nothing a system depends on was lost.\n')
    out.append('**How variants are covered:**\n')
    out.append('- Settlement buildings are listed once per type × architectural style: 22 types in 15 styles.')
    out.append('- Plot rotation and terrain are exercised by `LandmarkCheck`, which tests every rotation, and by the real generated towns '
               '(`GL_DUMP_TOWNS`).\n')
    total = sum(len(v) for v in rows.values())
    rebuilt = sum(1 for v in rows.values() for r in v if r[3] is not None)
    out.append(f'**Coverage:** {total} unique structures and variants; {rebuilt} present in the AFTER dump; '
               f'{len(problems)} flagged.\n')
    if problems:
        out.append('**Flags**')
        for p in problems:
            out.append(f'- {p}')
        out.append('')
    # landmarks
    out.append(f'## Landmarks ({len(rows["landmark"])})\n')
    out.append('| id | host island (style) | footprint | blocks before → after | height | kit blocks used | anchors after | preserved | review |')
    out.append('|---|---|---|---|---|---|---|---|---|')
    for i, kind, b, a, sb, sa, pres in rows['landmark']:
        j = a or b
        rev = lnotes.get(i)
        review = f'batch {rev[0]}: see [notes](audit/landmarks_{rev[0]}.md)' if rev else 'not reviewed'
        if renders and os.path.exists(os.path.join(ROOT, renders, i + '.png')):
            review += f' · [render](audit/renders/{i}.png)'
        out.append(f'| `{i}` | {j.get("note", "")} | {j["w"]}×{j["d"]} | {sb["blocks"] if sb else "—"} → {sa["blocks"] if sa else "—"} | '
                   f'{sa["height"] if sa else "—"} | {fmt_counter(sa["kit"], 5) if sa else "—"} | {fmt_counter(sa["anchors"], 5) if sa else "—"} | {pres} | {review} |')
    # settlement buildings grouped by type
    out.append(f'\n## Settlement buildings ({len(rows["building"])} type × style variants)\n')
    by_type = collections.defaultdict(list)
    for r in rows['building']:
        by_type[r[0].split('__')[0]].append(r)
    for t in sorted(by_type):
        n = settle.get(t, {})
        out.append(f'### {t}\n')
        if n:
            out.append(f'- **Role:** {n.get("role", "")}')
            out.append(f'- **Before:** {n.get("before", "")}')
            out.append(f'- **After:** {n.get("after", "")}')
            out.append(f'- **Anchors kept:** {n.get("anchors", "")}')
            if n.get('expected_why'):
                out.append(f'- **Anchor change by design:** {n["expected_why"]}')
            out.append(f'- **Status:** {n.get("status", "")}\n')
        out.append('| style | footprint | blocks before → after | height | kit blocks used | anchors after | preserved |')
        out.append('|---|---|---|---|---|---|---|')
        for i, kind, b, a, sb, sa, pres in sorted(by_type[t]):
            j = a or b
            out.append(f'| {i.split("__")[1]} | {j["w"]}×{j["d"]} | {sb["blocks"] if sb else "—"} → {sa["blocks"] if sa else "—"} | '
                       f'{sa["height"] if sa else "—"} | {fmt_counter(sa["kit"], 4) if sa else "—"} | {fmt_counter(sa["anchors"], 4) if sa else "—"} | {pres} |')
        out.append('')
    for k, title in (('secret', 'Island secrets'), ('interior', 'Ship interiors')):
        out.append(f'## {title} ({len(rows[k])})\n')
        out.append('| id | blocks before → after | kit blocks used | anchors after | preserved | notes |')
        out.append('|---|---|---|---|---|---|')
        for i, kind, b, a, sb, sa, pres in rows[k]:
            n = settle.get(i, {})
            out.append(f'| `{i}` | {sb["blocks"] if sb else "—"} → {sa["blocks"] if sa else "—"} | {fmt_counter(sa["kit"], 4) if sa else "—"} | '
                       f'{fmt_counter(sa["anchors"], 4) if sa else "—"} | {pres} | {n.get("after", "")} |')
        out.append('')
    # where each kit block is used (AFTER dumps), for docs/BUILDING_KIT.md
    usage = collections.defaultdict(list)
    for i, j in after.items():
        for blk, n in stats(j)['kit'].items():
            usage[blk].append((n, i))
    kit_md = kit_doc(usage)
    with open(os.path.join(DOCS, 'BUILDING_KIT.md'), 'w', encoding='utf-8') as f:
        f.write(kit_md)
    extra = settle.get('_streets')
    if extra:
        out.append('## Streets, harbours and town composition\n')
        out.append(extra + '\n')
    path = os.path.join(DOCS, 'STRUCTURE_AUDIT.md')
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(out) + '\n')
    print(path, total, 'structures', len(problems), 'flags')
    for p in problems[:40]:
        print('  ', p)


if __name__ == '__main__':
    main()
