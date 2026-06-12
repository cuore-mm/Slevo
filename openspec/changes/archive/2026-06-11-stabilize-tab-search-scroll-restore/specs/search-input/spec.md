## ADDED Requirements

### Requirement: 検索入力の IME composition 保持
システムは共通検索入力を使う画面において、IME の未確定変換状態を表す `TextFieldValue.composition` を破棄してはならないMUST NOT。システムは検索入力の値を `String` だけで管理せず、入力文字列・選択範囲・composition を含む `TextFieldValue` 相当の状態として保持しなければならないMUST。

#### Scenario: 板画面検索で日本語変換中の文字が即確定されない
- **WHEN** ユーザーが板画面の検索バーで日本語入力を行い、IME の変換候補を選択する前の未確定文字列を入力している
- **THEN** システムは未確定変換範囲を保持し、文字を即確定させずに変換候補選択を継続できる

#### Scenario: スレッド画面検索で日本語変換中の文字が即確定されない
- **WHEN** ユーザーがスレッド画面の検索バーで日本語入力を行い、IME の変換候補を選択する前の未確定文字列を入力している
- **THEN** システムは未確定変換範囲を保持し、文字を即確定させずに変換候補選択を継続できる

#### Scenario: 検索ロジックは入力 text から派生した query を使う
- **WHEN** 検索入力状態が `TextFieldValue` として更新される
- **THEN** システムはフィルタリング、ハイライト、検索解除処理に `searchInputValue.text` から派生した検索クエリを使用する
