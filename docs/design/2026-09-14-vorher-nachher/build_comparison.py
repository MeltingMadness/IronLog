from pathlib import Path
from html import escape
from base64 import b64encode
from io import BytesIO
import textwrap, json
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.colors import HexColor
from reportlab.lib.utils import ImageReader

ROOT=Path(__file__).resolve().parents[3]
OUT=Path(__file__).resolve().parent
E=ROOT/'docs/validation/2026-09-13-competitor-audit/evidence'
PDF=ROOT/'output/pdf/IronLog-vorher-nachher.pdf'
for key,name in [('R','Arial.ttf'),('B','Arial Bold.ttf')]:
 pdfmetrics.registerFont(TTFont(key,'/System/Library/Fonts/Supplemental/'+name))
BG='#15120f'; CARD='#24201b'; LINE='#403a32'; TEXT='#f8f3e9'; MUTED='#b9afa1'; ACC='#ffa31a'; GREEN='#82d5ab'; BLUE='#8bc8e4'
W,H=1190,842

class Draw:
 def __init__(self,c=None,ox=0,oy=0,s=1): self.c=c; self.ox=ox; self.oy=oy; self.s=s; self.svg=[]
 def rect(self,x,y,w,h,fill=CARD,r=0,stroke=None):
  self.svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill or "none"}" stroke="{stroke or "none"}"/>')
  if self.c:
   c=self.c;c.setFillColor(HexColor(fill or BG));c.setStrokeColor(HexColor(stroke or fill or BG));c.setLineWidth(self.s)
   c.roundRect(self.ox+x*self.s,H-self.oy-(y+h)*self.s,w*self.s,h*self.s,r*self.s,stroke=int(bool(stroke)),fill=int(bool(fill)))
 def text(self,x,y,t,size=14,color=TEXT,bold=False,anchor='start'):
  self.svg.append(f'<text x="{x}" y="{y}" fill="{color}" font-family="Arial, sans-serif" font-size="{size}" font-weight="{700 if bold else 400}" text-anchor="{anchor}">{escape(t)}</text>')
  if self.c:
   c=self.c;c.setFillColor(HexColor(color));c.setFont('B' if bold else 'R',size*self.s)
   fn=c.drawString if anchor=='start' else c.drawCentredString if anchor=='middle' else c.drawRightString
   fn(self.ox+x*self.s,H-self.oy-y*self.s,t)
 def line(self,x1,y1,x2,y2,color=LINE,width=1):
  self.svg.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="{width}" stroke-linecap="round"/>')
  if self.c:
   self.c.setStrokeColor(HexColor(color));self.c.setLineWidth(width*self.s);self.c.line(self.ox+x1*self.s,H-self.oy-y1*self.s,self.ox+x2*self.s,H-self.oy-y2*self.s)
 def tick(self,x,y,color=GREEN): self.line(x,y+5,x+5,y+10,color,2.2);self.line(x+5,y+10,x+14,y,color,2.2)
 def wrap(self,x,y,t,width=320,size=14,color=MUTED,bold=False,leading=21):
  words=t.split(); line=''
  for word in words:
   candidate=(line+' '+word).strip()
   if pdfmetrics.stringWidth(candidate,'B' if bold else 'R',size)>width and line:
    self.text(x,y,line,size,color,bold);y+=leading;line=word
   else:line=candidate
  if line:self.text(x,y,line,size,color,bold);y+=leading
  return y
 def button(self,y,t,primary=True,x=20,w=350):
  self.rect(x,y,w,48,ACC if primary else CARD,12,None if primary else LINE)
  self.text(x+w/2,y+30,t,15,BG if primary else TEXT,True,'middle')
 def chip(self,x,y,w,t,selected=False):
  self.rect(x,y,w,30,'#4d3515' if selected else CARD,15,ACC if selected else LINE)
  self.text(x+w/2,y+20,t,12,ACC if selected else MUTED,selected,'middle')
 def status(self,title,action='Beenden',ios=False):
  self.rect(0,0,390,867,BG,26)
  self.text(22,29,'9:41',13,TEXT,True)
  for i in range(3):self.rect(317+i*5,23-i*3,3,5+i*3,TEXT,1)
  self.rect(342,17,25,12,None,3,TEXT);self.rect(345,20,17,6,TEXT,1)
  if ios:
   self.text(20,78,'‹',28,ACC);self.text(195,76,title,18,TEXT,True,'middle');self.text(370,76,action,13,ACC,False,'end')
  else:self.text(20,77,title,23,TEXT,True);self.text(370,76,action,13,ACC,False,'end')
  self.line(20,100,370,100)
  self.rect(137,849,116,4,'#d7d1c7',2)
 def output(self):return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 390 867" role="img">'+''.join(self.svg)+'</svg>'

def logging(d,ios=False):
 d.status('Audit A','Beenden',ios)
 d.text(20,127,'04:20  ·  600 kg  ·  1 / 9 Sätze',13,MUTED)
 d.rect(20,145,350,47,'#1d2c32',12)
 d.text(34,174,'Pause  1:32',16,BLUE,True);d.text(354,174,'+15 s   Überspringen',12,BLUE,False,'end')
 d.text(20,225,'Bankdrücken',23,TEXT,True);d.text(20,247,'Langhantel  ·  Ziel: 3 × 10',13,MUTED)
 d.text(356,229,'···',24,ACC,False,'end')
 d.rect(20,267,350,195,CARD,14)
 for x,t in [(34,'SATZ'),(85,'VORHER'),(220,'KG'),(280,'WDH.')]: d.text(x,291,t,10,MUTED,True)
 for i,y in enumerate([315,363,411]):
  if i==1:d.rect(27,y-5,336,45,'#3c2b16',9,ACC)
  d.text(39,y+22,str(i+1),16,TEXT,True)
  d.text(88,y+21,'–',14,MUTED)
  for x,val in [(201,'60'),(263,'10')]:
   d.rect(x,y,52,34,'#302a23' if i!=1 else '#201a12',8)
   d.text(x+26,y+23,val,17,TEXT,True,'middle')
  if i==0:d.tick(336,y+11)
  elif i==1:d.rect(328,y,30,34,ACC,8);d.tick(335,y+11,BG)
  else:d.rect(328,y,30,34,None,8,LINE)
 d.text(20,485,'Satz 2: Werte aus Satz 1 übernommen',12,ACC)
 d.rect(20,504,350,49,CARD,12,LINE);d.text(35,534,'RPE / RIR, Satztyp & Absicht',14,TEXT);d.text(352,534,'+',22,ACC,False,'end')
 d.text(20,580,'+ Satz hinzufügen',14,ACC);d.text(370,580,'Scheibenrechner',13,ACC,False,'end')
 d.line(20,603,370,603)
 d.text(20,634,'Als Nächstes',11,MUTED,True)
 d.rect(20,650,350,61,CARD,12);d.text(35,677,'Langhantelrudern',16,TEXT,True);d.text(35,699,'3 × 10  ·  Noch nicht begonnen',12,MUTED)
 d.button(760,'Satz 2 bestätigen · 60 kg × 10')
 d.text(195,830,'Werte bleiben vor dem Bestätigen editierbar',10,MUTED,False,'middle')

def picker(d):
 d.status('Übungen wählen','Fertig')
 d.rect(20,121,350,46,CARD,12,LINE);d.text(36,150,'Übung suchen',15,MUTED)
 for x,w,t,sel in [(20,54,'Alle',True),(82,65,'Brust',False),(155,77,'Rücken',False),(240,64,'Beine',False)]:d.chip(x,182,w,t,sel)
 d.text(20,241,'3 ausgewählt',13,ACC,True);d.text(370,241,'Auswahl leeren',12,MUTED,False,'end')
 d.text(20,276,'HÄUFIG VERWENDET',10,MUTED,True)
 rows=[('Bankdrücken','Brust · Langhantel',True),('Langhantelrudern','Rücken · Langhantel',True),('Kniebeuge','Beine · Langhantel',True),('Schulterdrücken','Schultern · Kurzhantel',False),('Latzug','Rücken · Kabel',False)]
 for i,(t,sub,sel) in enumerate(rows):
  y=292+i*76;d.rect(20,y,350,66,'#31271b' if sel else CARD,12,ACC if sel else None)
  d.text(35,y+27,t,17,TEXT,True);d.text(35,y+48,sub,12,MUTED)
  d.rect(328,y+21,25,25,ACC if sel else None,6,None if sel else LINE)
  if sel:d.tick(334,y+28,BG)
 d.text(20,704,'+ Eigene Übung erstellen',14,ACC)
 d.button(760,'3 Übungen hinzufügen')
 d.text(195,830,'Auswahl bleibt bei Suche und Filterwechsel erhalten',10,MUTED,False,'middle')

def planner(d):
 d.status('Audit A','Speichern')
 d.text(20,130,'1. Bankdrücken',23,TEXT,True);d.text(20,153,'Langhantel  ·  Brust',13,MUTED)
 d.rect(20,178,350,43,CARD,11)
 d.rect(194,182,171,35,'#513817',9);d.text(106,205,'Einfach: 3 × 10',13,MUTED,False,'middle');d.text(279,205,'Einzelne Sätze',13,ACC,True,'middle')
 d.rect(20,241,350,300,CARD,14)
 for x,t in [(35,'SATZ'),(84,'TYP'),(237,'KG'),(304,'WDH.')]:d.text(x,266,t,10,MUTED,True)
 rows=[('W1','Aufwärmen','20','10'),('W2','Aufwärmen','40','5'),('1','Arbeitssatz','60','8'),('2','Backoff','55','10')]
 for i,(n,t,kg,reps) in enumerate(rows):
  y=280+i*59;d.text(36,y+29,n,14,MUTED if i<2 else TEXT,True);d.text(84,y+29,t,13,MUTED if i<2 else TEXT)
  for x,val in [(217,kg),(289,reps)]:d.rect(x,y+5,62,40,'#302a23',8);d.text(x+31,y+31,val,17,TEXT,True,'middle')
  if i<3:d.line(35,y+55,354,y+55)
 d.text(20,574,'+ Arbeitssatz',14,ACC);d.text(220,574,'+ Aufwärmsatz',14,ACC)
 d.rect(20,601,350,70,'#27241e',12,LINE);d.text(35,629,'Progression: Manuell',15,TEXT,True);d.text(35,651,'Gilt für Arbeitssätze · Bearbeiten',12,MUTED)
 d.text(20,710,'Superset & Reihenfolge',14,ACC)
 d.button(760,'Satzvorgaben speichern')
 d.text(195,830,'Die Vorgaben erscheinen später im Training',10,MUTED,False,'middle')

def finish(d):
 d.status('Audit A','')
 d.text(20,132,'05:00  ·  1.140 kg  ·  2 / 9 Sätze',13,MUTED)
 d.rect(20,163,350,114,CARD,14);d.text(35,194,'Bankdrücken',20,TEXT,True);d.text(35,228,'1     60 kg × 10',15,TEXT);d.text(35,257,'2     60 kg × 9',15,TEXT);d.tick(337,217);d.tick(337,246)
 d.rect(0,319,390,548,'#28221b',26)
 d.rect(164,332,62,4,'#756857',2)
 d.text(24,388,'Training beenden?',26,TEXT,True)
 d.text(24,425,'Noch 7 geplante Sätze offen',17,ACC,True)
 d.wrap(24,455,'Du hast 2 von 9 geplanten Sätzen absolviert. Dein bisheriges Training bleibt gespeichert.',340,14,MUTED)
 d.rect(24,514,342,133,'#201b15',12)
 for i,(name,n) in enumerate([('Bankdrücken','1 offen'),('Langhantelrudern','3 offen'),('Kniebeuge','3 offen')]):
  d.text(39,544+i*38,name,14,TEXT);d.text(349,544+i*38,n,13,MUTED,False,'end')
 d.button(679,'Weitertrainieren')
 d.button(741,'Trotzdem beenden',False)
 d.text(195,820,'Nur absolvierte Sätze zählen zum Ergebnis',11,MUTED,False,'middle')

def summary(d):
 d.status('Training gespeichert','Fertig')
 d.rect(168,126,54,54,'#20352a',27);d.tick(188,144,GREEN)
 d.text(195,221,'Audit A abgeschlossen',25,TEXT,True,'middle')
 d.text(195,248,'Montag · 14. September',13,MUTED,False,'middle')
 for x,num,label in [(20,'5 min','Dauer'),(140,'2','Sätze'),(260,'1.140','kg Volumen')]:
  d.rect(x,278,110,93,CARD,12);d.text(x+55,317,num,23,TEXT,True,'middle');d.text(x+55,345,label,12,MUTED,False,'middle')
 d.rect(20,396,350,139,CARD,14);d.text(35,426,'Bankdrücken',20,TEXT,True);d.text(35,461,'Satz 1',14,MUTED);d.text(350,461,'60 kg × 10',16,TEXT,True,'end');d.text(35,499,'Satz 2',14,MUTED);d.text(350,499,'60 kg × 9',16,TEXT,True,'end')
 d.rect(20,559,350,96,'#24251e',12);d.text(35,589,'Teiltraining gespeichert',15,TEXT,True);d.wrap(35,613,'Die 7 offenen Sätze wurden nicht als Leistung erfasst.',316,13,MUTED,False,19)
 d.rect(20,678,350,51,CARD,12,LINE);d.text(35,709,'Änderungen am Plan prüfen',14,ACC);d.text(353,710,'›',23,ACC,False,'end')
 d.button(757,'Trainingsdetails öffnen')
 d.text(195,829,'Der Plan bleibt bis zu deiner Entscheidung unverändert',10,MUTED,False,'middle')

def changes(d):
 d.status('Planänderungen','Später')
 d.text(20,134,'Für das nächste Training',23,TEXT,True)
 d.wrap(20,163,'Wähle bewusst, was aus der heutigen Einheit in Audit A übernommen wird.',350,14,MUTED)
 d.rect(20,217,350,125,CARD,14);d.text(35,247,'Bankdrücken · Satz 2',16,TEXT,True)
 d.text(35,279,'Plan',12,MUTED);d.text(188,279,'Heute',12,MUTED)
 d.text(35,310,'60 kg × 10',20,TEXT,True);d.text(188,310,'60 kg × 9',20,ACC,True)
 d.text(20,378,'WAS SOLL SICH ÄNDERN?',10,MUTED,True)
 options=[('Nur dieses Training','Plan unverändert lassen',True),('Satzwerte übernehmen','Nur ausgeführte Sätze aktualisieren',False),('Plan im Editor anpassen','Übungen und Vorgaben selbst bearbeiten',False)]
 for i,(title,sub,sel) in enumerate(options):
  y=396+i*92;d.rect(20,y,350,78,'#3a2b16' if sel else CARD,12,ACC if sel else LINE)
  d.rect(35,y+27,22,22,ACC if sel else None,11,None if sel else MUTED)
  if sel:d.rect(42,y+34,8,8,BG,4)
  d.text(70,y+31,title,15,TEXT,True);d.text(70,y+54,sub,11,MUTED)
 d.wrap(20,706,'Offene Übungen bleiben im Plan. Vorschläge ändern nichts ohne deine Bestätigung.',350,12,MUTED,False,18)
 d.button(770,'Plan unverändert lassen')

pages=[
 dict(key='logging',title='Den nächsten Satz direkt bestätigen',sub='Android · Kompakte Satzzeilen, sinnvolle Vorbelegung und erreichbares Bestätigen.',before='ironlog-android-weight.png',draw=lambda d:logging(d),notes=[('Heute','Der Loggen-Knopf liegt nach der Gewichtseingabe unterhalb des sichtbaren Bildschirms.'),('Im Entwurf','Drei Sätze bleiben gleichzeitig sichtbar. Satz 2 übernimmt 60 × 10 aus dem gerade geloggten Satz.'),('Details bei Bedarf','RPE/RIR, Satztyp, Absicht und Scheibenrechner bleiben erreichbar, belegen aber nicht ständig den Eingabebereich.')],benefit='Ziel: einen identischen Folgesatz ohne erneute Gewichtseingabe und ohne Scrollen bestätigen.',foot='Nachher zeigt den Zustand nach Satz 1. Ohne frühere Einheit bleibt VORHER leer; die Übernahme stammt ausdrücklich aus dieser Session.'),
 dict(key='ios',title='Auch auf iOS im Training bleiben',sub='iOS · Derselbe einfache Ablauf mit plattformgerechter Navigation.',before='ironlog-ios-second-set-default.jpg',draw=lambda d:logging(d,True),notes=[('Heute','Ein eigener Editor pro Satz. Im geprüften ersten Training beginnt Satz 2 wieder bei 0 kg.'),('Im Entwurf','Gewicht, Wiederholungen, Vorwerte und Abschluss bleiben im Trainingsbild. Alle Felder sind sichtbar beschriftet.'),('Gemeinsame Regeln','Die Bedienlogik ist auf beiden Plattformen gleich. Navigation und Bedienelemente können jeweils nativ umgesetzt werden.')],benefit='Ziel: Werte kontrollieren, bei Bedarf ändern und den Satz direkt bestätigen.',foot='Echter iOS-Screenshot links; rechts eine schematische UI-Richtung. Kein Nachweis einer bereits implementierten iOS-Oberfläche.'),
 dict(key='auswahl',title='Den ganzen Plan in einer Auswahlrunde aufbauen',sub='Android · Mehrfachauswahl ergänzt die bereits vorhandenen Muskelgruppenfilter.',before='ironlog-android-picker.png',draw=picker,notes=[('Heute','Eine gewählte Übung schließt den Picker. Für drei Übungen muss er dreimal geöffnet werden.'),('Im Entwurf','Drei markierte Übungen und ein eindeutiger Abschluss. Die Auswahl überlebt Suche und Filterwechsel.'),('Orientierung','Suchfeld und Filter bleiben oben. Eigene Übungen sind weiterhin erreichbar; ausgewählte Einträge klar markiert.')],benefit='Ziel: Bankdrücken, Rudern und Kniebeuge zusammen übernehmen.',foot='Die Auswahl rechts ist ein Beispielzustand. Die konkreten Treffer dürfen sich während der Suche ändern, die Auswahl bleibt bestehen.'),
 dict(key='satzplanung',title='Aufwärmen, Arbeit und Backoff gezielt vorgeben',sub='Planeditor · Einfacher Einstieg und einzelne Satzvorgaben in derselben Ansicht.',before='ironlog-android-plan-bench.png',draw=planner,notes=[('Heute','Einheitliche Ziele je Übung: Satzanzahl, Wiederholungen und Gewicht. Links ist zusätzlich die damalige Emulator-Eingabehilfe sichtbar.'),('Im Entwurf','Der Modus „Einfach“ bleibt erhalten. „Einzelne Sätze“ ermöglicht unterschiedliche Gewichte und Wiederholungen.'),('Trainingslogik','Aufwärmsätze bleiben von Arbeitssätzen getrennt. Progression bleibt sichtbar und editierbar.')],benefit='Ziel: unterschiedliche Satzvorgaben vor dem Training festlegen und später unverändert wiederfinden.',foot='Die Werte rechts sind illustrative Planvorgaben. Das Beispiel ist keine automatische Empfehlung für das Testtraining.'),
 dict(key='abschluss',title='Ein Teiltraining bewusst beenden',sub='Trainingsabschluss · Offenen Umfang verständlich machen.',before='ironlog-android-finish.png',draw=finish,notes=[('Heute','Die allgemeine Bestätigung nennt nicht, dass erst zwei von neun geplanten Sätzen absolviert wurden.'),('Im Entwurf','Die Zahl offener Sätze und ihre Übungen sind sichtbar. Weitertrainieren bleibt die hervorgehobene Möglichkeit.'),('Freie Entscheidung','Ein Teiltraining ist erlaubt. „Trotzdem beenden“ speichert ausschließlich die bereits absolvierten Sätze.')],benefit='Ziel: verstehen, was gespeichert wird, bevor die Session endet.',foot='Beispiel aus dem Android-Test: 2 erfasste Sätze, 7 offene Sätze. Es werden keine unbestätigten Sätze automatisch als absolviert markiert.'),
 dict(key='ergebnis',title='Das gespeicherte Training sofort sehen',sub='Nach dem Abschluss · Ergebnis statt unmittelbarer Rückkehr auf Home.',before='ironlog-android-finished.png',draw=summary,notes=[('Heute','Nach dem Beenden erscheint Home. Das korrekt gespeicherte Ergebnis muss im Verlauf gesucht werden.'),('Im Entwurf','Sätze, Dauer und Volumen bestätigen den Abschluss. Die konkreten Satzwerte sind direkt überprüfbar.'),('Nächster Schritt','Details öffnen oder bewusst Planänderungen prüfen. Es gibt keinen verpflichtenden Teilen- oder Bewertungsablauf.')],benefit='Ziel: die erfolgreiche Speicherung und die tatsächliche Leistung erkennen.',foot='1.140 kg = 60 × 10 + 60 × 9 aus dem geprüften Verlauf. Datum im Entwurf illustrativ; keine neue Trainingsaufzeichnung.'),
 dict(key='planuebernahme',title='Heute trainiert heißt nicht automatisch neu geplant',sub='Optionale Erweiterung · Trainingswerte und Planänderungen bewusst trennen.',before='ironlog-android-history-detail.png',draw=changes,notes=[('Bestand','Links sind die gespeicherten Satzdetails. Ein vollständiger Übernahmeablauf wurde nicht appweit nachgewiesen.'),('Im Entwurf','Plan und heutige Leistung stehen nebeneinander. „Nur dieses Training“ ist vorausgewählt.'),('Explizite Entscheidung','Werte übernehmen oder den Plan selbst bearbeiten. Offene Übungen werden nicht stillschweigend entfernt.')],benefit='Ziel: eine einmalige Abweichung darf den nächsten Trainingsplan nicht unbemerkt verändern.',foot='Erweiterung zur Diskussion, keine sicher nachgewiesene Funktionslücke. Der Vergleich 60 × 10 im Plan zu 60 × 9 heute ist ein Beispielszenario.')
]

c=canvas.Canvas(str(PDF),pagesize=(W,H));c.setTitle('IronLog - Visueller Vorher-Nachher-Vergleich');c.setAuthor('IronLog Design Review')
html_sections=[]
for i,p in enumerate(pages):
 c.setFillColor(HexColor('#f5f3ee'));c.rect(0,0,W,H,fill=1,stroke=0)
 page=Draw(c)
 page.text(40,31,'IRONLOG  /  DESIGNREVIEW  /  14.09.2026',10,'#706956',True)
 page.text(1150,31,f'{i+1:02d} / {len(pages):02d}',10,'#706956',True,'end')
 page.text(40,74,p['title'],28,'#221e17',True)
 page.text(40,102,p['sub'],13,'#6b6255')
 page.text(54,140,'VORHER · TESTAUFNAHME 13.09.',10,'#6b6255',True)
 page.text(417,140,'NACHHER · ENTWURF',10,'#9b5b00',True)
 source=E/p['before'];iw,ih=ImageReader(str(source)).getSize(); ph=597;pw=ph*iw/ih
 c.drawImage(str(source),54+(269-pw)/2,H-154-ph,width=pw,height=ph,mask='auto')
 d=Draw(c,417,154,597/867);p['draw'](d);svg=d.output();(OUT/(p['key']+'.svg')).write_text(svg)
 ny=181
 for title,body in p['notes']:
  page.text(756,ny,title,14,'#251e14',True);ny=page.wrap(756,ny+25,body,365,14,'#5b5143',False,21)+26
 page.rect(742,max(ny+2,590),403,111,'#e9e0cc',12)
 page.wrap(760,max(ny+2,590)+29,p['benefit'],365,15,'#352c1f',True,22)
 page.wrap(40,780,p['foot'],1100,10,'#6b6255',False,15)
 page.text(40,826,'Links: unveränderte Originalaufnahme. Rechts: schematischer Vorschlag, keine Produktänderung.',9,'#827665')
 c.showPage()
 notes=''.join(f'<h3>{escape(a)}</h3><p>{escape(b)}</p>' for a,b in p['notes'])
 mime='image/jpeg' if source.suffix=='.jpg' else 'image/png';data=b64encode(source.read_bytes()).decode()
 html_sections.append(f'<section id="{p["key"]}"><div class="sectionhead"><span>{i+1:02d} / {len(pages):02d}</span><h2>{escape(p["title"])}</h2><p>{escape(p["sub"])}</p></div><div class="comparison"><figure><figcaption>VORHER <small>Testaufnahme vom 13.09.</small></figcaption><img class="phone" src="data:{mime};base64,{data}" alt="IronLog vorher: {escape(p["notes"][0][1])}"></figure><figure class="after"><figcaption>NACHHER <small>Entwurf zur Diskussion</small></figcaption>{svg}</figure><aside>{notes}<div class="benefit">{escape(p["benefit"])}</div></aside></div><p class="footnote">{escape(p["foot"])}</p></section>')
c.save()
nav=''.join(f'<a href="#{p["key"]}">{name}</a>' for p,name in zip(pages,['Logging','iOS','Übungsauswahl','Satzplanung','Beenden','Ergebnis','Planübernahme']))
html='''<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>IronLog - Vorher und Nachher</title><style>
:root{font-family:Arial,system-ui,sans-serif;line-height:1.5;color:#241f17;background:#f5f3ee}*{box-sizing:border-box}body{margin:0}header,main{max-width:1370px;margin:auto;padding:34px 32px}header{padding-bottom:10px}.eyebrow{font-size:12px;letter-spacing:.12em;font-weight:700;color:#786747}h1{font-size:clamp(32px,4vw,52px);letter-spacing:-.04em;line-height:1.12;margin:16px 0}.intro{font-size:18px;max-width:920px;color:#625747}.notice{background:#e9e0cc;border-left:4px solid #b47713;padding:12px 18px;max-width:1020px;font-size:14px}nav{display:flex;flex-wrap:wrap;gap:9px;margin:24px 0 0}nav a{padding:8px 12px;border:1px solid #c9bea9;border-radius:8px;color:#55442a;text-decoration:none;background:#fffdfa}nav a:hover{background:#eadabb}a:focus-visible{outline:3px solid #c17909;outline-offset:3px}section{padding:30px 0 46px;border-top:1px solid #d2c9b9;scroll-margin-top:20px}.sectionhead>span{float:right;font-size:12px;color:#786747}.sectionhead h2{font-size:28px;line-height:1.2;margin:0 0 8px;letter-spacing:-.025em}.sectionhead>p{color:#6b6255;margin:0 0 26px}.comparison{display:grid;grid-template-columns:minmax(260px,1fr) minmax(260px,1fr) minmax(240px,.83fr);gap:32px;align-items:start}figure{margin:0}figcaption{font-size:12px;font-weight:700;letter-spacing:.07em;margin-bottom:14px}figcaption small{display:block;color:#7d7465;font-weight:400;font-size:11px;letter-spacing:0}.after figcaption{color:#9b5b00}.phone,figure svg{width:100%;max-width:390px;display:block;border-radius:24px;box-shadow:0 14px 30px #2d1e101c}.phone{height:auto}figure svg{aspect-ratio:390 / 867}aside{padding:34px 0 0}h3{font-size:16px;margin:0 0 7px}aside p{font-size:15px;color:#625747;margin:0 0 26px}.benefit{background:#e9e0cc;border-radius:12px;padding:20px;font-weight:700;font-size:16px}.footnote{font-size:12px;color:#7a7060;margin-top:24px;max-width:1070px}footer{padding:24px 32px 50px;max-width:1370px;margin:auto;font-size:13px;color:#776b58}@media(max-width:980px){.comparison{grid-template-columns:1fr 1fr;gap:24px}aside{grid-column:1/-1;padding:0;max-width:760px}aside p{margin-bottom:18px}header,main{padding-left:22px;padding-right:22px}}@media(max-width:560px){.comparison{grid-template-columns:1fr}figure{max-width:390px;margin:0 auto 18px;width:100%}.sectionhead h2{font-size:25px}aside{grid-column:auto}.phone,figure svg{border-radius:22px}.intro{font-size:16px}}@media print{@page{size:A4 landscape;margin:10mm}header,footer{display:none}main{padding:0;max-width:none}section{break-before:page;border:0;padding:0}.sectionhead h2{font-size:19px}.sectionhead>p{font-size:10px;margin-bottom:12px}.comparison{grid-template-columns:1fr 1fr 1.1fr;gap:22px}.phone,figure svg{height:148mm;width:auto;max-width:100%;box-shadow:none;border-radius:10px}figcaption{font-size:9px;margin-bottom:6px}figcaption small{font-size:8px}aside{grid-column:auto;padding-top:20px}h3{font-size:11px}aside p{font-size:10px;margin-bottom:17px}.benefit{font-size:11px;padding:12px}.footnote{font-size:8px;margin-top:9px}}
</style></head><body><header><div class="eyebrow">IRONLOG · VISUELLE GEGENÜBERSTELLUNG · 14.09.2026</div><h1>Weniger Aufwand zwischen zwei Sätzen.</h1><p class="intro">Links die vorhandene App aus dem Praxistest. Rechts konkrete Vorschläge für schnellere Eingabe, verständlichere Planung und einen klaren Trainingsabschluss.</p><div class="notice"><b>Entwürfe zur Diskussion.</b> Alle Nachher-Ansichten sind schematische Vorschläge mit Beispieldaten. Die App wurde nicht verändert. Dunkle Flächen und orange Akzente bleiben als vertraute Gestaltung erhalten. Die Bildschirme sind statisch.</div><nav>'''+nav+'''</nav></header><main>'''+''.join(html_sections)+'''</main><footer>Grundlage: gespeicherte Screenshots und Auditbefunde vom 13. September 2026. Kein neuer Emulatorstart. Browserdarstellung nicht geprüft; PDF wurde separat als Dokument gerendert und visuell geprüft.</footer></body></html>'''
(OUT/'IronLog-vorher-nachher.html').write_text(html)
(OUT/'README.md').write_text('''# IronLog: visuelle Vorher-Nachher-Gegenüberstellung

Entwurf vom 14. September 2026 auf Basis der Auditaufnahmen vom 13. September.

- HTML: `IronLog-vorher-nachher.html`, selbstständig mit eingebetteten Screenshots/SVGs.
- PDF: `../../../output/pdf/IronLog-vorher-nachher.pdf`.
- Builder: `build_comparison.py` (ReportLab, Arial-Systemschriften).
- Sieben Vergleiche: Logging Android, Logging iOS, Übungsauswahl, individuelle Satzplanung, Teilabschluss, Ergebnis und optionale Planübernahme.

Links stehen unveränderte Testaufnahmen. Rechts stehen schematische Entwürfe, keine implementierte App. Die Planübernahme ist eine optionale Erweiterung nach weiterem Bestandscheck. Es wurden keine Produktdateien geändert oder Emulatoren gestartet.

PDF-Seiten werden mit Poppler gerendert und geprüft. Die HTML-Browserdarstellung bleibt ungeprüft; es wird kein erneuter Zugriff auf die zuvor blockierte lokale Browser-URL versucht.
''')
print(json.dumps({'pdf':str(PDF),'html':str(OUT/'IronLog-vorher-nachher.html'),'pages':len(pages)},ensure_ascii=False))
