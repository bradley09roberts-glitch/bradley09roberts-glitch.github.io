// Applies the reader's theme (theme.ts) as a side effect of being imported. main.tsx imports
// this module first, so in the bundle it runs before React DOM and every page module are
// evaluated, not after them: on a slow phone that evaluation takes a few hundred milliseconds,
// during which the page would show in the Auto theme before switching to the reader's choice.
// It has to be in the bundle, not in a script in index.html: the snapshot's one-file page
// keeps only the bundle (footy/snapshot.py).

import { initTheme } from "./theme";

initTheme();
