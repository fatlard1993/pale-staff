#!/usr/bin/env python3
"""
Draw the staff, its sockets, the charge bar and the icon, and write the item model that picks
between them.

The staff is pale oak: the shaft is the wood's own greys, the head a crook of branch closing round
an empty socket, with a bead of resin where the creaking heart's orange would be.

Imbued, the socket holds what imbued it: the sugar, the blaze powder, the fire charge, the golden
dandelion. Each is the item's own texture from the game, cropped rather than shrunk: the five-pixel
window of it with the most colour in it, set into the socket pixel for pixel, so it stays crisp at
the staff's scale. Anything else, a block or an ingredient from another mod, is set as a plain gem
in the imbuement's colour.

The moobloom, the cow a golden dandelion bolt makes, is the game's own cow repainted. The icon is
the staff with a golden dandelion set, on darkened pale oak bark.

The charge bar sits two pixels above where the game draws durability, the same thirteen pixels
wide, so the two read as a pair. Its fill is white and tinted with the imbuement's colour; there
is one texture per width, 1 to 13, since an item model can pick a texture but cannot stretch one.

Usage: python3 generate_textures.py [path/to/minecraft-merged.jar]
"""
import colorsys
import io
import json
import os
import pathlib
import sys
import zipfile

from PIL import Image

HERE = pathlib.Path(__file__).parent
NS = "pale-staff-justfatlard"
ASSETS = HERE / f"src/main/resources/assets/{NS}"
TEXTURES = ASSETS / "textures/item"
MODELS = ASSETS / "models/item"

PALETTE = {
	".": None,
	"o": (0x4f, 0x45, 0x42),  # bark outline
	"d": (0x8f, 0x82, 0x7d),  # shaft shadow
	"l": (0xc9, 0xbe, 0xb8),  # pale oak
	"w": (0xec, 0xe6, 0xe2),  # highlight
	"R": (0xf0, 0x8a, 0x2a),  # resin
	"m": (0x7c, 0x86, 0x74),  # pale moss
	"M": (0xa3, 0xad, 0x99),  # pale moss, lit
	"s": (0x24, 0x1f, 0x1e),  # socket, in shadow
	"S": (0x3a, 0x33, 0x31),  # socket, catching light
}

STAFF = """
...........ooo..
..........olwlo.
.........olssslo
........olssssSo
........odssssSo
.......RldsssSSo
......oldolSSSlo
.....oldo..ooo..
....oldo..mMo...
...oldo...m.....
..oldo..........
.oldo...........
oldo............
odo.............
oo..............
................
"""

# The socket's pixels: five across, corners cut. Rows 2 to 6, columns 10 to 14.
SOCKET_LEFT, SOCKET_TOP, SOCKET_SIZE = 10, 2, 5
SOCKET_MASK = [(x, y) for y in range(5) for x in range(5) if not (x in (0, 4) and y in (0, 4))]

GEM_SHADES = {".": None, "a": 0x70, "b": 0xa8, "c": 0xd8, "W": 0xff}
GEM = """
.bcc.
bcWWc
bccWc
abccb
.aab.
"""

BAR_LEFT, BAR_WIDTH, BAR_ROW = 2, 13, 11

# What crafts into the staff besides the brewing ingredients, which are read from the game's
# recipes. A source left off still works: it is set as the plain gem.
SPECIALS = ["fire_charge", "golden_dandelion", "wind_charge", "echo_shard", "heart_of_the_sea", "ender_pearl", "shulker_shell", "glow_ink_sac"]

# What the item model reads, all from custom_model_data: the flag says imbued, the colour tints.
IMBUED = {"type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": 0}
TINT = {"type": "minecraft:custom_model_data", "index": 0, "default": -1}


def find_jar(argv):
	if len(argv) > 1:
		return argv[1]
	version = None
	for line in (HERE / "gradle.properties").read_text().splitlines():
		key, _, value = line.partition("=")
		if key.strip() == "minecraft_version":
			version = value.strip()
	jar = pathlib.Path(os.path.expanduser(f"~/.gradle/caches/fabric-loom/{version}/minecraft-merged.jar"))
	if not jar.exists():
		sys.exit(f"no Minecraft {version} jar under the Loom cache; build once, or pass its path")
	return jar


class Jar:
	def __init__(self, path):
		self.zip = zipfile.ZipFile(path)

	def json(self, path):
		return json.loads(self.zip.read(path))

	def texture(self, ref):
		namespace, _, path = ref.partition(":")
		data = self.zip.read(f"assets/{namespace or 'minecraft'}/textures/{path}.png")
		return Image.open(io.BytesIO(data)).convert("RGBA").crop((0, 0, 16, 16))

	def texture_full(self, ref):
		namespace, _, path = ref.partition(":")
		return Image.open(io.BytesIO(self.zip.read(f"assets/{namespace}/textures/{path}.png"))).convert("RGBA")

	def reagents(self):
		"""What brews an effect out of an awkward potion, or a water bottle, as Dose reads it."""
		found = []
		for name in sorted(self.zip.namelist()):
			if not name.startswith("data/minecraft/recipe/brewing/potion_"):
				continue
			recipe = self.json(name)
			base = recipe["input"].get("potion_contents", {}).get("potions")
			made = recipe["output"].get("components", {}).get("minecraft:potion_contents", {}).get("potion", "")
			if base in ("minecraft:awkward", "minecraft:water") and made not in ("minecraft:mundane", "minecraft:thick", "minecraft:awkward", "minecraft:water", ""):
				found.append(recipe["reagent"]["item"].partition(":")[2])
		return list(dict.fromkeys(found))

	def layers(self, item):
		"""The item's sprite layers and its item model's tints, or None when it is not a flat sprite."""
		model = self.json(f"assets/minecraft/items/{item}.json")["model"]
		if model.get("type") != "minecraft:model":
			return None
		ref = model["model"].partition(":")[2]
		textures = self.json(f"assets/minecraft/models/{ref}.json").get("textures", {})
		layers = [textures[f"layer{i}"] for i in range(8) if f"layer{i}" in textures]
		return (layers, model.get("tints", [])) if layers else None


def draw(grid, colour_of):
	rows = grid.strip("\n").split("\n")
	image = Image.new("RGBA", (len(rows[0]), len(rows)))
	for y, row in enumerate(rows):
		for x, ch in enumerate(row):
			colour = colour_of(ch)
			if colour is not None:
				image.putpixel((x, y), colour)
	return image


def staff():
	return draw(STAFF, lambda ch: None if PALETTE[ch] is None else PALETTE[ch] + (255,))


def gem():
	"""The plain gem, drawn into the socket's place on a 16 by 16 sheet."""
	image = Image.new("RGBA", (16, 16))
	image.alpha_composite(draw(GEM, lambda ch: None if GEM_SHADES[ch] is None else (GEM_SHADES[ch],) * 3 + (255,)),
		(SOCKET_LEFT, SOCKET_TOP))
	return image


def bar_back():
	image = Image.new("RGBA", (16, 16))
	for x in range(BAR_LEFT, BAR_LEFT + BAR_WIDTH):
		for y in (BAR_ROW, BAR_ROW + 1):
			image.putpixel((x, y), (0, 0, 0, 255))
	return image


def bar_fill(width):
	image = Image.new("RGBA", (16, 16))
	for x in range(BAR_LEFT, BAR_LEFT + width):
		image.putpixel((x, BAR_ROW), (255, 255, 255, 255))
	return image


def best_window(images, tinted):
	"""Where in the sprite the socket should look: the window most covered, and most coloured."""
	def score(u, v):
		total = 0.0
		for dx, dy in SOCKET_MASK:
			for image, tint in zip(images, tinted):
				r, g, b, a = image.getpixel((u + dx, v + dy))
				if a:
					saturation = 1.0 if tint else colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[1]
					total += a / 255 * (1 + 2 * saturation)
		return total - 0.01 * (abs(u - 5.5) + abs(v - 5.5))
	return max(((u, v) for u in range(12) for v in range(12)), key=lambda uv: score(*uv))


def socket_elements(texture_count, window, tints):
	"""Two crossed slabs make the socket's rounded five: a wide one and a tall one, same pixels."""
	u, v = window
	elements = []
	for layer in range(texture_count):
		depth = 0.6 + 0.05 * layer
		for (x0, y0, x1, y1) in ((0, 1, 5, 4), (1, 0, 4, 5)):
			uv = [u + x0, v + y0, u + x1, v + y1]
			face = {"texture": f"#layer{layer}", "uv": uv}
			back = {"texture": f"#layer{layer}", "uv": [uv[2], uv[1], uv[0], uv[3]]}
			if layer < len(tints):
				face["tintindex"] = back["tintindex"] = layer
			elements.append({
				"from": [SOCKET_LEFT + x0, 16 - SOCKET_TOP - y1, 8 - depth],
				"to": [SOCKET_LEFT + x1, 16 - SOCKET_TOP - y0, 8 + depth],
				"shade": False,
				"faces": {"south": face, "north": back},
			})
	return elements


def write_json(path, obj):
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_text(json.dumps(obj, indent="\t") + "\n")


def socket_model(name, textures, window, tints):
	write_json(MODELS / f"socket/{name}.json", {
		"parent": "minecraft:item/handheld",
		"textures": {f"layer{i}": t for i, t in enumerate(textures)} | {"particle": textures[0]},
		"elements": socket_elements(len(textures), window, tints),
	})
	return {"type": "minecraft:model", "model": f"{NS}:item/socket/{name}", "tints": tints} if tints else \
		{"type": "minecraft:model", "model": f"{NS}:item/socket/{name}"}


def item_model(cases):
	gem_model = socket_model("gem", [f"{NS}:item/pale_staff_gem"], (SOCKET_LEFT, SOCKET_TOP), [TINT])
	bar = {
		"type": "minecraft:range_dispatch",
		"property": "minecraft:custom_model_data",
		"index": 0,
		"entries": [{"threshold": round((w - 1) / BAR_WIDTH + 0.0001, 5),
			"model": {"type": "minecraft:model", "model": f"{NS}:item/charge_bar_{w}",
				"tints": [{"type": "minecraft:constant", "value": -1}, TINT]}}
			for w in range(1, BAR_WIDTH + 1)],
		"fallback": {"type": "minecraft:model", "model": f"{NS}:item/charge_bar_0"},
	}
	return {"model": {
		"type": "minecraft:composite",
		"models": [
			{"type": "minecraft:model", "model": f"{NS}:item/pale_staff"},
			IMBUED | {
				"on_true": {
					"type": "minecraft:select",
					"property": "minecraft:custom_model_data",
					"index": 0,
					"cases": cases,
					"fallback": gem_model,
				},
				"on_false": {"type": "minecraft:empty"}},
			{"type": "minecraft:select", "property": "minecraft:display_context",
				"cases": [{"when": "gui", "model": IMBUED | {"on_true": bar, "on_false": {"type": "minecraft:empty"}}}],
				"fallback": {"type": "minecraft:empty"}},
		],
	}}


def tinted(image, rgb):
	out = image.copy()
	for x in range(out.width):
		for y in range(out.height):
			r, g, b, a = out.getpixel((x, y))
			if a:
				out.putpixel((x, y), (r * rgb[0] // 255, g * rgb[1] // 255, b * rgb[2] // 255, a))
	return out


def socketed(jar, item, tint=(0xff, 0xff, 0xff)):
	"""The staff as the game draws it with {item} in the socket, for the icon and for looking at."""
	frame = staff()
	layers, tints = jar.layers(item)
	images = [jar.texture(t) for t in layers]
	u, v = best_window(images, [i < len(tints) for i in range(len(images))])
	for i, image in enumerate(images):
		if i < len(tints):
			image = tinted(image, tint)
		for dx, dy in SOCKET_MASK:
			pixel = image.getpixel((u + dx, v + dy))
			if pixel[3]:
				frame.putpixel((SOCKET_LEFT + dx, SOCKET_TOP + dy), pixel)
	return frame


def moobloom(cow):
	"""A cow repainted buttercup: the brown goes yellow, the grey patches cream, the rest stays."""
	out = cow.copy()
	for x in range(out.width):
		for y in range(out.height):
			r, g, b, a = out.getpixel((x, y))
			if not a:
				continue
			h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
			if v < 0.16 or (s > 0.25 and (h < 0.02 or h > 0.9)):
				continue  # hooves, eyes, nose and the pink of the udder
			if s < 0.12 and v > 0.4:
				shade = 0.78 + 0.22 * v  # the patches
				out.putpixel((x, y), (int(250 * shade), int(242 * shade), int(214 * shade), a))
			elif s < 0.12:
				continue  # the horns' grey
			else:
				shade = 0.55 + 1.6 * v  # the coat
				out.putpixel((x, y), (min(255, int(232 * shade)), min(255, int(178 * shade)), min(255, int(36 * shade)), a))
	return out


def icon(jar):
	"""The staff with a golden dandelion set, on the bark of the pale oak it is cut from, darkened so
	the pale wood stands off it. Filled to the edge, as most of the suite's icons are, so it holds
	its own in a list of them."""
	bark = jar.texture_full("minecraft:block/pale_oak_log").crop((0, 0, 16, 16))
	frame = Image.new("RGBA", (16, 16))
	for x in range(16):
		for y in range(16):
			r, g, b, a = bark.getpixel((x, y))
			frame.putpixel((x, y), (r * 45 // 100, g * 45 // 100, b * 45 // 100, 255))
	frame.alpha_composite(socketed(jar, "golden_dandelion"))
	return frame.resize((128, 128), Image.NEAREST)


def main():
	jar = Jar(find_jar(sys.argv))
	entity = ASSETS / "textures/entity/cow"
	entity.mkdir(parents=True, exist_ok=True)
	moobloom(jar.texture_full("minecraft:entity/cow/cow_temperate")).save(entity / "moobloom.png")
	moobloom(jar.texture_full("minecraft:entity/cow/cow_temperate_baby")).save(entity / "moobloom_baby.png")

	TEXTURES.mkdir(parents=True, exist_ok=True)
	staff().save(TEXTURES / "pale_staff.png")
	gem().save(TEXTURES / "pale_staff_gem.png")
	bar_back().save(TEXTURES / "charge_bar_back.png")
	for width in range(1, BAR_WIDTH + 1):
		bar_fill(width).save(TEXTURES / f"charge_bar_{width}.png")

	write_json(MODELS / "pale_staff.json", {"parent": "minecraft:item/handheld",
		"textures": {"layer0": f"{NS}:item/pale_staff"}})
	write_json(MODELS / "charge_bar_0.json", {"parent": "minecraft:item/generated",
		"textures": {"layer0": f"{NS}:item/charge_bar_back"}})
	for width in range(1, BAR_WIDTH + 1):
		write_json(MODELS / f"charge_bar_{width}.json", {"parent": "minecraft:item/generated",
			"textures": {"layer0": f"{NS}:item/charge_bar_back", "layer1": f"{NS}:item/charge_bar_{width}"}})

	cases = []
	for old in (MODELS / "socket").glob("*.json"):
		old.unlink()
	for item in dict.fromkeys(jar.reagents() + SPECIALS):
		found = jar.layers(item)
		if found is None:
			print(f"skipping {item}: not a flat sprite, it gets the gem")
			continue
		layers, tints = found
		window = best_window([jar.texture(t) for t in layers], [i < len(tints) for i in range(len(layers))])
		cases.append({"when": f"minecraft:{item}", "model": socket_model(item, layers, window, tints)})
	write_json(ASSETS / "items/pale_staff.json", item_model(cases))

	icon(jar).save(ASSETS / "icon.png")


if __name__ == "__main__":
	main()
