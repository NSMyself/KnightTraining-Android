"""Builds Android values-xx/strings.xml from the iOS catalogs (exact English match) plus hand drafts.

usage: build.py IOS_APP IOS_CORE ANDROID_RES DRAFTS_JSON [--missing OUT_JSON]
"""
import json, re, sys, os, xml.etree.ElementTree as ET

LANGS = {'pt-PT': 'pt-rPT', 'es': 'es', 'pt-BR': 'pt-rBR', 'nl': 'nl', 'it': 'it', 'de': 'de', 'fr': 'fr'}
ios_app, ios_core, res, drafts_path = sys.argv[1:5]
missing_out = sys.argv[6] if len(sys.argv) > 6 and sys.argv[5] == '--missing' else None

SPEC = re.compile(r'%(?:(\d+)\$)?(lld|ld|d|@|s|\.\d+f|f)')

def norm(t):
    t = t.replace('\u2019', "'").replace("\\'", "'").replace('\\"', '"').replace('\\n', '\n')
    t = t.replace('%%', '\0')
    t = SPEC.sub('%', t)
    return t.strip()

def android_text(el):
    return ''.join(el.itertext())

def placeholders(text):
    """Android placeholders by position: {1: '%1$d', ...}, and whether they were positional."""
    out = {}; seq = 0
    for m in SPEC.finditer(text.replace('%%', '\0\0')):
        seq += 1
        pos = int(m.group(1)) if m.group(1) else seq
        kind = 'd' if m.group(2) in ('d', 'ld', 'lld') else 's'
        out[pos] = kind
    return out

def convert(translation, android_en):
    """iOS translation -> Android string with the English string's placeholder types, positional when >1."""
    targets = placeholders(android_en)
    seq = 0
    def repl(m):
        nonlocal seq
        seq += 1
        pos = int(m.group(1)) if m.group(1) else seq
        kind = targets.get(pos, 'd' if m.group(2) in ('d', 'ld', 'lld') else 's')
        return f'%{pos}${kind}' if len(targets) > 1 else f'%{kind}'
    t = translation.replace('%%', '\0')
    t = SPEC.sub(repl, t)
    return t.replace('\0', '%%')

def escape(t):
    t = t.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')
    if t.startswith('@') or t.startswith('?'):
        t = '\\' + t
    return t

catalog = {}
for path in (ios_app, ios_core):
    for key, e in json.load(open(path))['strings'].items():
        locs = e.get('localizations', {})
        src = locs.get('en', {}).get('stringUnit', {}).get('value', key)
        trans = {l: locs[l]['stringUnit']['value'] for l in LANGS if 'stringUnit' in locs.get(l, {})}
        if trans:
            catalog.setdefault(norm(src), trans)

drafts = json.load(open(drafts_path)) if os.path.exists(drafts_path) else {}
root = ET.parse(os.path.join(res, 'values', 'strings.xml')).getroot()
missing = {}
for lang, folder in LANGS.items():
    out = ET.Element('resources', {'xmlns:tools': 'http://schemas.android.com/tools'})
    for el in root:
        name = el.get('name')
        if el.get('translatable') == 'false':
            continue
        draft = drafts.get(name, {}).get(lang)
        if el.tag == 'string':
            en = android_text(el)
            if draft is not None:
                value = draft
            elif lang in catalog.get(norm(en), {}):
                value = convert(catalog[norm(en)][lang], en)
            else:
                missing.setdefault(name, {'en': en})
                continue
            child = ET.SubElement(out, 'string', name=name)
            child.text = escape(value)
        elif el.tag == 'plurals':
            if draft is None:
                missing.setdefault(name, {'en': {i.get('quantity'): android_text(i) for i in el}})
                continue
            child = ET.SubElement(out, 'plurals', name=name)
            forms = dict(draft)
            # A 'one' with no number reads wrong for 0 where 'one' covers 0 (fr, pt). These plurals
            # are never shown for 0 (a detected batch, a completed week, always one or more).
            if '%' not in forms.get('one', '%'):
                child.set('tools:ignore', 'ImpliedQuantity')
            # CLDR's 'many' (huge round numbers) reads like 'other' in these languages; lint requires it.
            if lang in ('es', 'fr', 'it', 'pt-PT', 'pt-BR'):
                forms.setdefault('many', forms['other'])
            for quantity, text in forms.items():
                item = ET.SubElement(child, 'item', quantity=quantity)
                item.text = escape(text)
    ET.indent(out, space='    ')
    folder_path = os.path.join(res, f'values-{folder}')
    os.makedirs(folder_path, exist_ok=True)
    with open(os.path.join(folder_path, 'strings.xml'), 'w') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from the iOS catalogs plus drafts; see AGENTS.md (Localization). -->\n')
        f.write(ET.tostring(out, encoding='unicode'))
        f.write('\n')
if missing_out:
    json.dump(missing, open(missing_out, 'w'), indent=1, ensure_ascii=False)
print(f'{len(missing)} names still missing in at least one language')
