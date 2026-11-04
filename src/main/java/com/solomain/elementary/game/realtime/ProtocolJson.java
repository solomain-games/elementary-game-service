package com.solomain.elementary.game.realtime;

import com.fasterxml.jackson.annotation.JsonValue;
import com.solomain.elementary.core.model.LocalizedText;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * JSON сообщений протокола по WebSocket.
 *
 * <p>Модель ядра не знает про Jackson, поэтому особенности формата задаются здесь, через
 * mix-in — «примесь» аннотаций к чужому классу без его изменения. {@link LocalizedText} — record
 * с одним полем {@code values}; по умолчанию он стал бы {@code {"values": {"ru": "..."}}},
 * а протокол (раздел 2) требует просто {@code {"ru": "..."}}.
 */
public final class ProtocolJson {

    public static final JsonMapper MAPPER = JsonMapper.builder()
            .addMixIn(LocalizedText.class, LocalizedTextMixin.class)
            .build();

    private ProtocolJson() {
    }

    /** {@code @JsonValue}: объект записывается как значение этого метода — словарь переводов. */
    abstract static class LocalizedTextMixin {

        @JsonValue
        abstract Map<String, String> values();
    }
}
