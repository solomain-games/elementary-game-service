package com.solomain.elementary.game.room;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Код комнаты (протокол, 4.4): 6 символов из A–Z и 2–9 без похожих (0/O, 1/I),
 * чтобы код легко продиктовать голосом. Вариантов 32⁶ ≈ 10⁹ — угадать чужой код перебором
 * практически нельзя, поэтому {@link SecureRandom}, а не предсказуемый {@code Random}.
 */
@Component
public class RoomCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 6;

    /** Строка, похожая на код комнаты (до поиска в хранилище). */
    public static final Pattern CODE = Pattern.compile("^[" + ALPHABET + "]{" + LENGTH + "}$");

    private final SecureRandom random = new SecureRandom();

    public String next() {
        var code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
