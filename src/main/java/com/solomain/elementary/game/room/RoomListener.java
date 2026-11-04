package com.solomain.elementary.game.room;

/**
 * Кому сообщать об изменениях комнаты. В работе — рассылка по WebSocket
 * ({@code realtime.RoomBroadcaster}); {@link RoomService} о WebSocket не знает.
 */
public interface RoomListener {

    /**
     * Комната изменилась. Вызывается под замком комнаты, поэтому изменения одной комнаты
     * сообщаются строго по порядку.
     *
     * @param room  комната после изменения
     * @param event что произошло; {@code null} — сама комната не менялась (например, кто-то
     *              подключился или отключился), но участникам нужен свежий снимок
     */
    void roomChanged(Room room, RoomEvent event);
}
