// The page registry. To add a page: write src/pages/<Name>.tsx exporting a component that
// takes PageProps, then add one entry below. The router and the navigation read this list;
// neither needs editing. The first matching `path` wins, so list specific paths first.

import type { ComponentType } from "react";
import type { IconName } from "../components/Svg";
import type { Params } from "../router";
import { Fixtures } from "./Fixtures";
import { Match } from "./Match";
import { Past } from "./Past";
import { Performance } from "./Performance";
import { Data } from "./Data";
import { Lab } from "./Lab";
import { redirectTo } from "./Placeholder";
import { Team } from "./Team";
import { Teams } from "./Teams";

export type PageProps = {
  /** Path parameters, e.g. { id: "123" } for "/match/:id". */
  params: Params;
  /** The hash query, e.g. competition=E0 in "#/past?competition=E0". Read-only. */
  query: URLSearchParams;
};

export type PageDef = {
  /** "/fixtures", or a pattern with parameters such as "/match/:id". */
  path: string;
  /** Browser tab title (a page can set a more specific one with useDocumentTitle). */
  title: string;
  component: ComponentType<PageProps>;
  /** Present for pages in the main navigation. `tabBar: false` keeps a page off the phone's
   * tab bar (five items at most), where the app bar links to it instead. */
  nav?: { label: string; short?: string; icon?: IconName; order: number; tabBar?: boolean };
  /** For pages outside the navigation: the nav entry to highlight. */
  section?: string;
};

/** Where "#/" and an empty hash go. */
export const DEFAULT_PATH = "/fixtures";

export const pages: readonly PageDef[] = [
  { path: "/fixtures", title: "Fixtures", component: Fixtures, nav: { label: "Fixtures", icon: "fixtures", order: 10 } },
  { path: "/match/:id", title: "Match", component: Match, section: "/fixtures" },
  { path: "/past", title: "Past matches", component: Past, nav: { label: "Past matches", short: "Past", icon: "past", order: 20 } },
  { path: "/teams", title: "Teams", component: Teams, nav: { label: "Teams", icon: "teams", order: 25 } },
  { path: "/team/:key", title: "Team", component: Team, section: "/teams" },
  { path: "/performance", title: "Performance", component: Performance, nav: { label: "Performance", icon: "performance", order: 30 } },
  { path: "/lab", title: "Backtest lab", component: Lab, nav: { label: "Backtest lab", short: "Lab", icon: "lab", order: 40 } },
  { path: "/data", title: "Data and methodology", component: Data, nav: { label: "Data", icon: "data", order: 50, tabBar: false } },
  // System status lives on the Data page now.
  { path: "/status", title: "Data and methodology", component: redirectTo("/data"), section: "/data" },
];
