package com.solomain.elementary.game.room;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Изменения одной комнаты выполняются строго по очереди: «прочитать → изменить → сохранить».
 * Без этого два одновременных входа прочитали бы одну и ту же комнату, и второе сохранение
 * затёрло бы первое (потерянное обновление).
 *
 * <p>Блокировки «полосатые» (lock striping): фиксированный набор замков, комната берёт замок
 * по хешу своего кода. Память не растёт с числом комнат; две разные комнаты изредка делят
 * замок — это лишь небольшое ожидание, не ошибка.
 *
 * <p>Ограничение: замки живут внутри одного процесса. Пока game-service запущен в одном
 * экземпляре, этого достаточно; для нескольких экземпляров понадобится блокировка в Redis.
 */
@Component
public class RoomLocks {

    private static final int STRIPES = 256;

    private final ReentrantLock[] locks = new ReentrantLock[STRIPES];

    public RoomLocks() {
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    public <T> T withLock(String code, Supplier<T> action) {
        ReentrantLock lock = locks[Math.floorMod(code.hashCode(), STRIPES)];
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
