## 1. Implementation
- [ ] 1.1 Board/Thread の初期化シーケンス（フェーズ順）を確定し、必要な入力/出力を整理する
- [ ] 1.2 BoardViewModel の初期化処理をフェーズ順に整理し、重複初期化ガードと強制再初期化の導線を統一する
- [ ] 1.3 ThreadViewModel の初期化処理を同じフェーズ順に整理し、Board と同じ設計に揃える
- [ ] 1.4 BoardScaffold / ThreadScaffold の初期化呼び出し位置を必要に応じて調整し、責務の一貫性を保つ
- [ ] 1.5 影響範囲の動作確認（Board/Thread の初期表示、更新、タブ復元、ブックマーク表示）
- [ ] 1.6 CI で build + unit tests を実行して結果を確認する
