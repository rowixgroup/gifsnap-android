#!/usr/bin/env python3
"""Create SDK-only distribution artifacts from an already built local Maven repository."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

root = Path(__file__).resolve().parents[1]
repo = root / 'build/repository'
dist = root / 'dist'
required = [repo / f'com/rowix/gifsnap/gifsnap-{name}/0.1.0/gifsnap-{name}-0.1.0.aar' for name in ('client', 'compose')]
if not all(p.is_file() for p in required):
    raise SystemExit('Build both Maven publications before packaging.')
dist.mkdir(exist_ok=True)

def digest(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def zip_files(target, pairs):
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
        for file, name in sorted(pairs, key=lambda pair: pair[1]):
            info = zipfile.ZipInfo(name, (2026, 9, 30, 0, 0, 0))
            info.external_attr = (0o100755 if file.name == 'gradlew' else 0o100644) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, file.read_bytes())

repository_files = [(p, 'repository/' + p.relative_to(repo).as_posix()) for p in repo.rglob('*') if p.is_file()]
zip_files(dist / 'gifsnap-android-maven-0.1.0.zip', repository_files)
for file in required:
    shutil.copy2(file, dist / file.name)
excluded = {'build', '.gradle', '.kotlin', '.idea', 'dist', 'evidence', '.git', '__pycache__'}
pairs = []
for file in root.rglob('*'):
    relative = file.relative_to(root)
    if any(part in excluded for part in relative.parts) or not file.is_file():
        continue
    if file.is_symlink() or file.name in {'local.properties', '.DS_Store'} or file.name.startswith(('.env', '._')) or file.suffix in {'.log', '.keystore', '.jks'}:
        continue
    pairs.append((file, 'gifsnap-android/' + relative.as_posix()))
zip_files(dist / 'gifsnap-android-source-0.1.0.zip', pairs)
manifest = {
    'version': '0.1.0',
    'repositoryRoot': str(repo),
    'hostedRepositoryPath': '/sdk/android/',
    'repository': {p.relative_to(repo).as_posix(): {'sha256': digest(p), 'bytes': p.stat().st_size} for p, _ in repository_files},
    'artifacts': {p.name: {'sha256': digest(p), 'bytes': p.stat().st_size} for p in dist.iterdir() if p.is_file() and p.suffix in {'.zip', '.aar'}},
    'sourceFileCount': len(pairs),
    'source': {name.removeprefix('gifsnap-android/'): {'sha256': digest(file), 'bytes': file.stat().st_size} for file, name in pairs},
}
(dist / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
(dist / 'SOURCE_SHA256SUMS').write_text(''.join(digest(file) + '  ' + name.removeprefix('gifsnap-android/') + '\n' for file, name in sorted(pairs, key=lambda pair: pair[1])))
(dist / 'SHA256SUMS').write_text(''.join(v['sha256'] + '  ' + n + '\n' for n, v in sorted(manifest['artifacts'].items())))
print(json.dumps({'artifacts': manifest['artifacts'], 'repositoryFiles': len(repository_files), 'sourceFiles': len(pairs)}, indent=2))
