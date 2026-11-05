package com.solomain.elementary.game.timer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Включает {@code @Scheduled} (планировщик таймеров) и настройки {@code elementary.timers}. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(TimersProperties.class)
public class TimerConfig {
}
