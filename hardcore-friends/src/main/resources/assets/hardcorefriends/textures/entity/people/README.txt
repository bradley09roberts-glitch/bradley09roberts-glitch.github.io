Extra skins for the people of the camp (newcomers and children).

Put 64x64 player skin PNGs in:
  wide/  - skins drawn for the classic (Steve) arms, 4 pixels wide
  slim/  - skins drawn for the slim (Alex) arms, 3 pixels wide

Then list each one in assets/hardcorefriends/skins.json, for example:
  {"id": 1000, "texture": "hardcorefriends:textures/entity/people/wide/baker_girl.png", "model": "wide", "for": "child"}

File names: lower case letters, digits and underscores only. Ids: 1000 and up, each used once, never changed later.
"for" is adult, child or any. Full instructions: docs/v3/people.md.
