# ADR-0006: Offer a detailed view and a simple view of the Unity scene

[Français](0006-detailed-and-simple-view.fr.md)

## Status
Proposed
Date: 2026-11-21

## Context
[ADR-0003](0003-minimal-3d-assets.md) chose plain shapes (capsules, sphere, cylinders) so that
the swarm behaviour stays easy to read and the scene stays light.

Step 1 of the roadmap asks for a nice Unity scene to present the project. Since then the scene has
gained 3D models and decoration: the ROV model for drones (#73), the real seabed with textures and
rocks (#75), a shipwreck as the search target, a Greenland shark as the predator,
and hydrothermal chimneys as obstacles.

These two needs pull in opposite directions:
- **A demo** (a video, a presentation) needs a scene that looks like a real seabed search.
- **The operator** follows a mission. They care about where the drones are, what they detect,
  and whether the swarm stays together. Decoration costs rendering time (more triangles, textures) and can hide what matters.

## Decision
Every decorative element of the scene has two versions, and one switch chooses between them:

| Element | Detailed view | Simple view |
|---|---|---|
| Drone | ROV model | capsule |
| Predator | Greenland shark model | red sphere |
| Search target | wreck model on the seabed: an old wooden ship (an aircraft model is also available) | plain ship shape (boxes, cylinder) |
| Obstacle | hydrothermal chimney (open crater, black smoke) | plain dark column |

- The **V** key switches the whole scene between detailed and simple during Play.
  The **Show Rov Model** box of `SwarmVisualizer` sets the starting view.
- The rocks on the seabed have their own switch (**B** key, **Use Seabed Rocks**), because they
  cost the most and are not tied to a simulation object. The simple view always hides them.
- The code keeps one shared state, `DetailedView`. Each visual element listens to it and shows
  one of its two versions. Adding a new element means building both versions.
- The simple view stays the reference for reading the behaviour; the detailed view is for showing
  the project.

## Consequences
+ The same scene works for a demo and for following a mission.
+ The cost of the decoration is optional: on a small machine, the simple view and no rocks.
+ The models are made by scripts (`tools/models/`, procedural meshes in code), so they stay
  small and can be rebuilt.
− Each new visual element needs two versions: a little more work.
− The detailed view shows things the simulation does not know: the wreck lies on the seabed
  under the target point. This gap closes when the backend models the seabed and the wreck
  (the `Contact` to detect).
− For now an obstacle is a sphere for the server, but it is drawn as a chimney standing on the
  seabed: a drone passing under the sphere goes through the chimney on screen. The fix is on
  the server side: obstacles with real shapes, such as a vertical column (#142).

## Alternatives considered
- **Detailed only**: rejected. It costs rendering time even when nobody looks at the decoration,
  and it makes the swarm harder to read.
- **Simple only (ADR-0003 as is)**: rejected for step 1. The project needs a scene that people
  understand at first sight as an underwater search.
- **One switch per element**: rejected, except for the rocks. Too many keys for the same idea.
- **Unity quality levels**: rejected for now. They change shadows and resolution, not which
  models are shown.
