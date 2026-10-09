from build_brand import *
from html import escape

def text(x,y,s,size=40,weight=700,color='white'):
 return f'<text x="{x}" y="{y}" font-family="Kohinoor Bangla, Arial" font-size="{size}" font-weight="{weight}" fill="{color}">{escape(s)}</text>'
def badge(x,y,w=590,h=82):
 b64=base64.b64encode((ROOT/'brand/kry-logo-official.png').read_bytes()).decode()
 return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{h/2}" fill="white"/><image x="{x+22}" y="{y+13}" width="120" height="56" xlink:href="data:image/png;base64,{b64}"/>'+text(x+164,y+51,'সৌজন্যে: KRY International',26,600,BLUE)
def build():
 for theme in ('kry','global'):
  kry=theme=='kry'; bg=BLUE if kry else NIGHT
  body=f'<rect width="1024" height="500" fill="{bg}"/>'
  if kry:
   for r,c in [(480,'#0042C6'),(420,'#00C2CC'),(360,'#C0CDD8')]: body+=f'<circle cx="1380" cy="235" r="{r}" fill="none" stroke="{c}" stroke-width="18"/>'
   body+=badge(48,48)
   body+=text(48,220,'ফোন কেনা-বেচার আগে',49)+text(48,286,'একবার চেক',56)
   body+=text(48,370,'IMEI check · Display test · Mic test · Camera test',21,600)
  else:
   body+='<circle cx="1370" cy="180" r="420" fill="none" stroke="#D9FE06" stroke-width="34"/>'
   body+=text(48,186,'Check a phone',49)+text(48,252,'before you buy or sell it',49)
   body+=text(48,350,'Camera test · Mic test · Display test · Battery health',20,600)
  if kry:
   body+=''.join(f'<circle cx="824" cy="216" r="{r*.30}" fill="none" stroke="{c}" stroke-width="6"/>' for r,c in [(458,"#00C2CC")])
  body+=f'<g transform="translate(670 62) scale(.30)">{art(1,"#00C2CC" if kry else LIME)}</g>'
  body+=text(711,412,'DeviceGPT',38)
  save('feature/'+theme+'-feature',svg(body,1024,500))

if __name__ == "__main__":
 build()
