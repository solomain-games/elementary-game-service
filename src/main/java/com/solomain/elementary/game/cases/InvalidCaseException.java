package com.solomain.elementary.game.cases;

import java.nio.file.Path;
import java.util.List;

/** Дело не прошло проверку. Содержит все найденные ошибки сразу, чтобы автор исправил их за один раз. */
public class InvalidCaseException extends RuntimeException {

    private final List<String> problems;

    public InvalidCaseException(Path caseDir, List<String> problems) {
        super("Case " + caseDir.getFileName() + " is invalid:\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
