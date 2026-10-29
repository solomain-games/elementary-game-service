package com.solomain.elementary.game.room;

import com.solomain.elementary.game.security.PlayerIdentity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Комнаты (протокол, 4.4–4.6). Игрок берётся из проверенного токена ({@code @AuthenticationPrincipal}),
 * никогда из тела запроса.
 */
@RestController
@RequestMapping("/api/game/rooms")
public class RoomController {

    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @PostMapping
    public ResponseEntity<CreateRoomResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                     @Valid @RequestBody CreateRoomRequest request) {
        Room room = rooms.create(PlayerIdentity.from(jwt), request);
        return ResponseEntity.created(URI.create("/api/game/rooms/" + room.code()))
                .body(new CreateRoomResponse(room.code()));
    }

    @GetMapping("/{code}")
    public RoomInfo info(@PathVariable String code) {
        return rooms.info(code);
    }

    @PostMapping("/{code}/join")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void join(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        rooms.join(PlayerIdentity.from(jwt), code);
    }

    @PostMapping("/{code}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        rooms.leave(PlayerIdentity.from(jwt), code);
    }

    @PostMapping("/{code}/kick")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kick(@AuthenticationPrincipal Jwt jwt, @PathVariable String code,
                     @Valid @RequestBody KickRequest request) {
        rooms.kick(PlayerIdentity.from(jwt), code, request.playerId());
    }

    @PostMapping("/{code}/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void start(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        rooms.start(PlayerIdentity.from(jwt), code);
    }
}
