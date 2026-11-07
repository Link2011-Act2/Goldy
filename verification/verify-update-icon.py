"""Compare actual vector geometry, opaque masks, and optional public AAR resource content."""
import argparse, hashlib, json, re, zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--reference', action='append', type=Path, required=True)
p.add_argument('--drawable', type=Path, default=Path('elief/src/main/res/drawable/elief_update.xml'))
p.add_argument('--aar', type=Path)
p.add_argument('--output', type=Path, required=True)
args = p.parse_args()
ns = '{http://schemas.android.com/apk/res/android}'
def geometry(data):
    root = ET.fromstring(data)
    assert root.tag == 'vector' and root.get(ns+'tint') is None
    def node(element):
        attributes = dict(element.attrib)
        for key in ['fillColor', 'strokeColor']:
            if ns+key in attributes:
                assert attributes.pop(ns+key).upper().startswith('#FF'), 'Mask must remain opaque'
        if ns+'pathData' in attributes:
            attributes[ns+'pathData'] = re.sub(r'\s+', '', attributes[ns+'pathData'])
        return {'kind': element.tag, 'geometry': attributes, 'children': [node(x) for x in element]}
    return node(root)
actual = geometry(args.drawable.read_bytes())
references = []
for ref in args.reference:
    assert actual == geometry(ref.read_bytes()), f'Different vector geometry: {ref}'
    references.append(str(ref))
public = None
if args.aar:
    with zipfile.ZipFile(args.aar) as z:
        data = z.read('res/drawable/elief_update.xml')
        assert geometry(data) == actual
        assert geometry(z.read('res/drawable/elief_refresh.xml')) == geometry(Path('elief/src/main/res/drawable/elief_refresh.xml').read_bytes())
        with zipfile.ZipFile(__import__('io').BytesIO(z.read('classes.jar'))) as classes:
            assert 'META-INF/elief/MaterialSymbols-APACHE-2.0.txt' in classes.namelist()
        public = hashlib.sha256(args.aar.read_bytes()).hexdigest()
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps({'passed': True, 'references': references, 'sameGeometry': actual,
    'opaqueMaskWithInheritedComposeTint': True, 'aarSha256': public}, indent=2)+'\n')
print('browser_updated geometry matches all references; no fixed drawable tint')
