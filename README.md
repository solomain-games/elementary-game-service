# Elementary — игровой сервис (game-service)

Комнаты, партии и реалтайм (WebSocket) игры **Elementary**. Правила игры берутся из библиотеки `game-core`, состояние партий хранится в Redis.

## Запуск локально

Требуется JDK 25 и запущенный Redis из [`elementary-infra`](https://github.com/solomain-games/elementary-infra) (`local/docker-compose.yml`).

```powershell
.\mvnw spring-boot:run
```

Сервис стартует на порту **8081** с профилем `local`.

Проверка: http://localhost:8081/actuator/health → `"status": "UP"`, в `components` виден `redis`.

## Профили

| Профиль | Где | Настройки |
|---|---|---|
| `local` (по умолчанию) | компьютер разработчика | `application-local.yaml`: Redis на `localhost:6379` |
| `prod` | боевой сервер | `application-prod.yaml`: значения из переменных окружения `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` |

Профиль задаётся переменной `SPRING_PROFILES_ACTIVE`.

## Сборка и тесты

```powershell
.\mvnw verify
```
