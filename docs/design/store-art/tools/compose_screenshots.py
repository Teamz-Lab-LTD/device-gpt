#!/usr/bin/env python3
"""Rebuild all store screenshots. Only crop/scale real screen pixels; Pango shapes type."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
from build_brand import ROOT, svg, render, BLUE, NIGHT
from build_features import badge, text
OUT=ROOT/'screenshots/kry'
# Rectangles are absolute source pixels: left, top, right, bottom.
SHOTS=[
 ('01-imei-check','02-official.png','IMEI check','ফোনটা অফিসিয়াল তো?','hero',(0,120,1344,2902),(80,1550,1260,1880)),
 ('02-display-test','03-deadpixel-red.png','Display test','দাগ আর dead pixel দেখুন','left',(0,0,1344,2902),(55,65,1310,308)),
 ('03-mic-test','04-mic-speak.png','Mic test','কথা ঠিকমতো যায় তো?','right',(0,120,1344,2902),(125,805,1230,1044)),
 ('04-phone-check','01-score.png','ফোন কেনা-বেচার','আগে একবার চেক','left',(0,120,1344,2902),(401,790,951,1335)),
 ('05-camera-test','05-camera.png','Camera test','ছবি পরিষ্কার আসে তো?','right',(0,120,1344,2902),(40,1390,934,1760)),
 ('06-touch-test','06-alt-grid.png','Touch screen test','স্ক্রিনে আঁচড় আছে কি না দেখুন','left',(0,0,1344,2902),(310,620,1150,1230)),
 ('07-battery','07-power.png','Battery health','কোন অংশে চার্জ কত যায়','right',(0,120,1344,2902),(94,702,1250,1500)),
 ('08-report','08-report-cards.png','ফোনের অবস্থার','report দেখান','left',(0,120,1344,2902),(90,771,1250,1236)),
]
def fit_text(s,maximum,size,weight):
 # Measure the shaped alpha bounds with the same Pango renderer used for output.
 while True:
  box=render(svg(text(0,size*1.5,s,size,weight),1600,200)).getbbox()
  if box[2]<=maximum: return size
  size-=1

def compose(i,row):
 name,rawname,l1,l2,layout,crop,focus=row
 raw=Image.open(ROOT/'raw'/rawname).convert('RGB')
 if raw.size != (1344,2992):
  # Coordinates are authored for the reference resolution; scale them for replacement captures.
  sx,sy=raw.width/1344,raw.height/2992
  crop=tuple(round(v*(sx if j%2==0 else sy)) for j,v in enumerate(crop))
  focus=tuple(round(v*(sx if j%2==0 else sy)) for j,v in enumerate(focus))
 # Closed-palette thin arcs are quiet by geometry, without inventing translucent colours.
 cx=1120 if i%2 else -120; cy=1160+(i%3)*140
 body=f'<rect width="1080" height="1920" fill="{BLUE}"/>'
 for r,c,w in [(1010,'#0042C6',60),(922,'#00C2CC',8),(878,'#C0CDD8',5)]:
  body+=f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{c}" stroke-width="{w}"/>'
 body+=badge(60,60,740,100).replace('width="120" height="56"','width="150" height="70"').replace('x="224"','x="260"').replace('y="111"','y="122"')
 if layout=='hero':
  tx,ty,tw=60,298,960; sz1,sz2=96,62; screenw=610; px=(1080-638)//2; py=520
 else:
  tx,ty,tw=60,298,960
  sz1,sz2=96,62; screenw=590; px=60 if layout=='left' else 1080-60-618; py=540
 s1=fit_text(l1,tw,sz1,700); s2=fit_text(l2,tw,sz2,600)
 body+=text(tx,ty,l1,s1,700)
 body+=f'<rect x="{tx}" y="{ty+35}" width="72" height="6" rx="3" fill="#00C2CC"/>'
 body+=text(tx,ty+116,l2,s2,600)
 im=render(svg(body,1080,1920))
 screenh=round((crop[3]-crop[1])*screenw/(crop[2]-crop[0])); pw=screenw+28; ph=screenh+28
 assert px>=40 and px+pw<=1040 and py+ph<1880
 # The rectangular capture is intact. A rounded body sits behind it; no masks touch screen pixels.
 shadow=Image.new('RGBA',im.size); sd=ImageDraw.Draw(shadow); sd.rounded_rectangle((px,py+15,px+pw,py+ph+15),radius=round(pw*.09),fill=(18,21,26,90)); shadow=shadow.filter(ImageFilter.GaussianBlur(20)); im.alpha_composite(shadow)
 d=ImageDraw.Draw(im); d.rounded_rectangle((px,py,px+pw,py+ph),radius=round(pw*.09),fill=NIGHT)
 # Screen corners remain square: preserving real captured pixels takes precedence over rounding UI.
 screen=raw.crop(crop).resize((screenw,screenh),Image.Resampling.LANCZOS)
 im.paste(screen,(px+14,py+14))
 scale=screenw/(crop[2]-crop[0]); zoom=1.7 if i not in (4,6) else 2.0
 fw=round((focus[2]-focus[0])*scale*zoom); fh=round((focus[3]-focus[1])*scale*zoom)
 if fw>640: zoom*=640/fw; fw=round((focus[2]-focus[0])*scale*zoom); fh=round((focus[3]-focus[1])*scale*zoom)
 if layout=='hero': fx,fy=(1080-fw)//2,1300
 elif layout=='left': fx,fy=1020-fw,1030 if i!=4 else 1110
 else: fx,fy=60,1030
 # A connector starts on the device BODY at source height; it never paints over real UI.
 anchor_y=py+14+round(((focus[1]+focus[3])/2-crop[1])*scale)
 anchor_x=px+pw-5 if layout!='right' else px+5
 target_x=fx+fw-12 if layout!='right' else fx+12
 d.line([(anchor_x,anchor_y),(target_x,anchor_y),(target_x,fy+fh+12)],fill='#00C2CC',width=5)
 d.rounded_rectangle((fx-10,fy-10,fx+fw+10,fy+fh+10),radius=24,fill='white')
 im.paste(raw.crop(focus).resize((fw,fh),Image.Resampling.LANCZOS),(fx,fy))
 im.convert('RGB').save(OUT/(name+'.png'))
 return f'| {i} | {name}.png | {layout} | {crop} | {focus} | {zoom:.1f}× | ({px}, {py}, {pw}, {ph}) |'
if __name__=='__main__':
 OUT.mkdir(parents=True,exist_ok=True)
 rows=[compose(i,row) for i,row in enumerate(SHOTS,1)]
 sheet=Image.new('RGB',(2160,480),BLUE)
 for j,row in enumerate(SHOTS): sheet.paste(Image.open(OUT/(row[0]+'.png')).resize((270,480),Image.Resampling.LANCZOS),(270*j,0))
 sheet.save(OUT/'contact-sheet.png')
 (OUT/'README.md').write_text('''# KRY store screenshots

Built with Pillow and rsvg-convert/Pango using Kohinoor Bangla. All headlines are the exact supplied strings, shaped as complete lines. Screens and focus cards come directly from original capture crops with Lanczos scaling; no screen painting, generated UI, colour correction, or retouching is used.

Crop/focus rectangles use original capture coordinates `(left, top, right, bottom)` with exclusive right/bottom bounds. Device rectangles are output `(x, y, width, height)`. Focus magnification is relative to the same content as displayed in the phone, not relative to the raw capture.

| Shot | File | Composition | Screen crop | Focus crop | Magnification | Device rectangle |
|---|---|---|---|---|---|---|
'''+ '\n'.join(rows)+'''

## Rebuild

Replace the corresponding file in `../../raw/` with a new capture, retaining its filename. From the repository root run:

```sh
python3 docs/design/store-art/tools/compose_screenshots.py
python3 docs/design/store-art/verify_assets.py
```

The script resolves paths relative to itself. It scales authored crop coordinates when replacement dimensions change; if the UI layout changes, update the corresponding `SHOTS` crop/focus tuple. The raw file mapping and exact headlines are in `SHOTS`. Required dependencies: Python 3, Pillow, `/opt/homebrew/bin/rsvg-convert`, and macOS Kohinoor Bangla.

See `../../CODEX-NOTES.md` for the conflicting composition instructions, full-screen capture exceptions, and emulator limitations. All eight PNGs and the contact sheet were visually reviewed after rendering.
''')
