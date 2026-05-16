using UnityEngine;

public class Rotator : MonoBehaviour
{
    [SerializeField] private float speedDegreesPerSecond = 90f;

    private void Update()
    {
        transform.Rotate(Vector3.up * speedDegreesPerSecond * Time.deltaTime);
    }
}