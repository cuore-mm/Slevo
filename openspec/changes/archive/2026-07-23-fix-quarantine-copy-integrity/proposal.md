## Why

`PendingRestoreApplier`のquarantine copy fallbackは、invalid live DBのcopy後に`source.delete()`が失敗してもdestinationの存在だけで成功を返す。その結果、Room pathにinvalid DBが残ったままFAILED markerとresultを書いてpending recovery stateを削除し、次回起動で安全に再試行できなくなる。

## What Changes

- quarantine開始時に存在したmain databaseとsidecarsの成功条件を「incident内のartifactが実在し、かつRoom pathのsourceが存在しない」に強化する。
- rename失敗後のcopyが成功してもsource削除に失敗した場合はquarantine未完了として扱い、成功pathを報告しない。
- DB set内の任意fileのquarantine未完了時は元のretryable markerとpending artifactsを保持し、`cleanupPending()`を実行しない。
- filesystem操作を決定的にfailure injectionできるtest seamと、rename失敗・copy成功・delete失敗の回帰testを追加する。

## Capabilities

### New Capabilities

- `quarantine-copy-integrity`: invalid live databaseのquarantine完了条件と、copy/delete部分失敗時のretryable recovery state保持を定義する。

### Modified Capabilities

- なし。

## Impact

- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplier.kt`: quarantine file操作の結果判定、main DB未退避時のmarker/result/cleanup分岐、test seamを変更する。
- `app/src/test/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplierTest.kt`: copy fallbackのsource削除失敗と再試行可能状態を検証する。
- Room schema、backup/restore format、marker/result JSON schema、既存quarantine directory lifecycle、UI、外部dependencyは変更しない。
