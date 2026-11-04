package com.solomain.elementary.game.room;

import java.util.List;

/**
 * Кому сообщать об изменениях комнаты и партии. В работе — рассылка по WebSocket
 * ({@code realtime.RoomBroadcaster}); {@link RoomService} о WebSocket не знает.
 */
public interface RoomListener {

    /**
     * Комната или партия изменилась. Вызывается под замком комнаты, поэтому изменения одной
     * комнаты сообщаются строго по порядку.
     *
     * @param room   комната после изменения
     * @param events что произошло, по порядку; пусто — сама комната не менялась (например, кто-то
     *               подключился или отключился), но участникам нужен свежий снимок
     */
    void roomChanged(Room room, List<RoomEvent> events);
}
