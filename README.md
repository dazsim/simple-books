# Simple Books

Guide books for Minecraft mods, written as JSON. Each mod ships its own pages; Simple Books draws them as a double-page parchment spread with real item icons and names, numbered steps joined by ink arrows, pictures and clickable links.

Minecraft 26.1 to 26.1.x, NeoForge and Fabric (Fabric needs Fabric API). Client side rendering; the guide item is registered on both sides.

Status: 0.1.0-alpha.2, milestone 2 (contents spread, remembered page, book stand).

## Optional integration

Simple Books is optional for every mod that uses it. A mod with a book must run fine without it:

- Put the book, its pages, its pictures and its lang lines in your own mod's resources (paths below). Nothing else is needed: no code dependency, no jar-in-jar.
- If Simple Books is not installed those files are just unused resources. No crash, no missing class.
- If you want to give or craft a book, guard it:
  - Recipes: add a load condition so the recipe only loads with Simple Books present, for example `"neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "simplebooks" }]` on NeoForge or `"fabric:load_conditions": [{ "condition": "fabric:all_mods_loaded", "values": ["simplebooks"] }]` on Fabric. The result is `simplebooks:guide` with the component `"simplebooks:book": "yourmod:yourbook"`.
  - Code: check the mod is loaded (`ModList.get().isLoaded("simplebooks")` or `FabricLoader.getInstance().isModLoaded("simplebooks")`) and look the item and component up by id from the registries, so your mod never links against Simple Books classes.
- Declare Simple Books as an optional (suggested) dependency in your mod metadata.
- Modpack makers can change any page, template or picture with a resource pack.

Give a book by command: `/give @p simplebooks:guide[simplebooks:book="morefood:cookbook"]`

## Files

All under `assets/<namespace>/` so resource packs can override everything (reload with F3+T):

```
simplebooks/books/<book>.json               book definition
simplebooks/books/<book>/pages/<page>.json  one file per spread (subfolders allowed)
simplebooks/templates/<template>.json       templates
textures/simplebooks/<book>/...png          pictures
lang/<locale>.json                          text
```

Book `morefood:cookbook` lives at `assets/morefood/simplebooks/books/cookbook.json`, its pages in `assets/morefood/simplebooks/books/cookbook/pages/`. Page ids are `<book>/<page path>`, for example `morefood:cookbook/pizza`.

## Book

```json
{
  "title": "book.morefood.cookbook.title",
  "default_template": "simplebooks:recipe",
  "style": { "ink": "#3f2a14", "head": "#802c16", "accent": "#8b2e16" },
  "sections": ["contents", "toppings", "tools", "crops", "recipes"],
  "contents_title": "simplebooks.contents"
}
```

`sections` orders the pages: each page names its `section` and pages sort by section, then `sort`, then file name. If `contents` is listed, a contents spread (template `simplebooks:index`) is made for it automatically: every page grouped under its section, with clickable titles and page numbers. A page with `"contents": false` is left out. Section headings use the lang key `book.<namespace>.<book>.section.<name>`, or the section name. Every spread then has a contents bookmark at the top right.

The guide item remembers the spread it was closed on (component `simplebooks:spread`, sent to the server when the screen closes) and opens there next time.

`style` values become template variables (`$ink`, `$head` and so on). Without `sections`, pages are ordered by their `sort` number, then file name. The guide item's name comes from the lang key `book.<namespace>.<book>.title`.

## Pages

One file is one spread. Any text can be a lang key or literal text. The fields a page needs depend on its template. For the built-in `simplebooks:recipe`:

| Field | Use |
| --- | --- |
| `title`, `subtitle`, `icon` | left page header (icon drawn either side of the title) |
| `right_title`, `right_subtitle`, `right_icon` | right page header, falling back to the left ones |
| `ingredients`, `tools` | "For this recipe you will need..." lists, icon and name for each |
| `need_note` | grey line under the lists |
| `steps` | numbered steps and other blocks; they fill the left page, then continue on the right |
| `links` | optional link list at the foot of the right page |
| `first_step` | number of the first step, for a recipe continued from the page before |

Item references are an id (`"minecraft:wheat"`), a tag (`"#minecraft:logs"`, icons cycle) or an object:
`{ "item": "...", "name": "...", "count": 2, "note": "first" }`, or `"items": [...]` to cycle several icons. Names come from the game unless `name` is given. Unknown items draw a pink and black marker with the id as name; they never crash the book.

Step kinds (numbered, joined by brown ink arrows):

- `craft_shapeless`: `inputs` drawn as A + B + C, then an arrow and `output`. Labelled "Crafting Table (shapeless)" unless `note` is set.
- `craft_shaped`: `pattern` (3 strings) and `key`, drawn as a grid, then `output`.
- `process`: `inputs` in `appliance` on `on`, then `output`. Use `note` for times.
- `assembly`: title, `text`, picture, then a row of big icons (`inputs` then `output`) with arrows and each item's `note` underneath.
- `sequence`: a row of big icons from `cells`, then `text` underneath, picture beside.

Other blocks: `text` (`"text"`, `"style"`), `links` (`"header"`, `"columns"`, `"links": [{ "item", "label", "page" }]`), `image`, `arrow` (a down arrow), `page_break` (move on to the next page).

Any step can have `title` (defaults to the output's name), `note`, `image` (a texture id such as `morefood:textures/simplebooks/cookbook/pot_on_stove.png`), `image_size` `[w, h]`, or `placeholder` text for a picture still to come. Pictures are cropped to fill their box.

Links take a page id, either relative to the book (`"toppings"`) or full (`"morefood:cookbook/toppings"`). Click to jump; right click or Backspace goes back. Links should only be used for optional content.

## Templates

A template places slots on the left and right page. Coordinates are page units: each page's content area is 644 wide and 920 tall, with text scale 1 being the Minecraft font at 2 units per pixel. These are the same numbers as the 1600 x 1000 mockup, and the screen scales the whole spread to fit.

```json
{
  "extends": "simplebooks:recipe",
  "vars": { "ink": "#203040" },
  "styles": { "title": { "color": "$ink", "scale": 2, "align": "center" } },
  "slots": {
    "left.need": null,
    "left.body": { "type": "text", "box": [0, 100, 644, 760], "bind": "text", "style": "body", "optional": true }
  }
}
```

- `extends` (or `parent`) pulls in every slot, style and variable of the parent; a child slot replaces the fields it names, and `null` removes a slot. Parent cycles are an error.
- `bind` names the page field a slot shows; `"right_title|title"` falls back left to right. A bound field is required unless the slot is `optional`.
- Slot types: `title`, `subtitle`, `text`, `rule`, `need_list` (also `ingredient_list`, `tool_list`), `step_rows`, `image_box`, `link_list`, `item_icon`, `grid`, `page_number`, `arrow`, `contents` (`columns`).
- Styles: `color` (`#rrggbb` or `$var`), `scale`, `align` (`left`, `center`, `right`), `line_height`.
- Variables resolve from the book's `style`, then the template, then library defaults (`ink`, `soft`, `head`, `link`, `rule`, `page`, `arrow`, `cover`, `error`).
- Built-in templates: `simplebooks:recipe` (the full recipe spread), `simplebooks:steps` (steps over both pages, for a recipe continued from the page before), `simplebooks:text` (title, text, optional steps), `simplebooks:index` (the contents spread). `tool` and `crop` are the same as `text` for now.

## Validation

Books are checked on every resource reload. Problems are logged with the file path and a JSON pointer, and a broken spread shows a red-bordered error page listing them while the rest of the book keeps working. Checked: JSON syntax, unknown template, template parent cycles, unknown slot types, boxes outside the page, missing required fields, unknown step kinds and links to missing pages. Unknown items and missing pictures are logged as warnings. Content that does not fit on the spread (steps, rows wider than the page, a need list taller than its box) is reported in red at the foot of the right page. `/simplebooks validate` lays out every spread and lists all of these too.

## Book stand

`simplebooks:book_stand` is a wooden lectern for guides. Use a guide on an empty stand to put it down; the book lies open on the stand at its saved spread. Use the stand with an empty hand to read it; turning pages also turns the book on the stand, for everyone. Sneak and use it with an empty hand to take the book back. Breaking the stand drops the book.

Recipe (shaped): three wooden slabs on top, a stick in the middle, three planks at the bottom.

## Commands (client side)

- `/simplebooks open <book> [page]`
- `/simplebooks validate <book>`
- `/simplebooks list`

## Development

`dev/example-pack` is an example resource pack with the More Foods cookbook and a deliberately broken book (`simplebooks:broken_test`). Most cookbook pages are made by `dev/cookbook_gen.py` from the More Foods recipe files (`python3 dev/cookbook_gen.py <more foods repo> <vanilla en_us.json>`); `pizza.json` is written by hand. `runClient` copies it into the run folder and enables it; it is never packed into the jar. If `dev/morefood-fabric.jar` or `dev/morefood-neoforge.jar` exist (More Foods 2.0 alpha jars, not in git), they are put on the dev client's classpath so the cookbook shows real items.

Not yet done: grant events, page-turn animation, Python preview renderer.
