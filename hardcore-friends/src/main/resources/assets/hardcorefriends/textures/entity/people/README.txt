Extra skins for the people of the camp (newcomers and children).

Put 64x64 player skin PNGs in:
  wide/  - skins drawn for the classic (Steve) arms, 4 pixels wide
  slim/  - skins drawn for the slim (Alex) arms, 3 pixels wide

Then run  python3 tools/add_skins.py  (in hardcore-friends/), which lists each new one in
assets/hardcorefriends/skins.json, for example:
  {"id": 1082, "texture": "hardcorefriends:textures/entity/people/wide/adult_potter_june.png", "model": "wide", "for": "adult"}
Names starting child_ are worn only by children, adult_ only by grown-ups. A trade or place word after the prefix
(adult_baker_..., adult_desert_...) becomes a tag, so people of that trade or from that land wear it more often.

File names: lower case letters, digits and underscores only. Ids: 1000 and up, each used once, never changed later.
"for" is adult, child or any. Full instructions: docs/v3/people.md.
