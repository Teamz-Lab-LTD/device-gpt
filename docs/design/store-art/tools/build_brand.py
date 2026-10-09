from pathlib import Path
import subprocess, io, base64, sys
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]
NIGHT='#12151A'; BLUE='#2E32D4'; LIME='#D9FE06'; WHITE='#FFFFFF'
SLUGS={1:'pulse-tick',2:'ring',3:'found-it'}
def svg(body,w=1024,h=1024):
    return f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{w}" height="{h}" viewBox="0 0 {w} {h}">{body}</svg>'
def render(source):
    return Image.open(io.BytesIO(subprocess.check_output(['/opt/homebrew/bin/rsvg-convert'],input=source.encode()))).convert('RGBA')
def save(name,source,rgb=True):
    (ROOT/name).with_suffix('.svg').write_text(source)
    im=render(source)
    if rgb: im=im.convert('RGB')
    im.save((ROOT/name).with_suffix('.png'))
    return im
def art(n,accent=LIME):
    # Deliberate negative space at overlaps keeps the artwork legible in one colour too.
    pulse='M232 536 H348 L418 400 L500 622 L788 392'
    phone='<rect x="302" y="212" width="354" height="598" rx="70" fill="none" stroke="white" stroke-width="64"/>'
    if n==1:
        return f'<defs><mask id="pulse-clear"><rect width="1024" height="1024" fill="white"/><path d="{pulse}" fill="none" stroke="black" stroke-width="132" stroke-linecap="round" stroke-linejoin="round"/></mask></defs><g mask="url(#pulse-clear)">{phone}</g><path d="{pulse}" fill="none" stroke="{accent}" stroke-width="94" stroke-linecap="round" stroke-linejoin="round"/>'
    if n==2:
        return f'<path d="M512 248 A264 264 0 1 1 248 512 L298 574 L386 470" fill="none" stroke="{accent}" stroke-width="102" stroke-linecap="round" stroke-linejoin="round"/><path fill="white" fill-rule="evenodd" d="M476 366 H572 Q604 366 604 398 V628 Q604 660 572 660 H476 Q444 660 444 628 V398 Q444 366 476 366 Z M481 602 V618 H567 V602 Z"/>'
    return f'<defs><mask id="lens-clear"><rect width="1024" height="1024" fill="white"/><circle cx="620" cy="398" r="177" fill="black"/><path d="M718 496 L792 580" stroke="black" stroke-width="98" stroke-linecap="round"/></mask></defs><rect x="252" y="218" width="364" height="588" rx="70" fill="none" stroke="white" stroke-width="64" mask="url(#lens-clear)"/><g fill="none" stroke="white" stroke-width="64" stroke-linecap="round"><circle cx="620" cy="398" r="126"/><path d="M716 494 L790 578"/></g><circle cx="620" cy="398" r="58" fill="{accent}"/>'
def icon(n,theme='global',kind=None):
    bg=NIGHT if theme=='global' else BLUE
    a=art(n,'white' if kind=='monochrome' else LIME if theme=='global' else '#00C2CC')
    frame=''
    if theme=='kry':
        frame=''.join(f'<circle cx="512" cy="512" r="{r}" fill="none" stroke="{c}" stroke-width="20"/>' for r,c in [(458,'#00C2CC')])
    return svg(('' if kind else f'<path fill="{bg}" d="M0 0H1024V1024H0Z"/>')+frame+a)
def build_one(n,theme):
    stem=f'icons/{theme}-{n}-{SLUGS[n]}'
    im=save(stem,icon(n,theme)); im.resize((512,512),Image.Resampling.LANCZOS).save(ROOT/(stem+'-512.png'))
    if theme=='global':
        for k in ('foreground','monochrome'): save(stem+'-'+k,icon(n,theme,k),False)
if __name__=='__main__':
    if '--first' in sys.argv:
        build_one(1,'global')
        im=Image.new('RGB',(720,580),'#808080'); a=Image.open(ROOT/'icons/global-1-pulse-tick.png')
        for x,s in [(8,512),(536,128),(572,48)]: im.paste(a.resize((s,s),Image.Resampling.LANCZOS),(x,8 if s!=48 else 180))
        im.save(ROOT/'icons/concept-1-review.png')
    else:
        for n in SLUGS:
            for theme in ('global','kry'): build_one(n,theme)
        sheet=Image.new('RGB',(2200,1530),'#808080'); d=ImageDraw.Draw(sheet)
        for t,theme in enumerate(('global','kry')):
            for j,n in enumerate(SLUGS):
                x=j*730; y=t*600; a=Image.open(ROOT/f'icons/{theme}-{n}-{SLUGS[n]}.png')
                for dx,s in [(0,512),(524,128),(668,48)]: sheet.paste(a.resize((s,s),Image.Resampling.LANCZOS),(x+dx,y+10))
                d.text((x+12,y+535),f'{theme}-{n}-{SLUGS[n]}.png  |  512 / 128 / 48 px',fill='white',font=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf',22))
        for n in SLUGS:
            a=Image.open(ROOT/f'icons/global-{n}-{SLUGS[n]}.png').resize((128,128),Image.Resampling.LANCZOS)
            for k in range(3):
                mask=Image.new('L',(128,128)); md=ImageDraw.Draw(mask)
                if k==0: md.ellipse((0,0,127,127),fill=255)
                elif k==1:
                    import math
                    pts=[(64+63*math.copysign(abs(math.cos(t))**.5,math.cos(t)),64+63*math.copysign(abs(math.sin(t))**.5,math.sin(t))) for t in [i*math.pi/360 for i in range(720)]]; md.polygon(pts,fill=255)
                else: md.rounded_rectangle((0,0,127,127),radius=26,fill=255)
                x=(n-1)*730+k*180+20; sheet.paste(a,(x,1250),mask); d.text((x,1395),['circle','squircle','rounded square'][k],fill='white')
        sheet.save(ROOT/'icons/icon-sizes.png')
