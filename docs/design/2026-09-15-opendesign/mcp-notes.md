# Open Design lokal per MCP

Am 15.09.2026 mit Open Design 0.22.2 geprüft. Verwendet wurde der installierte stdio-Server:

```text
/Users/maert/.nvm/versions/node/v24.16.0/bin/node
/Applications/Open Design.app/Contents/Resources/app/prebundled/daemon/daemon-cli.mjs
mcp --daemon-url http://127.0.0.1:<Port des laufenden lokalen Daemons>
```

Die Argumente sind einzelne Einträge der MCP-command-Liste, nicht eine Shell-Zeile. Der Port gehört zur laufenden Desktop-Sitzung und darf nicht als dauerhaft fest angenommen werden. Im vorhandenen Setup war der gespeicherte IPC-Pfad nicht erreichbar; die explizite lokale Daemon-Adresse funktionierte.

Nachgewiesen: initialize, tools/list, list_agents, list_skills, create_project und start_run. Die neue Projekt-ID lautet ironlog-ux-september-2026. Der Designlauf wird mit get_run abgefragt. Bildexporte erfolgen über den offiziellen `od export`-Befehl mit dem Chromium der Desktop-App.

OpenCode ist eine mögliche Agent-Laufzeit und ein MCP-Client; Open Design ist hier die erzeugende Designanwendung. Für diesen Lauf wurde die in Open Design verfügbare lokale Codex-CLI-Laufzeit verwendet.

Dokumentation:
- https://dev.opencode.ai/docs/mcp-servers/
- https://github.com/nexu-io/open-design
- https://github.com/nexu-io/open-design/releases/tag/open-design-v0.22.2

Installation: offizieller ARM64-Download, SHA-256 geprüft, notarisiertes DMG und installierte App-Signatur geprüft. Fehlende Paketdateien wurden aus dem offiziellen DMG wiederhergestellt; zusätzliche .ignored-Dateien wurden außerhalb des App-Bundles gesichert. Die Ursache ihrer Entstehung wurde nicht abschließend bestimmt.

Während des Laufs fehlten Paketdateien im installierten Bundle erneut. Deshalb nutzte die MCP-Brücke zeitweise die identische, schreibgeschützte CLI aus dem offiziellen gemounteten DMG. Die Designarbeit und Exporte blieben am laufenden lokalen App-Daemon. Am Ende wurde das installierte Bundle erneut aus dem DMG hergestellt; codesign --verify --deep --strict bestand. Die Ursache des wiederholten Paketverschiebens ist offen.
