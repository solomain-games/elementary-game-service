# Elementary — игровой сервис (game-service)

Комнаты, партии и реалтайм (WebSocket) игры **Elementary**. Правила игры берутся из библиотеки `game-core`, состояние партий хранится в Redis.

## Запуск локально

Требуется JDK 25 и запущенный Redis из [`elementary-infra`](https://github.com/solomain-games/elementary-infra) (`local/docker-compose.yml`).

```powershell
.\mvnw spring-boot:run
```

Сервис стартует на порту **8081** с профилем `local`.

Проверка: http://localhost:8081/actuator/health → `"status": "UP"`, в `components` виден `redis`.

Запросы с токеном — в `http/auth.http` (нужен запущенный user-service).

## Аутентификация

Токены выпускает user-service (ADR 0003), game-service проверяет их сам открытым ключом из JWKS user-service. Ключ скачивается при первой проверке и кешируется, поэтому сервис запускается и без user-service, но для запросов с токеном user-service должен работать.

| Где | Как передаётся токен | Ошибка |
|---|---|---|
| REST | `Authorization: Bearer <token>` | `401`, Problem Details с `code: UNAUTHORIZED` |
| WebSocket `/ws` | заголовок `Authorization` в кадре STOMP `CONNECT` | кадр `ERROR` с `message:UNAUTHORIZED`, разрыв соединения |

Игрок определяется только по токену: `sub` — id игрока, `role` — роль (`ROLE_GUEST`). Без токена открыты только `/actuator/health/**` и `/actuator/info`.

## Профили

| Профиль | Где | Настройки |
|---|---|---|
| `local` (по умолчанию) | компьютер разработчика | `application-local.yaml`: Redis на `localhost:6379` |
| `prod` | боевой сервер | `application-prod.yaml`: значения из переменных окружения (ниже) |

Переменные окружения `prod`:

| Переменная | Что это |
|---|---|
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | подключение к Redis |
| `JWKS_URI` | адрес открытого ключа user-service, например `http://user-service:8082/.well-known/jwks.json` |
| `WEBSOCKET_ALLOWED_ORIGINS` | адрес сайта, с которого можно открыть WebSocket; несколько — через запятую |

Профиль задаётся переменной `SPRING_PROFILES_ACTIVE`.

## Сборка и тесты

```powershell
.\mvnw verify
```
