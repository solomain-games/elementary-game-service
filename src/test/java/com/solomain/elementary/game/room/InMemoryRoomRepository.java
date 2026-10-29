package com.solomain.elementary.game.room;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Комнаты в памяти — для тестов без Redis. Ведёт себя как {@link RedisRoomRepository}, кроме срока хранения. */
public class InMemoryRoomRepository implements RoomRepository {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    @Override
    public Optional<Room> find(String code) {
        return Optional.ofNullable(rooms.get(code));
    }

    @Override
    public boolean createIfAbsent(Room room) {
        return rooms.putIfAbsent(room.code(), room) == null;
    }

    @Override
    public void save(Room room) {
        rooms.put(room.code(), room);
    }

    @Override
    public void delete(String code) {
        rooms.remove(code);
    }

    public int size() {
        return rooms.size();
    }
}
