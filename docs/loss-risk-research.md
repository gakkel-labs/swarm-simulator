# Underwater drone loss risk: what studies say

[Français](loss-risk-research.fr.md)

This document gathers the studies read to choose the **loss risk** levels (`LossRiskLevel`).
The decision itself is in [ADR-0012](adr/0012-loss-risk-replaces-the-predator.md). The term is
defined in the [glossary](produit/glossaire.md).

## In short

- An underwater drone is almost never lost because something chases it. It is lost through
  ordinary faults: leak, battery, navigation, implosion at depth.
- A fault becomes a loss mostly when nobody can go and get the drone: ice, great depth, bad
  weather, a support ship that is far away or stuck.
- The risk grows with the time (or distance) spent diving. For the same fault, it is about
  5 times higher under sea ice and 9 times higher under an ice shelf than in open water.
- Almost all studies are about **one drone**. The risk that is specific to a swarm is still an
  open research question.

## How researchers measure loss risk

The reference is the team from Southampton and the National Oceanography Centre (Mario Brito,
Gwyn Griffiths). They started this work after Autosub2 was lost under the ice in 2005. Their
method has three steps ([Brito, Griffiths and Challenor, 2010](https://nora.nerc.ac.uk/id/eprint/169592/)):

1. **List the real faults.** The Autosub3 log has 63 faults or incidents over 38 missions,
   1,931 km in total.
2. **Ask experts.** Eight experts give, for each fault, the chance that it loses the drone in
   four environments: open water, coastal water, under sea ice, under an ice shelf.
3. **Compute a survival curve.** A Kaplan-Meier estimator (a classic method from medicine)
   gives the chance that the drone survives as a function of the distance travelled.

In 2016, the same authors add the **support ship**: the faster it can reach a drone in trouble,
the lower the risk. A ship at 10 knots usually lowers the risk by one class; a ship stuck in
ice raises it by one class ([Brito and Griffiths, 2016](https://nora.nerc.ac.uk/id/eprint/512604/)).
For Gakkel, this links directly to the **drone carrier**.

## The figures found

**Chance that a fault loses the drone** (median of 8 experts, Autosub3,
[Brito et al. 2010, table I](https://nora.nerc.ac.uk/id/eprint/169592/))

| Environment | Median | 25 % quartile | 75 % quartile | Compared with open water |
|---|---|---|---|---|
| Open water | 1.8 % | 0.83 % | 2.6 % | × 1 |
| Coastal water | 2.1 % | 0.83 % | 3.7 % | × 1.2 |
| Under sea ice | 8.8 % | 4.5 % | 17 % | × 4.9 |
| Under an ice shelf | 17 % | 7.2 % | 40 % | × 9.4 |

**Chance of loss for a 24 h polar mission**
([Journal of Marine Science and Engineering, 2023](https://www.mdpi.com/2077-1312/11/10/1976),
fuzzy fault tree)

| Causes included | Chance of loss |
|---|---|
| Internal faults only | 0.18 % |
| Polar environment, good conditions | 4.4 % |
| Polar environment, moderate conditions | 13.2 % |
| Polar environment, bad conditions | 35 % |

**Coastal waters: two REMUS-100 in California**
([Griffiths, Brito, Robbins and Moline, 2009](https://nora.nerc.ac.uk/id/eprint/169184/)).
This is the best data set found for commercial drones used near the coast.

| Measure | Value |
|---|---|
| Missions | 189 (2003 to 2009), 4,031 km |
| Faults or incidents logged | 507, one every 8 km |
| Successful missions | 91.5 % |
| Drones lost | 0 |
| Chance of loss for a 14 km mission (expert judgment) | 0.3 % (optimists) to 1.9 % (pessimists) |
| Mean distance before a loss (expert judgment) | 3,248 km (optimists) to 504 km (pessimists) |

The authors themselves find the pessimistic experts too pessimistic, since the two drones made
189 missions without a loss.

**A whole campaign.** Under the Pine Island glacier (Antarctica, 2009), the team estimated the
risk of losing Autosub3 at 26 % to 96 % depending on the plan, before precautions. The owner
accepted 10 % to 23 %. With open-water trials and close watch over the first 48 km, the
shortest plan dropped to 3.5 %. The drone came back.

**Underwater gliders.** While the Seaglider was developed, 8 of the first 10 units were lost,
then 2 of the next 12 (2005 figures, quoted by Brito et al. 2010). The risk drops a lot as a
drone model matures.

## Outside polar waters: coasts, structures, ships

There is less data outside polar waters. No study with figures was found on work near drilling
platforms.

- **Near structures.** In the REMUS-100 log, the cases the experts found most dangerous are
  possible collisions: a drone stopped under a pontoon among the piles, a drone heading for the
  rocks after a beacon mix-up. A review of offshore structure inspection notes that a drone
  without a cable cannot get tangled, but there is less you can do if it has a problem
  ([JMSE, 2023](https://www.mdpi.com/2077-1312/11/11/2172)).
- **Ships at the surface.** For underwater gliders in the North Sea, Merckelbach turns the ship
  density measured by **AIS** into a map of the chance of loss by collision
  ([Merckelbach, 2013](https://link.springer.com/article/10.1007/s00773-012-0189-7), abstract
  only). The Gakkel mission console already shows AIS.

## What the 2021 review and the 2023 thesis add

The review of 42 papers ([Chen, Bose, Brito et al., 2021](https://eprints.soton.ac.uk/451143))
and Xi Chen's thesis (Memorial University of Newfoundland, 2023) confirm the model and add four
ideas.

1. **Risk depends on the phase.** Launch and recovery are the riskiest; transit while diving is
   the safest. See issue #178.
2. **The middle of the water column is the safest.** In a glider scenario, the chance of loss
   rises near the surface when traffic is dense, and near the drone's maximum depth.
3. **Risk changes during the mission.** Higher waves or a strong current raise it within an
   hour. One fixed level for the whole mission is therefore a simplification.
4. **Missions with several drones are little studied.** The risk specific to a swarm (two
   drones that collide, communication between drones) is still open. This is Gakkel's ground.

## The real causes of loss

The log of Autosub3's 63 faults shows these families:

- **Technical faults**: motor, battery, flooded rudder actuator, leaking connectors.
- **Navigation and communication**: no GPS fix, silent acoustic telemetry, a speed sensor that
  mistakes the surface for the seabed.
- **Surfacing and recovery**: recovery line caught in the propeller, rudder or antenna damaged
  while lifting the drone on board.
- **Collision with terrain**: in 2009, Autosub3 hit the ice in a crack under the Pine Island
  glacier, then came back.
- **Human error**: manufacturing, assembly, maintenance, mission set-up.

No figures were found on fishing nets.

## Hydrothermal vents

Drones do not go through vent gases: they fly above and follow the plume, which is what they
are looking for. In 2024, the Sentry drone followed a plume over the Vailulu'u seamount, flying
several tens of metres above the seabed
([NOAA Ocean Exploration, 2024](https://oceanexplorer.noaa.gov/expedition/24autonomous-fluid-flow/)).
The real danger is the terrain: vent chimneys are tall and uneven.

## Well-known losses

| Date | Drone | Where | What happened |
|---|---|---|---|
| May 2014 | [Nereus](https://www.whoi.edu/press-room/news-release/nereus-lost/) (WHOI) | Kermadec Trench, 9,990 m | Probable implosion under pressure. |
| March 2010 | [ABE](https://www.whoi.edu/press-room/news-release/pioneering-deep-sea-robot-lost-at-sea/) (WHOI) | Off Chile, about 3,000 m | Contact lost on reaching the seabed; probable implosion of a buoyancy sphere. |
| 2005 | [Autosub2](https://nora.nerc.ac.uk/id/eprint/141098/) (NOC) | Under the Fimbulisen ice shelf | Technical fault of the drone. The inquiry found that connectors and leaks matter more than outside threats. |

Xi Chen's thesis also lists drones that were "lost" and then found: Autosub Long Range off
Ireland in 2014 (three months later), a Hugin under sea ice in 2019 (four days later).

## Cross-check of the Gakkel levels

Studies talk about distance; Gakkel talks about **24 h of diving**. To compare, we convert with
the drone's speed (about 6 km/h, so 144 km in 24 h). These conversions are our own
calculations, not published figures.

| Source | Published figure | Converted to 24 h of diving |
|---|---|---|
| JMSE 2023, internal faults only | 0.18 % per 24 h | 0.18 % |
| REMUS-100, observed | 0 losses in 4,031 km (about 28 days) | 0 % observed; at most about 10 % (rule of three) |
| REMUS-100, optimistic experts | one loss every 3,248 km | about 4 % |
| REMUS-100, pessimistic experts | one loss every 504 km | about 25 % |
| Autosub3, open water | 1 fault every 31 km, 1.8 % of faults lose the drone | about 7 % (before precautions) |
| JMSE 2023, polar, good conditions | 4.4 % per 24 h | 4.4 % |
| JMSE 2023, polar, moderate conditions | 13.2 % per 24 h | 13.2 % |
| JMSE 2023, polar, bad conditions | 35 % per 24 h | 35 % |

The **rule of three** is a simple statistical rule: after n trials with no failure, the failure
rate is, with 95 % confidence, below about 3/n. Here n is about 28 days of diving, so the
ceiling is about 10 % per day.

| Level | Value | Sources that support it | Confidence |
|---|---|---|---|
| `NONE` | 0 % | None: a setting for the live demo | Not applicable |
| `LOW` | 0.2 % | JMSE 2023, internal faults only (0.18 %) | Good |
| `MEDIUM` | 2 % | Between the 0 observed on the REMUS-100 and the expert estimates (4 % to 25 %) | Low |
| `HIGH` | 5 % | JMSE 2023, polar in good conditions (4.4 %); optimistic REMUS experts (about 4 %) | Medium |
| `EXTREME` | 15 % | JMSE 2023, polar in moderate conditions (13.2 %) | Medium |

The ratio between `MEDIUM` and `HIGH` or `EXTREME` (2.5 to 7.5) is in the same range as the one
Brito 2010 finds between open water and ice (5 to 9).

`MEDIUM` is the least certain value: no study gives a loss per 24 h in coastal waters directly.
It is lower than the expert estimates, but the drones actually observed lost nothing. It suits a
mature drone; for a prototype, `HIGH` is the safer choice.

## Limits

- The figures come mostly from **polar** work and from **expert judgment** on one or two drone
  models. They are orders of magnitude.
- Real risk drops a lot as a drone model matures (Seaglider: 8 losses out of 10, then 2 out
  of 12).
- One level for the whole mission ignores the phase (launch, recovery), the depth and the
  weather.
- No figures on fishing nets, drilling platforms or hitting vent chimneys.

## Sources

Read for this document:

- Brito, Griffiths, Challenor, [Risk analysis for Autonomous Underwater Vehicle Operations in Extreme Environments](https://nora.nerc.ac.uk/id/eprint/169592/), *Risk Analysis* 30, 2010.
- Brito, Griffiths, [A Bayesian approach for predicting risk of AUV loss during their missions](https://nora.nerc.ac.uk/id/eprint/512604/), *Reliability Engineering & System Safety* 146, 2016.
- [Risk Analysis of Autonomous Underwater Vehicle Operation in a Polar Environment Based on Fuzzy Fault Tree Analysis](https://www.mdpi.com/2077-1312/11/10/1976), *Journal of Marine Science and Engineering* 11(10), 2023.
- Griffiths, Brito, Robbins, Moline, [Reliability of two REMUS-100 AUVs based on fault log analysis and elicited expert judgment](https://nora.nerc.ac.uk/id/eprint/169184/), UUST 2009.
- [Exploring Autonomous and Remotely Operated Vehicles in Offshore Structure Inspections](https://www.mdpi.com/2077-1312/11/11/2172), *Journal of Marine Science and Engineering* 11(11), 2023.
- Merckelbach, [On the probability of underwater glider loss due to collision with a ship](https://link.springer.com/article/10.1007/s00773-012-0189-7), *Journal of Marine Science and Technology*, 2013 (abstract only).
- Chen, Bose, Brito, Khan, Thanyamanta, Zou, [A Review of Risk Analysis Research for the Operations of AUVs](https://eprints.soton.ac.uk/451143), *Reliability Engineering & System Safety* 216, 2021.
- Xi Chen, [Risk analysis and decision making for autonomous underwater vehicles](https://memorial.scholaris.ca/items/b41d1287-e812-46d4-9c9e-2a056cef5d77), thesis, Memorial University of Newfoundland, 2023 (chapters 2 and 3).
- Strutt, [Report of the inquiry into the loss of Autosub2 under the Fimbulisen](https://nora.nerc.ac.uk/id/eprint/141098/), NOC Southampton, 2006.
- WHOI, [Nereus](https://www.whoi.edu/press-room/news-release/nereus-lost/), 2014; [ABE](https://www.whoi.edu/press-room/news-release/pioneering-deep-sea-robot-lost-at-sea/), 2010.
- NOAA Ocean Exploration, [Autonomous Localization of Seafloor Fluid Flow Sources](https://oceanexplorer.noaa.gov/expedition/24autonomous-fluid-flow/), 2024.
