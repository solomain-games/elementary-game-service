package com.solomain.elementary.game.room;

import java.util.Optional;

/** Хранилище комнат. В работе — Redis ({@link RedisRoomRepository}), в тестах — память. */
public interface RoomRepository {

    Optional<Room> find(String code);

    /**
     * Сохраняет новую комнату, только если код ещё свободен.
     *
     * @return {@code false}, если комната с таким кодом уже есть
     */
    boolean createIfAbsent(Room room);

    /** Сохраняет изменённую комнату и продлевает срок её хранения. */
    void save(Room room);

    void delete(String code);
}
