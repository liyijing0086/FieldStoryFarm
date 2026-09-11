import glob, os, sys

files = glob.glob('*.md') + glob.glob('模块规范/*.md') + glob.glob('docs/*.md') + glob.glob('*.txt') + glob.glob('模块规范/*.txt')
for f in files:
    d = open(f, 'rb').read()
    enc = None
    for e in ['utf-8', 'gbk', 'utf-16']:
        try:
            d.decode(e)
            enc = e
            break
        except Exception:
            pass
    print(f, '->', enc, len(d))
