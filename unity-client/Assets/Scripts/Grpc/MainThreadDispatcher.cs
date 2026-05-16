using System;
using System.Collections;
using System.Collections.Concurrent;
using UnityEngine;

namespace Gakkel.Swarm.Unity
{
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

        public static void Enqueue(Action action) => Instance._queue.Enqueue(action);

        private IEnumerator ProcessQueue()
        {
            while (true)
            {
                while (_queue.TryDequeue(out var action))
                    action();
                yield return null;
            }
        }
    }
}
