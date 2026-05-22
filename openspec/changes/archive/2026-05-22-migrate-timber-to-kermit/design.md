## Context

現在のアプリは Timber をログ出力に利用しており、初期化は `SlevoApplication`、出力は Repository / DataSource / ViewModel / NetworkModule / 一部 Compose UI に散在している。Timber は Android/JVM 向けの利用が中心で KMP 移行時の shared code では使いにくいため、将来の KMP 化を見据えた抽象化が必要になる。

Issue #480 の受け入れ条件は、Timber の廃止、Kermit の導入、Kermit を interface 経由で利用すること、現時点では KMP 移行を行わず将来移行しやすい設計にすること。したがって、今回の設計は「Android アプリ内で動作する Kermit 実装」と「Android 非依存の logging interface」を分離する。

## Goals / Non-Goals

**Goals:**
- Timber 依存と `Timber` 直接参照をアプリコードから除去する。
- Kermit をログ出力の実装として導入する。
- アプリ内のログ出力は Android 非依存の `AppLogger` interface を介して行う。
- Hilt の既存 DI 方針に合わせて、Repository / DataSource / ViewModel / NetworkModule へ logger を注入できるようにする。
- 将来 shared module へ移しやすいよう、interface は Android SDK 型や Kermit 型に依存しない形にする。

**Non-Goals:**
- 今回は KMP module、shared source set、expect/actual 構成を追加しない。
- ログ収集基盤、クラッシュレポート連携、リモート送信などの新機能は追加しない。
- 既存のアプリ挙動やユーザー向け UI を変更しない。
- すべてのログ文言やログレベルの見直しは行わず、Timber からの等価移行を基本とする。

## Decisions

### 1. アプリ独自の `AppLogger` interface を導入する

`AppLogger` は `d` / `i` / `e` など既存利用に必要なログレベルを提供し、`message`、任意の `tag`、任意の `Throwable` を受け取る。interface は Kotlin 標準型のみを公開し、Kermit の `Logger` や Android の `Log` を公開 API に含めない。

代替案として Kermit を各クラスで直接使う方法もあるが、受け入れ条件の「インターフェースを介して Kermit を利用する」に反する。また将来 KMP 化時にログ実装の差し替え範囲が広がるため採用しない。

### 2. `KermitAppLogger` を Android app module の実装として提供する

Kermit への委譲は `KermitAppLogger` に集約する。ログ呼び出し側は `AppLogger` のみを知り、Kermit の API 差分、tag の扱い、Throwable の引数順などは実装内で吸収する。

代替案として static singleton/object logger を全体から直接参照する方法もあるが、既存の Hilt / constructor injection 方針と合わず、テストで差し替えにくいため採用しない。

### 3. Hilt module で `AppLogger` を singleton binding する

既存の `DataSourceModule` などと同様に `@Module` / `@InstallIn(SingletonComponent::class)` / `@Binds` または `@Provides` で `AppLogger` を提供する。これにより、Repository、DataSource、ViewModel、NetworkModule は constructor または provider parameter 経由で logger を受け取れる。

### 4. Compose 内の単発デバッグログは削除を優先する

Compose UI へ logger を DI するために CompositionLocal や引数伝播を導入すると、今回の目的に対して設計が重くなる。現在確認されている Compose 内の `tabs` デバッグログはユーザー挙動に影響しないため、ログ基盤のための UI API 変更を避けて削除する。

### 5. KMP 対応は「移行しやすい境界」までに留める

今回の成果物は Android app module 内で完結させる。ただし `AppLogger` は Android 非依存にするため、将来 shared module を作成する際に interface を移動し、Kermit 実装を shared または platform 実装へ移しやすくする。

## Risks / Trade-offs

- Constructor injection の引数追加で既存テストがコンパイルエラーになる → テスト用の no-op logger または mock logger を追加して既存テストの生成処理を更新する。
- Timber と Kermit の API 差分により message / throwable / tag の対応を誤る → 置換時は `KermitAppLogger` に委譲形式を集約し、呼び出し側では `logger.e(message, throwable = e)` のように明示的な引数名を使う。
- NetworkModule の OkHttp interceptor が logger を捕捉する → logger は singleton として注入し、interceptor では tag 付き debug ログのみを出力する。
- Release ビルドでログ出力の量が変わる可能性がある → Kermit 初期化方針を `BuildConfig.DEBUG` で制御し、現行の Timber DebugTree 相当の挙動を維持する。
- Compose 内ログ削除によりデバッグ情報が減る → 削除対象は単発の `tabs` ログに限定し、必要になった場合は ViewModel や状態管理層で `AppLogger` 経由のログを追加する。

## Migration Plan

1. 依存関係を Timber から Kermit に差し替える。
2. `AppLogger` interface、`KermitAppLogger`、Hilt binding module を追加する。
3. `SlevoApplication` の Timber 初期化を Kermit 初期化へ置き換える。
4. Repository と DataSource の Timber 呼び出しを `AppLogger` 注入に置き換える。
5. ViewModel の Timber 呼び出しを `AppLogger` 注入に置き換え、関連テストを更新する。
6. `NetworkModule` の OkHttp ログ出力を `AppLogger` 経由にする。
7. Compose 内の不要な単発 Timber ログを削除する。
8. `import timber.log.Timber` と Timber 依存が残っていないことを確認する。
9. CI または指定された build / unit test で検証する。

Rollback は、Kermit 依存と logging interface の追加を取り消し、Timber 依存と既存呼び出しへ戻すことで可能。ただし移行後は呼び出し箇所が分散するため、変更は小さなステップで行い、各段階でコンパイル確認できる状態を維持する。

## Open Questions

- Release ビルドで完全 no-op にするか、Kermit の標準 Android writer を残すかは実装時に現在の Timber DebugTree 相当の挙動を確認して決定する。
- `AppLogger` に `v` / `w` / `wtf` まで含めるか、現行利用に必要な `d` / `i` / `e` から始めるかは、将来拡張性と最小 API のバランスで実装時に決める。
