import { z } from "zod";

/**
 * Validation for the subset of Deadlock API responses CounterCoach consumes. Unknown fields are
 * allowed (passthrough) because the community API adds fields often; missing required fields
 * fail validation and the active snapshot is not replaced.
 */

const looseRecord = z.record(z.string(), z.unknown());

export const rawSteamInfo = z.object({
  client_version: z.number().int(),
  server_version: z.number().int().optional(),
  version_date: z.string().optional(),
  version_time: z.string().optional(),
  version_datetime: z.string().optional(),
});

export const rawLevelInfo = z.record(
  z.string(),
  z.object({
    bonus_currencies: z.array(z.string()).optional().nullable(),
    required_gold: z.number(),
    use_standard_upgrade: z.boolean().optional().nullable(),
  }),
);

export const rawHero = z
  .object({
    id: z.number().int(),
    class_name: z.string(),
    name: z.string(),
    player_selectable: z.boolean().nullable().optional(),
    disabled: z.boolean().nullable().optional(),
    in_development: z.boolean().nullable().optional(),
    hero_type: z.string().nullable().optional(),
    tags: z.array(z.string()).nullable().optional(),
    complexity: z.number().nullable().optional(),
    gun_tag: z.string().nullable().optional(),
    images: looseRecord.nullable().optional(),
    items: z.record(z.string(), z.string()).nullable().optional(),
    level_info: rawLevelInfo.nullable().optional(),
    popular_items: z
      .object({
        timestamp: z.number().nullable().optional(),
        early_game: z.array(z.object({ item_id: z.number(), class_name: z.string().optional(), pick_pct: z.number(), winrate_pct: z.number() })).optional(),
        mid_game: z.array(z.object({ item_id: z.number(), class_name: z.string().optional(), pick_pct: z.number(), winrate_pct: z.number() })).optional(),
        late_game: z.array(z.object({ item_id: z.number(), class_name: z.string().optional(), pick_pct: z.number(), winrate_pct: z.number() })).optional(),
      })
      .nullable()
      .optional(),
  })
  .passthrough();

export const rawProperty = z
  .object({
    value: z.union([z.string(), z.number(), z.boolean()]).nullable().optional(),
    provided_property_type: z.string().nullable().optional(),
    label: z.string().nullable().optional(),
    postfix: z.string().nullable().optional(),
    usage_flags: z.array(z.string()).nullable().optional(),
  })
  .passthrough();

export const rawItem = z
  .object({
    id: z.number().int(),
    class_name: z.string(),
    name: z.string().nullable().optional(),
    type: z.string(),
    item_slot_type: z.string().nullable().optional(),
    item_tier: z.number().nullable().optional(),
    cost: z.number().nullable().optional(),
    activation: z.string().nullable().optional(),
    is_active_item: z.boolean().nullable().optional(),
    shopable: z.boolean().nullable().optional(),
    disabled: z.boolean().nullable().optional(),
    component_items: z.array(z.string()).nullable().optional(),
    heroes: z.array(z.number()).nullable().optional(),
    imbue: z.string().nullable().optional(),
    image: z.string().nullable().optional(),
    shop_image: z.string().nullable().optional(),
    shop_filters: z.array(z.string()).nullable().optional(),
    properties: z.record(z.string(), z.unknown()).nullable().optional(),
    description: z.union([z.string(), z.record(z.string(), z.unknown())]).nullable().optional(),
    tooltip_sections: z.array(z.unknown()).nullable().optional(),
    corrupted_info: z.unknown().optional(),
    upgrades: z.array(z.object({ property_upgrades: z.array(z.object({ name: z.string(), bonus: z.union([z.string(), z.number()]) })).optional() })).nullable().optional(),
    ability_type: z.string().nullable().optional(),
    hero: z.number().nullable().optional(),
  })
  .passthrough();

export const rawPatch = z
  .object({
    title: z.string(),
    pub_date: z.string(),
    link: z.string(),
    source: z.string().optional(),
  })
  .passthrough();

export const rawGeneric = z
  .object({
    item_price_per_tier: z.array(z.number()).optional(),
    street_brawl: z.unknown().optional(),
  })
  .passthrough();

export const rawBuild = z
  .object({
    hero_build: z
      .object({
        hero_id: z.number(),
        last_updated_timestamp: z.number().nullable().optional(),
        details: z
          .object({
            ability_order: z
              .object({
                currency_changes: z
                  .array(z.object({ ability_id: z.number(), currency_type: z.number(), delta: z.number() }).passthrough())
                  .nullable()
                  .optional(),
              })
              .nullable()
              .optional(),
          })
          .passthrough(),
      })
      .passthrough(),
  })
  .passthrough();

export const rawAbilityOrderStat = z
  .object({
    abilities: z.array(z.number().int()),
    matches: z.number().int(),
    wins: z.number().int(),
  })
  .passthrough();

export type RawHero = z.infer<typeof rawHero>;
export type RawItem = z.infer<typeof rawItem>;
export type RawBuild = z.infer<typeof rawBuild>;
export type RawAbilityOrderStat = z.infer<typeof rawAbilityOrderStat>;
