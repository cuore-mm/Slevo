## Why

`refactor-tab-controller-state-machine` の実装後にも、現在の通常経路から参照されない state／API と、極端な同一タブ連打や commit 同時 cancellation の順序だけを保証する照合 machinery が残っている。既存の永続化安全性と通常 UI 契約は維持しつつ、観測されていない低確率 race のための複雑性を今は持たない、より小さい Controller 実装へ前進させる。

## What Changes

- Thread confirmation から同一 key predecessor guard と pending-state revision を除去し、各 operation を baseline より新しい Room snapshot と operation 自身の最小条件だけで確認する。同一タブへの rapid pin toggle が厳密な受理順どおりに収束する保証は外す。
- Thread ensure/info の canonical confirmation を対象 identity の存在確認へ縮小する。Repository と pending projection の metadata merge は維持するが、無関係な post-write Flow emission で pending projection が先に外れ、次の canonical emission まで古い metadata が一時表示され得ることを許容する。
- Thread の未参照 `TabControllerState` mirror、未使用 `TabReducerTransition`、Board の無制限 `commandResults` 履歴を削除する。atomic な `TabPresentationState`、Loading/Loaded、canonical tabs、pending projection、selected key は各 domain の実際の公開経路で維持する。
- 未使用の Thread command-result Deep Link delegation と未使用の Thread repository result wrapper を削除し、現在の単一 Deep Link 経路と targeted repository API に集約する。
- commit 後 caller cancellation の厳密な同時発生順序だけを検証する冗長 test と rapid same-tab pin の厳密順序 test を削除する。受理前 cancellation、受理済み mutation の Controller ownership、teardown、失敗回復は維持する。
- Board／Thread の full-list bulk API は通常 mutation の writer ではなく明示 bulk／test setup 境界なので維持し、通常 add/delete/pin/info/scroll は引き続き targeted writer だけを使用する。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `tab-controller-state-machine`: 単一 state mirror、同一 key confirmation 順序、metadata exact-match confirmation、terminal result 履歴の必須条件を、現在の安全性を保つ最小 Controller 契約へ縮小する。

## Impact

- Production: `TabControllerContracts.kt`、`BoardTabsCoordinator.kt`、`ThreadTabsCoordinator.kt`、`ThreadTabsProjection.kt`、`TabSessionStore.kt`、`TabsRepository.kt`。
- Tests: `ThreadTabsCoordinatorTest.kt` を中心に低価値 race test を削除し、identity-based metadata confirmation と必須安全性の回帰 test に更新する。既存の Board／Deep Link／retained close／Room targeted mutation／Loading・Empty／atomic presentation test は維持する。
- DB schema、DAO query、resource、UI text、layout、icon、theme、accessibility、navigation、route format、依存ライブラリは変更しない。
- `refactor-tab-controller-state-machine` は履歴として変更せず、本変更が上記の限定した requirement と実装を forward-only で supersede する。
