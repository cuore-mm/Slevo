## MODIFIED Requirements

### Requirement: NavigationExtensionsの責務限定
システムは共通ナビゲーション関数で、設定値の取得や `5ch.net` から `5ch.io` への正規化を行わないことを SHALL 要求する。共通関数は、呼び出し元から渡された正規化済みrouteを使い、タブ登録、タブ選択、画面遷移のうち呼び出し元が要求した責務だけを実行することを SHALL 要求する。同種別のタブを選択する操作は、既存タブ選択または新規タブ登録のどちらでもNavControllerのback stackを追加してはならないMUST NOT。別種別タブの選択は、画面種別と入口に応じたpush、pop、replaceの履歴操作を許可する。

#### Scenario: 正規化済みrouteをタブ選択関数に渡す
- **WHEN** 呼び出し元が正規化済みrouteをタブ登録・選択関数に渡す
- **THEN** システムは渡されたrouteを変更せず、同じrouteでタブ保証と選択中タブ key 更新を行う

#### Scenario: 正規化済みrouteを画面遷移関数に渡す
- **WHEN** 呼び出し元が正規化済みrouteを画面遷移関数に渡す
- **THEN** システムは渡されたrouteを変更せず、呼び出し元が指定した NavOptions で対象画面種別へ遷移する

#### Scenario: 同種別タブ選択だけの操作を実行する
- **WHEN** ユーザーがタブ一覧シートから現在画面と同じ画面種別の既存タブを選択する
- **THEN** システムは選択中タブ key を更新し、同種別の back stack entry を追加しない

#### Scenario: 同種別の新規タブを登録して選択する
- **WHEN** ユーザーが現在画面と同じ画面種別の未登録タブを開く
- **THEN** システムは対象タブを登録して選択し、同種別の back stack entry を追加しない
