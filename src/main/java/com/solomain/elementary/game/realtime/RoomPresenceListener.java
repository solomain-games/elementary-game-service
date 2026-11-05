package com.solomain.elementary.game.realtime;

import com.solomain.elementary.game.room.RoomLocks;
import com.solomain.elementary.game.room.RoomService;
import com.solomain.elementary.game.timer.GameTimer;
import com.solomain.elementary.game.timer.TimerQueue;
import com.solomain.elementary.game.timer.TimersProperties;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.time.Clock;
import java.util.Set;

/**
 * Следит за подключениями (Г10). Подписка на события комнаты — игрок в сети, закрытие сессии —
 * нет. Важен только переход «в сети ↔ не в сети»: вторая вкладка или закрытие одной из двух
 * ничего не меняют ({@link RoomPresence}).
 *
 * <ul>
 *   <li>пропал из сети посреди партии — статус {@code DISCONNECTED} и таймер ожидания;
 *       не вернулся вовремя — для партии это выход;</li>
 *   <li>вернулся — таймер отменяется, игрок снова {@code ACTIVE} со своими картами;</li>
 *   <li>в зале ожидания и после финала — просто свежие снимки (точка «в сети»).</li>
 * </ul>
 *
 * <p>Таймер ожидания — в очереди таймеров ({@link TimerQueue}, Redis), срабатывает через
 * {@code TimerScheduler}.
 */
@Component
public class RoomPresenceListener {

    private final RoomPresence presence;
    private final RoomService rooms;
    private final RoomLocks locks;
    private final TimerQueue timers;
    private final TimersProperties properties;
    private final Clock clock;

    public RoomPresenceListener(RoomPresence presence, RoomService rooms, RoomLocks locks, TimerQueue timers,
                                TimersProperties properties, Clock clock) {
        this.presence = presence;
        this.rooms = rooms;
        this.locks = locks;
        this.timers = timers;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Проверка присутствия (таймер {@code PresenceCheck}): кто в партии числится {@code ACTIVE},
     * но не в сети, — тот отключён. Сервис не видел, как они ушли: они были не в сети при старте
     * партии или не переподключились после перезапуска сервиса.
     */
    public void checkPresence(String code) {
        locks.withLock(code, () -> {
            rooms.activePlayers(code).forEach(playerId -> wentOffline(code, playerId));
            return null;
        });
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        Principal user = event.getUser();
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (user == null) {
            return;
        }
        RoomTopics.roomCodeOf(accessor.getDestination()).ifPresent(code -> {
            if (presence.subscribed(accessor.getSessionId(), user.getName(), code)) {
                cameOnline(code, user.getName());
            }
        });
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        presence.playerOf(sessionId).ifPresent(playerId -> {
            Set<String> offlineRooms = presence.disconnected(sessionId);
            offlineRooms.forEach(code -> wentOffline(code, playerId));
        });
    }

    /*
     * События подписки и отключения приходят из разных потоков и могут обогнать друг друга:
     * игрок закрыл вкладку и тут же открыл новую. Поэтому под замком комнаты ещё раз смотрим,
     * в сети ли он сейчас, — решает текущее состояние, а не порядок событий.
     */

    private void cameOnline(String code, String playerId) {
        locks.withLock(code, () -> {
            if (presence.isOnline(code, playerId)) {
                timers.cancel(new GameTimer.DisconnectWait(code, playerId));
                rooms.markReconnected(code, playerId);
            }
            return null;
        });
    }

    private void wentOffline(String code, String playerId) {
        locks.withLock(code, () -> {
            if (!presence.isOnline(code, playerId) && rooms.markDisconnected(code, playerId)) {
                timers.schedule(new GameTimer.DisconnectWait(code, playerId),
                        clock.instant().plus(properties.disconnectTimeout()));
            }
            return null;
        });
    }
}
