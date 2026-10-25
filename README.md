# Simple Books

Guide books for Minecraft mods, written as JSON. Each mod ships its own pages; Simple Books draws them as a double-page parchment spread with real item icons and names, numbered steps joined by ink arrows, pictures and clickable links.

Minecraft 26.1 to 26.1.x, NeoForge and Fabric (Fabric needs Fabric API). Client side rendering; the guide item is registered on both sides.

Status: 0.1.0-alpha.5 (per-book styling, chapter tabs, optional pages, page-turn animation).

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

Step kinds (numbered). A brown ink arrow joins a step to the one above only when it uses what that step made. Set `"join": true` to force the arrow, or `"join": false` to start a new group under a thin rule.

- `craft_shapeless`: `inputs` drawn as A + B + C, then an arrow and `output`. Labelled "Crafting Table (shapeless)" unless `note` is set.
- `craft_shaped`: `pattern` (3 strings) and `key`, drawn as a grid, then `output`.
- `process`: `inputs` in `appliance` on `on`, then `output`. Use `note` for times.
- `assembly`: title, `text`, picture, then a row of big icons (`inputs` then `output`) with arrows and each item's `note` underneath.
- `sequence`: a row of big icons from `cells` joined by arrows, then `text` underneath, picture beside. If the last cell has `"note": "result"`, the other cells are joined by `+` and only the result gets an arrow.

Other blocks: `text` (`"text"`, `"style"`), `links` (`"header"`, `"columns"`, `"links": [{ "item", "label", "page" }]`), `image`, `arrow` (a down arrow), `page_break` (move on to the next page).

Any step can have `title` (defaults to the output's name), `note`, `image` (a texture id such as `morefood:textures/simplebooks/cookbook/pot_on_stove.png`), `image_size` `[w, h]`, or `placeholder` text for a picture still to come. Pictures are cropped to fill their box and get a dark frame; `"frame": false` on the step or `image_box` slot leaves it off.

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
- Variables resolve from the page's `style`, then the book's `style`, then the template, then library defaults; the full list is under [Styling](#5-styling).
- A page field named `image`, ending in `_image`, or whose value ends in `.png` is checked as a texture, so templates can bind several pictures per spread.
- Built-in templates: `simplebooks:recipe` (the full recipe spread), `simplebooks:steps` (steps over both pages, for a recipe continued from the page before), `simplebooks:text` (title, text, optional steps), `simplebooks:index` (the contents spread). `tool` and `crop` are the same as `text` for now.

## Validation

Books are checked on every resource reload. Problems are logged with the file path and a JSON pointer, and a broken spread shows a red-bordered error page listing them while the rest of the book keeps working. Checked: JSON syntax, unknown template, template parent cycles, unknown slot types, boxes outside the page, missing required fields, unknown step kinds and links to missing pages. Unknown items and missing pictures are logged as warnings. Content that does not fit on the spread (steps, rows wider than the page, a need list taller than its box) is reported in red at the foot of the right page. `/simplebooks validate` lays out every spread and lists all of these too.

## Book stand

`simplebooks:book_stand` is a wooden lectern for guides. Use a guide on an empty stand to put it down; the book lies open on the stand at its saved spread. Use the stand with an empty hand to read it; turning pages also turns the book on the stand, for everyone. Sneak and use it with an empty hand to take the book back. Breaking the stand drops the book.

A book can set `stand_cover` and `stand_page` textures for the stand, and a page can set `stand_left` and `stand_right` to show its own pictures there instead of titles and icons. These take a texture id, with or without the `textures/` folder and `.png`.

Recipe (shaped): three wooden slabs on top, a stick in the middle, three planks at the bottom.

## Making your own book

This walks through a book from nothing, using the blueprint sample in `dev/example-pack` (`simplebooks:blueprints`) as the example. Open it in a dev client with `/simplebooks open simplebooks:blueprints`.

### 1. Files

Pick your mod id as the namespace and a book name, then create:

```
assets/yourmod/simplebooks/books/manual.json                 the book
assets/yourmod/simplebooks/books/manual/pages/intro.json     one file per spread
assets/yourmod/simplebooks/books/manual/pages/parts/gear.json  subfolders are fine
assets/yourmod/simplebooks/templates/sheet.json              your own templates (optional)
assets/yourmod/textures/simplebooks/manual/*.png             pictures, paper and stand textures
assets/yourmod/lang/en_us.json                               book.yourmod.manual.title and any lang keys you use
```

The book id is `yourmod:manual` and page ids are `yourmod:manual/intro`, `yourmod:manual/parts/gear`. Run `/simplebooks validate yourmod:manual` after every change (F3+T reloads).

### 2. The book file

```json
{
  "title": "book.yourmod.manual.title",
  "default_template": "simplebooks:text",
  "tabs": true,
  "page_turn": true,
  "style": { "page": "#1d4f8c", "ink": "#ffffff", "head": "#ffffff" },
  "stand_cover": "yourmod:simplebooks/manual/stand_cover",
  "stand_page": "yourmod:simplebooks/manual/stand_page",
  "sections": [
    "contents",
    "machines",
    { "id": "parts", "title": "Parts", "color": "#d6e6f6" },
    { "id": "storage", "requires": ["muchmorestorage"], "icon": "muchmorestorage:screwdriver" }
  ]
}
```

| Key | Use |
| --- | --- |
| `title` | lang key or text for the book |
| `default_template` | template for pages that do not name one (`simplebooks:recipe` if unset) |
| `style` | styling variables, see below |
| `sections` | page order; a section is a name or an object with `id`, and optionally `title`, `color` and `icon` (for its tab), `"tab": false` and `requires` |
| `tabs` | `true` draws a chapter tab per section on the fore edge, except `contents` and empty or hidden sections. Click a tab to jump to the first page of that chapter. Default `false` |
| `page_turn` | `false` turns off the page-turn animation. Default `true` |
| `stand_cover`, `stand_page` | textures used when the book lies on a book stand |
| `requires` | mod ids; the whole book is skipped unless all are loaded |
| `contents_title` | lang key for the contents heading |

### 3. Pages

Each page names its `section`, optional `sort` and `template`, plus the fields its template binds. With `simplebooks:text`:

```json
{
  "section": "machines",
  "sort": 1,
  "title": "Gearbox",
  "subtitle": "Sheet 1 of 3",
  "text": "A gearbox turns a fast, weak shaft into a slow, strong one.",
  "right_title": "Assembly",
  "steps": [
    { "kind": "craft_shaped", "pattern": ["IGI", "GSG", "IGI"], "key": { "I": "minecraft:iron_ingot", "G": "minecraft:gold_nugget", "S": "minecraft:stick" }, "output": "minecraft:piston" },
    { "kind": "text", "text": "With Simple Machines a crank turns the input shaft.", "requires": ["simplemachines"] }
  ],
  "links": [{ "item": "minecraft:stick", "label": "Drive shaft", "page": "shaft" }]
}
```

The field list for each built-in template and all step kinds are under [Pages](#pages) above.

### 4. Templates and inheritance

A template is a set of slots, styles and variables. Start from a built-in one with `extends` and change only what you need; a child slot replaces the fields it names and `null` removes a slot. The sample's `simplebooks:blueprint_sheet` shortens the text box of `simplebooks:text` and adds a drawing and a caption:

```json
{
  "extends": "simplebooks:text",
  "vars": { "rule": "#9cc0e4" },
  "slots": {
    "left.body": { "box": [0, 100, 644, 300] },
    "left.drawing": { "type": "image_box", "box": [42, 420, 560, 350], "bind": "drawing", "frame": false, "optional": true },
    "left.caption": { "type": "subtitle", "box": [0, 790, 644, 18], "bind": "caption", "style": "subtitle", "optional": true }
  }
}
```

Templates can extend templates from any namespace, so a pack can extend your template too. See [Templates](#templates) for slot types and style fields.

### 5. Styling

Every colour, the paper and the font come from variables. They are looked up in this order: the page's own `style` object, the book's `style`, the template's `vars`, then the defaults below. A value can point at another variable with `$name`. Leave them all out and the book looks like the parchment cookbook.

| Variable | Default | Used for |
| --- | --- | --- |
| `page` | `#f1e4c2` | paper colour |
| `page_texture` | none | texture id drawn over the paper, for example `yourmod:textures/simplebooks/manual/grid.png` |
| `page_tile` | none | tile width in page units; without it the texture is stretched over each page |
| `font` | `minecraft:default` | font id for all book text, vanilla (`minecraft:uniform`, `minecraft:alt`) or one from a resource pack |
| `ink` | `#3f2a14` | body text |
| `soft` | `#785c3c` | subtitles, notes, item names |
| `head` | `#802c16` | titles and headings |
| `link` | `#284896` | link text |
| `rule` | `#c4ac82` | ruled lines |
| `arrow` | `#5c3e22` | arrows between steps |
| `edge` | `#aa8c60` | border round the paper |
| `cover`, `cover_edge` | `#60361a`, `#3a200e` | the cover behind the paper and its outline |
| `badge`, `badge_edge`, `badge_text` | `$head`, `#c4785a`, `#faeed2` | step number badges |
| `slot`, `slot_shadow`, `slot_light` | `#8b8b8b`, `#373737`, `#ffffff` | item slots |
| `frame`, `frame_inner` | `#5c4028`, `#1e1610` | picture frames |
| `placeholder`, `placeholder_hatch` | `#decea8`, `#d4c298` | boxes for pictures still to come |
| `mark`, `mark_hover`, `mark_line` | `#802c16`, `#a83a1e`, `#e0b080` | the contents bookmark |
| `tabs` | `#802c16,#2c5a80,#3c6a2c,#8a6a1c,#5a2c70` | tab colours, used in turn; a section's `color` wins |
| `tab_text`, `tab_edge` | `$page`, `$cover_edge` | tab labels and outlines |
| `button_tint` | `#ffffff` | tint for the page-turn buttons |
| `stand_ink` | `#3f2a14` | title text on the book stand |
| `error` | `#b01818` | error pages |

A blueprint book with blue paper, a grid and white ink:

```json
"style": {
  "page": "#1d4f8c",
  "page_texture": "simplebooks:textures/simplebooks/blueprints/grid.png",
  "page_tile": "128",
  "font": "minecraft:uniform",
  "ink": "#ffffff", "soft": "#c8dcf0", "head": "#ffffff", "link": "#ffe680", "rule": "#9cc0e4",
  "edge": "#9cc0e4", "cover": "#16365e", "cover_edge": "#0c1e36",
  "frame": "#ffffff", "frame_inner": "#1d4f8c",
  "tabs": "#ffffff,#d6e6f6,#aecdec", "tab_text": "#1d4f8c",
  "stand_ink": "#ffffff"
}
```

Draw line art as white lines on a transparent PNG and set `"frame": false` so it sits straight on the paper. A single page can override anything, for example `"style": { "head": "#ffe680" }` in the page file.

### 6. Optional content with `requires`

`requires` is a mod id or a list of them. Anything that has it is only shown when every listed mod is loaded:

- a book: the whole book is skipped
- a section (object form): the section, its tab and all its pages
- a page: the page
- a step or other block in `steps`, or an entry in `links`

Hidden pages are removed before the contents spread and page numbers are worked out, so numbering has no gaps, and links that point at a hidden page are dropped rather than reported. Another mod can add pages to your book the same way: it ships `assets/yourmod/simplebooks/books/manual/pages/othermod_crate.json` in its own jar with `"requires": ["othermod"]`, and the page appears only when both are installed.

### 7. Book stand textures

`stand_cover` and `stand_page` are 16 x 16 textures (any size works, they are stretched) given as texture ids, with or without `textures/` and `.png`. The cover shows round the edge of the open book, the page texture under the titles and icons. A page can set `stand_left` and `stand_right` to show a picture instead of its titles and icons. Long titles shrink a little, then wrap to two lines, and only past that are they cut short. Set `stand_ink` when your paper is dark.

### 8. Shipping a book as an optional dependency

Your book is plain resources, so a mod with a book needs no code changes and still runs without Simple Books (see [Optional integration](#optional-integration)). To hand players the book, add a recipe with a load condition so it only exists when Simple Books is installed:

```json
{
  "fabric:load_conditions": [{ "condition": "fabric:all_mods_loaded", "values": ["simplebooks"] }],
  "neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "simplebooks" }],
  "type": "minecraft:crafting_shapeless",
  "ingredients": ["minecraft:book", "yourmod:gear"],
  "result": { "id": "simplebooks:guide", "components": { "simplebooks:book": "yourmod:manual" } }
}
```

Both condition keys can sit in the same file; each loader ignores the other's. Declare Simple Books as a suggested or optional dependency in `fabric.mod.json` (`"suggests"`) and `neoforge.mods.toml` (`type = "optional"`).

## Commands (client side)

- `/simplebooks open <book> [page]`
- `/simplebooks validate <book>`
- `/simplebooks list`

## Development

`dev/example-pack` is an example resource pack with a deliberately broken book (`simplebooks:broken_test`) and the blueprint sample (`simplebooks:blueprints`). The More Foods cookbook now ships inside More Foods; most of its pages are made by `dev/cookbook_gen.py` from the More Foods recipe files and written straight into that repo (`python3 dev/cookbook_gen.py <more foods repo> <vanilla en_us.json>`); `pizza.json` and `pizza_2.json` are written by hand. `runClient` copies it into the run folder and enables it; it is never packed into the jar. If `dev/morefood-fabric.jar` or `dev/morefood-neoforge.jar` exist (More Foods 2.0 alpha jars, not in git), they are put on the dev client's classpath so the cookbook shows real items.

Not yet done: grant events, Python preview renderer.
