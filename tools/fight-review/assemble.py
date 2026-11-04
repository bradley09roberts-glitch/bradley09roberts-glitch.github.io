"""Insert the fight review into the Octagon Season Ledger page.
Usage: assemble.py <source page.html> <review-data.json> <review.js> <review.css> <out.html>
Every patch must match exactly once, or the script stops without writing."""
import sys
src, data_p, js_p, css_p, out_p = sys.argv[1:6]
page = open(src, encoding="utf8").read()
data = open(data_p, encoding="utf8").read().replace("</", "<\\/")
js = open(js_p, encoding="utf8").read()
css = open(css_p, encoding="utf8").read()
if 'id="review-data"' in page: sys.exit("source already has a review block: start from the unpatched page")
# A page read back from the artifact service carries the skeleton the service adds at publish; drop it so a republish
# isn't wrapped twice.
if page.startswith("<!doctype html><html><head><meta charset=utf8>"):
    k = page.index("</head><body>") + len("</head><body>")
    page = page[k:].lstrip("\n")
    if page.rstrip().endswith("</body></html>"): page = page.rstrip()[:-len("</body></html>")].rstrip() + "\n"

def once(old, new, where):
    global page
    n = page.count(old)
    if n != 1: sys.exit(f"patch '{where}': expected 1 match, found {n}")
    page = page.replace(old, new)

# 1. styles, after the page's own stylesheet
once('</style>\n\n<a class="skip-link"', '</style>\n<style>\n' + css + '</style>\n\n<a class="skip-link"', "css")
# 2. data block, right after the ledger's own data block
i = page.index('<script type="application/json" id="ledger-data">')
j = page.index('</script>', i) + len('</script>')
page = page[:j] + '\n<script type="application/json" id="review-data">' + data + '</script>' + page[j:]
# 3. the module, before boot
once("// ---- 99-boot.js", js + "\n// ---- 99-boot.js", "module")

# 4. hooks
once('const MU_TABS = [["overview", "Overview"], ["method", "Method"],',
     'const MU_TABS = [["overview", "Overview"], ["review", "Fight review"], ["method", "Method"],', "tabs")
once('tabIndex: id === muTab ? 0 : -1 }, lab);',
     'tabIndex: id === muTab ? 0 : -1 }, id === "review" && !b.result ? "Weak spots" : lab);', "tab label")
once('clear(panel).append((id === "method" ? methodTab :',
     'clear(panel).append((id === "review" ? reviewTab : id === "method" ? methodTab :', "tab select")
once(': h("span.faint", "No market price")));',
     ': h("span.faint", "No market price"), verdictPill(b)));', "card verdict")
once('h("span.mark." + (r.ok ? "ok" : "no"), icon(r.ok ? "check" : "cross"), r.ok ? "Model right" : "Model wrong")),\n    h("p.res-line"',
     'h("div.rv-head-r", verdictPill(b, { link: false }), h("span.mark." + (r.ok ? "ok" : "no"), icon(r.ok ? "check" : "cross"), r.ok ? "Model right" : "Model wrong"))),\n    h("p.res-line"', "result panel verdict")
once('` won by ${r.how}${r.round ? ` in round ${r.round}` : ""}${r.time ? ` at ${r.time}` : ""}.`),',
     '` won by ${r.how}${r.round ? ` in round ${r.round}` : ""}${(r.time || (r.how !== "Decision" && reviewOf(b) ? reviewOf(b).tm : null)) ? ` at ${r.time || reviewOf(b).tm}` : ""}.`),', "result time")
once('r.time ? null : h("p.foot", "Finishing times aren\'t recorded in this ledger, only the round."));',
     'r.time || reviewOf(b) ? (reviewOf(b) ? h("p.foot", "Round-by-round numbers, the judges and Sherdog\'s scoring are in the Fight review tab.") : null) : h("p.foot", "Finishing times aren\'t recorded in this ledger, only the round."));', "result foot")
once('  left.append(statsPanel(f));\n',
     '  left.append(statsPanel(f));\n  { const ws = weakSpotsPanel(f); if (ws) left.append(ws); }\n', "fighter weak spots")
once('· model gave them ${pct(mineP)}`)),',
     '· model gave them ${pct(mineP)}`), fighterVerdictPill(x, me.side)),', "fighter list verdict")
once('  { id: "performance", label: "Performance", icon: "chart", match: t => t === "performance" },\n',
     '  { id: "performance", label: "Performance", icon: "chart", match: t => t === "performance" },\n  { id: "review", label: "Review", icon: "octagon", sub: "Why each pick won or lost, round by round", match: t => t === "review" },\n', "nav")
once("Below them, the fights graded in this ledger (${L.season}) and how the model did. Round-by-round statistics and judges' scorecards aren't in this page.",
     "Below them, the fights graded in this ledger (${L.season}) and how the model did. Round-by-round statistics, the judges' cards and Sherdog's round scoring are in the Fight review tab.", "history note")
once('No over/under round lines are recorded, so none are shown."));\n  return out;',
     'No over/under round lines are recorded, so none are shown."));\n  if (b.result && reviewOf(b)) out.append(h("div.note", icon("info"), h("p", "What happened in each round, with the stats and Sherdog\'s scoring, is in ", h("button.linkbtn", { type: "button", onclick: () => { const t = $("#mt-review"); if (t) t.click(); } }, "Fight review"), ".")));\n  return out;', "rounds tab note")
once('h("p.cap", "The model\'s most confident picks that lost."),',
     'h("p.cap", "The model\'s most confident picks that lost. ", h("a", { href: "#review" }, "Why each miss happened"), "."),', "perf misses link")
once('const li = (b, v) => h("li.ulist-i", h("a", { href: "#" + b.route },',
     'const li = (b, v) => h("li.ulist-i", h("a", { href: "#" + b.route, onclick: () => { muTab = "review"; } },', "perf misses tab")
open(out_p, "w", encoding="utf8").write(page)
print("written", out_p, len(page.encode()), "bytes")
