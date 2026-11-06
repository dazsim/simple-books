# Simple Books

Guide books for Minecraft mods, written as JSON. Each mod ships its own pages; Simple Books draws them as a double-page parchment spread with real item icons and names, numbered steps joined by ink arrows, pictures and clickable links.

Minecraft 1.20.1 (Forge, Fabric), 1.21.1, 26.1.2 and 26.3 (NeoForge, Fabric). Fabric needs Fabric API. Client side rendering; the guide item is registered on both sides. See [Versions](#versions) for what differs between them.

Status: 1.1.0 (multi-version build, `simplebooks:guide` recipe type). 1.0.x was 26.1 only.

## Optional integration

Simple Books is optional for every mod that uses it. A mod with a book must run fine without it:

- Put the book, its pages, its pictures and its lang lines in your own mod's resources (paths below). Nothing else is needed: no code dependency, no jar-in-jar.
- If Simple Books is not installed those files are just unused resources. No crash, no missing class.
- If you want to give or craft a book, guard it:
  - Recipes: add a load condition so the recipe only loads with Simple Books present: `"neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "simplebooks" }]` on NeoForge, `"conditions": [{ "type": "forge:mod_loaded", "modid": "simplebooks" }]` on Forge 1.20.1, `"fabric:load_conditions": [{ "condition": "fabric:all_mods_loaded", "values": ["simplebooks"] }]` on Fabric. The easiest recipe is the `simplebooks:guide` type (see [Recipes, loot and advancements](#recipes-loot-and-advancements)).
  - Code: check the mod is loaded (`ModList.get().isLoaded("simplebooks")` or `FabricLoader.getInstance().isModLoaded("simplebooks")`) and look the item and component up by id from the registries, so your mod never links against Simple Books classes.
- Declare Simple Books as an optional (suggested) dependency in your mod metadata.
- Modpack makers can change any page, template or picture with a resource pack.

Give a book by command: `/give @p simplebooks:guide[simplebooks:book="morefood:cookbook"]` (1.21.1 and later) or `/give @p simplebooks:guide{"simplebooks:book":"morefood:cookbook"}` (1.20.1).

## Versions

| Minecraft | Loaders | Java | Book data on the item |
| --- | --- | --- | --- |
| 1.20.1 | Forge, Fabric | 17 | root NBT |
| 1.21.1 | NeoForge, Fabric | 21 | components |
| 26.1.2 | NeoForge, Fabric | 25 | components |
| 26.3 | NeoForge, Fabric | 25 | components |

Book, page and template JSON, pictures, fonts and lang files are the same on every version, and so are their paths (`assets/<namespace>/simplebooks/...`). One resource pack or mod jar folder works everywhere; only `pack.mcmeta` and the data files below differ.

The item id is `simplebooks:guide` everywhere. Which book it opens, and the spread it was left at, are stored as:

- 1.21.1 and later: components `simplebooks:book` (a resource id string) and `simplebooks:spread` (an int). Same as 1.0.x, so existing books keep working.
- 1.20.1: root NBT tags on the stack, string `simplebooks:book` and int `simplebooks:spread`, for example `{"simplebooks:book":"muchmorestorage:manual","simplebooks:spread":3}`. There is no component system on 1.20.1; the tag names match the component ids so commands and data files read the same.

A different item look per book needs the vanilla `minecraft:item_model` component, which only exists from 1.21.2. Set it next to `simplebooks:book` in a vanilla recipe result or a `/give`. On 1.20.1 and 1.21.1 every book uses the default guide model.

### Recipes, loot and advancements

Recipe type `simplebooks:guide` is a crafting recipe whose result is a guide for `book`. It works the same on every version, so you do not need to write the result stack yourself. With `pattern` and `key` it is shaped, with `ingredients` it is shapeless. `book` is required; `count` is optional and should stay 1, since the guide does not stack (1.21.1 rejects anything larger). Ingredients use the version's own format: `{ "item": "minecraft:book" }` or `{ "tag": "c:ingots/iron" }` on 1.20.1 and 1.21.1, plain strings `"minecraft:book"` or `"#c:ingots/iron"` on 26.x. Folder names follow vanilla: `recipes`, `loot_tables`, `advancements` on 1.20.1, `recipe`, `loot_table`, `advancement` on 1.21.1 and later.

1.20.1 (Forge; drop `conditions` and add `fabric:load_conditions` on Fabric):

```json
{
  "conditions": [{ "type": "forge:mod_loaded", "modid": "simplebooks" }],
  "type": "simplebooks:guide",
  "book": "yourmod:manual",
  "ingredients": [{ "item": "minecraft:book" }, { "item": "yourmod:gear" }]
}
```

1.21.1 and 26.x (both condition keys can sit in one file; each loader ignores the other's):

```json
{
  "fabric:load_conditions": [{ "condition": "fabric:all_mods_loaded", "values": ["simplebooks"] }],
  "neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "simplebooks" }],
  "type": "simplebooks:guide",
  "book": "yourmod:manual",
  "pattern": ["G", "B"],
  "key": { "G": "yourmod:gear", "B": "minecraft:book" }
}
```

On 1.21.1 write the keys as `{ "item": "yourmod:gear" }`.

Loot table entry, 1.20.1:

```json
{ "type": "minecraft:item", "name": "simplebooks:guide",
  "functions": [{ "function": "minecraft:set_nbt", "tag": "{\"simplebooks:book\":\"yourmod:manual\"}" }] }
```

Loot table entry, 1.21.1 and later:

```json
{ "type": "minecraft:item", "name": "simplebooks:guide",
  "functions": [{ "function": "minecraft:set_components", "components": { "simplebooks:book": "yourmod:manual" } }] }
```

Advancement icon, 1.20.1: `{ "item": "simplebooks:guide", "nbt": "{\"simplebooks:book\":\"yourmod:manual\"}" }`

Advancement icon, 1.21.1 and later: `{ "id": "simplebooks:guide", "components": { "simplebooks:book": "yourmod:manual" } }`

`pack.mcmeta` for a resource pack with only books: `"pack_format": 15` on 1.20.1, `34` on 1.21.1, `"min_format": 84, "max_format": [200, 0]` on 26.x.

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

Any numbered step can take `"number": false` to drop its badge and leave the count alone, for a list of things rather than steps in order.

Other blocks: `text` (`"text"`, `"style"`), `links` (`"header"`, `"columns"`, `"links": [{ "item", "label", "page" }]`), `image`, `arrow` (a down arrow), `page_break` (move on to the next page).

Any step can have `title` (defaults to the output's name), `note`, `image` (a texture id such as `morefood:textures/simplebooks/cookbook/pot_on_stove.png`), `image_size` `[w, h]`, or `placeholder` text for a picture still to come. Pictures are cropped to fill their box and get a dark frame; `"frame": false` on the step or `image_box` slot leaves it off.

Links take a page id, either relative to the book (`"toppings"`) or full (`"morefood:cookbook/toppings"`). Click to jump; right click or Backspace goes back. Links should only be used for optional content.

### Recipes drawn in pictures

A page whose picture shows a recipe as line art can add `drawn_recipes`, so readers can tell similar looking items apart. Each entry gets the item tooltip when the mouse is over a drawn cell or the result, and a title with a named list such as "8x Oak Planks, 1x Chest" at `text`.

```json
"drawn_recipes": [{
  "side": "right",
  "text": [40, 720, 560],
  "grid": { "at": [50, 400], "cell": 70, "pattern": ["PPP", "PCP", "PPP"] },
  "key": { "P": { "items": ["minecraft:oak_planks", "minecraft:spruce_planks"], "name": "mymod.planks_any" }, "C": "#c:chests/wooden" },
  "result": { "item": "mymod:carcass", "count": 1, "box": [430, 450, 110, 110] }
}]
```

- `side`: `left` or `right` (default). Boxes are page units from the top left of that page, the same as the picture when it fills the page.
- `grid`: where the drawn grid sits, its cell size and the recipe pattern. Counts in the list come from the pattern.
- `ingredients`: a list of item references with `count` instead of `grid`, for a recipe drawn some other way. Add `box` to one to give it a tooltip.
- `result`: the output; its `box` gets a tooltip and a `count` above 1 adds "makes N" to the title.
- `title`: defaults to the result's name. `hover`: extra `{ "box", "item" }` tooltip areas.

Generate these from your recipe files when you can, so the list always matches the real recipe. `/simplebooks validate` reports pattern letters with no key, items that do not resolve, and text laid over a drawn cell or running off the page.

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
- `step_rows` can take `"below": "<slot>"` to start under whatever that slot drew (a need list of any length, a text or subtitle) plus `gap` (default 14), keeping the bottom of its box.
- Built-in templates: `simplebooks:recipe` (the full recipe spread), `simplebooks:steps` (steps over both pages, for a recipe continued from the page before), `simplebooks:text` (title, text, optional steps), `simplebooks:index` (the contents spread). `tool` and `crop` are the same as `text` for now.

### Flowing pages

With `"flow": true` in the book file a page file no longer has to be one spread. Its content goes on the left slots first, then on as many single pages using the right slots as it needs, so a long recipe is one file and a short one takes one page. Links are drawn on the last page of the file. When a file of two or more pages would start on a right page, the next one page file of the same section moves up to fill the gap. Page numbers, the contents and tabs follow the real pages. In a flowing book a step label shrinks when that keeps its row of icons on one line. Books without `flow` are laid out as before.

## Validation

Books are checked on every resource reload. Problems are logged with the file path and a JSON pointer, and a broken spread shows a red-bordered error page listing them while the rest of the book keeps working. Checked: JSON syntax, unknown template, template parent cycles, unknown slot types, boxes outside the page, missing required fields, unknown step kinds and links to missing pages. Unknown items and missing pictures are logged as warnings. Content that does not fit on the spread (steps, rows wider than the page, a need list taller than its box) is reported in red at the foot of the right page. `/simplebooks validate` lays out every spread and lists all of these too. It also reports pages that are blank or only hold links, steps sent to the next page while the list above left room for them, and a file whose first step would fit at the end of the file before.

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
| `flow` | `true` lays the book out page by page instead of one file per spread, see [Flowing pages](#flowing-pages). Default `false` |

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

Your book is plain resources, so a mod with a book needs no code changes and still runs without Simple Books (see [Optional integration](#optional-integration)). To hand players the book, add a `simplebooks:guide` recipe with a load condition so it only exists when Simple Books is installed (examples per version in [Recipes, loot and advancements](#recipes-loot-and-advancements)). A vanilla crafting recipe with the result `{ "id": "simplebooks:guide", "components": { "simplebooks:book": "yourmod:manual" } }` still works on 1.21.1 and later. Declare Simple Books as a suggested or optional dependency in `fabric.mod.json` (`"suggests"`), `neoforge.mods.toml` (`type = "optional"`) or the Forge 1.20.1 `mods.toml` (`mandatory = false`).

## Pack-wide books (config folder)

Anything in `config/simplebooks/` is loaded as an always-on pack called "Simple Books (pack config)" on both the server and the client, with no pack.mcmeta needed. Put books in `config/simplebooks/assets/<namespace>/simplebooks/books/` and data in `config/simplebooks/data/<namespace>/...`, the same layout as inside a mod jar. It sits above mod packs and below world data packs and player resource packs, so those can still override it. Handy for modpacks that want a book without KubeJS or a loader mod.

## Quests

Simple Books can turn a book into a quest book: checklists, skill trees, story chapters, repeatable chores, milestones, branching paths, bounty boards and big team hand-ins. Quests are data files, so a modpack needs no code. Progress lives on the team, rewards are claimed per player, and everything is checked on the server. Quests need Simple Books on both the server and the client.

A complete example that uses every quest style is in `dev/example-pack/config/` (book `example:guide`). Copy its `simplebooks` folder and `simplebooks.json` into a game's `config/` folder to try it. `/sbquests validate example:guide` reports 0 errors and 0 warnings.

### File layout

Quests are server data. A pack ships them in a data pack, a mod jar, or the always-on config pack:

```
config/simplebooks.json                                              server options
config/simplebooks/data/mypack/simplebooks/quests/journal/chapters/<chapter>.json
config/simplebooks/data/mypack/simplebooks/quests/journal/quests/<any/path>.json
config/simplebooks/data/mypack/simplebooks/quests/journal/pools/<pool>.json
config/simplebooks/assets/mypack/simplebooks/books/journal.json      the book look (optional)
config/simplebooks/assets/mypack/lang/en_us.json                     lang keys (optional)
```

The quest book id is `mypack:journal`. Ids follow the file paths: the chapter `chapters/stone_age.json` is `stone_age` (full id `mypack:journal/stone_age`), the quest `quests/stone_age/iron.json` is `stone_age/iron`. The same layout works inside a mod jar (`data/<modid>/simplebooks/quests/...`) or a world data pack.

Refs in `depends` and pools can be short: a ref is looked up next to the file first (`iron` from another file in `quests/stone_age/`), then from the book's quest folder (`stone_age/iron`), or written in full (`mypack:journal/stone_age/iron`). `"miner#2"` means tier 2 of the milestone quest `miner`.

The book file is optional. Without one, a default book is made with a contents spread, one tab per chapter in `sort` order and a team tab. With one, every quest chapter is a section with `quests`, and `team_page` adds the team spread. Normal pages can sit next to quest chapters:

```json
{
  "title": "book.mypack.journal.title",
  "tabs": true,
  "sections": [
    "contents",
    { "id": "welcome", "title": "Welcome" },
    { "id": "stone_age", "quests": "stone_age", "title": "Stone Age", "icon": "minecraft:stone_pickaxe" },
    { "id": "bounties", "quests": "bounties", "title": "Bounty Board" },
    { "id": "team", "team_page": true, "title": "simplebooks.team.title" }
  ]
}
```

Hand the book out with `first_join_book` (below), a `/give` (see [Optional integration](#optional-integration)) or a `simplebooks:guide` recipe. Any text field can be a lang key or literal text.

### Chapters

`chapters/stone_age.json`:

```json
{ "title": "Stone Age", "icon": "minecraft:stone_pickaxe", "sort": 1, "layout": "tree", "depends": ["basics/look"], "visibility": "locked", "text": "Get off the ground." }
```

| Field | Use |
| --- | --- |
| `title`, `icon`, `text` | heading, tab icon and intro line |
| `sort` | order in the contents and default book |
| `layout` | `checklist` (default): a list with tick boxes. `tree`: a skill tree, lines follow `depends`. `story`: one spread per quest in `sort` order, each unlocked by the one before. `board`: a bounty board, see below |
| `depends` | chapter or quest refs; until they are done the chapter is locked |
| `visibility` | `locked` (default, shown greyed out with what it needs), `visible`, or `hidden` (no tab or contents entry until unlocked) |
| `pool` | board chapters only: the pool to show. Default is the pool with the chapter's file name |
| `background` | optional backdrop behind the chapter's pages: a texture id (`mypack:textures/gui/bg.png`), or `{ "texture": "...", "mode": "stretch"|"tile", "tile": 64 }`. `stretch` (default) fills the page; `tile` repeats using `tile` as the width in spread units (default 64) |
| `decorations` | tree chapters only: free-placed images and text that scroll with the skill tree, see below |
| `lines` | tree chapters only: how dependency arrows are drawn. `elbow` (default) is the stepped path; `diagonal` is a straight line from node edge to node edge with the arrowhead along it |
| `requires`, `disabled` | see [Requires and overrides](#requires-and-overrides) |

Tree decorations (each entry is an image or a text, not both):

```json
"decorations": [
  { "image": "mypack:textures/gui/banner.png", "x": 20, "y": -36, "width": 192, "height": 64, "layer": "behind" },
  { "text": "Craft and build", "x": 116, "y": -20, "scale": 1.2, "colour": "#faeed2", "centred": true, "layer": "front" }
]
```

`x`/`y`/`width`/`height` are in the tree's own space (cell `(0,0)` is at `0,0`, same units as quest `pos`), so they move when the tree is panned. `layer` is `behind` (default, under the link lines) or `front` (over the lines, under the quest nodes). Text also takes `scale`, `colour` (or `color`) and `centred`.

A quest may set its own `background` the same way; that overrides the chapter for that quest's pages (and for a story page). You can also put `background` inside the quest's `page` object. A quest may also set `lines` (`elbow` or `diagonal`) to override the chapter for every link drawn into that quest.

### Quests

`quests/stone_age/iron.json`, with most fields:

```json
{
  "chapter": "stone_age",
  "sort": 4,
  "pos": [3, 1],
  "title": "Iron Age",
  "subtitle": "Better tools",
  "icon": "minecraft:iron_pickaxe",
  "text": "Smelt some iron and make a pickaxe.",
  "depends": ["furnace", "stone_tools"],
  "depends_mode": "all",
  "visibility": "locked",
  "tasks": [
    { "id": "smelt", "type": "smelt", "item": "minecraft:iron_ingot", "count": 8 },
    { "id": "pick", "type": "craft", "item": "minecraft:iron_pickaxe" }
  ],
  "task_mode": "all",
  "rewards": [{ "item": "minecraft:torch", "count": 16 }, { "xp": 50 }],
  "choose_one": [{ "item": "minecraft:iron_sword" }, { "item": "minecraft:shield" }],
  "toast": "complete"
}
```

| Field | Use |
| --- | --- |
| `chapter` | the chapter it is in. Required unless `pool` is set |
| `pool` | a bounty pool instead of a chapter; the quest is then only offered on the board |
| `sort` | order in checklists; story chapters need a different `sort` on every quest |
| `pos` | `[column, row]` in a tree. Without it, tree nodes are placed by depth |
| `title`, `subtitle`, `icon`, `text` | shown in lists and on the quest's spread |
| `page` | normal page content drawn on the quest spread, for example `{ "steps": [ { "kind": "craft_shaped", ... } ] }` with any [step kind](#pages). Shown once the quest is unlocked |
| `depends`, `depends_mode` | quest refs that must be done first; `all` (default) or `any` |
| `visibility` | `locked` (default, greyed out with what it needs), `visible`, `hidden` (not shown until unlocked) or `secret` (not shown at all until done, then a "Secret found" toast) |
| `optional` | `true` for a side quest: dashed border, not counted in the chapter total |
| `tasks`, `task_mode` | what to do; `all` (default) or `any` task finishes the quest |
| `rewards`, `choose_one` | given on claim; the player picks one entry of `choose_one` |
| `repeat` | `{ "cooldown": "20m", "limit": 0 }`, see [Repeatable quests](#repeatable-quests) |
| `tiers` | milestones, see [Milestones](#milestones) |
| `exclusive` | `{ "group": "starter_path", "lock_on": "choose" }`, see [Branching paths](#branching-paths) |
| `toast` | `complete` (default), `unlock`, `both` or `none` |
| `background` | optional; same shape as the chapter field, overrides it for this quest's pages |
| `lines` | optional; `elbow` or `diagonal`. Overrides the chapter for links into this quest |
| `requires`, `disabled` | see [Requires and overrides](#requires-and-overrides) |

Quest states, per team: hidden, locked, available, in progress, complete, and closed for a rival path that was not taken. On top of complete each player has their own claimable or claimed.

### Tasks

Every task can have `id` (progress is saved by id, so set one; the default is the list index), `type`, `count` (default 1, counting types only), `text` (replaces the line shown in the book) and `requires`. Item references are an id, a tag (`#minecraft:logs`) or a list in `items`. All counts are 64-bit, so a task for a million items works.

| Type | Fields | Done when |
| --- | --- | --- |
| `item` | `item`/`items`, `count`, `match`, `consume`, `hand_in`, `team_total` | the team holds the items (or hands them in, with `consume`) |
| `craft` | `item`/`items`, `count`, `match`, `from_stats` | crafted after the quest opened |
| `smelt` | `item`/`items`, `count`, `match`, `from_stats` | taken out of a furnace, smoker or blast furnace |
| `pickup` | `item`/`items`, `count`, `match`, `from_stats` | picked up |
| `use` | `item`/`items`, `count`, `match`, `from_stats` | used: right click, eat, drink, throw (vanilla also counts placing a block item, use `place` for blocks) |
| `place`, `break` | `block` (id or tag), `count`, `from_stats` | blocks placed or mined |
| `kill` | `entity` (id or tag), `count`, `from_stats` | mobs killed by any member |
| `stat` | `stat`, `value`, `count`, `from_stats` | a vanilla or modded stat, for example `"stat": "minecraft:custom", "value": "minecraft:jump"` |
| `advancement` | `advancement` | any member has the advancement |
| `dimension` | `dimension` | a member stands in the dimension |
| `biome` | `biome` (id or tag) | a member stands in the biome |
| `checkbox` | `text` (needed) | a member ticks it in the book |
| `<modid>:<name>` | the mod's own fields | added by a mod, see [Custom task types](#custom-task-types) |

- Counting tasks count from when the quest becomes available, added up over the team. `"from_stats": true` uses the player's vanilla stats instead, so things done earlier count too (not allowed in repeatable quests).
- `item` tasks add up what every online member holds in their main inventory and offhand. `"team_total": false` makes one member hold the full count.
- `"consume": true` turns an item task into a hand-in: the book shows a Hand in button that takes what the task still needs, and every member's hand-ins add up. `hand_in` picks how: `both` (default) for the button and the hand-in box, `hand` for the button only, `box` for the box only. Handed-in items are gone for good.
- `match` checks item data: `components` on 1.21.1 and later, `nbt` on 1.20.1.

### Rewards

Each entry in `rewards` or `choose_one` is one of:

| Entry | Gives |
| --- | --- |
| `{ "item": "minecraft:iron_ingot", "count": 8, "components": { ... } }` | items (no tags; `nbt` instead of `components` on 1.20.1). A full inventory drops them at the player's feet |
| `{ "xp": 100 }` | experience points |
| `{ "xp_levels": 5 }` | experience levels |
| `{ "command": "effect give @s minecraft:speed 60 1", "label": "Speed for a minute", "icon": "minecraft:sugar" }` | runs a command as the claiming player (`@s`), output hidden, at `command_reward_permission` |
| `{ "loot_table": "mypack:rewards/stone_age" }` | one roll of the loot table |
| `{ "type": "mymod:repair_area", "area": "spawn", "label": "Repair the crater", "icon": "mymod:repair_kit" }` | a custom reward from another mod, see [Custom reward types](#custom-reward-types). `label` and `icon` are what the book shows when that mod is missing on a client |

Every player claims each finished quest once, even after changing teams, and a late joiner can claim what the team already did. The book, tabs and the guide tooltip show rewards waiting.

### Repeatable quests

`"repeat": { "cooldown": "20m", "limit": 3 }` opens the quest again once the cooldown has passed, counted from when it was finished. Cooldowns are real time on the server clock (`90s`, `20m`, `6h`, `1h30m`), so sleeping or `/time set` does nothing, and the timer keeps running while the server is off. `limit` is how many times the team can do it; 0 or left out means no limit. A player who missed several cycles still gets one claim.

Item tasks in a repeatable quest need `"consume": true`, otherwise items still held would finish every cycle at once. Advancement tasks and `from_stats` do not work in repeatable quests for the same reason.

### Milestones

One counting task and a list of tiers:

```json
{
  "chapter": "camp", "title": "Stone breaker",
  "tasks": [{ "id": "b", "type": "break", "block": "minecraft:stone" }],
  "tiers": [
    { "count": 10, "title": "Bronze", "rewards": [{ "item": "minecraft:copper_ingot", "count": 4 }] },
    { "count": 50, "title": "Silver", "rewards": [{ "item": "minecraft:iron_ingot", "count": 4 }] },
    { "count": 100, "title": "Gold", "rewards": [{ "item": "minecraft:gold_ingot", "count": 4 }] }
  ]
}
```

Tier counts must go up, and the task's own `count` is ignored. Each tier is claimed on its own; quest `rewards` come with the last tier. The quest counts as done for `depends` at the first tier, and `"camp/miner#2"` waits for tier 2.

### Branching paths

Give each rival quest the same `"exclusive": { "group": "starter_path", "lock_on": "choose" }`. With `choose`, the team owner takes a path with a button and a confirm step, and the rivals close for the whole team. With `complete`, the first rival finished closes the rest and anyone can do it. A group needs at least two quests. A quest after the split can depend on all rivals with `"depends_mode": "any"`. Ops undo a choice with `/sbquests path reset <team> <group>`.

### Bounty board

A pool file, `pools/bounties.json`:

```json
{
  "offers": 3,
  "refresh": { "cooldown": "1h" },
  "reroll": { "allowed": true, "cost": [{ "item": "minecraft:emerald", "count": 2 }] },
  "quests": [
    { "quest": "bounties/rats", "weight": 3 },
    { "quest": "bounties/dragon", "weight": 1, "depends": ["paths/veteran"] }
  ]
}
```

Each pool quest has `"pool": "bounties"` instead of `chapter`, and is shown on a chapter with `"layout": "board"` (chapter file `bounties.json`, or any name with `"pool": "bounties"`). Each team gets its own random offers, picked by `weight` and saved with the world. A finished offer stays until its refresh cooldown has passed, then a new one takes its place. Any member can reroll the unfinished offers by paying the cost (items or `xp_levels`). An entry's `depends` keeps it off the board until the team is ready. Pool quests cannot use `repeat` or `exclusive`, because the board brings them back.

### Hand-in box

The block `simplebooks:hand_in_box` (crafted from iron, a book, a hopper and a chest) takes items from hoppers, droppers, pipes and other mods' item transfer, and hands them in to the owning team's open `consume` tasks that allow the box (`hand_in` `both` or `box`). It only accepts what a task still needs and refuses anything else, so items stay in the hopper. It stores nothing: accepted items are counted and voided. The box belongs to the placer's team; in its screen the team can aim it at one quest. A label above it shows the progress, and a comparator reads how full the target task is. `"hand_in": "box"` makes a task that can only be filled by automation, good for 100,000 iron.

### Teams

Every player is in exactly one team; a new player is a team of one. Team members share quest progress. Players manage teams in the book's team spread or with `/sbteam`:

- `/sbteam create <name>`, `/sbteam invite <player>` (owner), `/sbteam join <name> [confirm]` (needs an invite; `confirm` is asked for when joining would replace your own progress with the team's)
- `/sbteam leave`, `/sbteam kick <player>` (owner), `/sbteam owner <player>`, `/sbteam list`, `/sbteam info [team]`

A player who leaves or is kicked becomes a team named after them and keeps a copy of everything the team had done. Their claims stay with them, so they can never claim the same reward twice. If the owner leaves, the member who joined first becomes owner.

### Server options

`config/simplebooks.json`, written with defaults on first start and read again with `/sbquests reload-config`:

```json
{ "team_source": "simplebooks", "first_join_book": "", "hand_in_box": true, "command_reward_permission": 2 }
```

| Key | Use |
| --- | --- |
| `team_source` | `simplebooks` (default): the teams above. `scoreboard`: follow vanilla `/team` teams; `/sbteam create`, `invite`, `join`, `leave` and `kick` are then turned off. A mod can add more sources, see [Team sources](#team-sources). An unknown source falls back to `simplebooks` with a warning |
| `first_join_book` | a book id such as `mypack:journal`. Every player gets it once on their first join with this set. Empty turns it off |
| `hand_in_box` | `false` makes hand-in boxes accept nothing |
| `command_reward_permission` | permission level for `command` rewards (default 2) |

### Requires and overrides

`requires` (a list of mod ids) works on chapters, quests, tasks and reward entries: the thing is skipped unless every mod is loaded. A quest that depends on a skipped quest treats it as done, so an optional mod never blocks a quest line. A task of a custom type from a mod that is not installed skips its quest quietly. A data pack with the same file path replaces a quest; `{ "disabled": true }` removes it. Other mods can add quests to your book by shipping files in its folder.

### Validating and admin commands

Quest files are checked on every load and `/reload`; problems are logged with the file and the JSON field. A broken quest is skipped and the rest of the book keeps working. Run `/sbquests validate [book]` after every change: it checks the files as they are now, prints the counts of errors and warnings, and lists each problem.

Admin commands (permission level 2). A player name means that player's team.

- `/sbquests validate [book]`
- `/sbquests complete <player|team> <quest>` and `/sbquests uncomplete <player|team> <quest>`
- `/sbquests reset <player|team> [book|chapter|quest]`
- `/sbquests claims reset <player> [quest]`
- `/sbquests progress <player>`: every open quest and its counts
- `/sbquests path reset <team> <group>`, `/sbquests pool reroll <team> <pool>`
- `/sbquests team owner <team> <player>`
- `/sbquests open <player> <book> [spread]`
- `/sbquests prune`: drops saved progress of quests that no longer exist (it is kept otherwise, so removing a mod for a while loses nothing)
- `/sbquests reload-config`

### Common mistakes

- Leaving out task `id`s: progress is saved by id, so reordering tasks without ids resets their counts.
- An `item` task without `consume` in a repeatable quest: it finishes again as soon as it reopens.
- A `checkbox` without `text`, or a quest with no tasks (it is done as soon as it unlocks).
- Depending on an `optional` quest. Side quests should be dead ends.
- A story chapter where two quests share a `sort`, or one has none.
- Two tree quests on the same `pos`.
- Setting `count` on a milestone task (the tiers set the counts), or tier counts that do not go up.
- `hand_in` without `consume`, or item fields on a task that is not item based.
- An `exclusive` group with one quest, or `depends` on two rivals of one group with `depends_mode` `all` (it can never unlock).
- A pool quest that its pool does not list, or a board chapter that also has normal quests.
- Tags in item rewards (rewards need one item id), or `nbt` on 1.21.1 and later (use `components`).
- A custom reward `type` with no mod registered for it, or both a built-in key (`item`, `xp`, ...) and a `type` on the same entry.
- Tree `decorations` or `lines` on a checklist, story or board chapter (they are ignored there).
- `cooldown` text in days: only `s`, `m` and `h` are read.
- Expecting offline members' items to count towards a team item task. Only online members count.

### Custom task types

Mods can add task types with their own JSON fields. The API is in `com.workshopcraft.simplebooks.api` and uses plain string ids on every version. Register on both sides, so the book can show the task:

```java
public final class SimpleBooksCompat implements SimpleBooksPlugin {
    @Override
    public void register() {
        SimpleBooksApi.registerTaskType("mymod:power_produced", new TaskType() {
            @Override
            public TaskSpec parse(JsonObject json, TaskProblems problems) {
                TaskSpec spec = TaskSpec.of(json);
                if (spec.count() < 1) problems.error("count", "must be at least 1");
                return spec;
            }

            @Override
            public Display display(TaskSpec spec) {
                return new Display("mymod:generator", "Produce " + spec.count() + " FE");
            }
        });
    }

    // called by the mod's generator code, on the server thread
    public static void produced(ServerPlayer owner, BlockState state, long fe) {
        SimpleBooksApi.progress(owner, "mymod:power_produced", fe, spec -> spec.matchesBlock("machine", state));
    }
}
```

The quest then uses `{ "id": "p", "type": "mymod:power_produced", "machine": "#mymod:generators", "count": 500000 }`. For a state rather than an event ("own 5 golems"), override `poll(ServerPlayer, TaskSpec)` instead: it is called once a second while the task is open, added up over online members. `progressTeam(server, uuid, type, amount, filter)` credits whatever team a player is in, online or not, for machines that run while nobody is near.

Keep Simple Books optional: compile against it `compileOnly` (`modCompileOnly` with Loom on 1.20.1 and 1.21.1 Fabric), keep every call in one class like the one above, and let Simple Books load that class only when it is installed:

- Fabric: `"entrypoints": { "simplebooks": ["com.example.mymod.SimpleBooksCompat"] }` in `fabric.mod.json`.
- Forge and NeoForge: in `InterModEnqueueEvent`, `InterModComms.sendTo("simplebooks", "plugin", () -> "com.example.mymod.SimpleBooksCompat")`.

Calls from your own code (such as `produced` above) must check that Simple Books is loaded first. With no code at all, a mod can register a custom stat or fire an advancement, and packs use the `stat` or `advancement` task.

### Custom reward types

Mods can add reward types the same way. Register on both sides so the book can show them:

```java
public final class SimpleBooksCompat implements SimpleBooksPlugin {
    @Override
    public void register() {
        SimpleBooksApi.registerRewardType("mymod:repair_area", new RewardType() {
            @Override
            public RewardSpec parse(JsonObject json, RewardProblems problems) {
                RewardSpec spec = RewardSpec.of(json);
                if (spec.string("area", "").isBlank()) problems.error("area", "missing");
                return spec;
            }

            @Override
            public Display display(RewardSpec spec) {
                return new Display("mymod:repair_kit", "Repair " + spec.string("area", "area"));
            }

            @Override
            public void grant(RewardContext ctx, RewardSpec spec) {
                MyMod.repair(ctx.teamId(), spec.string("area", ""));
            }
        });
    }
}
```

The quest then uses `{ "type": "mymod:repair_area", "area": "spawn", "label": "Repair the crater", "icon": "mymod:repair_kit" }`. `grant` runs on the server when that player claims, with the claiming player and their team id and name in `RewardContext`. Put a `label` and `icon` on the entry so clients without your mod still show something sensible; an unknown type fails `/sbquests validate` on the server. Use the same Fabric entrypoint or Forge/NeoForge IMC plugin as for task types.

### Team sources

A party or teams mod can provide the teams with `api.TeamSource`, registered from the same plugin:

```java
SimpleBooksApi.registerTeamSource("myparties", player -> Parties.partyIdOf(player));
```

`teamOf(player)` returns a team id or null for no team, and the optional `name(team)` gives the name shown in the book. Packs pick it with `"team_source": "myparties"`. Simple Books asks once a second per online player, so no membership callbacks are needed; joining and leaving follow the same rules as its own teams.

## Commands (client side)

- `/simplebooks open <book> [page]`
- `/simplebooks validate <book>`
- `/simplebooks list`

## Development

`dev/example-pack` is an example resource pack with a deliberately broken book (`simplebooks:broken_test`) and the blueprint sample (`simplebooks:blueprints`). Its `config/` folder is the example quest pack (`example:guide`), laid out as a pack ships it; copy it into a run folder's `config/` to try it. `dev/quest-fixtures/config/simplebooks` holds the quest test books (`cfgtest:journal` with deliberately broken files and `sbdemo:guide`), copied the same way. The More Foods cookbook now ships inside More Foods; most of its pages are made by `dev/cookbook_gen.py` from the More Foods recipe files and written straight into that repo (`python3 dev/cookbook_gen.py <more foods repo> <vanilla en_us.json>`); `pizza.json` and `pizza_2.json` are written by hand. `runClient` copies it into the run folder and enables it; it is never packed into the jar. If `dev/morefood-fabric.jar` or `dev/morefood-neoforge.jar` exist (More Foods 2.0 alpha jars, not in git), they are put on the dev client's classpath so the cookbook shows real items.

The build uses Stonecutter: one source tree in `src/main`, one project per target under `versions/` (for example `1.20.1-forge`, `26.1.2-fabric`). `./gradlew build` builds all of them; `./gradlew :1.21.1-neoforge:runClient` runs one. Files that only differ by format on old versions (recipe, loot table, item model, pack.mcmeta) live in `src/legacy/<version>` and win over `src/main/resources`. Add `-PquickPlay=localhost:25565` to `runClient` to join a server straight away as `Dev`.

Not yet done: grant events, Python preview renderer (it does not draw quest spreads either).
