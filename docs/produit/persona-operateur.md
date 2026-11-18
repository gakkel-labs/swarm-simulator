# Persona: the mission operator

> Version française : [`persona-operateur.fr.md`](persona-operateur.fr.md)

This page describes Gakkel's main user: the person who prepares and monitors a drone mission. The reference mission is a wreck search, but Gakkel must serve any type of mission.

## The Gakkel concept in short

- A **swarm of low-cost drones** performs a first detection over the area.
- When the swarm finds an interesting contact, a **more expensive specialised drone** leaves the **drone carrier** to confirm it on site. Each fleet can have one or more, chosen for the mission.
- The **drone carrier** communicates with the swarm and relays the information to the **operations centre**, where the operator works.

## Who he is

- Michel, 41, underwater operations engineer at a public marine research body
- 12 years of experience on sea campaigns, including 3 wreck searches
- Comfortable with sonars and nautical charts, not a developer

## His work context

- Works from the operations centre of a research vessel, at sea for several weeks
- Coordinates 3 to 5 drone fleets at the same time (each fleet is a system: a swarm of low-cost drones, a drone carrier and one or more specialised drones for the mission)
- Does not talk to the drones directly: he receives information from the drone carriers
- Reports to the search director and the investigators, and works with the drone technicians and the crew
- Round-the-clock shifts, fatigue, slow satellite connection

## What he wants to achieve

- Find a sunken wreck (aircraft or ship) in an area of 50 to 100 km²
- Cover the whole area with no gaps and no overlaps
- Quickly validate each sonar contact (wreck or rock?) by deciding when to send a specialised drone. Depending on the mission, this can be automated: during preparation Michel chooses between manual decision and automatic dispatch, and can take back control at any time
- Keep a complete record: covered areas, contacts, decisions

## His difficulties

- Slow, intermittent acoustic link between the swarm and the drone carrier: information arrives late
- Short weather window to launch and recover the drones
- Too many sonar contacts to sort, while the specialised drones cannot check them all

## His fears

- Missing the wreck in an area marked as "covered"
- Losing an expensive drone
- Making a decision on incomplete information
- Missing the weather window

## His information needs

- **Before**: area, depth, weather (GO/NO-GO), AIS traffic, available fleets, proposed coverage plan
- **During**: status of each fleet (position, battery, active drones), coverage map, contacts reported by the drone carrier, link status, position and status of the specialised drones
- **After**: final map, list of contacts with their status (confirmed, dismissed, to review), mission report

## Key moments of a mission

1. **Prepare**: define the area and the objective, check weather and traffic, validate the proposed plan or build his own plan without simulation, choose whether specialised drones will be sent manually or automatically.
2. **Launch**: deploy the fleets during the weather window.
3. **Detect**: the swarms cover the area, the drone carriers report contacts.
4. **Confirm**: Michel picks the contacts to check and sends a specialised drone, or lets Gakkel do it automatically if he planned it.
5. **Close**: recover the drones and produce the report.

## His expectations of Gakkel

- Automatically propose the best plan (number of fleets, swarm paths)
- Also let him create a mission alone, from start to end, without the simulation
- See everything on a single screen, fleet by fleet
- Rank contacts to know which ones deserve a specialised drone
- Be alerted only when it matters

## Other possible missions

Cable and pipeline inspection, seabed mapping, mine countermeasures.

> "I need to know what is happening, where my drones are and whether my mission is going as planned, without having to search for information across several tools."
