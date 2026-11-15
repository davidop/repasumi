#!/usr/bin/env python3
"""Prueba el sanitizador Java sin requerir el SDK de Android."""
from pathlib import Path
import subprocess
import tempfile

with tempfile.TemporaryDirectory() as temp_dir:
    subprocess.run([
        'javac', '-d', temp_dir,
        'app/src/main/java/com/davidop/estudiaenelbus/PromptSafety.java',
        'tools/tests/PromptSafetyTest.java',
    ], check=True)
    subprocess.run([
        'java', '-cp', temp_dir, 'com.davidop.estudiaenelbus.PromptSafetyTest',
    ], check=True)

print('Prueba Java de seguridad del prompt superada.')
