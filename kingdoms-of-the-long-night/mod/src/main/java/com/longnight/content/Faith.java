package com.longnight.content;

import com.google.gson.JsonObject;

/** A religion, loaded from data/longnight/longnight_faiths/<id>.json. */
public class Faith {
    public String id;
    public String name;
    public String adherent = "";      // "Hearthkeeper"
    public String clergy = "Priest";  // title of clergy
    public String description = "";
    public String burial = "burial";  // burial | cremation | sea | tree | tomb | sky
    public String burialText = "";    // what a funeral looks like
    public String festival = "";
    public int festivalMonth;
    public String shrineBlock = "minecraft:campfire";
    public boolean forbidden;
    public float strictness = 0.5f;   // how strongly adherents prefer marrying within the faith
    public float charity = 0.5f;      // how much the faith encourages helping the needy

    public static Faith parse(String id, JsonObject o) {
        Faith f = new Faith();
        f.id = id;
        f.name = Culture.str(o, "name", id);
        f.adherent = Culture.str(o, "adherent", "faithful");
        f.clergy = Culture.str(o, "clergy", "Priest");
        f.description = Culture.str(o, "description", "");
        f.burial = Culture.str(o, "burial", "burial");
        f.burialText = Culture.str(o, "burial_text", "");
        f.festival = Culture.str(o, "festival", "");
        f.festivalMonth = o.has("festival_month") ? o.get("festival_month").getAsInt() : 0;
        f.shrineBlock = Culture.str(o, "shrine_block", "minecraft:campfire");
        f.forbidden = o.has("forbidden") && o.get("forbidden").getAsBoolean();
        f.strictness = o.has("strictness") ? o.get("strictness").getAsFloat() : 0.5f;
        f.charity = o.has("charity") ? o.get("charity").getAsFloat() : 0.5f;
        return f;
    }
}
