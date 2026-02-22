## ADDED Requirements

### Requirement: ポップアップ外タップ判定の全画面適用
システムはレスポップアップ表示中、ポップアップ外タップ判定をスレッド content 領域に限定せず、画面全体に適用しなければならないMUST。bottomBar 領域のタップもポップアップ外タップとして扱わなければならないMUST。

#### Scenario: bottomBar タップで最上位ポップアップが閉じる
- **WHEN** ユーザーがポップアップ表示中に bottomBar 領域をタップする
- **THEN** システムはそのタップをポップアップ外タップとして扱い、最上位ポップアップを閉じる

### Requirement: ポップアップ上位レイヤー描画
システムはレスポップアップを、Scaffold の content と bottomBar の双方を覆える上位レイヤーで描画しなければならないMUST。これにより、画面全体で一貫した外側タップ挙動を提供しなければならないMUST。

#### Scenario: content と bottomBar の双方で外側タップが同一挙動になる
- **WHEN** ユーザーがポップアップ外の content 領域または bottomBar 領域をタップする
- **THEN** システムはどちらの場合も同じ閉じる挙動を実行する
