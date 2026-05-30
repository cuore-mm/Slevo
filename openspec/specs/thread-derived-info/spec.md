# thread-derived-info Specification

## Purpose
TBD - created by archiving change centralize-thread-derived-info. Update Purpose after archive.
## Requirements
### Requirement: スレッド派生情報の共通計算
システムはスレッドキーとレス数から、スレッド作成日時と勢いを共通の計算規則で導出しなければならないMUST。派生情報の計算は、subject.txt パース、板一覧表示、スレッド画面、タブ詳細表示で同じ規則を利用しなければならないMUST。

#### Scenario: 有効な epoch thread key から作成日時を導出する
- **WHEN** thread key が数値であり、`1 until THREAD_KEY_THRESHOLD` の範囲に含まれる
- **THEN** システムは thread key を UNIX 秒として扱い、Asia/Tokyo 基準の年月日・時分・曜日を導出する

#### Scenario: 有効な epoch thread key とレス数から勢いを導出する
- **WHEN** thread key が数値であり、`1 until THREAD_KEY_THRESHOLD` の範囲に含まれ、レス数が 1 以上である
- **THEN** システムはレス数を経過日数で割った値を勢いとして導出する

#### Scenario: 無効な thread key ではデフォルト派生情報を返す
- **WHEN** thread key が数値に変換できない、または `1 until THREAD_KEY_THRESHOLD` の範囲外である
- **THEN** システムは作成日時を `ThreadDate(0, 0, 0, 0, 0, "")`、勢いを `0.0` として扱う

#### Scenario: レス数が0以下の場合は勢いを0にする
- **WHEN** thread key が有効な epoch thread key であっても、レス数が 0 以下である
- **THEN** システムは作成日時を導出し、勢いを `0.0` として扱う

### Requirement: 時刻基準を指定できる勢い計算
システムは勢いを計算する際、呼び出し側が現在時刻に相当する UNIX 秒を指定できなければならないMUST。時刻基準が指定されない場合は、表示時点の現在時刻を使用しなければならないMUST。

#### Scenario: 呼び出し側が時刻基準を指定する
- **WHEN** 呼び出し側が `nowSeconds` を指定して勢いを計算する
- **THEN** システムは指定された `nowSeconds` と thread key の差分から経過日数を算出する

#### Scenario: 時刻基準を省略する
- **WHEN** 呼び出し側が時刻基準を指定せずに勢いを計算する
- **THEN** システムは表示時点の現在時刻を時刻基準として使用する

### Requirement: 派生情報は永続化しない
システムはスレッド作成日時と勢いを、開いているタブ状態や共通スレッド状態の永続項目として保存してはならないMUST NOT。派生情報は thread key、レス数、時刻基準から必要時に計算しなければならないMUST。

#### Scenario: タブ状態保存時に派生情報を保存しない
- **WHEN** システムが開いているスレッドタブの状態を保存する
- **THEN** システムは作成日時や勢いをタブ固有状態として保存しない

#### Scenario: 共通スレッド状態保存時に派生情報を保存しない
- **WHEN** システムが共通スレッド状態を保存する
- **THEN** システムは作成日時や勢いを共通スレッド状態の正本として保存しない

