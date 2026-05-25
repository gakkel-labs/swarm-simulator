# Notes personnelles — Threading gRPC / Unity

> Doc interne, FR uniquement. La vraie doc publique sera dans `03-unity-integration.md`.

---

## Le problème de départ

Unity a une règle stricte :

> **Seul le main thread peut modifier des objets 3D.**

Si tu touches un `Transform` depuis un autre thread → crash immédiat.

Or le serveur envoie du `WorldState` à 20 Hz via gRPC. Ces données arrivent sur un
**thread réseau** géré par .NET — pas sur le main thread Unity.

```
Serveur (Java)
     │
     │  gRPC stream (20 Hz)
     ▼
.NET thread pool  ← ici arrivent les WorldState
     │
     │  ❌ on ne peut PAS toucher Unity ici
     ▼
Main thread Unity ← ici seulement on peut bouger des GameObjects
```

---

## La solution : ConcurrentQueue\<Action\>

On utilise une **file d'attente partagée** entre les deux threads.

Au lieu d'exécuter le code Unity directement sur le thread réseau, on le **programme**
pour plus tard sous forme d'`Action` (un bout de code à exécuter).

```
Thread réseau                    Main thread Unity
─────────────────────────────────────────────────────
                  ConcurrentQueue<Action>
                 ┌─────────────────────┐
  Enqueue() ──► │  action             │ ──► Dequeue() → exécute
  Enqueue() ──► │  action             │ ──► Dequeue() → exécute
  Enqueue() ──► │  action             │ ──► Dequeue() → exécute
                └─────────────────────┘
```

`ConcurrentQueue` est conçue pour être utilisée par plusieurs threads simultanément
sans planter — c'est exactement son rôle ici.

---

## Les 3 briques du pattern

### 1. MainThreadDispatcher — le gardien de la queue

`MonoBehaviour` singleton qui :
- **Possède** la `ConcurrentQueue<Action>`
- **Vide** la queue à chaque frame via une coroutine

```
Awake() ──► StartCoroutine(ProcessQueue)
              │
              ▼
         ┌─────────────────────────┐
         │  while (true)           │  ← tourne pour toujours
         │    Dequeue toutes les   │
         │    actions en attente   │
         │    yield return null    │  ← attends la prochaine frame
         └─────────────────────────┘
```

`yield return null` = "reprends à la frame suivante". La coroutine est
donc appelée par Unity sur le **main thread**, 60 fois par seconde.

---

### 2. WorldStateReceiver — le récepteur gRPC

`MonoBehaviour` qui ouvre le stream gRPC sur un **background Task** (.NET).

À chaque `WorldState` reçu :

```csharp
// Thread réseau — on ne fait QUE ça
MainThreadDispatcher.Enqueue(() => OnWorldStateReceived(snapshot));
```

On ne touche à rien d'Unity. On dépose juste une action dans la queue.

---

### 3. OnWorldStateReceived — le vrai travail

Appelée par la coroutine, donc sur le **main thread**. C'est ici qu'on peut
modifier des GameObjects en toute sécurité.

Aujourd'hui (issue 14) : juste un log de validation.
```csharp
Debug.Log($"[gRPC] WorldState t={ws.TimestampUnixMs} agents={ws.Agents.Count}");
```

Demain (issue affichage) : déplacer les capsules des agents.
```csharp
foreach (var agent in ws.Agents)
{
    _agentObjects[agent.Id].transform.position = new Vector3(
        agent.PositionXyz.X,
        agent.PositionXyz.Y,
        agent.PositionXyz.Z
    );
}
```

---

## Vue d'ensemble

```
Serveur Java
    │
    │ gRPC stream 20 Hz
    ▼
WorldStateReceiver.ReceiveLoopAsync()        ← background Task (.NET)
    │
    │ pour chaque WorldState reçu :
    │ MainThreadDispatcher.Enqueue(action)
    │
    ▼
ConcurrentQueue<Action>                      ← partagée, thread-safe
    │
    │ vidée à chaque frame par la coroutine
    ▼
MainThreadDispatcher.ProcessQueue()          ← main thread Unity (60 fps)
    │
    │ exécute les actions
    ▼
OnWorldStateReceived(ws)                     ← safe pour toucher les GameObjects
```

---

## Fréquences et timing

- Serveur envoie à **20 Hz** → 1 WorldState toutes les 50 ms
- Unity tourne à **60 fps** → 1 frame toutes les ~16 ms
- Résultat : **~3 WorldState traités par frame** en moyenne

La queue absorbe les micro-décalages entre les deux rythmes.

---

## Reconnexion automatique

Si le serveur s'arrête, gRPC envoie un `StatusCode.Unavailable`.
Au lieu de mourir, `WorldStateReceiver` attend 3 secondes et retente.

```
Stream ouvert ──► WorldState reçus
      │
      │ serveur coupe
      ▼
Unavailable ──► log warning ──► attente 3s ──► retente
                                                   │
                                              Stream ouvert
                                                   │
                                              WorldState reçus
```

Le délai de retry est configurable dans l'Inspector Unity.
