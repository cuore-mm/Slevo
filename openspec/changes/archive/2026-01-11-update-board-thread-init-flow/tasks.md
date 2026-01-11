## 1. Implementation
- [x] 1.1 Board/Thread の初期化シーケンス（フェーズ順）を確定し、必要な入力/出力を整理する
- [x] 1.2 BaseViewModel に初期化テンプレートを追加し、BoardViewModel の初期化処理をフェーズ順に整理する
- [x] 1.3 ThreadViewModel の初期化処理を同じテンプレートに合わせ、Board と同じ設計に揃える
- [x] 1.4 BoardScaffold / ThreadScaffold の初期化呼び出し位置を必要に応じて調整し、責務の一貫性を保つ
- [x] 1.5 影響範囲の動作確認（Board/Thread の初期表示、更新、タブ復元、ブックマーク表示）※コードパスで確認
- [x] 1.6 CI で build + unit tests を実行して結果を確認する
