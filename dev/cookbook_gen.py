#!/usr/bin/env python3
"""Generates the More Foods cookbook pages in the example pack from the More Foods recipe data."""
import json, os, sys, glob, re

MF = sys.argv[1] if len(sys.argv) > 1 else '/workspace/mods/morefoods-261'
VANILLA_LANG = sys.argv[2] if len(sys.argv) > 2 else '/tmp/vl/assets/minecraft/lang/en_us.json'
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'example-pack/assets/morefood/simplebooks/books/cookbook/pages')
RES = MF + '/common/src/main/resources'
IMG = 'morefood:textures/simplebooks/cookbook/'

lang = json.load(open(VANILLA_LANG))
lang.update(json.load(open(RES + '/assets/morefood/lang/en_us.json')))
tags = {}
for f in glob.glob(RES + '/data/morefood/tags/item/*.json'):
    tags['morefood:' + os.path.basename(f)[:-5]] = json.load(open(f))['values']

def name(i):
    if isinstance(i, list):
        return name(i[0]) + (' or ' + name(i[1]) if len(i) == 2 else ' or other')
    if i.startswith('#'):
        return {'morefood:roast_veg': 'Roast Veg', 'morefood:pizza_toppings': 'Toppings'}.get(i[1:], i[1:].split(':')[1].replace('_', ' ').title())
    ns, p = i.split(':')
    for k in ('item', 'block'):
        if f'{k}.{ns}.{p}' in lang:
            return lang[f'{k}.{ns}.{p}']
    return p.replace('item_', '').replace('_', ' ').title()

TOOLS = ['minecraft:crafting_table', 'morefood:item_pestle_mortar', 'morefood:item_mixing_bowl', 'morefood:item_mincer',
         'morefood:item_juicer', 'morefood:item_knife', 'morefood:item_pot', 'morefood:item_frying_pan',
         'morefood:block_stove', 'minecraft:furnace', 'morefood:block_assembly_board', 'morefood:item_baking_peel']
BUCKETS = {'minecraft:water_bucket', 'minecraft:milk_bucket'}
RANK = {'morefood:assembly': 0, 'morefood:boiling': 1, 'morefood:frying': 1, 'morefood:baking': 1,
        'minecraft:crafting_shapeless': 2, 'minecraft:crafting_shaped': 2, 'minecraft:smelting': 3}

def ing(x):
    """Recipe ingredient to a plain id, '#tag' or list of ids."""
    if isinstance(x, dict):
        x = x.get('item') or ('#' + x['tag'])
    if isinstance(x, list):
        return x[0] if len(x) == 1 else x
    return x

recipes = {}
for f in sorted(glob.glob(RES + '/data/morefood/recipe/**/*.json', recursive=True)):
    r = json.load(open(f))
    out = r['result']['id']
    flat = json.dumps(r)
    if 'item_pot_' in flat or 'frying_pan"' in flat.replace('item_frying_pan"', '') or '_frying_pan' in out:
        continue
    r['_file'] = os.path.relpath(f, RES)
    recipes.setdefault(out, []).append(r)
for k in recipes:
    recipes[k].sort(key=lambda r: RANK.get(r['type'], 9))

def inputs(r):
    t = r['type']
    if t == 'morefood:assembly':
        l = [r['base']] + r.get('fillings', []) + ([r['lid']] if 'lid' in r else [])
    elif t in ('morefood:boiling', 'minecraft:crafting_shapeless'):
        l = r['ingredients']
    elif t == 'minecraft:crafting_shaped':
        cnt = {}
        for row in r['pattern']:
            for c in row:
                if c != ' ':
                    cnt[c] = cnt.get(c, 0) + 1
        l = [r['key'][c] for c in r['pattern'][0] + ''.join(r['pattern'][1:]) if c != ' ']
    else:
        l = [r['ingredient']]
    return [ing(x) for x in l]

def ref(i, **kw):
    if isinstance(i, list):
        d = {'items': i, 'name': name(i)}
    elif i.startswith('#'):
        d = {'item': i, 'name': name(i)}
    else:
        d = {'item': i}
    d.update({k: v for k, v in kw.items() if v is not None})
    return d if len(d) > 1 or 'item' not in d else d['item']

def secs(t):
    return f'{t // 20} seconds'

POT = ',\nor over a\ncampfire, slower'

def row_width(cells):
    w = 0
    for c in cells:
        w += {'+': 28, '>': 34, 'in': 30, 'on': 32}.get(c, 56)
    return w

class Page:
    def __init__(self):
        self.steps, self.raw, self.tools, self.done = [], {}, set(), set()

    def need(self, i, n=1):
        if isinstance(i, str) and i in TOOLS:
            self.tools.add(i)
            return
        k = json.dumps(i)
        self.raw[k] = self.raw.get(k, 0) + n

    def make(self, item, stack=()):
        """Adds the steps that make item; returns False when it is a raw ingredient."""
        if isinstance(item, list) or item.startswith('#') or item in TOOLS or (item.startswith('minecraft:') and stack) or item not in recipes or item in stack:
            return False
        if item in self.done:
            return True
        r = recipes[item][0]
        for i in inputs(r):
            if not self.make(i, stack + (item,)):
                self.need(i)
        self.step(item, r)
        self.done.add(item)
        return True

    def step(self, item, r):
        t = r['type']
        cnt = r['result'].get('count', 1)
        ins = inputs(r)
        out = ref(item, count=cnt if cnt > 1 else None)
        if t == 'minecraft:crafting_shapeless':
            self.tools.add('minecraft:crafting_table')
            tl = [i for i in ins if isinstance(i, str) and i in TOOLS]
            rest = [i for i in ins if i not in tl]
            order = tl + rest
            cells = []
            for i in order:
                cells += ['+', 'x']
            cells = cells[1:] + ['>', 'x']
            s = {'kind': 'craft_shapeless', 'inputs': [ref(i) for i in order], 'output': out}
            if row_width(cells) > 430:
                s['label_width'] = max(140, 644 - row_width(cells) - 4)
            if row_width(cells) > 500:
                s = {'kind': 'sequence', 'title': name(item), 'text_first': True,
                     'text': 'Crafting Table (shapeless): put these in the grid in any order.',
                     'cells': [ref(i) for i in order] + [ref(item, count=cnt if cnt > 1 else None, note='result')]}
            self.steps.append(s)
        elif t == 'minecraft:crafting_shaped':
            self.tools.add('minecraft:crafting_table')
            key = {k: ref(ing(v)) for k, v in r['key'].items()}
            self.steps.append({'kind': 'craft_shaped', 'pattern': r['pattern'], 'key': key, 'output': out})
        elif t == 'morefood:boiling':
            self.tools |= {'morefood:item_pot', 'morefood:block_stove'}
            pot = list(ins)
            if r.get('water', 0) > 0:
                pot = ['minecraft:water_bucket'] + pot
                self.need('minecraft:water_bucket')
            portions = r.get('portions', 1)
            vessel = r.get('vessel')
            note = secs(r['cookingtime']) + POT
            if vessel:
                self.need(vessel)
                note += f'.\nScoop out with\na {name(vessel)}'
            out = ref(item, count=portions if portions > 1 else None)
            cells = []
            for i in pot:
                cells += ['+', 'x']
            cells = cells[1:] + ['in', 'x', 'on', 'x', '>', 'x']
            if row_width(cells) <= 430:
                self.steps.append({'kind': 'process', 'title': name(item), 'note': note, 'inputs': [ref(i) for i in pot],
                                   'appliance': 'morefood:item_pot', 'on': 'morefood:block_stove', 'output': out})
            else:
                txt = (f'Put these in the Pot on the Stove, in any order, {secs(r["cookingtime"])} (or over a campfire, slower).'
                       + (f' Scoop out each portion with a {name(vessel)}.' if vessel else ''))
                self.steps.append({'kind': 'sequence', 'title': name(item) + ' in the Pot', 'text_first': True, 'text': txt,
                                   'cells': [ref(i) for i in pot] + [ref(item, count=portions if portions > 1 else None, note='result')]})
        elif t == 'morefood:frying':
            self.tools |= {'morefood:item_frying_pan', 'morefood:block_stove'}
            self.steps.append({'kind': 'process', 'title': name(item), 'note': secs(r['cookingtime']) + ',\nfrom the stove\noutput slot',
                               'inputs': [ref(ins[0])], 'appliance': 'morefood:item_frying_pan', 'on': 'morefood:block_stove', 'output': out})
        elif t == 'morefood:baking':
            self.tools.add('morefood:block_stove')
            self.steps.append({'kind': 'process', 'title': name(item), 'note': secs(r['cookingtime']) + ',\nin the oven slot,\nor use a Baking Peel',
                               'inputs': [ref(ins[0])], 'appliance': {'item': 'morefood:block_stove', 'name': 'Stove oven'}, 'output': out})
        elif t == 'minecraft:smelting':
            self.tools.add('minecraft:furnace')
            self.steps.append({'kind': 'process', 'title': name(item), 'note': secs(r.get('cookingtime', 200)),
                               'inputs': [ref(ins[0])], 'appliance': 'minecraft:furnace', 'output': out})
        elif t == 'morefood:assembly':
            self.tools.add('morefood:block_assembly_board')
            ords = ['first', 'second', 'third', 'fourth', 'fifth', 'sixth']
            cells = [ref(i, note=ords[n]) for n, i in enumerate(ins)]
            if 'extras' in r:
                ex = ing(r['extras']['ingredient'])
                cells.append(ref(ex, note=f'up to {r["extras"]["max"]}'))
            cells.append(ref(item, note='done'))
            self.steps.append({'kind': 'assembly', 'title': 'Build it on the Assembly Board',
                               'text': 'Use each item on the Assembly Board in this order, then take it off with an empty hand:',
                               'image': IMG + 'board.png', 'cells': cells})

    def need_lists(self):
        ings = []
        for k, n in self.raw.items():
            i = json.loads(k)
            if isinstance(i, str) and i in BUCKETS:
                n = 1 if i == 'minecraft:water_bucket' else n
            ings.append(ref(i, count=n if n > 1 else None))
        tools = [t for t in TOOLS if t in self.tools]
        return ings, tools

def recipe_page(item, section, sort, subtitle=None, extra=None):
    p = Page()
    p.make(item)
    ings, tools = p.need_lists()
    j = {'section': section, 'template': 'simplebooks:recipe', 'sort': sort, 'title': name(item), 'icon': item}
    if subtitle:
        j['subtitle'] = subtitle
    j['ingredients'] = ings
    j['tools'] = tools
    j['need_note'] = 'Tools and Buckets are not consumed'
    j['steps'] = p.steps + (extra or [])
    return j

def last_kind(item):
    return recipes[item][0]['type'] if item in recipes else None

SUB = {'morefood:boiling': 'Cooked in the pot on the stove.', 'morefood:baking': 'Baked in the stove oven.',
       'morefood:frying': 'Fried in the pan on the stove.', 'morefood:assembly': 'Built on the assembly board.',
       'minecraft:crafting_shapeless': 'Put together at the crafting table.', 'minecraft:smelting': 'Cooked in a furnace.'}

pages = {}

# toppings
TOPPINGS = tags['morefood:pizza_toppings']
made_toppings = [t for t in TOPPINGS if t in recipes]
for n, t in enumerate(made_toppings):
    pid = 'topping_' + t.split(':')[1].replace('item_', '')
    pg = recipe_page(t, 'toppings', 100 + n, 'A pizza topping. ' + SUB.get(last_kind(t), ''))
    pg['links'] = [{'label': 'Back to Pizza', 'page': 'pizza'}]
    pages[pid] = pg

CROPS = sorted({inputs(r)[0] for k, rs in recipes.items() if 'item_seed_' in k for r in rs})
crop_page = {c: 'crop_' + c.split(':')[1].replace('item_fruit_', '').replace('item_', '') for c in CROPS}

def topping_page(t):
    if t in made_toppings:
        return 'topping_' + t.split(':')[1].replace('item_', '')
    return crop_page.get(t, 'toppings')

pages['toppings'] = {
    'section': 'toppings', 'template': 'simplebooks:text', 'sort': 99, 'title': 'Toppings', 'icon': 'morefood:item_bacon',
    'subtitle': 'Extras for a pizza, up to 3.',
    'text': 'Any three of these can go on a pizza after the cheese slices. Click one to see how to make it. Mushrooms are picked as they are.',
    'steps': [{'kind': 'links', 'header': 'Toppings:', 'columns': 3,
               'links': [{'item': t, 'page': topping_page(t)} for t in TOPPINGS]}]}

# tools
TOOL_TEXT = {
    'morefood:block_stove': ('The heart of the kitchen.', 'Put fuel in the bottom slot to light it. Use a Pot or Frying Pan on the top to set it on one of the two rings; sneak with an empty hand to take an empty one back. Food for the oven goes in the oven slot, or slide it in with a Baking Peel. Cooked food lands in the output slots.'),
    'morefood:item_pot': ('Boils, stews and simmers.', 'Set it on a Stove ring, or on a lit campfire where it cooks slower. Add a Water Bucket or Water Bottles if the recipe needs water, then use each ingredient on it. When it is done, scoop each portion out with the right vessel, or take it from the stove output. An empty hand takes the last thing back out.'),
    'morefood:item_frying_pan': ('Fries on the stove.', 'Set it on a Stove ring and use the food on it. It sizzles while it cooks and the cooked food goes to the stove output slots.'),
    'morefood:item_knife': ('Chops and slices.', 'Craft it together with the food to chop or slice it. The Knife comes back each time.'),
    'morefood:item_mincer': ('Minces meat and makes sauce.', 'Craft it together with meat to mince it, or with tomatoes or apples to make sauce. The Mincer comes back each time.'),
    'morefood:item_pestle_mortar': ('Grinds wheat into flour.', 'Craft it together with Wheat to make Flour. The Pestle and Mortar comes back each time.'),
    'morefood:item_mixing_bowl': ('Mixes dough and batter.', 'Craft it together with the ingredients to mix doughs, batter and fillings. The Mixing Bowl comes back each time.'),
    'morefood:item_juicer': ('Squeezes juice.', 'Craft it together with the fruit and a Glass Bottle to make juice. The Juicer comes back each time.'),
    'morefood:block_assembly_board': ('Builds pizzas, pies and sandwiches.', 'Place it, ideally next to a Stove. Use each layer on it in order; the board only takes what fits the next layer. Take the finished food with an empty hand. Sneak with an empty hand to take the last layer back off.'),
    'morefood:item_baking_peel': ('Carries food to the oven.', 'Use it on a finished pizza or pie on the Assembly Board to pick it up, then use it on a Stove to slide it into the oven. Sneak and use it to tip the food back into your inventory.'),
}
for n, t in enumerate(TOOL_TEXT):
    sub, txt = TOOL_TEXT[t]
    p = Page()
    if t in recipes:
        p.step(t, recipes[t][0])
    pages['tool_' + t.split(':')[1].replace('item_', '').replace('block_', '')] = {
        'section': 'tools', 'template': 'simplebooks:tool', 'sort': 200 + n, 'title': name(t), 'icon': t,
        'subtitle': sub, 'text': txt, 'steps': [{'kind': 'text', 'text': 'How to make it:'}] + p.steps}

# pages too long for one spread go on over a second one; numbers are how many steps did not fit
OVER = {'pie_chicken_leek': 2, 'pie_cottage': 4, 'pie_shepherds': 4, 'pie_vegetable': 1, 'burger_bacon_cheese': 3,
        'breakfast_full': 5, 'cheese_burger': 1, 'burger_salad': 1, 'wrap_spinach_cheese': 1}
NUMBERED = {'craft_shapeless', 'craft_shaped', 'process', 'assembly', 'sequence'}

def split(pid):
    if pid not in OVER:
        return
    j = pages[pid]
    cut = len(j['steps']) - OVER[pid] - 1
    first, rest = j['steps'][:cut], j['steps'][cut:]
    j['steps'] = first
    j['links'] = [{'label': 'Continued on the next page', 'page': pid + '_2'}]
    pages[pid + '_2'] = {'section': j['section'], 'template': 'simplebooks:steps', 'sort': j['sort'] + 1, 'contents': False,
                         'title': j['title'] + ', continued', 'icon': j['icon'],
                         'first_step': 1 + sum(1 for st in first if st['kind'] in NUMBERED), 'steps': rest}

# recipes
used = {}
for k, rs in recipes.items():
    for i in inputs(rs[0]):
        for x in (i if isinstance(i, list) else [i]):
            used.setdefault(x, set()).add(k)

FINAL = [k for k in recipes if k not in used and k not in TOOL_TEXT and k not in TOOLS and 'item_seed_' not in k
         and k != 'morefood:item_pizza_cooked']
FINAL += [k for k in ('morefood:item_toast', 'morefood:item_bread_slice', 'morefood:item_cheese_slice', 'morefood:item_bacon',
                      'morefood:item_sausage', 'morefood:item_fried_egg', 'morefood:item_chips', 'morefood:item_potato_mashed',
                      'morefood:item_yorkshire_pudding', 'morefood:item_potato_roast', 'morefood:item_gravy', 'minecraft:bread')
          if k in recipes and k not in FINAL]

GROUPS = [('pie', 'pies'), ('pasty', 'pies'), ('soup', 'soups'), ('stew', 'soups'), ('roast', 'roasts'), ('breakfast', 'breakfast'),
          ('egg', 'breakfast'), ('toast', 'breakfast'), ('bacon', 'breakfast'), ('sausage', 'breakfast'), ('hashbrown', 'breakfast'),
          ('sandwich', 'sandwiches'), ('burger', 'sandwiches'), ('wrap', 'sandwiches'), ('footlong', 'sandwiches'),
          ('spaghetti', 'pasta'), ('juice', 'drinks')]
ORDER = ['pies', 'soups', 'roasts', 'breakfast', 'sandwiches', 'pasta', 'other', 'drinks']

def group(k):
    for w, g in GROUPS:
        if w in k:
            return g
    return 'other'

FINAL.sort(key=lambda k: (ORDER.index(group(k)), name(k)))
for n, k in enumerate(FINAL):
    pid = k.split(':')[1].replace('item_', '')
    pages[pid] = recipe_page(k, 'recipes', 1000 + 2 * n, SUB.get(last_kind(k)))
    split(pid)

# crops
for n, c in enumerate(CROPS):
    seed = next(k for k, rs in recipes.items() if 'item_seed_' in k and inputs(rs[0])[0] == c)
    p = Page()
    p.step(seed, recipes[seed][0])
    users = sorted({k for k in FINAL + made_toppings if c in json.dumps(recipe_page(k, 'x', 0)['ingredients'])}, key=name)
    pid_of = {k: (topping_page(k) if k in made_toppings else k.split(':')[1].replace('item_', '')) for k in users}
    steps = p.steps
    if users:
        steps = steps + [{'kind': 'links', 'header': 'Used in:', 'columns': 3, 'links': [{'item': u, 'page': pid_of[u]} for u in users[:18]]}]
    pages[crop_page[c]] = {
        'section': 'crops', 'template': 'simplebooks:crop', 'sort': 300 + n, 'title': name(c), 'icon': c,
        'subtitle': 'A crop. Seeds drop from grass.',
        'text': f'Break grass to find {name(seed)}. Plant them on farmland and harvest when fully grown; Bone Meal speeds it up. One {name(c)} crafts back into seeds.',
        'steps': steps}

keep = {'pizza.json'}
for f in os.listdir(OUT):
    if f not in keep:
        os.remove(os.path.join(OUT, f))
for pid, j in pages.items():
    with open(os.path.join(OUT, pid + '.json'), 'w') as fh:
        json.dump(j, fh, indent=2)
        fh.write('\n')

# pizza links follow the topping pages
pz = os.path.join(OUT, 'pizza.json')
s = json.load(open(pz))
for st in s['steps']:
    if st.get('kind') == 'links':
        for l in st['links']:
            l['page'] = topping_page(l['item'])
with open(pz, 'w') as fh:
    json.dump(s, fh, indent=2)
    fh.write('\n')
print(len(pages) + 1, 'pages:', {sec: sum(1 for j in pages.values() if j['section'] == sec) + (sec == 'recipes') for sec in ('toppings', 'tools', 'crops', 'recipes')})
