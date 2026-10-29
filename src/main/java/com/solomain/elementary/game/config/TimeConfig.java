package com.solomain.elementary.game.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Часы сервиса. Отдельным бином, чтобы в тестах подставить фиксированное время. */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
