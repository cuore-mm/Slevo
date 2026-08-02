## ADDED Requirements

### Requirement: Deep Link の明示 command result による遷移
システムは Board／Thread Deep Link の tab registration、persistence、selection を Controller command として実行し、明示 terminal result が成功した場合だけ navigation することを SHALL 要求する。presentation または selected key Flow の観測を command success の代用にしてはならない。

#### Scenario: Board Deep Link 成功
- **WHEN** Board Deep Link の ensure/persistence と selection command が成功 result を返す
- **THEN** システムは target Board へ一度だけ navigation する

#### Scenario: Board persistence failure
- **WHEN** Board Deep Link の targeted repository command が失敗する
- **THEN** command は terminal failure で有限に完了し、navigation せず既存 selection/page を保持し、`Selected(target)` を無期限に待たない

#### Scenario: Thread Deep Link 成功
- **WHEN** Thread Deep Link の readiness、ensure/persistence、selection command が成功 result を返す
- **THEN** システムは registration を重複せず target Thread へ一度だけ navigation する

#### Scenario: caller cancellation
- **WHEN** Deep Link caller が accepted command の完了前に cancel される
- **THEN** navigation と caller 待機は停止するが accepted mutation は Controller ownership で継続し、既存画面の選択を caller 側から変更しない

#### Scenario: Controller failure または no target
- **WHEN** Controller が failure または navigation 不可の no-op result を返す
- **THEN** システムは navigation せず既存 error path と現在の selection/page を維持し、新しい UI 文言を追加しない
