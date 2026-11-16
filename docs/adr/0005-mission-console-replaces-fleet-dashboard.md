# ADR-0005: mission-console replaces fleet-dashboard

> Version française : [`0005-mission-console-replaces-fleet-dashboard.fr.md`](0005-mission-console-replaces-fleet-dashboard.fr.md)

## Status
Accepted — 2026-11-16

## Context
The sister project `fleet-dashboard` was planned as a Java/Vaadin screen to watch the
system in real time (detections sent by the Raspberry Pi, onboard status). It never went
beyond a README.

The roadmap now needs a full web operator console: prepare a mission on an S-57 nautical
chart, receive and edit the fleets recommended by the server, then follow the running
mission and send commands. Real-time monitoring is only one part of it.

## Decision
- The new repository [`gakkel-labs/mission-console`](https://github.com/gakkel-labs/mission-console)
  holds the web operator console. `fleet-dashboard` is archived.
- The Java server (`swarm-simulator`) keeps all computation; mission-console only displays
  and sends operator input (REST + WebSocket). Unity stays the 3D view.
- The front-end framework (Angular, React or Vaadin) is not chosen yet: it is
  [mission-console#1](https://github.com/gakkel-labs/mission-console/issues/1), at the start
  of roadmap step 4, recorded in its own ADR.

## Consequences
- One web project instead of two: clearer scope, no duplicate tracking screen.
- Earlier ADRs that name `fleet-dashboard` (0002, 0003) keep their decisions; they carry a
  note pointing here.

## Alternatives considered
- **Keep the name `fleet-dashboard`**: rejected, the console is about missions, not only
  the fleet.
- **Keep both projects**: rejected, the tracking screen would exist twice.
