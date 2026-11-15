#!/usr/bin/env python3
"""Comprobaciones estáticas; no sustituyen las pruebas en Android real."""
from pathlib import Path
import subprocess
import tempfile

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
with tempfile.TemporaryDirectory() as temp_dir:
    script_path = Path(temp_dir) / 'repasumi-script-check.js'
    script_path.write_text(js, encoding='utf-8')
    subprocess.run(['node', '--check', str(script_path)], check=True)

subprocess.run(['node', '--check', 'web/local-ai.js'], check=True)
subprocess.run(['node', '--test', 'tools/tests/prompt-safety.test.mjs'], check=True)

native_ai = Path('app/src/main/java/com/davidop/estudiaenelbus/NativeGguf.java').read_text(encoding='utf-8')
web_ai = Path('web/local-ai.js').read_text(encoding='utf-8')
assert native_ai.count('PromptSafety.escapeUntrustedText(') >= 6
assert web_ai.count('sanitizePromptText(') >= 6

workflow = Path('.github/workflows/android-debug.yml').read_text(encoding='utf-8')
assert 'permissions:\n  contents: read' in workflow
assert '  publish-preview:\n' in workflow
assert '    permissions:\n      contents: write' in workflow
build_job = workflow.split('jobs:\n', 1)[1].split('  publish-preview:\n', 1)[0]
assert 'contents: write' not in build_job

print('Fuentes, pruebas de seguridad y permisos de CI: comprobaciones superadas.')
