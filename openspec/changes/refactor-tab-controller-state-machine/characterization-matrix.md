# Characterization matrix

既存回帰テストを削除・弱化せず、Controller 移行後も確認する対応表。

| 要件 | 既存テスト資産 |
| --- | --- |
| loading / loaded-empty | `ThreadTabsCoordinatorTest.ensureThreadTab_waitsForInitialSnapshotBeforeDatabaseWrite`、`loadedEmpty_allowsMutationAfterInitialEmptyEmission` |
| selection repair / PendingMissing | `BoardTabsCoordinatorTest` の close 系、`ThreadTabsCoordinatorTest.closeSelectedThreadTab_publishesPendingMissingUntilCanonicalConfirmation`、`BbsRouteScaffoldSelectionTest` |
| metadata merge | `ThreadTabsCoordinatorTest.ensureConfirmation_requiresMergedMetadataMatch`、`projection_placeholderEnsurePreservesResolvedMetadataAndTabFields` |
| retained close / teardown | `TabSessionStoreTest.requestCloseThreadTab_survivesCallerCancellationAndConfirmsCanonicalDeletion`、`close_cancelsRetainedCloseAtStoreLifetimeBoundary` |
| Deep Link ordering / failure | `DeepLinkHandlerTest` の Board／Thread ordering、selection failure、registration failure、cancellation |
| caller cancellation | `ThreadTabsCoordinatorTest` の readiness、repository、commit 境界 cancellation 群 |

本 change ではこれらのテスト名と期待値を削除せず、pure primitive と Controller の追加テストを横に追加する。
