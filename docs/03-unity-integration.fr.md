# Intégration Unity — pont gRPC thread-safe

> Comment le flux WorldState à 20 Hz du serveur parvient aux GameObjects Unity sans crash ni data race.
> Lecture complémentaire : [`02-grpc-contract.fr.md`](02-grpc-contract.fr.md).

---

## 1. La contrainte

Unity impose une règle stricte : **seul le main thread peut modifier des objets de la scène** (`Transform`, `Renderer`, champs de composants, …). La violer fait crasher l'éditeur ou provoque une corruption silencieuse.

Les données gRPC arrivent sur un **Task du thread-pool .NET** — un thread d'arrière-plan géré par le runtime, jamais le main thread Unity.

Appeler naïvement `transform.position = …` depuis le callback gRPC crash immédiatement.

---

## 2. Diagramme complet du flux de threads

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Processus Java (serveur)                                                 │
│                                                                           │
│  thread sim-loop (30 Hz)                                                  │
│  SimulationLoop.tick()                                                    │
│    agent.update(pos, vel)   ← écrit les champs volatile de Agent.java     │
│         │                                                                 │
│         │ écriture volatile (Agent.position / Agent.velocity)             │
│         ▼                                                                 │
│  thread swarm-broadcaster (20 Hz)                                         │
│  SwarmObserverImpl.broadcast()                                            │
│    buildWorldState()        ← lit les champs volatile, construit le proto │
│    obs.onNext(state)        ← pousse WorldState sur le stream gRPC        │
└────────────────────┬─────────────────────────────────────────────────────┘
                     │
                     │  gRPC server-streaming  (HTTP/2, TCP)
                     │  WorldState poussé à 20 Hz
                     │
┌────────────────────▼─────────────────────────────────────────────────────┐
│  Processus Unity                                                          │
│                                                                           │
│  Task .NET (arrière-plan)                                                 │
│  WorldStateReceiver.ReceiveLoopAsync()                                    │
│    await foreach ws in stream                                             │
│    MainThreadDispatcher.Enqueue(() => OnWorldStateReceived(ws))           │
│         │                                                                 │
│         │ ConcurrentQueue<Action>  ← point de transfert thread-safe      │
│         │                                                                 │
│         ▼                                                                 │
│  Main thread Unity (60 fps)                                               │
│  MainThreadDispatcher.ProcessQueue()  ← coroutine, exécutée chaque frame │
│    action()  →  OnWorldStateReceived(ws)                                  │
│    visualizer.Apply(ws)   ← sûr pour toucher les GameObjects ici          │
└──────────────────────────────────────────────────────────────────────────┘
```

Trois horloges indépendantes : **sim-loop à 30 Hz**, **broadcaster à 20 Hz**, **Unity à ~60 fps**. La queue absorbe le décalage de timing entre elles.

---

## 3. Synchronisation côté Java

Le thread broadcaster lit l'état des agents pendant que le thread sim-loop les écrit en même temps. Pas de verrou — `position` et `velocity` sont déclarés `volatile` :

```java
// Agent.java
private volatile Vector3D position;
private volatile Vector3D velocity;

public void update(Vector3D newPosition, Vector3D newVelocity) {
    this.position = newPosition;   // écriture volatile
    this.velocity = newVelocity;
}
```

Une écriture de référence `volatile` en Java est **atomique** et immédiatement visible par les autres threads. `Vector3D` est immuable (tous les champs finaux), donc un lecteur ne peut jamais observer un objet à moitié construit.

> Les champs internes de `SimulationLoop` (`tickCount`, `lastCentroid`, …) ne sont **pas** volatiles — ils sont confinés au thread sim-loop par conception et ne doivent pas être exposés au broadcaster sans synchronisation. Voir le commentaire dans `SimulationLoop.java:44`.

---

## 4. Le pattern ConcurrentQueue (côté Unity)

### 4.1 MainThreadDispatcher

Un singleton `MonoBehaviour` qui possède la queue et la vide à chaque frame via une coroutine :

```csharp
// MainThreadDispatcher.cs
private readonly ConcurrentQueue<Action> _queue = new();

public static void Enqueue(Action action)
{
    Instance._queue.Enqueue(action);   // appelable depuis n'importe quel thread
}

private IEnumerator ProcessQueue()
{
    while (true)
    {
        while (_queue.TryDequeue(out var action))
            action();              // s'exécute sur le main thread
        yield return null;         // suspend jusqu'à la prochaine frame
    }
}
```

`yield return null` reprend à la frame suivante — la coroutine s'exécute toujours sur le **main thread**. `ConcurrentQueue<T>` est sans verrou et sûre pour plusieurs producteurs avec un seul consommateur.

### 4.2 WorldStateReceiver

La boucle gRPC tourne comme un `async Task` (thread d'arrière-plan). Elle ne touche jamais directement les objets Unity :

```csharp
// WorldStateReceiver.cs
await foreach (var ws in call.ResponseStream.ReadAllAsync(ct))
{
    var snapshot = ws;
    MainThreadDispatcher.Enqueue(() => OnWorldStateReceived(snapshot));
    // ↑ seulement une fermeture en file — zéro appel à l'API Unity ici
}
```

`snapshot` est capturé par valeur pour que la prochaine itération ne puisse pas l'écraser avant que l'action s'exécute.

### 4.3 OnWorldStateReceived

Appelée par le dispatcher sur le main thread. Modifie librement les GameObjects :

```csharp
private void OnWorldStateReceived(WorldState ws)
{
    visualizer?.Apply(ws);   // déplace les capsules, met à jour les trails — tout est sûr ici
}
```

---

## 5. Pourquoi les autres approches échouent

### Appel direct depuis le Task

```csharp
// ❌ crash avec "can only be called from the main thread"
transform.position = new Vector3(x, y, z);
```

Unity détecte le contexte du thread et lève une `UnityException`. Les objets non-Unity (listes, compteurs) peuvent être lus sans danger, mais tout appel à l'API `UnityEngine` crash.

### `lock` sur les données de position

```csharp
// ❌ techniquement safe mais mauvais outil
lock (_positionLock) { transform.position = pos; }
```

Un `lock` protège des données partagées contre l'accès concurrent, mais il **ne change pas quel thread exécute le code**. L'assignation `transform.position` s'exécute toujours sur le Task thread — crash quand même.

### `UnityMainThreadDispatcher.Instance().Enqueue()` (bibliothèque externe)

La bibliothèque open-source populaire `UnityMainThreadDispatcher` (AsyncIO) utilise un pattern identique. L'utiliser fonctionnerait techniquement, mais ajoute une dépendance externe pour une classe de ~50 lignes. Le projet embarque sa propre version minimaliste pour rester léger en dépendances.

### `Dispatcher.Invoke()` (pattern WPF)

N'existe pas dans Unity. Il n'y a pas de dispatch basé sur `SynchronizationContext` intégré au runtime Unity pour l'accès au game thread.

---

## 6. Performance et limites

| Métrique | Valeur | Notes |
|----------|--------|-------|
| Fréquence de push WorldState | 20 Hz | Côté serveur, `SwarmObserverImpl.STREAM_RATE_HZ` |
| Fréquence Unity | ~60 fps | Queue vidée une fois par frame |
| Actions en queue par frame | ~0,3 | 20 Hz ÷ 60 fps — un `WorldState` toutes les ~3 frames |
| Profondeur queue en régime | 0–1 entrées | Le consommateur (60 fps) est plus rapide que le producteur (20 Hz) |
| Mémoire par action en queue | ~64 o | Fermeture capturant une référence `WorldState` |
| Débit max soutenable | >500 Hz | `ConcurrentQueue` est sans verrou ; limité par la pression GC, pas la contention |

La queue ne s'accumule jamais dans des conditions normales car le main thread (60 fps) la vide ~3× plus vite que le broadcaster ne la remplit (20 Hz). Un pic n'arrive que si Unity tombe sous 20 fps — et là, les frames plus anciennes sont de toute façon supplantées par les WorldState plus récents, donc le lag visuel reste borné.

La queue n'a pas de taille maximale explicite. Si le serveur spam à un rythme déraisonnable et qu'Unity se bloque, la mémoire croît. Pour ce projet (20 Hz, loopback local, 20 agents) ce scénario ne peut pas se produire en pratique.

---

## 7. Checklist de setup

1. Ajouter `MainThreadDispatcher` comme **GameObject persistant** dans la première scène chargée (`DontDestroyOnLoad` est configuré automatiquement dans `Awake`).
2. Ajouter `WorldStateReceiver` à n'importe quel GameObject actif et câbler la référence `SwarmVisualizer` dans l'Inspector.
3. Démarrer le serveur Java (`./mvnw -pl swarm-server exec:java`) avant d'entrer en Play mode.
4. Le log de debug `[gRPC]` apparaît dans la Console (éditeur uniquement — garde `#if UNITY_EDITOR`) confirmant que le stream est actif.
