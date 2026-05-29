# Unity integration — thread-safe gRPC bridge

> How the swarm server's 20 Hz WorldState stream reaches Unity GameObjects without crashes or data races.
> Companion reading: [`02-grpc-contract.md`](02-grpc-contract.md).

---

## 1. The constraint

Unity enforces a hard rule: **only the main thread may modify scene objects** (`Transform`, `Renderer`, component fields, …). Violating it crashes the editor or produces silent corruption.

gRPC data arrives on a **.NET thread-pool Task** — a background thread managed by the runtime, never the Unity main thread.

Naively calling `transform.position = …` from the gRPC callback crashes immediately.

---

## 2. Full thread-flow diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Java server process                                                      │
│                                                                           │
│  sim-loop thread (30 Hz)                                                  │
│  SimulationLoop.tick()                                                    │
│    agent.update(pos, vel)   ← writes volatile fields in Agent.java        │
│         │                                                                 │
│         │ volatile write (Agent.position / Agent.velocity)                │
│         ▼                                                                 │
│  swarm-broadcaster thread (20 Hz)                                         │
│  SwarmObserverImpl.broadcast()                                            │
│    buildWorldState()        ← reads volatile fields, builds proto message │
│    obs.onNext(state)        ← pushes WorldState over gRPC stream          │
└────────────────────┬─────────────────────────────────────────────────────┘
                     │
                     │  gRPC server-streaming  (HTTP/2, TCP)
                     │  WorldState pushed at 20 Hz
                     │
┌────────────────────▼─────────────────────────────────────────────────────┐
│  Unity process                                                            │
│                                                                           │
│  .NET Task (background)                                                   │
│  WorldStateReceiver.ReceiveLoopAsync()                                    │
│    await foreach ws in stream                                             │
│    MainThreadDispatcher.Enqueue(() => OnWorldStateReceived(ws))           │
│         │                                                                 │
│         │ ConcurrentQueue<Action>  ← thread-safe handoff point           │
│         │                                                                 │
│         ▼                                                                 │
│  Unity main thread (60 fps)                                               │
│  MainThreadDispatcher.ProcessQueue()  ← coroutine, runs each frame       │
│    action()  →  OnWorldStateReceived(ws)                                  │
│    visualizer.Apply(ws)   ← safe to touch GameObjects here                │
└──────────────────────────────────────────────────────────────────────────┘
```

Three independent clocks: **sim-loop at 30 Hz**, **broadcaster at 20 Hz**, **Unity at ~60 fps**. The queue absorbs the timing mismatch.

---

## 3. Synchronisation on the Java side

The broadcaster thread reads agent state while the sim-loop thread writes it concurrently. No locks are used — instead `position` and `velocity` are declared `volatile`:

```java
// Agent.java
private volatile Vector3D position;
private volatile Vector3D velocity;

public void update(Vector3D newPosition, Vector3D newVelocity) {
    this.position = newPosition;   // volatile write
    this.velocity = newVelocity;
}
```

A `volatile` reference write in Java is **atomic** and immediately visible to other threads. `Vector3D` is immutable (all fields final), so a reader can never see a half-constructed object.

> Internal `SimulationLoop` fields (`tickCount`, `lastCentroid`, …) are **not** volatile — they are confined to the sim-loop thread by design and must not be exposed to the broadcaster without added synchronisation. See the comment at `SimulationLoop.java:44`.

---

## 4. The ConcurrentQueue pattern (Unity side)

### 4.1 MainThreadDispatcher

A singleton `MonoBehaviour` that owns the queue and drains it every frame via a coroutine:

```csharp
// MainThreadDispatcher.cs
private readonly ConcurrentQueue<Action> _queue = new();

public static void Enqueue(Action action)
{
    Instance._queue.Enqueue(action);   // called from any thread
}

private IEnumerator ProcessQueue()
{
    while (true)
    {
        while (_queue.TryDequeue(out var action))
            action();              // executes on main thread
        yield return null;         // suspend until next frame
    }
}
```

`yield return null` resumes on the next frame — the coroutine is always running on the **main thread**. `ConcurrentQueue<T>` is lock-free and safe for concurrent producers with a single consumer.

### 4.2 WorldStateReceiver

The gRPC loop runs as an `async Task` (background thread). It never touches Unity objects directly:

```csharp
// WorldStateReceiver.cs
await foreach (var ws in call.ResponseStream.ReadAllAsync(ct))
{
    var snapshot = ws;
    MainThreadDispatcher.Enqueue(() => OnWorldStateReceived(snapshot));
    // ↑ only enqueues a closure — zero Unity API calls here
}
```

`snapshot` is captured by value so the next loop iteration cannot overwrite it before the action executes.

### 4.3 OnWorldStateReceived

Called by the dispatcher on the main thread. Freely modifies GameObjects:

```csharp
private void OnWorldStateReceived(WorldState ws)
{
    visualizer?.Apply(ws);   // moves capsules, updates trails — all safe here
}
```

---

## 5. Why other approaches fail

### Direct call from the Task

```csharp
// ❌ crashes with "can only be called from the main thread"
transform.position = new Vector3(x, y, z);
```

Unity detects the thread context and throws `UnityException`. Non-Unity objects (lists, counters) can be read safely, but any `UnityEngine` API call crashes.

### `lock` on position data

```csharp
// ❌ technically safe but wrong tool
lock (_positionLock) { transform.position = pos; }
```

A `lock` protects shared data from concurrent access, but it **does not change which thread executes the code**. The `transform.position` assignment still runs on the Task thread — still crashes.

### `UnityMainThreadDispatcher.Instance().Enqueue()`

The popular open-source `UnityMainThreadDispatcher` (AsyncIO) uses an identical pattern. Using it would work technically, but adds an external dependency for a ~50-line class. The project ships its own minimalist version to stay dependency-light.

### `Dispatcher.Invoke()` (WPF pattern)

Does not exist in Unity. There is no `SynchronizationContext`-based dispatch built into the Unity runtime for game-thread access.

---

## 6. Performance and limits

| Metric | Value | Notes |
|--------|-------|-------|
| WorldState push rate | 20 Hz | Server-side, `SwarmObserverImpl.STREAM_RATE_HZ` |
| Unity frame rate | ~60 fps | Queue drained once per frame |
| Actions enqueued per frame | ~0.3 | 20 Hz ÷ 60 fps — one `WorldState` every ~3 frames |
| Queue depth at steady state | 0–1 entries | Consumer (60 fps) is faster than producer (20 Hz) |
| Memory per queued action | ~64 B | Closure capturing one `WorldState` reference |
| Max sustainable throughput | >500 Hz | `ConcurrentQueue` is lock-free; bounded by GC pressure, not contention |

The queue never accumulates under normal conditions because the main thread (60 fps) drains it ~3× faster than the broadcaster fills it (20 Hz). A spike only occurs if Unity drops below 20 fps — at which point older frames are superseded by newer WorldState snapshots anyway, so the visual lag stays bounded.

There is no explicit size cap on the queue. If the server spams at an unreasonable rate and Unity stalls, memory grows. For this project (20 Hz, local loopback, 20 agents) that scenario cannot occur in practice.

---

## 7. Setup checklist

1. Add `MainThreadDispatcher` as a **persistent GameObject** in the first scene loaded (`DontDestroyOnLoad` is set automatically in `Awake`).
2. Add `WorldStateReceiver` to any active GameObject and wire the `SwarmVisualizer` reference in the Inspector.
3. Start the Java server (`./mvnw -pl swarm-server exec:java`) before entering Play mode.
4. The `[gRPC]` debug log appears in the Console (Editor only — `#if UNITY_EDITOR` guard) confirming the stream is live.

---

## 8. Opening the project in Unity 6 (migration gotchas)

The project was authored under Unity 2022.3 and is now pinned to **Unity 6 (6000.4.6f1)** (see `ProjectVersion.txt`). Opening it for the first time on a fresh machine exposes two upgrade traps that make the HUD look broken even though nothing is logically wrong:

### 8.1 Removed built-in Arial font → blank legacy `UI.Text` labels

Unity 6 **removed the built-in `Arial.ttf`** (`{fileID: 10102, guid: 0000000000000000e000000000000000}`), replaced by `LegacyRuntime.ttf`. Any legacy `UnityEngine.UI.Text` referencing the old Arial renders **empty**. The 4 toggle labels in `SampleScene` were affected.

**Fix (already applied):** their `m_Font` was repointed to `LiberationSans.ttf` (`{fileID: 12800000, guid: e3265ab4bf004d28a9537516768c1c75, type: 3}`), which ships with TextMeshPro and is present in the project. `LegacyRuntime.ttf` works too. The TMP `hudText` (Agents/FPS/Spread) is unaffected — TMP Essentials and `LiberationSans SDF` are committed under `Assets/TextMesh Pro/`.

### 8.2 Canvas Scaler must be *Scale With Screen Size*

The HUD Canvas Scaler was set to **Constant Pixel Size**, so the UI kept a fixed pixel size and shrank into a corner on a higher-resolution monitor — appearing absent. **Fix (already applied):** UI Scale Mode = **Scale With Screen Size**, Reference Resolution = **1920×1080**, Screen Match Mode = Match Width Or Height, Match = **0.5**.

Two related editor gotchas when verifying this:
- **Test the Game view at a fixed `1920×1080`, not *Free Aspect*.** Free Aspect re-scales the UI live as you drag the window, which looks like elements "disappearing" — a real build behaves like a fixed resolution, not like Free Aspect.
- **Component changes made in Play mode are discarded on exit.** Set the Canvas Scaler in **Edit mode**, then save the scene (`Ctrl+S`).
