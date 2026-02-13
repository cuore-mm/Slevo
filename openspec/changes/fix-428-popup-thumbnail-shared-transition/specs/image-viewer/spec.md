## ADDED Requirements

### Requirement: 遷移元文脈と一致する shared transition キー
システムは画像ビューア遷移時、遷移元サムネイルが使用した shared transition の表示文脈を引き継ぎ、ビューア側で同一キーを再構築しなければならないMUST。

#### Scenario: ポップアップ起点でも shared transition が成立する
- **WHEN** ユーザーが 2 段目以降のポップアップにある画像サムネイルをタップして画像ビューアを開く
- **THEN** システムは遷移元と同一の文脈情報を使って shared transition キーを構成し、遷移アニメーションを成立させる

### Requirement: shared transition 文脈追加後の既存遷移互換
システムは shared transition 用の文脈情報を追加しても、既存の画像ビューア初期表示契約を維持しなければならないMUST。

#### Scenario: 通常リスト起点の初期表示が変わらない
- **WHEN** ユーザーがスレッド通常リストの画像サムネイルをタップして画像ビューアを開く
- **THEN** システムは従来どおりタップした画像を初期ページとして表示する
