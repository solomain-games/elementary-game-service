package com.solomain.elementary.game.realtime;

import com.solomain.elementary.core.model.LocalizedText;
import com.solomain.elementary.core.model.TextAlign;
import com.solomain.elementary.core.view.CardFace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JSON протокола")
class ProtocolJsonTest {

    @Test
    @DisplayName("текст дела — объект переводов {\"ru\": ...}, без обёртки values")
    void localizedText() {
        String json = ProtocolJson.MAPPER.writeValueAsString(new LocalizedText(Map.of("ru", "Шкатулка")));

        assertThat(json).isEqualTo("{\"ru\":\"Шкатулка\"}");
    }

    @Test
    @DisplayName("лица карт — как в протоколе 5.3: тип виден по полю image или background")
    void cardFaces() {
        String image = ProtocolJson.MAPPER.writeValueAsString(
                new CardFace.ImageFace(1, "cards/01.webp", new LocalizedText(Map.of("ru", "Шкатулка"))));
        String text = ProtocolJson.MAPPER.writeValueAsString(
                new CardFace.TextFace(4, "backgrounds/paper.webp", new LocalizedText(Map.of("ru", "Записка")),
                        TextAlign.CENTER));

        assertThat(image).contains("\"number\":1", "\"image\":\"cards/01.webp\"", "\"caption\":{\"ru\":\"Шкатулка\"}");
        assertThat(text).contains("\"background\":\"backgrounds/paper.webp\"", "\"text\":{\"ru\":\"Записка\"}",
                "\"align\":\"CENTER\"");
    }
}
