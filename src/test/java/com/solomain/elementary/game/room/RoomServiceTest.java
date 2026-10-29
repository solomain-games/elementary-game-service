package com.solomain.elementary.game.room;

import com.solomain.elementary.game.cases.CaseCatalog;
import com.solomain.elementary.game.cases.CasesProperties;
import com.solomain.elementary.game.security.PlayerIdentity;
import com.solomain.elementary.game.web.ApiException;
import com.solomain.elementary.game.web.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Зал ожидания")
class RoomServiceTest {

    private static final CaseCatalog CASES = new CaseCatalog(new CasesProperties(testCasesDir()));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);

    private static final PlayerIdentity ANNA = new PlayerIdentity("g-anna", "Анна");
    private static final PlayerIdentity BORIS = new PlayerIdentity("g-boris", "Борис");
    private static final PlayerIdentity VERA = new PlayerIdentity("g-vera", "Вера");

    private final InMemoryRoomRepository repository = new InMemoryRoomRepository();
    private final RoomService service = serviceWith(new RoomCodeGenerator());

    private RoomService serviceWith(RoomCodeGenerator codes) {
        return new RoomService(repository, CASES, codes, new RoomLocks(), CLOCK);
    }

    private static CreateRoomRequest request(int maxPlayers) {
        return new CreateRoomRequest("demo", "ru", maxPlayers, null);
    }

    private Room room(String code) {
        return repository.find(code).orElseThrow();
    }

    private static void assertError(ErrorCode code, Executable action) {
        assertThatThrownBy(action::execute)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    @DisplayName("сценарий из задачи: создал → двое вошли → один исключён → запуск")
    void scenario() {
        String code = service.create(ANNA, request(6)).code();
        service.join(BORIS, code);
        service.join(VERA, code);
        service.kick(ANNA, code, "g-boris");
        service.start(ANNA, code);

        Room room = room(code);
        assertThat(room.members()).extracting(Member::id).containsExactly("g-anna", "g-vera");
        assertThat(room.hostId()).isEqualTo("g-anna");
        assertThat(room.status()).isEqualTo(RoomStatus.IN_GAME);
        assertThat(room.version()).isEqualTo(4);   // четыре изменения после создания
    }

    @Nested
    @DisplayName("создание")
    class Create {

        @Test
        @DisplayName("создатель — хозяин, настройки сохранены")
        void create() {
            Room created = service.create(ANNA, new CreateRoomRequest("demo", "ru", 4, 60));

            Room room = room(created.code());
            assertThat(room.hostId()).isEqualTo("g-anna");
            assertThat(room.members()).containsExactly(new Member("g-anna", "Анна", CLOCK.instant()));
            assertThat(room.maxPlayers()).isEqualTo(4);
            assertThat(room.turnTimeoutSeconds()).isEqualTo(60);
        }

        @Test
        @DisplayName("нет такого дела")
        void unknownCase() {
            assertError(ErrorCode.CASE_NOT_FOUND,
                    () -> service.create(ANNA, new CreateRoomRequest("nope", "ru", 4, null)));
        }

        @Test
        @DisplayName("у дела нет перевода на язык комнаты")
        void unsupportedLanguage() {
            assertError(ErrorCode.LANGUAGE_NOT_SUPPORTED,
                    () -> service.create(ANNA, new CreateRoomRequest("demo", "en", 4, null)));
        }

        @Test
        @DisplayName("занятый код — берётся другой")
        void codeCollision() {
            String taken = service.create(ANNA, request(4)).code();
            Queue<String> codes = new ArrayDeque<>(List.of(taken, "ABCDEF"));
            RoomService withCollision = serviceWith(new RoomCodeGenerator() {
                @Override
                public String next() {
                    return codes.remove();
                }
            });

            assertThat(withCollision.create(BORIS, request(4)).code()).isEqualTo("ABCDEF");
            assertThat(room(taken).hostId()).isEqualTo("g-anna");   // чужая комната не затёрта
        }
    }

    @Nested
    @DisplayName("вход")
    class Join {

        @Test
        @DisplayName("повторный вход ничего не меняет")
        void rejoin() {
            String code = service.create(ANNA, request(4)).code();
            service.join(BORIS, code);
            service.join(BORIS, code);

            assertThat(room(code).members()).hasSize(2);
            assertThat(room(code).version()).isEqualTo(1);
        }

        @Test
        @DisplayName("код в любом регистре")
        void caseInsensitiveCode() {
            String code = service.create(ANNA, request(4)).code();
            service.join(BORIS, code.toLowerCase());

            assertThat(room(code).isMember("g-boris")).isTrue();
        }

        @Test
        @DisplayName("нет мест")
        void full() {
            String code = service.create(ANNA, request(2)).code();
            service.join(BORIS, code);

            assertError(ErrorCode.ROOM_FULL, () -> service.join(VERA, code));
        }

        @Test
        @DisplayName("в идущую партию не войти, но участник может вернуться")
        void gameStarted() {
            String code = service.create(ANNA, request(4)).code();
            service.join(BORIS, code);
            service.start(ANNA, code);

            assertError(ErrorCode.GAME_ALREADY_STARTED, () -> service.join(VERA, code));
            service.join(BORIS, code);   // участник — без ошибки
        }

        @Test
        @DisplayName("нет такой комнаты")
        void notFound() {
            assertError(ErrorCode.ROOM_NOT_FOUND, () -> service.join(ANNA, "ZZZZZZ"));
            assertError(ErrorCode.ROOM_NOT_FOUND, () -> service.join(ANNA, "not a code"));
        }
    }

    @Nested
    @DisplayName("выход")
    class Leave {

        @Test
        @DisplayName("ушёл хозяин — хозяином становится вошедший следующим")
        void hostLeaves() {
            String code = service.create(ANNA, request(4)).code();
            service.join(BORIS, code);
            service.join(VERA, code);

            service.leave(ANNA, code);

            assertThat(room(code).hostId()).isEqualTo("g-boris");
        }

        @Test
        @DisplayName("ушёл последний — комната удалена")
        void lastLeaves() {
            String code = service.create(ANNA, request(4)).code();

            service.leave(ANNA, code);

            assertThat(repository.find(code)).isEmpty();
            assertError(ErrorCode.ROOM_NOT_FOUND, () -> service.info(code));
        }

        @Test
        @DisplayName("не участник")
        void notMember() {
            String code = service.create(ANNA, request(4)).code();

            assertError(ErrorCode.NOT_IN_ROOM, () -> service.leave(BORIS, code));
        }
    }

    @Nested
    @DisplayName("исключение и запуск")
    class HostActions {

        @Test
        @DisplayName("исключать и запускать может только хозяин")
        void onlyHost() {
            String code = service.create(ANNA, request(4)).code();
            service.join(BORIS, code);

            assertError(ErrorCode.NOT_HOST, () -> service.kick(BORIS, code, "g-anna"));
            assertError(ErrorCode.NOT_HOST, () -> service.start(BORIS, code));
        }

        @Test
        @DisplayName("хозяин не исключает сам себя")
        void kickSelf() {
            String code = service.create(ANNA, request(4)).code();

            assertError(ErrorCode.BAD_REQUEST, () -> service.kick(ANNA, code, "g-anna"));
        }

        @Test
        @DisplayName("исключение окончательное: вернуться по той же ссылке нельзя")
        void kickedCannotRejoin() {
            String code = service.create(ANNA, request(4)).code();
            service.join(BORIS, code);
            service.kick(ANNA, code, "g-boris");

            assertError(ErrorCode.KICKED_FROM_ROOM, () -> service.join(BORIS, code));
            assertThat(room(code).isMember("g-boris")).isFalse();
        }

        @Test
        @DisplayName("исключить того, кого нет, — не ошибка")
        void kickAbsent() {
            String code = service.create(ANNA, request(4)).code();

            service.kick(ANNA, code, "g-nobody");

            assertThat(room(code).version()).isZero();
        }

        @Test
        @DisplayName("в партии исключать и запускать повторно нельзя")
        void afterStart() {
            String code = service.create(ANNA, request(4)).code();
            service.join(BORIS, code);
            service.start(ANNA, code);

            assertError(ErrorCode.GAME_ALREADY_STARTED, () -> service.kick(ANNA, code, "g-boris"));
            assertError(ErrorCode.GAME_ALREADY_STARTED, () -> service.start(ANNA, code));
        }
    }

    @Test
    @DisplayName("открытые сведения: название дела и число игроков")
    void info() {
        String code = service.create(ANNA, request(6)).code();
        service.join(BORIS, code);

        RoomInfo info = service.info(code);

        assertThat(info.caseTitle()).containsEntry("ru", "Пропавший браслет");
        assertThat(info.players()).isEqualTo(2);
        assertThat(info.maxPlayers()).isEqualTo(6);
        assertThat(info.status()).isEqualTo(RoomStatus.WAITING);
    }

    @Test
    @DisplayName("одновременные входы не теряются")
    void concurrentJoins() throws InterruptedException {
        String code = service.create(ANNA, request(8)).code();
        List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            var player = new PlayerIdentity("g-" + i, "Игрок " + i);
            threads.add(Thread.ofVirtual().start(() -> service.join(player, code)));
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(room(code).members()).hasSize(8);
    }

    private static String testCasesDir() {
        try {
            return Path.of(RoomServiceTest.class.getResource("/test-cases").toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
