using System;
using System.Collections;
using System.Collections.Concurrent;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
    // Marshals actions from background threads to the Unity main thread.
    // Call Enqueue() from any thread; actions execute on the main thread each frame.
    public class MainThreadDispatcher : MonoBehaviour
    {
        public static MainThreadDispatcher Instance { get; private set; }

        private readonly ConcurrentQueue<Action> _queue = new();

        private void Awake()
        {
            if (Instance != null) { Destroy(gameObject); return; }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            StartCoroutine(ProcessQueue());
        }

        public static void Enqueue(Action action)
        {
            if (Instance == null)
            {
                Debug.LogError("[MainThreadDispatcher] Instance is null — is the dispatcher in the scene?");
                return;
            }
            Instance._queue.Enqueue(action);
        }

        private IEnumerator ProcessQueue()
        {
            while (true)
            {
                while (_queue.TryDequeue(out var action))
                {
                    try { action(); }
                    catch (Exception ex) { Debug.LogException(ex); }
                }
                yield return null;
            }
        }
    }
}
