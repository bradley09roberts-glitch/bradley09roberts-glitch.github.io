package io.github.bradley09roberts.hardcorefriends.architecture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.build.MaterialSpec;
import io.github.bradley09roberts.hardcorefriends.camp.build.WoodWork;

/**
 * Reads one building plan file (the format is described in {@code docs/v3/architecture.md}) into a {@link Blueprint},
 * checking everything on the way: sizes, rows, unknown characters, unknown materials, block properties that the
 * material does not have, doors without room for their top half, beds without room for their head, markers outside
 * the plan, and what a house must have (a door, beds, a chest). A plan with any error is refused with every error
 * listed; warnings are listed but the plan is kept. Nothing here can throw on bad input: every problem becomes a
 * message.
 *
 * <p>The drawing may face any way ({@code front}); it is turned so the front faces north, the way every plan is kept.
 */
public final class PlanParser {
	/** The outcome: the plan (null if refused), the errors that refused it and the warnings. */
	public record Result(@Nullable Blueprint plan, List<String> errors, List<String> warnings) {
	}

	/** One palette character worked out. */
	private record Paint(MaterialSpec spec, UnaryOperator<BlockState> tweak, Map<String, String> props, @Nullable String variant,
		boolean attach, boolean optional, Blueprint.@Nullable Alternative fallback) {
	}

	/** Largest footprint side, in blocks. */
	public static final int MAX_SIDE = 32;
	/** Most layers. */
	public static final int MAX_LAYERS = 24;
	/** Characters with a fixed meaning: leave as it is (dot and space), and must be air (dash). */
	public static final char LEAVE = '.';
	public static final char AIR = '-';
	private static final Pattern KIND = Pattern.compile("[a-z0-9_]+(:[a-z0-9_/]+)?");
	private static final Pattern NAME = Pattern.compile("[a-z0-9_]+");
	private static final List<String> RESERVED_KEYS = List.of("block", "attach", "optional", "fallback", "wood", "colour", "color");

	private final String id;
	private final String planId;
	private final List<String> errors = new ArrayList<>();
	private final List<String> warnings = new ArrayList<>();

	private PlanParser(String id, String planId) {
		this.id = id;
		this.planId = planId;
	}

	/** Reads a plan with this id (its site key and library id) from parsed JSON. */
	public static Result parse(String id, JsonElement json) {
		return parse(id, id, json);
	}

	/**
	 * Reads a plan with this id (its default site key) and plan id (what a reserved site remembers it by) from parsed
	 * JSON.
	 */
	public static Result parse(String id, String planId, JsonElement json) {
		PlanParser p = new PlanParser(id, planId);
		Blueprint plan = null;
		try {
			plan = p.read(json);
		} catch (RuntimeException e) {
			p.errors.add("could not be read: " + e);
		}
		if (!p.errors.isEmpty()) {
			plan = null;
		}
		return new Result(plan, List.copyOf(p.errors), List.copyOf(p.warnings));
	}

	private @Nullable Blueprint read(JsonElement json) {
		if (!json.isJsonObject()) {
			errors.add("is not a JSON object");
			return null;
		}
		JsonObject root = json.getAsJsonObject();
		String kind = string(root, "kind", null);
		if (kind == null || !KIND.matcher(kind).matches()) {
			errors.add("needs a \"kind\" such as \"house\" or \"shop:bakery\" (lower case, letters, digits and _)");
			kind = "unknown";
		}
		String name = string(root, "name", null);
		if (name != null && (name.isBlank() || name.length() > 48)) {
			errors.add("\"name\" must be 1 to 48 characters");
		}
		List<String> styles = new ArrayList<>();
		if (root.has("styles")) {
			JsonArray arr = array(root, "styles");
			if (arr != null) {
				for (JsonElement e : arr) {
					String s = e.isJsonPrimitive() ? e.getAsString().toLowerCase(Locale.ROOT) : "";
					if (!NAME.matcher(s).matches()) {
						errors.add("style \"" + e + "\" is not a plain lower-case word");
					} else {
						styles.add(s);
					}
				}
			}
		}
		int width = 0;
		int depth = 0;
		JsonArray size = array(root, "size");
		if (size == null || size.size() != 2 || !isInt(size.get(0)) || !isInt(size.get(1))) {
			errors.add("needs \"size\": [width, depth]");
		} else {
			width = size.get(0).getAsInt();
			depth = size.get(1).getAsInt();
			if (width < 1 || depth < 1 || width > MAX_SIDE || depth > MAX_SIDE) {
				errors.add("size must be 1 to " + MAX_SIDE + " each way");
				width = 0;
			}
		}
		Direction front = Direction.NORTH;
		String frontName = string(root, "front", "north");
		Direction parsedFront = frontName == null ? null : Direction.byName(frontName.toLowerCase(Locale.ROOT));
		if (parsedFront == null || parsedFront.getAxis().isVertical()) {
			errors.add("\"front\" must be north, south, east or west");
		} else {
			front = parsedFront;
		}
		String wood = string(root, "wood", null);
		if (wood != null && !WoodWork.isWood(wood)) {
			errors.add("\"wood\": \"" + wood + "\" is not a kind of wood");
			wood = null;
		}
		if (wood == null) {
			for (String s : styles) {
				if (WoodWork.isWood(s)) {
					wood = s;
					break;
				}
			}
		}
		boolean foundations = !root.has("foundations") || bool(root, "foundations", true);
		Map<Character, Paint> palette = palette(root);
		JsonArray layers = array(root, "layers");
		if (layers == null || layers.isEmpty()) {
			errors.add("needs \"layers\": a list of layers, bottom first, each a list of rows");
			return null;
		}
		if (layers.size() > MAX_LAYERS) {
			errors.add("has " + layers.size() + " layers; at most " + MAX_LAYERS);
			return null;
		}
		if (width == 0) {
			return null;
		}
		int height = layers.size();
		char[][][] grid = new char[height][depth][width];
		for (int y = 0; y < height; y++) {
			JsonElement layer = layers.get(y);
			if (!layer.isJsonArray() || layer.getAsJsonArray().size() != depth) {
				errors.add("layer " + y + " must be a list of " + depth + " rows");
				continue;
			}
			JsonArray rows = layer.getAsJsonArray();
			for (int z = 0; z < depth; z++) {
				JsonElement row = rows.get(z);
				String text = row.isJsonPrimitive() ? row.getAsString() : null;
				if (text == null || text.length() != width) {
					errors.add("layer " + y + " row " + z + " must be a string of exactly " + width + " characters");
					continue;
				}
				for (int x = 0; x < width; x++) {
					char ch = text.charAt(x);
					if (ch != LEAVE && ch != ' ' && ch != AIR && !palette.containsKey(ch)) {
						errors.add("layer " + y + " row " + z + " column " + x + ": '" + ch + "' is not in the palette");
					}
					grid[y][z][x] = ch == ' ' ? LEAVE : ch;
				}
			}
		}
		if (!errors.isEmpty()) {
			return null;
		}
		// Every cell becomes an entry (drawing coordinates for now); second halves are added where they belong.
		Map<Long, Blueprint.Entry> cells = new LinkedHashMap<>();
		for (int y = 0; y < height; y++) {
			for (int z = 0; z < depth; z++) {
				for (int x = 0; x < width; x++) {
					char ch = grid[y][z][x];
					if (ch == LEAVE) {
						continue;
					}
					if (ch == AIR) {
						cells.put(key(x, y, z), new Blueprint.Entry(x, y, z, MaterialSpec.AIR, UnaryOperator.identity(), false));
						continue;
					}
					Paint paint = palette.get(ch);
					cells.put(key(x, y, z), new Blueprint.Entry(x, y, z, paint.spec(), paint.tweak(), paint.attach(), paint.variant(),
						paint.optional(), paint.fallback()));
				}
			}
		}
		addSecondHalves(cells, palette, grid, width, depth, height);
		Map<String, List<int[]>> markers = markers(root, width, depth, height);
		Map<String, String> meta = meta(root);
		checkKind(kind, cells, markers, meta);
		if (!errors.isEmpty()) {
			return null;
		}
		return assemble(root, kind, name, styles, wood, foundations, front, width, depth, height, cells, markers, meta);
	}

	// ------------------------------------------------------------------- palette

	private Map<Character, Paint> palette(JsonObject root) {
		Map<Character, Paint> palette = new HashMap<>();
		JsonElement el = root.get("palette");
		if (el == null || !el.isJsonObject()) {
			errors.add("needs a \"palette\" object mapping characters to materials");
			return palette;
		}
		for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
			String key = e.getKey();
			if (key.length() != 1 || key.charAt(0) == LEAVE || key.charAt(0) == AIR || key.charAt(0) == ' ') {
				errors.add("palette key \"" + key + "\" must be one character other than '.', '-' and space");
				continue;
			}
			Paint paint = paint("palette '" + key + "'", e.getValue(), true);
			if (paint != null) {
				palette.put(key.charAt(0), paint);
			}
		}
		return palette;
	}

	/** One palette value (a material name, or an object with "block" and properties); null after an error. */
	private @Nullable Paint paint(String where, JsonElement value, boolean allowFallback) {
		String blockName;
		JsonObject obj = null;
		if (value.isJsonPrimitive()) {
			blockName = value.getAsString();
		} else if (value.isJsonObject()) {
			obj = value.getAsJsonObject();
			blockName = string(obj, "block", null);
			if (blockName == null) {
				errors.add(where + " needs \"block\"");
				return null;
			}
		} else {
			errors.add(where + " must be a material name or an object");
			return null;
		}
		var resolved = PlanMaterials.resolve(blockName);
		if (resolved.isEmpty()) {
			errors.add(where + ": unknown material \"" + blockName + "\"");
			return null;
		}
		MaterialSpec spec = resolved.get().spec();
		String variant = resolved.get().variant();
		Map<String, String> props = new LinkedHashMap<>();
		boolean attach = PlanMaterials.isAttachmentByDefault(spec);
		boolean optional = PlanMaterials.isDecorationByDefault(spec);
		Blueprint.Alternative fallback = null;
		if (obj != null) {
			for (Map.Entry<String, JsonElement> p : obj.entrySet()) {
				String k = p.getKey().toLowerCase(Locale.ROOT);
				if (RESERVED_KEYS.contains(k)) {
					continue;
				}
				if (!p.getValue().isJsonPrimitive()) {
					errors.add(where + ": property \"" + k + "\" must be a plain value");
					continue;
				}
				props.put(k, p.getValue().getAsString().toLowerCase(Locale.ROOT));
			}
			attach = bool(obj, "attach", attach);
			optional = bool(obj, "optional", optional);
			String woodWish = string(obj, "wood", null);
			String colourWish = string(obj, "colour", string(obj, "color", null));
			if (woodWish != null) {
				if (spec.variant() != MaterialSpec.Variant.WOOD || !WoodWork.isWood(woodWish)) {
					errors.add(where + ": \"wood\": \"" + woodWish + "\" does not fit " + spec.fileName());
				} else {
					variant = woodWish;
				}
			}
			if (colourWish != null) {
				if (spec.variant() != MaterialSpec.Variant.COLOUR || DyeColor.byName(colourWish, null) == null) {
					errors.add(where + ": \"colour\": \"" + colourWish + "\" does not fit " + spec.fileName());
				} else {
					variant = colourWish;
				}
			}
			if (obj.has("fallback")) {
				if (!allowFallback) {
					errors.add(where + ": a fallback cannot have a fallback of its own");
				} else {
					Paint alt = paint(where + " fallback", obj.get("fallback"), false);
					if (alt != null) {
						fallback = new Blueprint.Alternative(alt.spec(), alt.tweak(), alt.variant());
					}
				}
			}
		}
		// The second half of a two-part block is a different material: the top of a door, the head of a bed.
		if (spec == MaterialSpec.DOOR && "upper".equals(props.get("half"))) {
			spec = MaterialSpec.DOOR_TOP;
		} else if (spec == MaterialSpec.BED && "head".equals(props.get("part"))) {
			spec = MaterialSpec.BED_HEAD;
		} else if (spec == MaterialSpec.TALL_FLOWER && "upper".equals(props.get("half"))) {
			spec = MaterialSpec.TALL_FLOWER_TOP;
		}
		if (!checkProperties(where, spec, props)) {
			return null;
		}
		return new Paint(spec, tweak(props), props, variant, attach, optional, fallback);
	}

	/** True if every property exists on the material's blocks and every value is one it can take. */
	private boolean checkProperties(String where, MaterialSpec spec, Map<String, String> props) {
		BlockState sample = spec.sample().defaultBlockState();
		boolean ok = true;
		for (Map.Entry<String, String> p : props.entrySet()) {
			Property<?> property = sample.getBlock().getStateDefinition().getProperty(p.getKey());
			if (property == null) {
				errors.add(where + ": " + spec.fileName() + " has no property \"" + p.getKey() + "\"");
				ok = false;
			} else if (property.getValue(p.getValue()).isEmpty()) {
				errors.add(where + ": \"" + p.getValue() + "\" is not a value of \"" + p.getKey() + "\" (try one of "
					+ property.getPossibleValues().stream().map(v -> name(property, v)).toList() + ")");
				ok = false;
			}
		}
		return ok;
	}

	@SuppressWarnings("unchecked")
	private static <T extends Comparable<T>> String name(Property<T> property, Object value) {
		return property.getName((T) value);
	}

	/** The tweak setting each property on whatever block the chosen item makes (any wood, any colour). */
	private static UnaryOperator<BlockState> tweak(Map<String, String> props) {
		if (props.isEmpty()) {
			return UnaryOperator.identity();
		}
		Map<String, String> copy = Map.copyOf(props);
		return s -> {
			BlockState out = s;
			for (Map.Entry<String, String> p : copy.entrySet()) {
				Property<?> property = out.getBlock().getStateDefinition().getProperty(p.getKey());
				if (property != null) {
					out = with(out, property, p.getValue());
				}
			}
			return out;
		};
	}

	private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> property, String value) {
		return property.getValue(value).map(v -> s.setValue(property, v)).orElse(s);
	}

	// -------------------------------------------------------------- two halves

	/**
	 * Adds the top of each door and tall flower above it and the head of each bed beside its foot, where the drawing
	 * leaves room ('.' or '-'), with the same facing; a half drawn by hand is kept. A first half with no room for the
	 * second, or a second half with no first, is an error.
	 */
	private void addSecondHalves(Map<Long, Blueprint.Entry> cells, Map<Character, Paint> palette, char[][][] grid, int width,
		int depth, int height) {
		List<Blueprint.Entry> added = new ArrayList<>();
		for (Blueprint.Entry e : cells.values()) {
			MaterialSpec top = e.material() == MaterialSpec.DOOR ? MaterialSpec.DOOR_TOP
				: e.material() == MaterialSpec.TALL_FLOWER ? MaterialSpec.TALL_FLOWER_TOP : null;
			if (top != null) {
				int x = e.dx();
				int y = e.dy() + 1;
				int z = e.dz();
				Blueprint.Entry above = cells.get(key(x, y, z));
				if (above != null && above.material() == top) {
					continue;
				}
				if (above != null && above.material() != MaterialSpec.AIR) {
					errors.add(e.material().fileName() + " at " + x + " " + e.dy() + " " + z + " has no room for its top half");
					continue;
				}
				added.add(new Blueprint.Entry(x, y, z, top, e.tweak(), e.attachment(), e.variant(), e.optional(), null));
			}
			if (e.material() == MaterialSpec.BED) {
				Direction facing = facingOf(e, Direction.NORTH);
				int hx = e.dx() + facing.getStepX();
				int hz = e.dz() + facing.getStepZ();
				Blueprint.Entry head = cells.get(key(hx, e.dy(), hz));
				if (head != null && head.material() == MaterialSpec.BED_HEAD) {
					continue;
				}
				if (hx < 0 || hz < 0 || hx >= width || hz >= depth || head != null && head.material() != MaterialSpec.AIR) {
					errors.add("bed at " + e.dx() + " " + e.dy() + " " + e.dz() + " has no room for its head (facing "
						+ facing.getName() + ")");
					continue;
				}
				UnaryOperator<BlockState> headTweak = e.tweak();
				added.add(new Blueprint.Entry(hx, e.dy(), hz, MaterialSpec.BED_HEAD, headTweak, e.attachment(), e.variant(),
					e.optional(), null));
			}
		}
		for (Blueprint.Entry e : added) {
			cells.put(key(e.dx(), e.dy(), e.dz()), e);
		}
		for (Blueprint.Entry e : cells.values()) {
			if (e.material() == MaterialSpec.DOOR_TOP || e.material() == MaterialSpec.TALL_FLOWER_TOP) {
				Blueprint.Entry below = cells.get(key(e.dx(), e.dy() - 1, e.dz()));
				MaterialSpec want = e.material() == MaterialSpec.DOOR_TOP ? MaterialSpec.DOOR : MaterialSpec.TALL_FLOWER;
				if (below == null || below.material() != want) {
					errors.add(e.material().fileName() + " at " + e.dx() + " " + e.dy() + " " + e.dz() + " has no lower half below it");
				}
			}
			if (e.material() == MaterialSpec.BED_HEAD) {
				Direction facing = facingOf(e, Direction.NORTH);
				Blueprint.Entry foot = cells.get(key(e.dx() - facing.getStepX(), e.dy(), e.dz() - facing.getStepZ()));
				if (foot == null || foot.material() != MaterialSpec.BED) {
					errors.add("bed head at " + e.dx() + " " + e.dy() + " " + e.dz() + " has no foot behind it");
				}
			}
		}
	}

	/** The horizontal facing an entry's tweak gives its sample block, or the default. */
	private static Direction facingOf(Blueprint.Entry e, Direction fallback) {
		BlockState s = e.tweak().apply(e.material().sample().defaultBlockState());
		for (Property<?> p : s.getProperties()) {
			if (p.getName().equals("facing") && s.getValue(p) instanceof Direction d && d.getAxis().isHorizontal()) {
				return d;
			}
		}
		return fallback;
	}

	// ------------------------------------------------------------------ markers

	private Map<String, List<int[]>> markers(JsonObject root, int width, int depth, int height) {
		Map<String, List<int[]>> markers = new LinkedHashMap<>();
		JsonElement el = root.get("markers");
		if (el == null) {
			return markers;
		}
		if (!el.isJsonObject()) {
			errors.add("\"markers\" must be an object of lists of [x, y, z]");
			return markers;
		}
		for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
			String name = e.getKey().toLowerCase(Locale.ROOT);
			if (!NAME.matcher(name).matches()) {
				errors.add("marker name \"" + e.getKey() + "\" must be a plain lower-case word");
				continue;
			}
			JsonElement list = e.getValue();
			if (!list.isJsonArray()) {
				errors.add("marker \"" + name + "\" must be a list of [x, y, z]");
				continue;
			}
			JsonArray arr = list.getAsJsonArray();
			// A single [x, y, z] is accepted as well as a list of them.
			if (arr.size() == 3 && arr.get(0).isJsonPrimitive()) {
				JsonArray wrap = new JsonArray();
				wrap.add(arr);
				arr = wrap;
			}
			for (JsonElement p : arr) {
				if (!p.isJsonArray() || p.getAsJsonArray().size() != 3 || !isInt(p.getAsJsonArray().get(0))
					|| !isInt(p.getAsJsonArray().get(1)) || !isInt(p.getAsJsonArray().get(2))) {
					errors.add("marker \"" + name + "\": " + p + " is not [x, y, z]");
					continue;
				}
				int x = p.getAsJsonArray().get(0).getAsInt();
				int y = p.getAsJsonArray().get(1).getAsInt();
				int z = p.getAsJsonArray().get(2).getAsInt();
				// One block outside the footprint is allowed: the spot just outside a front door.
				if (x < -1 || z < -1 || x > width || z > depth || y < 0 || y > height) {
					errors.add("marker \"" + name + "\" at " + x + " " + y + " " + z + " is outside the plan");
					continue;
				}
				markers.computeIfAbsent(name, k -> new ArrayList<>()).add(new int[] {x, y, z});
			}
		}
		return markers;
	}

	private Map<String, String> meta(JsonObject root) {
		Map<String, String> meta = new LinkedHashMap<>();
		JsonElement el = root.get("meta");
		if (el == null) {
			return meta;
		}
		if (!el.isJsonObject()) {
			errors.add("\"meta\" must be an object");
			return meta;
		}
		for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
			meta.put(e.getKey(), e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString());
		}
		return meta;
	}

	/** What each kind of plan must have for the rest of the mod to use it. */
	private void checkKind(String kind, Map<Long, Blueprint.Entry> cells, Map<String, List<int[]>> markers, Map<String, String> meta) {
		if (kind.equals("house")) {
			if (markers.getOrDefault("door", List.of()).isEmpty()) {
				errors.add("a house needs a \"door\" marker (where to stand outside the front door)");
			}
			List<int[]> beds = markers.getOrDefault("bed", List.of());
			if (beds.isEmpty()) {
				errors.add("a house needs \"bed\" markers, one on the foot of each bed");
			}
			for (int[] b : beds) {
				Blueprint.Entry e = cells.get(key(b[0], b[1], b[2]));
				if (e == null || e.material() != MaterialSpec.BED) {
					errors.add("\"bed\" marker at " + b[0] + " " + b[1] + " " + b[2] + " is not on the foot of a bed");
				}
			}
			List<int[]> chests = markers.getOrDefault("chest", List.of());
			if (chests.isEmpty()) {
				errors.add("a house needs a \"chest\" marker");
			}
			for (int[] b : chests) {
				Blueprint.Entry e = cells.get(key(b[0], b[1], b[2]));
				if (e == null || e.material() != MaterialSpec.CHEST && e.material() != MaterialSpec.BARREL) {
					errors.add("\"chest\" marker at " + b[0] + " " + b[1] + " " + b[2] + " is not on a chest or barrel");
				}
			}
			String bedsMeta = meta.get("beds");
			if (bedsMeta == null) {
				meta.put("beds", Integer.toString(beds.size()));
			} else if (!bedsMeta.equals(Integer.toString(beds.size()))) {
				warnings.add("meta beds is " + bedsMeta + " but there are " + beds.size() + " bed markers; using " + beds.size());
				meta.put("beds", Integer.toString(beds.size()));
			}
		} else if (kind.startsWith("shop:") && markers.getOrDefault("counter", List.of()).isEmpty()) {
			warnings.add("a shop usually has a \"counter\" marker (where the shopkeeper stands)");
		} else if (kind.startsWith("workplace:") && markers.getOrDefault("job", List.of()).isEmpty()) {
			warnings.add("a workplace usually has a \"job\" marker (its work station)");
		}
		boolean anyBlock = false;
		for (Blueprint.Entry e : cells.values()) {
			if (e.material() != MaterialSpec.AIR) {
				anyBlock = true;
				break;
			}
		}
		if (!anyBlock) {
			errors.add("has no blocks to build");
		}
	}

	// ---------------------------------------------------------------- assemble

	/** Turns the drawing so its front faces north and builds the plan. */
	private Blueprint assemble(JsonObject root, String kind, @Nullable String name, List<String> styles, @Nullable String wood,
		boolean foundations, Direction front, int width, int depth, int height, Map<Long, Blueprint.Entry> cells,
		Map<String, List<int[]>> markers, Map<String, String> meta) {
		int fix = switch (front) {
			case EAST -> 3;
			case SOUTH -> 2;
			case WEST -> 1;
			default -> 0;
		};
		int w = fix % 2 == 1 ? depth : width;
		int d = fix % 2 == 1 ? width : depth;
		Rotation turn = Blueprint.rotation(fix);
		Blueprint.Builder b = Blueprint.builder(id, w, d).planId(planId).kind(kind).wood(wood).foundations(foundations).minHeight(height);
		b.name(name != null ? name.toLowerCase(Locale.ROOT) : defaultName(id));
		styles.forEach(b::style);
		for (Blueprint.Entry e : cells.values()) {
			int[] p = turn(fix, e.dx(), e.dz(), width, depth);
			UnaryOperator<BlockState> tweak = fix == 0 ? e.tweak() : s -> e.tweak().apply(s).rotate(turn);
			Blueprint.Alternative fb = e.fallback();
			if (fb != null && fix != 0) {
				Blueprint.Alternative alt = fb;
				fb = new Blueprint.Alternative(alt.material(), s -> alt.tweak().apply(s).rotate(turn), alt.variant());
			}
			b.entry(new Blueprint.Entry(p[0], e.dy(), p[1], e.material(), tweak, e.attachment(), e.variant(), e.optional(), fb));
		}
		markers.forEach((n, list) -> {
			for (int[] m : list) {
				int[] p = turn(fix, m[0], m[2], width, depth);
				b.marker(n, p[0], m[1], p[1]);
			}
		});
		meta.forEach(b::meta);
		JsonElement camp = root.get("camp");
		if (camp != null && camp.isJsonObject()) {
			JsonObject c = camp.getAsJsonObject();
			JsonArray offsets = array(c, "offsets");
			if (offsets != null) {
				for (JsonElement o : offsets) {
					if (o.isJsonArray() && o.getAsJsonArray().size() == 2) {
						b.at(o.getAsJsonArray().get(0).getAsInt(), o.getAsJsonArray().get(1).getAsInt());
					}
				}
			}
			if (!bool(c, "faces_centre", true)) {
				b.fixedFacing();
			}
		}
		return b.build();
	}

	/** Drawing coordinates turned so the front faces north, for a drawing {@code width × depth}. */
	static int[] turn(int fix, int x, int z, int width, int depth) {
		return switch (fix) {
			case 1 -> new int[] {depth - 1 - z, x};
			case 2 -> new int[] {width - 1 - x, depth - 1 - z};
			case 3 -> new int[] {z, width - 1 - x};
			default -> new int[] {x, z};
		};
	}

	/** "hardcorefriends:house/oak_cottage" → "oak cottage". */
	static String defaultName(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		if (path.contains("/")) {
			path = path.substring(path.lastIndexOf('/') + 1);
		}
		return path.replace('_', ' ');
	}

	// ----------------------------------------------------------------- helpers

	private static long key(int x, int y, int z) {
		return ((long) (x + 64) << 32) | ((long) (y + 64) << 16) | (z + 64);
	}

	private static boolean isInt(JsonElement e) {
		if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
			return false;
		}
		double d = e.getAsDouble();
		return d == Math.rint(d) && Math.abs(d) < 100_000;
	}

	private @Nullable String string(JsonObject o, String key, @Nullable String fallback) {
		JsonElement e = o.get(key);
		if (e == null) {
			return fallback;
		}
		if (!e.isJsonPrimitive() || !((JsonPrimitive) e).isString()) {
			errors.add("\"" + key + "\" must be text");
			return fallback;
		}
		return e.getAsString();
	}

	private boolean bool(JsonObject o, String key, boolean fallback) {
		JsonElement e = o.get(key);
		if (e == null) {
			return fallback;
		}
		if (!e.isJsonPrimitive() || !((JsonPrimitive) e).isBoolean()) {
			errors.add("\"" + key + "\" must be true or false");
			return fallback;
		}
		return e.getAsBoolean();
	}

	private @Nullable JsonArray array(JsonObject o, String key) {
		JsonElement e = o.get(key);
		if (e == null) {
			return null;
		}
		if (!e.isJsonArray()) {
			errors.add("\"" + key + "\" must be a list");
			return null;
		}
		return e.getAsJsonArray();
	}
}
