package com.solomain.elementary.game.game;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

@Configuration
class GameConfig {

    /**
     * Случайность для раздачи и порядка ходов. SecureRandom: колоду и порядок ходов нельзя
     * предсказать, даже зная, когда началась партия. В тестах ядро получает генератор с seed.
     */
    @Bean
    RandomGenerator gameRandom() {
        return new SecureRandom();
    }
}
