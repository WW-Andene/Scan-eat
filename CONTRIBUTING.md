# Contributing

Scan'eat is Kotlin-only. Do not reintroduce the removed PWA, Vercel, Node, TypeScript, service-worker, browser-fetch, or Capacitor wrapper paths unless the project owner explicitly changes the native-only requirement.

## Development

```bash
./gradlew test
./gradlew run
```

## Expectations

- Keep scoring and helper behavior deterministic and testable in Kotlin.
- Prefer embedded/native data sources over network calls.
- Add or update Kotlin tests under `src/test/kotlin/app/scaneat/` for behavior changes.
- Run `./gradlew test` before opening a pull request.
