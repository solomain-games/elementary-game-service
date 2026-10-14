package com.solomain.elementary.game.cases;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Дела для выбора в комнате (протокол, 4.2). Как и весь API игры, требует токен. */
@RestController
@RequestMapping("/api/game/cases")
public class CaseController {

    private final CaseCatalog catalog;

    public CaseController(CaseCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<CaseSummary> list() {
        return catalog.summaries();
    }
}
