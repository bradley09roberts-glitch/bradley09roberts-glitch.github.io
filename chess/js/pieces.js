/* Mosswood Chess: hand-drawn SVG creatures for every piece, the coach, and bot avatars. */
(function (global) {
  'use strict';

  const W = {
    L: '#3b2414', body: '#fff4de', body2: '#e6d2ad', cap: '#e0473b', cap2: '#b02f27', spot: '#fff8ea', leaf: '#86c95b', leaf2: '#a2dd73',
    fur: '#fbf1e1', fur2: '#e2cfb0', belly: '#fffaf0', earIn: '#f5b6a8', nose: '#f08a8a', dot: '#2b1a10', cheek: '#ff9a86',
    metal: '#e3e8ef', metal2: '#9fabbd', plume: '#e0473b', shield: '#3f78c9', gold: '#f6c847', gold2: '#c8901c',
    face: '#fffaf0', eyeW: '#ffffff', pupil: '#2b1a10', beak: '#f0a830', hat: '#3a78cf', hat2: '#28579b',
    door: '#8a5532', glow: '#ffe79a', flag: '#3a78cf',
    drag: '#f3d382', drag2: '#d9a845', scale: '#fff3c9', snout: '#f9e3a6', horn: '#fffaf0', wing: '#f09f5b', wing2: '#c8743a', gem: '#e23b4a', gem2: '#3a78cf',
    robe: '#3a6fd0', robe2: '#28519c', frog: '#a9d977', fbelly: '#eef7d2', ermine: '#ffffff', emblem: 'sun'
  };
  const B = {
    L: '#120a1a', body: '#4f4768', body2: '#352e4b', cap: '#6a3db0', cap2: '#472481', spot: '#86f5da', leaf: '#3fa58a', leaf2: '#58c4a5',
    fur: '#514a60', fur2: '#363044', belly: '#6f6788', earIn: '#9b5f9a', nose: '#c05b8a', dot: '#ffd84a', cheek: '#c0508f',
    metal: '#737c96', metal2: '#454c61', plume: '#86f5da', shield: '#7a2448', gold: '#cbbcff', gold2: '#8d7ae0',
    face: '#625a7c', eyeW: '#ffd84a', pupil: '#1a0f22', beak: '#d08a2a', hat: '#5a2a8e', hat2: '#3b1a63',
    door: '#1d1229', glow: '#86f5da', flag: '#86f5da',
    drag: '#45426a', drag2: '#2b2944', scale: '#8a78c7', snout: '#55517f', horn: '#cbbcff', wing: '#6a2f8a', wing2: '#42185c', gem: '#86f5da', gem2: '#ff5c8a',
    robe: '#8a1f45', robe2: '#5e1230', frog: '#457484', fbelly: '#8fb6ae', ermine: '#ddd6ee', emblem: 'moon'
  };
  const PAL = {
    w: W, b: B,
    ember: Object.assign({}, W, { L: '#2a0f0a', drag: '#e0533f', drag2: '#a8301f', scale: '#ffd27a', snout: '#ef7a63', horn: '#fff0d0', wing: '#7a1f2a', wing2: '#4f0f18', gold: '#f6c847', eyeW: '#ffe066', gem: '#ffe066' }),
    prince: Object.assign({}, W, { frog: '#8fd88a', fbelly: '#e8fbd8', robe: '#2fa37c', robe2: '#1f7a5b', gold: '#ffd76a' }),
    moss: Object.assign({}, W, { cap: '#5aa84a', cap2: '#3d7d32', spot: '#f2ffd8' }),
    rose: Object.assign({}, W, { fur: '#f6e3d4', hat: '#c0457a', hat2: '#8e2c59', flag: '#c0457a' })
  };

  const S = (p, w) => `stroke="${p.L}" stroke-width="${w || 2.6}" stroke-linejoin="round" stroke-linecap="round"`;
  const shadow = rx => `<ellipse class="pc-shadow" cx="50" cy="91" rx="${rx}" ry="4.2"/>`;

  const ART = {
    1: p => `${shadow(19)}<g class="pc-body">
      <path d="M38.5 89 C35.5 79 37 67 41.5 58.5 L58.5 58.5 C63 67 64.5 79 61.5 89 Z" fill="${p.body}" ${S(p)}/>
      <path d="M42 62 C40.2 70 40.2 80 41.6 87" fill="none" stroke="${p.body2}" stroke-width="3.5" stroke-linecap="round" opacity=".8"/>
      <g class="pc-eyes"><ellipse cx="45.2" cy="71" rx="2.5" ry="3.1" fill="${p.dot}"/><ellipse cx="54.8" cy="71" rx="2.5" ry="3.1" fill="${p.dot}"/><circle cx="46" cy="70" r=".9" fill="#fff" opacity=".85"/><circle cx="55.6" cy="70" r=".9" fill="#fff" opacity=".85"/></g>
      <circle cx="41.5" cy="76.5" r="2.4" fill="${p.cheek}" opacity=".55"/><circle cx="58.5" cy="76.5" r="2.4" fill="${p.cheek}" opacity=".55"/>
      <path d="M47.5 77 Q50 79.6 52.5 77" fill="none" stroke="${p.L}" stroke-width="1.7" stroke-linecap="round"/>
      <g class="pc-leaf"><path d="M50 29 L50 19.5" stroke="${p.L}" stroke-width="2.2" stroke-linecap="round"/>
        <path d="M50 21 C45 15 39.5 16 38 19.5 C42.5 23 47 23 50 21 Z" fill="${p.leaf}" ${S(p, 1.8)}/>
        <path d="M50 20.5 C55 13.5 61.5 14.5 63 18 C58.5 22 53.5 22.5 50 20.5 Z" fill="${p.leaf2}" ${S(p, 1.8)}/></g>
      <path d="M22.5 60 C21 39 35 27.5 50 27.5 C65 27.5 79 39 77.5 60 C64 64.5 36 64.5 22.5 60 Z" fill="${p.cap}" ${S(p)}/>
      <path d="M27 56.5 C40 60.5 60 60.5 73 56.5" fill="none" stroke="${p.cap2}" stroke-width="3" opacity=".75" stroke-linecap="round"/>
      <path d="M30 42 C33.5 35 41 31 47.5 30.6" fill="none" stroke="#fff" stroke-width="2.6" opacity=".3" stroke-linecap="round"/>
      <circle cx="37" cy="45" r="4.4" fill="${p.spot}"/><circle cx="55" cy="37.5" r="3.4" fill="${p.spot}"/><circle cx="66.5" cy="48.5" r="3.9" fill="${p.spot}"/><circle cx="48" cy="52" r="2.6" fill="${p.spot}"/>
    </g>`,

    2: p => `${shadow(21)}<g class="pc-body">
      <g class="pc-ears">
        <path d="M41 40 C33 27 31.5 10 37 7.5 C43 5.5 47 21 47.5 37 Z" fill="${p.fur}" ${S(p)}/>
        <path d="M41.5 33 C37.5 23 37 13.5 39 12.5 C42 12 44 22 44.5 33 Z" fill="${p.earIn}"/>
        <path d="M53 37 C54 21 59 5.5 65 7.5 C71 10 68.5 26 60 40 Z" fill="${p.fur}" ${S(p)}/>
        <path d="M56 33 C57 22 61 12.5 63.5 13 C65.5 14.5 64 24 59.5 34 Z" fill="${p.earIn}"/>
      </g>
      <path d="M31 89 C27 74 33 60.5 50 60.5 C67 60.5 73 74 69 89 Z" fill="${p.fur}" ${S(p)}/>
      <path d="M41 88 C39 79 42 71 50 71 C58 71 61 79 59 88 Z" fill="${p.belly}" opacity=".9"/>
      <ellipse cx="50" cy="48" rx="19.5" ry="16.5" fill="${p.fur}" ${S(p)}/>
      <path d="M31.5 46 C32 33 41 29 50 29 C59 29 68 33 68.5 46 C63 41.5 37 41.5 31.5 46 Z" fill="${p.metal}" ${S(p, 2.2)}/>
      <path d="M35 42.3 C44 39.3 56 39.3 65 42.3" stroke="${p.metal2}" stroke-width="2" fill="none" stroke-linecap="round"/>
      <circle cx="40" cy="36.5" r="1.2" fill="${p.metal2}"/><circle cx="60" cy="36.5" r="1.2" fill="${p.metal2}"/>
      <path d="M50 29.5 C49 22 54 17 61.5 18.5 C57.5 21 55.5 25 54.5 30 Z" fill="${p.plume}" ${S(p, 1.8)}/>
      <g class="pc-eyes"><ellipse cx="42.5" cy="50.5" rx="2.9" ry="3.5" fill="${p.dot}"/><ellipse cx="57.5" cy="50.5" rx="2.9" ry="3.5" fill="${p.dot}"/><circle cx="43.4" cy="49.3" r="1" fill="#fff" opacity=".85"/><circle cx="58.4" cy="49.3" r="1" fill="#fff" opacity=".85"/></g>
      <path d="M47.6 55.6 L52.4 55.6 L50 58.4 Z" fill="${p.nose}" ${S(p, 1.2)}/>
      <path d="M50 58.4 C49 60.8 46.5 61 45.5 59.6 M50 58.4 C51 60.8 53.5 61 54.5 59.6" fill="none" stroke="${p.L}" stroke-width="1.5" stroke-linecap="round"/>
      <path d="M40 56.5 L32 55 M40 58.8 L32.5 60.5 M60 56.5 L68 55 M60 58.8 L67.5 60.5" stroke="${p.L}" stroke-width="1" opacity=".5" stroke-linecap="round"/>
      <circle cx="38" cy="56" r="2.4" fill="${p.cheek}" opacity=".45"/><circle cx="62" cy="56" r="2.4" fill="${p.cheek}" opacity=".45"/>
      <path d="M41.5 67 L58.5 67 L58.5 76.5 C58.5 82.5 54.5 86.5 50 88.5 C45.5 86.5 41.5 82.5 41.5 76.5 Z" fill="${p.shield}" ${S(p, 2.2)}/>
      ${p.emblem === 'moon'
        ? `<path d="M52.6 71.4 A5.2 5.2 0 1 0 52.6 81.4 A4 4 0 1 1 52.6 71.4 Z" fill="${p.gold}"/>`
        : `<circle cx="50" cy="76" r="3.6" fill="${p.gold}"/><path d="M50 69.8 V71.2 M50 80.8 V82.2 M43.8 76 H45.2 M54.8 76 H56.2 M45.6 71.6 L46.6 72.6 M53.4 79.4 L54.4 80.4 M54.4 71.6 L53.4 72.6 M46.6 79.4 L45.6 80.4" stroke="${p.gold}" stroke-width="1.5" stroke-linecap="round"/>`}
      <ellipse cx="37.5" cy="88.5" rx="7" ry="3.4" fill="${p.fur2}" ${S(p, 2)}/><ellipse cx="62.5" cy="88.5" rx="7" ry="3.4" fill="${p.fur2}" ${S(p, 2)}/>
    </g>`,

    3: p => `${shadow(21)}<g class="pc-body">
      <path d="M29 58 C19 64 18 79 28.5 87 C31 77 31.5 67 29 58 Z" fill="${p.fur2}" ${S(p)}/>
      <path d="M71 58 C81 64 82 79 71.5 87 C69 77 68.5 67 71 58 Z" fill="${p.fur2}" ${S(p)}/>
      <path d="M28.5 87 C21.5 67 28 45.5 50 43.5 C72 45.5 78.5 67 71.5 87 C63 91.5 37 91.5 28.5 87 Z" fill="${p.fur}" ${S(p)}/>
      <path d="M38 85.5 C34 72 39.5 62 50 62 C60.5 62 66 72 62 85.5 C56 88 44 88 38 85.5 Z" fill="${p.belly}"/>
      <path d="M42.5 70 l3 2.6 l3 -2.6 M51.5 70 l3 2.6 l3 -2.6 M47 77.5 l3 2.6 l3 -2.6" fill="none" stroke="${p.fur2}" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/>
      <circle cx="41.5" cy="54.5" r="10" fill="${p.face}" ${S(p, 2)}/><circle cx="58.5" cy="54.5" r="10" fill="${p.face}" ${S(p, 2)}/>
      <g class="pc-eyes"><circle cx="41.5" cy="54.5" r="6.6" fill="${p.eyeW}" ${S(p, 1.6)}/><circle cx="58.5" cy="54.5" r="6.6" fill="${p.eyeW}" ${S(p, 1.6)}/>
        <circle cx="42.4" cy="55.1" r="3.4" fill="${p.pupil}"/><circle cx="57.6" cy="55.1" r="3.4" fill="${p.pupil}"/>
        <circle cx="43.4" cy="53.8" r="1.1" fill="#fff"/><circle cx="58.6" cy="53.8" r="1.1" fill="#fff"/></g>
      <path d="M46.8 60 L53.2 60 L50 66.5 Z" fill="${p.beak}" ${S(p, 1.6)}/>
      <ellipse cx="42" cy="89" rx="4" ry="2" fill="${p.beak}" ${S(p, 1.4)}/><ellipse cx="58" cy="89" rx="4" ry="2" fill="${p.beak}" ${S(p, 1.4)}/>
      <g class="pc-hat">
        <ellipse cx="50" cy="44" rx="23" ry="5" fill="${p.hat2}" ${S(p, 2.2)}/>
        <path d="M35.5 44 C40 32 44 20 47 11 C48.5 6 55 5 58.5 9.5 C60 11.5 59 13 57.2 12.2 C55 11.2 53.6 12.5 54.2 15.5 C56 25 59.5 35 64.5 44 C55 46.5 45 46.5 35.5 44 Z" fill="${p.hat}" ${S(p)}/>
        <path d="M37.8 39 C46 41.2 54 41.2 62.2 39 L63.6 42.6 C54.5 45 45.5 45 36.4 42.6 Z" fill="${p.gold}" ${S(p, 1.5)}/>
        <path class="pc-star" d="M50.5 21 l1.8 3.7 4 .5 -2.9 2.8 .7 4 -3.6 -1.9 -3.6 1.9 .7 -4 -2.9 -2.8 4 -.5z" fill="${p.gold}"/>
      </g>
    </g>`,

    4: p => `${shadow(24)}<g class="pc-body">
      <path d="M32.5 89 L35.5 47 L64.5 47 L67.5 89 Z" fill="${p.body}" ${S(p)}/>
      <path d="M36 58 H47 M53 58 H64 M35.3 69 H41 M46.5 69 H59 M63 69 H65.6 M34.6 80 H39.5 M60.5 80 H66" stroke="${p.body2}" stroke-width="2" stroke-linecap="round"/>
      <path d="M42.5 89 L42.5 77.5 C42.5 70.5 57.5 70.5 57.5 77.5 L57.5 89 Z" fill="${p.door}" ${S(p, 2.2)}/>
      <path d="M47.5 72.6 V89 M52.5 72.6 V89" stroke="${p.L}" stroke-width="1" opacity=".45"/>
      <circle cx="54.6" cy="81.5" r="1.3" fill="${p.gold}"/>
      <g class="pc-window"><circle cx="50" cy="59" r="5.2" fill="${p.glow}" ${S(p, 2)}/><path d="M50 53.8 V64.2 M44.8 59 H55.2" stroke="${p.L}" stroke-width="1.6"/></g>
      <path d="M50 15 V3.5" stroke="${p.L}" stroke-width="2.2" stroke-linecap="round"/>
      <g class="pc-flag"><path d="M50.5 3.5 C55 2 59 5.5 64.5 4 L64.5 11 C59 12.5 55 9 50.5 10.5 Z" fill="${p.flag}" ${S(p, 1.6)}/></g>
      <path d="M18.5 44 C18.5 25 33 14.5 50 14.5 C67 14.5 81.5 25 81.5 44 Z" fill="${p.cap}" ${S(p)}/>
      <path d="M26 30 C30.5 23 38 18.5 45 17.8" fill="none" stroke="#fff" stroke-width="2.6" opacity=".3" stroke-linecap="round"/>
      <circle cx="33" cy="34" r="4.2" fill="${p.spot}"/><circle cx="50" cy="25" r="3.6" fill="${p.spot}"/><circle cx="66" cy="32" r="4.6" fill="${p.spot}"/><circle cx="44" cy="38" r="2.5" fill="${p.spot}"/><circle cx="58.5" cy="40" r="2.2" fill="${p.spot}"/>
      <rect x="16.5" y="42" width="67" height="8.5" rx="4" fill="${p.cap2}" ${S(p)}/>
      <path d="M27.5 42.8 V49.7 M38.5 42.8 V49.7 M50 42.8 V49.7 M61.5 42.8 V49.7 M72.5 42.8 V49.7" stroke="${p.L}" stroke-width="1.6" opacity=".45"/>
    </g>`,

    5: p => `${shadow(22)}<g class="pc-body">
      <path d="M62 85 C76 88 86 80 84.5 69 C84 65.5 80.5 65.5 80.5 69 C80.5 77 73 80.5 63 79 Z" fill="${p.drag}" ${S(p)}/>
      <path d="M82.3 66.4 L85.6 59.6 L87.4 67.8 Z" fill="${p.horn}" ${S(p, 1.5)}/>
      <g class="pc-wing-l"><path d="M37 61 C28 50 15 46 7 51 C12.5 53 14.5 57 13 61.5 C18.5 59.5 22 63 22 67.5 C27.5 64 32 64.5 36 67 Z" fill="${p.wing}" ${S(p)}/>
        <path d="M34 62.5 L13.5 52 M30.5 64 L17.5 60" stroke="${p.wing2}" stroke-width="1.6" opacity=".85" stroke-linecap="round"/></g>
      <g class="pc-wing-r"><path d="M63 61 C72 50 85 46 93 51 C87.5 53 85.5 57 87 61.5 C81.5 59.5 78 63 78 67.5 C72.5 64 68 64.5 64 67 Z" fill="${p.wing}" ${S(p)}/>
        <path d="M66 62.5 L86.5 52 M69.5 64 L82.5 60" stroke="${p.wing2}" stroke-width="1.6" opacity=".85" stroke-linecap="round"/></g>
      <path d="M33 89 C28.5 73 34 57.5 50 57.5 C66 57.5 71.5 73 67 89 Z" fill="${p.drag}" ${S(p)}/>
      <path d="M42.5 88.5 C40.5 77 43.5 67 50 65 C56.5 67 59.5 77 57.5 88.5 Z" fill="${p.scale}" ${S(p, 1.6)}/>
      <path d="M43.4 72 H56.6 M42.4 78 H57.6 M42.5 84 H57.5" stroke="${p.drag2}" stroke-width="1.3" opacity=".7"/>
      <ellipse cx="38" cy="88.6" rx="6" ry="3" fill="${p.drag2}" ${S(p, 1.8)}/><ellipse cx="62" cy="88.6" rx="6" ry="3" fill="${p.drag2}" ${S(p, 1.8)}/>
      <path d="M37.5 31 C32.5 24 30.5 17 31.5 12.5 C35.5 16.5 40 22.5 42.5 28 Z" fill="${p.horn}" ${S(p, 2)}/>
      <path d="M62.5 31 C67.5 24 69.5 17 68.5 12.5 C64.5 16.5 60 22.5 57.5 28 Z" fill="${p.horn}" ${S(p, 2)}/>
      <path d="M32 43 C32 29 41.5 22.5 50 22.5 C58.5 22.5 68 29 68 43 C68 53 60 58.5 50 58.5 C40 58.5 32 53 32 43 Z" fill="${p.drag}" ${S(p)}/>
      <path d="M36 32 C39 27.5 43.5 25.5 47.5 25" fill="none" stroke="#fff" stroke-width="2.2" opacity=".3" stroke-linecap="round"/>
      <ellipse cx="50" cy="50" rx="11.5" ry="7.5" fill="${p.snout}" ${S(p, 2)}/>
      <ellipse cx="46" cy="48.4" rx="1.4" ry="1.1" fill="${p.L}"/><ellipse cx="54" cy="48.4" rx="1.4" ry="1.1" fill="${p.L}"/>
      <path d="M43.5 53 Q50 57.3 56.5 53" fill="none" stroke="${p.L}" stroke-width="1.6" stroke-linecap="round"/>
      <path d="M53.3 54.7 L54.4 57.5 L55.6 54.1" fill="#fff" stroke="${p.L}" stroke-width="1" stroke-linejoin="round"/>
      <g class="pc-eyes"><ellipse cx="41.5" cy="39.5" rx="4.6" ry="5.2" fill="${p.eyeW}" ${S(p, 1.6)}/><ellipse cx="58.5" cy="39.5" rx="4.6" ry="5.2" fill="${p.eyeW}" ${S(p, 1.6)}/>
        <ellipse cx="42" cy="40" rx="1.6" ry="3.6" fill="${p.pupil}"/><ellipse cx="58" cy="40" rx="1.6" ry="3.6" fill="${p.pupil}"/></g>
      <path d="M40.5 27 L42.5 16 L46.5 22 L50 12 L53.5 22 L57.5 16 L59.5 27 C53 25.5 47 25.5 40.5 27 Z" fill="${p.gold}" ${S(p, 2)}/>
      <circle cx="50" cy="22.6" r="2.2" fill="${p.gem}" ${S(p, 1.2)}/>
    </g>`,

    6: p => `${shadow(25)}<g class="pc-body">
      <path d="M23.5 87 C21 71 27 57.5 36 53 L64 53 C73 57.5 79 71 76.5 87 C66 91 34 91 23.5 87 Z" fill="${p.robe}" ${S(p)}/>
      <path d="M30 63 C28 70 28 78 29 85 M70 63 C72 70 72 78 71 85" stroke="${p.robe2}" stroke-width="2.4" fill="none" stroke-linecap="round"/>
      <path d="M31 88 C27 75 34 63 50 63 C66 63 73 75 69 88 C61 91.5 39 91.5 31 88 Z" fill="${p.frog}" ${S(p)}/>
      <ellipse cx="50" cy="79.5" rx="12.5" ry="8.2" fill="${p.fbelly}"/>
      <path d="M31 60 C40 67 60 67 69 60 C70.5 63 69 66.4 66 67.8 C56 71.6 44 71.6 34 67.8 C31 66.4 29.5 63 31 60 Z" fill="${p.ermine}" ${S(p, 2)}/>
      <circle cx="40" cy="68" r="1" fill="${p.L}"/><circle cx="50" cy="70" r="1" fill="${p.L}"/><circle cx="60" cy="68" r="1" fill="${p.L}"/>
      <ellipse cx="37" cy="87" rx="6.5" ry="3.4" fill="${p.frog}" ${S(p, 2)}/><ellipse cx="63" cy="87" rx="6.5" ry="3.4" fill="${p.frog}" ${S(p, 2)}/>
      <circle cx="34.5" cy="39" r="9.5" fill="${p.frog}" ${S(p)}/><circle cx="65.5" cy="39" r="9.5" fill="${p.frog}" ${S(p)}/>
      <path d="M25.5 51 C25.5 40 37 34.5 50 34.5 C63 34.5 74.5 40 74.5 51 C74.5 61 64 65.5 50 65.5 C36 65.5 25.5 61 25.5 51 Z" fill="${p.frog}" ${S(p)}/>
      <g class="pc-eyes"><circle cx="34.5" cy="38" r="6.2" fill="${p.eyeW}" ${S(p, 1.6)}/><circle cx="65.5" cy="38" r="6.2" fill="${p.eyeW}" ${S(p, 1.6)}/>
        <ellipse cx="35.2" cy="38.6" rx="3.6" ry="2.6" fill="${p.pupil}"/><ellipse cx="64.8" cy="38.6" rx="3.6" ry="2.6" fill="${p.pupil}"/>
        <circle cx="36.4" cy="37.4" r="1" fill="#fff"/><circle cx="66" cy="37.4" r="1" fill="#fff"/></g>
      <circle cx="46.5" cy="47.5" r="1.1" fill="${p.L}"/><circle cx="53.5" cy="47.5" r="1.1" fill="${p.L}"/>
      <path d="M35 53.5 Q50 63.5 65 53.5" fill="none" stroke="${p.L}" stroke-width="2.4" stroke-linecap="round"/>
      <ellipse cx="31.5" cy="55" rx="3.4" ry="2.2" fill="${p.cheek}" opacity=".55"/><ellipse cx="68.5" cy="55" rx="3.4" ry="2.2" fill="${p.cheek}" opacity=".55"/>
      <g class="pc-crown">
        <path d="M50 15 V5.5 M46 9 H54" stroke="${p.L}" stroke-width="4.8" stroke-linecap="round"/>
        <path d="M50 15 V5.5 M46 9 H54" stroke="${p.gold}" stroke-width="2.4" stroke-linecap="round"/>
        <path d="M39 36 L37 20 L44 27 L50 15 L56 27 L63 20 L61 36 C54 34.3 46 34.3 39 36 Z" fill="${p.gold}" ${S(p, 2)}/>
        <circle cx="50" cy="30" r="2.4" fill="${p.gem}" ${S(p, 1.2)}/><circle cx="43.2" cy="32" r="1.4" fill="${p.gem2}"/><circle cx="56.8" cy="32" r="1.4" fill="${p.gem2}"/>
      </g>
    </g>`
  };

  const NAMES = {
    1: { f: 'Sprout', c: 'Pawn', letter: 'P', value: 1 },
    2: { f: 'Hare', c: 'Knight', letter: 'N', value: 3 },
    3: { f: 'Owl', c: 'Bishop', letter: 'B', value: 3 },
    4: { f: 'Tower', c: 'Rook', letter: 'R', value: 5 },
    5: { f: 'Dragon', c: 'Queen', letter: 'Q', value: 9 },
    6: { f: 'Frog King', c: 'King', letter: 'K', value: 0 }
  };

  function palFor(code, override) {
    if (override) return PAL[override] || PAL.w;
    return (code >> 3) ? PAL.b : PAL.w;
  }

  function svg(code, opts) {
    opts = opts || {};
    const t = code & 7, p = palFor(code, opts.palette);
    const side = (code >> 3) ? 'b' : 'w';
    return `<svg class="pc-svg pc-t${t} pc-${side}${opts.cls ? ' ' + opts.cls : ''}" viewBox="0 0 100 100" aria-hidden="true">${ART[t](p)}</svg>`;
  }

  function mini(code) { return svg(code, { cls: 'mini' }); }

  /* Coach: Sage Bramblewick the hedgehog */
  function coach(expr) {
    expr = expr || 'happy';
    const L = '#3b2414';
    let spikes = '';
    const cx = 50, cy = 62, n = 17, a0 = Math.PI * 0.92, a1 = Math.PI * 2.08;
    const pts = [];
    for (let i = 0; i <= n * 2; i++) {
      const a = a0 + (a1 - a0) * (i / (n * 2));
      const r = i % 2 ? 44 : 33;
      pts.push((cx + Math.cos(a) * r).toFixed(1) + ' ' + (cy + Math.sin(a) * r * 0.95).toFixed(1));
    }
    spikes = `<path d="M${pts.join(' L')} Z" fill="#7d5536" stroke="${L}" stroke-width="2.4" stroke-linejoin="round"/>`;
    const inner = [];
    for (let i = 0; i <= n * 2; i++) {
      const a = a0 + 0.08 + (a1 - a0 - 0.16) * (i / (n * 2));
      const r = i % 2 ? 37 : 30;
      inner.push((cx + Math.cos(a) * r).toFixed(1) + ' ' + (cy + Math.sin(a) * r * 0.95).toFixed(1));
    }
    spikes += `<path d="M${inner.join(' L')}" fill="none" stroke="#a5774d" stroke-width="2" stroke-linejoin="round" opacity=".8"/>`;
    const E = {
      happy: { brows: 'M33 50.5 Q39.5 46.5 46 50.5 M54 50.5 Q60.5 46.5 67 50.5', mouth: `<path d="M43 78 Q50 84.5 57 78" fill="none" stroke="${L}" stroke-width="2.2" stroke-linecap="round"/>`, eyes: 'open' },
      proud: { brows: 'M33 50 Q39.5 46 46 50 M54 50 Q60.5 46 67 50', mouth: `<path d="M42.5 77 Q50 87 57.5 77 Z" fill="#7a2e2e" stroke="${L}" stroke-width="2" stroke-linejoin="round"/><path d="M45.5 81.5 Q50 84.5 54.5 81.5" fill="#f08a8a"/>`, eyes: 'closed' },
      worried: { brows: 'M33 53 L45 49.5 M67 53 L55 49.5', mouth: `<path d="M43.5 80.5 Q46.8 77.5 50 80.5 Q53.2 83.5 56.5 80.5" fill="none" stroke="${L}" stroke-width="2.2" stroke-linecap="round"/>`, eyes: 'open' },
      shocked: { brows: 'M33 47.5 Q39.5 43 46 47.5 M54 47.5 Q60.5 43 67 47.5', mouth: `<ellipse cx="50" cy="80.5" rx="3.6" ry="4.6" fill="#5a1f1f" stroke="${L}" stroke-width="2"/>`, eyes: 'wide' },
      thinking: { brows: 'M33 49 Q39.5 45.5 46 49 M54 51.5 L67 51.5', mouth: `<path d="M45 80 Q50 78.6 55.5 79.2" fill="none" stroke="${L}" stroke-width="2.2" stroke-linecap="round"/>`, eyes: 'side' },
      neutral: { brows: 'M33.5 50.5 L45.5 50.5 M54.5 50.5 L66.5 50.5', mouth: `<path d="M44.5 79 Q50 81.5 55.5 79" fill="none" stroke="${L}" stroke-width="2.2" stroke-linecap="round"/>`, eyes: 'open' }
    }[expr] || {};
    let eyes;
    if (E.eyes === 'closed') eyes = `<path d="M36 61 Q39.5 57 43 61 M57 61 Q60.5 57 64 61" fill="none" stroke="${L}" stroke-width="2.2" stroke-linecap="round"/>`;
    else {
      const r = E.eyes === 'wide' ? 3.9 : 3.3, dx = E.eyes === 'side' ? 1.3 : 0;
      eyes = `<g class="co-eyes"><circle cx="${39.5 + dx}" cy="60" r="${r}" fill="#2b1a10"/><circle cx="${60.5 + dx}" cy="60" r="${r}" fill="#2b1a10"/><circle cx="${40.6 + dx}" cy="58.8" r="1.1" fill="#fff"/><circle cx="${61.6 + dx}" cy="58.8" r="1.1" fill="#fff"/></g>`;
    }
    return `<svg class="coach-svg co-${expr}" viewBox="0 0 100 100" aria-hidden="true">
      <ellipse cx="50" cy="94" rx="30" ry="4" fill="#000" opacity=".22"/>
      ${spikes}
      <circle cx="28.5" cy="49" r="6" fill="#e9c79f" stroke="${L}" stroke-width="2"/><circle cx="71.5" cy="49" r="6" fill="#e9c79f" stroke="${L}" stroke-width="2"/>
      <ellipse cx="50" cy="65" rx="26" ry="23" fill="#f3dcb8" stroke="${L}" stroke-width="2.4"/>
      <ellipse cx="34" cy="71" rx="4.4" ry="2.8" fill="#ff9a86" opacity=".55"/><ellipse cx="66" cy="71" rx="4.4" ry="2.8" fill="#ff9a86" opacity=".55"/>
      <ellipse cx="50" cy="75" rx="10.5" ry="7.2" fill="#fbecd5" stroke="${L}" stroke-width="2"/>
      ${eyes}
      <circle cx="39.5" cy="60" r="7.6" fill="rgba(255,255,255,.16)" stroke="#c8901c" stroke-width="2.1"/>
      <circle cx="60.5" cy="60" r="7.6" fill="rgba(255,255,255,.16)" stroke="#c8901c" stroke-width="2.1"/>
      <path d="M47.1 59 Q50 56.8 52.9 59" fill="none" stroke="#c8901c" stroke-width="2"/>
      <path class="co-brows" d="${E.brows}" fill="none" stroke="${L}" stroke-width="2.2" stroke-linecap="round"/>
      <ellipse cx="50" cy="70.5" rx="4.3" ry="3.4" fill="#2b1a10"/><circle cx="48.6" cy="69.4" r="1.1" fill="#fff" opacity=".7"/>
      ${E.mouth}
      <g class="co-hat"><path d="M35 33 C35 20 65 20 65 33 C57 35.5 43 35.5 35 33 Z" fill="#8a5a2b" stroke="${L}" stroke-width="2.2" stroke-linejoin="round"/>
        <path d="M39 26.5 L61 26.5 M37 30.5 L63 30.5 M44 22.5 L56 22.5" stroke="#5e3b18" stroke-width="1.4" opacity=".7"/>
        <path d="M50 21.5 C50 17.5 52.5 15 55.5 14.5" fill="none" stroke="${L}" stroke-width="2.4" stroke-linecap="round"/>
        <path d="M54 15.5 C57.5 10.5 63.5 11 65 13.5 C61.5 16.8 57 17.2 54 15.5 Z" fill="#7cc35a" stroke="${L}" stroke-width="1.6" stroke-linejoin="round"/></g>
    </svg>`;
  }

  // Lone square badge glyphs for review classifications
  const CLASS_INFO = {
    brilliant: { label: 'Brilliant', sym: '!!', color: '#2ec4b6' },
    great: { label: 'Great', sym: '!', color: '#5b8def' },
    best: { label: 'Best', sym: '★', color: '#7fbf3f' },
    excellent: { label: 'Excellent', sym: '👍', color: '#8cc152' },
    good: { label: 'Good', sym: '✓', color: '#a3b18a' },
    book: { label: 'Book', sym: '📖', color: '#b08d57' },
    forced: { label: 'Forced', sym: '→', color: '#a3b18a' },
    inaccuracy: { label: 'Inaccuracy', sym: '?!', color: '#f2c14e' },
    mistake: { label: 'Mistake', sym: '?', color: '#f08c3a' },
    miss: { label: 'Missed chance', sym: '✗', color: '#e8743b' },
    blunder: { label: 'Blunder', sym: '??', color: '#e5484d' }
  };

  global.PieceArt = { svg, mini, coach, NAMES, PAL, CLASS_INFO };
})(typeof window !== 'undefined' ? window : globalThis);
