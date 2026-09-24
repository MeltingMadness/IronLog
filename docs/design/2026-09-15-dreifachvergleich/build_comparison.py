from pathlib import Path
from bs4 import BeautifulSoup
from base64 import b64encode
from html import escape
import json, hashlib
ROOT=Path(__file__).resolve().parents[3]
OUT=Path(__file__).resolve().parent
FIRST=ROOT/'docs/design/2026-09-14-vorher-nachher'
SECOND=ROOT/'docs/design/2026-09-15-opendesign'
EVIDENCE=ROOT/'docs/validation/2026-09-13-competitor-audit/evidence'
CASES=[
 ('01-logging-android','Trainingslogging · Android','logging.svg','ironlog-android-weight.png','Großer aktiver Satzeditor; Bestätigung liegt unterhalb des sichtbaren Ausschnitts.','Kompakte Tabelle, sichtbare Pause und Vorschau auf die nächste Übung.','Stärker getrennte Satzkarten; Herkunft der Werte und feste Hauptaktion.'),
 ('02-logging-ios','Trainingslogging · iOS','ios.svg','ironlog-ios-second-set-default.jpg','Eigenes Formular für jeden Satz; der zweite Satz beginnt hier bei 0 kg.','Satzübersicht mit direkter Bestätigung und kompaktem Pausenbereich.','Gruppierte iOS-Ansicht mit Satzliste und separaten Eingabefeldern.'),
 ('03-exercise-picker','Übungen auswählen','auswahl.svg','ironlog-android-picker.png','Eine Übung auswählen und den Picker für die nächste wieder öffnen.','Mehrfachauswahl mit Checkboxen und deutlich hervorgehobenen Zeilen.','Mehrfachauswahl mit ruhigeren Auswahlmarkierungen und fester Aktion.'),
 ('04-plan-create','Individuelle Satzvorgaben','satzplanung.svg','ironlog-android-plan-bench.png','Einheitliche Vorgabe pro Übung. Die eingeblendete Tastatur gehört zum Emulator.','Tabellarische Vorgaben mit großen Gewichts- und Wiederholungsfeldern.','Gruppierte Satztypen und eigene Karte für die Progressionsregel.'),
 ('05-finish-early','Training vorzeitig beenden','abschluss.svg','ironlog-android-finish.png','Allgemeine Bestätigung ohne Anzahl der noch offenen Sätze.','Offener Umfang und zwei eindeutige Optionen im Abschlussablauf.','Dialog mit 2 von 9 erfassten Sätzen und konkreten offenen Übungen.'),
 ('06-completion','Gespeichertes Ergebnis','ergebnis.svg','ironlog-android-finished.png','Nach dem Abschluss erscheint wieder die Startseite.','Kompakte Ergebnisansicht mit Leistungswerten und nächsten Schritten.','Deutlich markierter Teilabschluss, Kennzahlen und direkter Detailzugang.'),
 ('07-plan-changes','Planänderungen bewusst wählen','planuebernahme.svg','ironlog-android-history-detail.png','Verlauf mit gespeicherten Satzdetails; appweite Übernahmefunktion nicht abschließend geprüft.','Änderungen am Plan separat und ausdrücklich bestätigen.','Heutige Leistung und Planvorgabe vergleichen; Standard ist Plan beibehalten.')
]
CSS=SECOND.joinpath('ironlog-boards.css').read_text()
EXTRA='''
html,body{margin:0;width:1440px;min-width:1440px;overflow:hidden;background:#101317}
.board.threeway{width:1440px;height:1100px;padding:32px 44px 24px;background:#101317}
.threeway .triple-header{height:78px;display:flex;justify-content:space-between;align-items:start}
.board.threeway .triple-header h1{margin-top:8px;font-size:31px;max-width:1150px;line-height:1.14}
.triple-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:32px}
.triple-label{height:60px;border-bottom:1px solid #353a43;display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}
.triple-label h2{font-size:19px;font-weight:650;font-family:var(--font-display)}
.triple-label span{font-size:11px;color:#a8b0bc;letter-spacing:.04em}
.screen-slot{height:780px;display:flex;align-items:flex-start;justify-content:center}
.screen-slot>.screen-image{display:block;width:auto;height:780px;max-width:100%;object-fit:contain;border-radius:22px}
.screen-slot .phone{flex-shrink:0;box-shadow:none}
.column-note{font-size:13px;line-height:1.45;color:#bac1cc;margin-top:18px;max-width:416px;min-height:54px}
.triple-footer{display:flex;align-items:center;justify-content:space-between;border-top:1px solid #30353e;padding-top:13px;font-size:11px;color:#8f99a8;margin-top:10px}
.triple-footer a{color:#cbd2dc;text-decoration:none}
'''
def uri(p):
 mime='image/svg+xml' if p.suffix=='.svg' else 'image/jpeg' if p.suffix=='.jpg' else 'image/png'
 return 'data:'+mime+';base64,'+b64encode(p.read_bytes()).decode()
manifest=[]
for i,(slug,title,svg,original,a,b,c) in enumerate(CASES,1):
 soup=BeautifulSoup((SECOND/(slug+'.html')).read_text(),'html.parser')
 phone=soup.select_one('.phone'); assert phone
 board_id=soup.select_one('main')['data-od-id']
 cols=[]
 for label,date,content,note in [
  ('Original','AUDIT · 13.09.',f'<img class="screen-image" src="{uri(EVIDENCE/original)}" alt="Original: {escape(title)}">',a),
  ('Erster Entwurf','14.09.',f'<img class="screen-image" src="{uri(FIRST/svg)}" alt="Erster Entwurf: {escape(title)}">',b),
  ('Open-Design-Entwurf','15.09.',str(phone),c)]:
  cols.append(f'<section><div class="triple-label"><h2>{label}</h2><span>{date}</span></div><div class="screen-slot">{content}</div><p class="column-note">{escape(note)}</p></section>')
 footer='Identische Bildhöhe · Originale und bestehende Entwürfe unverändert zusammengestellt · Entwürfe sind nicht implementiert'
 if i==7:footer='Planübernahme: Vorschlag; eine appweite Funktionslücke ist nicht abschließend bestätigt.'
 page=f'''<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=1440,height=1100,initial-scale=1"><title>IronLog · {escape(title)} · Drei Fassungen</title><style>{CSS}\n{EXTRA}</style></head><body><main class="board threeway" data-od-id="{board_id}"><header class="triple-header"><div><div class="eyebrow">IRONLOG / ORIGINAL → ERSTER ENTWURF → OPEN DESIGN</div><h1>{escape(title)}</h1></div><div class="case-index">{i:02d} / 07</div></header><div class="triple-grid">{''.join(cols)}</div><footer class="triple-footer"><span>{footer}</span><a href="index.html">Alle Vergleiche ↗</a></footer></main></body></html>'''
 p=OUT/(slug+'.html');p.write_text(page)
 manifest.append({'file':p.name,'original':str(EVIDENCE/original),'firstDraft':str(FIRST/svg),'openDesignDraft':str(SECOND/(slug+'.html')),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
links=''.join(f'<a href="{slug}.html"><span>{i:02d}</span><b>{escape(title)}</b><small>Original · Erster Entwurf · Open Design →</small></a>' for i,(slug,title,*_) in enumerate(CASES,1))
(OUT/'index.html').write_text('''<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>IronLog · Drei Fassungen</title><style>body{background:#101317;color:#f2f0e9;font:16px -apple-system,BlinkMacSystemFont,sans-serif;margin:0;padding:48px;max-width:1000px}h1{font-size:36px}p{color:#bac1cc;line-height:1.6}a{display:grid;grid-template-columns:36px 1fr;gap:10px;padding:22px;margin:12px 0;background:#1c222c;border:1px solid #343b47;border-radius:14px;text-decoration:none;color:inherit}a span{color:#ff9e25;grid-row:span 2}small{color:#adb7c7}a:hover{border-color:#ff9e25}</style></head><body><p>IRONLOG / DESIGNVERGLEICH</p><h1>Drei Fassungen, direkt nebeneinander</h1><p>Links die Originalaufnahme, in der Mitte der erste Entwurf vom 14. September, rechts der Open-Design-Entwurf vom 15. September. Die Ansichten werden proportional auf dieselbe Höhe gebracht. Alle sieben Vergleiche sind auch als PNG im gleichen Ordner verfügbar.</p>'''+links+'</body></html>')
(OUT/'sources.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
(OUT/'README.md').write_text('# IronLog: Original – erster Entwurf – Open Design\n\nSieben Gegenüberstellungen der bereits vorhandenen Fassungen. Originalbilder und SVGs werden unverändert eingebettet, der neue Phone-Bereich wird mit seiner bestehenden CSS-Basis aus dem Open-Design-Board übernommen. Gleiche Bildhöhe, ursprüngliche Seitenverhältnisse. Kein neuer Produktentwurf, keine Implementierung.\n\n`index.html` öffnet die Übersicht. Alle HTML-Dateien sind eigenständig mit eingebetteten Assets. `sources.json` dokumentiert die Herkunft.\n')
print('Created',len(manifest),'three-way boards and index')
