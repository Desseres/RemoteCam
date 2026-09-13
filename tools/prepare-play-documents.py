"""Regenerate the hosted privacy document and validate Play listing text lengths."""
from pathlib import Path
from html import escape

root = Path(__file__).resolve().parents[1]
store = root / 'store/google-play'
policy = (root / 'app/src/main/resources/privacy-policy.txt').read_text(encoding='utf-8')
sections = []
for part in policy.split('\n\n')[1:]:
    heading, _, content = part.partition('\n')
    sections.append(f'<section><h2>{escape(heading)}</h2><p>{escape(content)}</p></section>')
page = '''<!doctype html>
<html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>RemoteCam — Privacy policy</title>
<style>
body{margin:0;background:#171819;color:#ecece9;font:18px/1.7 system-ui,sans-serif}
main{max-width:780px;margin:auto;padding:64px 24px}h1{font-size:42px;line-height:1.15}
h2{font-size:23px;color:#ffe15a;margin-top:40px}p{white-space:pre-line}a{color:#ffe15a}
</style><main><h1>RemoteCam</h1><h2>Privacy policy</h2>
<p>Publisher: Desseres<br>Contact: <a href="mailto:apps@kasztelan.me">apps@kasztelan.me</a>
<br>Effective date: 11 September 2026</p>'''
page += '\n'.join(sections)
page += '<p><a href="https://github.com/Desseres/RemoteCam">Project source code</a></p></main></html>\n'
(store / 'privacy-policy.html').write_text(page, encoding='utf-8')
listing = (store / 'listing-pl.txt').read_text(encoding='utf-8')
title, remainder = listing.removeprefix('TYTUŁ\n').split('\n\nKRÓTKI OPIS\n')
short, full = remainder.split('\n\nPEŁNY OPIS\n')
for name, content, limit in [('title', title, 30), ('short', short, 80), ('full', full, 4000)]:
    assert len(content.strip()) <= limit, (name, len(content), limit)
    print(f'{name}: {len(content.strip())}/{limit}')
