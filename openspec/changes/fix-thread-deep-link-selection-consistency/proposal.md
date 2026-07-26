## Why

スレッド Deep Link はタブ初期読込や登録保存を待たずに選択と navigation を進めるため、対象 key が一時的に canonical 一覧へ存在しない瞬間に selected key が失われ、Pager が先頭タブへ移動し得る。先行変更で確立する readiness と canonical mutation completion を利用し、選択と navigation を確定済みタブに対してだけ行う必要がある。

## What Changes

- `refactor-thread-tab-persistence-consistency` の完了を実装前提とし、その thread-tab readiness、mutation result、canonical confirmation API を利用する。
- スレッド Deep Link を、URL 解決、タブ readiness 待機、target registration 完了、canonical target 確認、選択、navigation の順で処理する。
- pending target は selected key と別に扱い、登録待機中または target が一時的に不在の間は現在の選択と Pager page を維持する。
- Pager は selected key が一時的に一覧へ存在しないことだけを理由に page 0 へ scroll しない。実際の削除時の adjacent/first 選択は coordinator が明示的に selected key を補正する。
- タブ登録または canonical confirmation が失敗した場合、既存 selection を保持して thread 画面へ遷移せず、既存 Deep Link error notification/consume 経路で現在画面に留まる。新しい UI や文言は追加しない。
- 対象確認前の選択、first-page jump、失敗時 navigation がないことを決定的テストで検証する。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `handle-deep-link`: スレッド Deep Link の遷移を readiness、canonical tab confirmation、選択完了の後に限定し、失敗時の既存 recovery 経路を規定する。
- `tab-selection-source-of-truth`: selected key の一時的不在時は Pager が先頭へ fallback せず現在 page を維持し、実削除時の補正と区別する。

## Impact

- Production: `DeepLinkHandler.kt`、`TabSessionStore.kt`、`ThreadTabsCoordinator.kt` の selection API、`BbsRouteScaffold.kt` の selected-page 導出/effect、`ThreadScaffold.kt` の route 初期化。
- Tests: 新規 Deep Link orchestration unit test、`TabSessionStoreTest.kt`、`ThreadTabsCoordinatorTest.kt`、`BbsRouteScaffoldSelectionTest.kt`、必要に応じて navigation test。
- DB/schema/migration は変更しない。UI component、表示文言、icon、theme、accessibility semantics は追加または変更しない。
- 実装順は `refactor-thread-tab-persistence-consistency` 完了後に本変更とし、逆順または同時の部分実装を行わない。

## 後続統合変更との関係

`refactor-tab-controller-state-machine` は本 change の failure 時非遷移、既存選択保持、registration 重複禁止、caller cancellation の要件を継承する。presentation／canonical observation を registration confirmation とする実装設計は、Controller の明示 command result を待つ設計へ supersede される。本 change は削除・archive せず、履歴要件／テスト資産として維持する。
