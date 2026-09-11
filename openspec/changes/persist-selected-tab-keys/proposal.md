## Why

板・スレッドの選択中stable keyはActivity-retainedなメモリ内だけにあり、アプリ再起動またはプロセス再生成後は失われて先頭タブへ補正される。このため、再起動後に直前の選択タブを復元できず、選択タブを基準にするPager表示と全画面タブ一覧の初期位置もユーザーの直前状態と一致しない。

## What Changes

- 選択中板タブの正規化済みboard URLと選択中スレッドタブの`ThreadId.value`を、既存のTabs用Preferences DataStoreへ永続化する。
- 初回canonical一覧の確定前に保存keyを一度読み込み、保存keyが有効なら同じkeyを選択した`TabPresentationState`を最初のloaded状態として公開する。
- 保存keyがない、または保存keyがloaded非空一覧に存在しない場合は、先頭ではなく一覧末尾のstable keyへ補正し、その補正結果を永続化する。
- 選択変更と選択タブ削除後の隣接選択を永続化し、最後のタブ削除時は対応する保存keyを削除する。
- 板とスレッドの選択keyを独立して保存し、page indexを選択状態の正本として使用しない。
- selected keyは端末内のセッション継続状態として扱い、既存のバックアップJSON形式には追加しない。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `tab-selection-source-of-truth`: 選択中stable keyを端末内で永続化し、再起動後に復元する要件と、未保存・確定無効時の末尾補正規則を追加する。
- `tab-controller-state-machine`: 初回canonical読込と永続key読込を同期し、最初のloaded presentationで復元または末尾補正を原子的に公開する規則へ変更する。

## Impact

- 永続化: `TabsLocalDataSource.kt`、`TabsLocalDataSourceImpl.kt`、`SlevoPreferenceDataStores.kt`へnullableな板・スレッドselected key APIとPreferences keyを追加する。
- Repository: `TabsRepository.kt`へselected keyの一回読込・保存APIを追加する。
- Coordinator: `BoardTabsCoordinator.kt`、`ThreadTabsCoordinator.kt`の初回bind、選択変更、削除後選択、空一覧処理、確定無効補正を変更する。
- Store/UI: `TabSessionStore.kt`は永続化済みselected keyを従来と同じ正本として公開し、`BbsRouteScaffold.kt`と`TabScreenContent.kt`は既存selected key APIを継続利用する。
- テスト: LocalDataSource、Repository、両Coordinator、TabSessionStore、起動時のPager・タブ一覧初期位置の回帰テストを更新する。
- Preferences DataStoreへの加法的なkey追加のみで、Room schema migration、新規依存関係、バックアップformat変更は行わない。
