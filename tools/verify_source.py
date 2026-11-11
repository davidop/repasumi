#!/usr/bin/env python3
"""Comprobaciones estáticas; no sustituyen las pruebas en Android real."""
from pathlib import Path
import subprocess

html = Path('app/src/main/assets/index.html').read_text(encoding='utf-8')
web = Path('web/index.html').read_text(encoding='utf-8')
assert html == web, 'Los HTML de Android y Web no coinciden.'
assert 'Relieve y ríos de España (ejemplo)' in html
assert '<title>Repasumi</title>' in html
assert 'Del libro al reto' in html
assert 'copy-report' in html and 'data-action="flag"' in html
assert all(f"type:'{kind}'" in html for kind in ('mc','tf','fill','match','order'))
assert '<script>' in html and '</script>' in html
js = html.split('<script>', 1)[1].split('</script>', 1)[0]
Path('/tmp/repasumi-script-check.js').write_text(js, encoding='utf-8')
subprocess.run(['node', '--check', '/tmp/repasumi-script-check.js'], check=True)
print('HTML y JavaScript: comprobaciones estáticas superadas.')
